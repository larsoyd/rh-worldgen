import hashlib
import math
import re

from verify import certcheck
from verify.gate_bench import primes_upto
from verify.reveal import Refused, bpsw, check_certificate, kronecker_odd

CONTRACTS = [{"id": "RHW-C1", "version": 1}, {"id": "RHW-C3", "version": 1}]
LEAN = "LFunction_ne_zero_of_half_lt_re"
PAPER = "Lamzouri, Li, Soundararajan, Math. Comp. 84 (2015), arXiv 1309.3595v3, Thm 1.1 (2)"
HYPOTHESES = {
    "RHW-C1": ["recomputed.c1.bits", "recomputed.c1.trial_limit", "recomputed.c1.square", "deferred.reveal.factors_status"],
    "RHW-C3": ["recomputed.c3.lcm_bits", "recomputed.c3.trial_limit", "recomputed.c3.independent",
               "deferred.reveal.factors_status"],
}
UNSHIPPED_DOC = re.compile(r"[\w./-]+\.md\b")
NOT_PROVED = [
    "GRH is not checked by this certificate. It is the hypothesis of the cited paper, machine-checked only as the Lean "
    "statement named in theorem",
    "the explicit bound (log q)^2 is a published result and is not machine-checked",
    "claims about the hidden factors hold only after a reveal that passes the reveal check",
    "probable prime means the named fixed-base test passed, not a proof, unless factors_status is proved",
    "nothing is claimed about any window other than the prefix 1..B",
    "hiding the factors rests on the hardness of factoring, not on RH",
]


def need(ok, field):
    if not ok:
        raise Refused(f"bad field {field}")


def world_id(cert):
    return hashlib.sha256(certcheck.dump({"header": cert["header"], "recomputed": cert["recomputed"]})).hexdigest()


def least_minus_one(m, bound):
    return next((x for x in range(1, bound + 1) if kronecker_odd(x, m) == -1), None)


def basis(layers, bound):
    kept, vectors, reduced = [], [], []
    primes = primes_upto(bound)
    for count, l in enumerate(primes, 1):
        v = sum(1 << i for i, m in enumerate(layers) if kronecker_odd(l, m) == -1)
        r = v
        for b in reduced:
            r = min(r, r ^ b)
        if r:
            reduced.append(r)
            kept.append(l)
            vectors.append(v)
            if len(kept) == len(layers):
                return {"primes": kept, "vectors": vectors, "rank": len(kept), "steps": count}
    return {"primes": kept, "vectors": vectors, "rank": len(kept), "steps": len(primes)}


def check_witness(obj, m, bound, field):
    w = least_minus_one(m, bound)
    need(obj == {"bound": bound, "steps": w, "witness": w}, field)


def check_c1(n, c1, b):
    side = math.isqrt(b - 1) + 1
    limit = max(b, side * side)
    expect = {"bits": n.bit_length(), "min_bits": 1024, "budget": b, "side": side, "trial_limit": limit,
              "square": math.isqrt(n) ** 2 == n, "probable_prime": certcheck.mr25(n),
              "primes_tested": len(primes_upto(limit))}
    for key, value in expect.items():
        need(c1.get(key) == value, f"recomputed.c1.{key}")
    need(certcheck.admissible(n) == "ok", "recomputed.c1")
    check_witness(c1.get("g2"), n, b, "recomputed.c1.g2")


def check_c3(c3, layers, b, field):
    need(c3.get("bound") == b, f"{field}.bound")
    need(c3.get("trial_limit") == b, f"{field}.trial_limit")
    need(c3.get("primes_tested") == len(primes_upto(b)), f"{field}.primes_tested")
    found = basis(layers, b)
    for key, value in found.items():
        need(c3.get("basis", {}).get(key) == value, f"{field}.basis.{key}")
    if "independent" in c3:
        need(c3["independent"] is (found["rank"] == len(layers)), f"{field}.independent")
    return found


def resolve(cert, dotted):
    node = cert
    for key in dotted.split("."):
        if not isinstance(node, dict) or key not in node:
            return False
        node = node[key]
    return True


def check_theorem(cert):
    for c in CONTRACTS:
        t = cert.get("theorem", {}).get(c["id"], {})
        field = f"theorem.{c['id']}"
        need(t.get("claim_scope") == "prefix 1..B", f"{field}.claim_scope")
        refs = [link.get("ref") for link in t.get("chain", [])]
        need(refs[:2] == [LEAN, PAPER], f"{field}.chain")
        texts = [t.get("claim", "")] + [v for link in t.get("chain", []) for v in link.values()]
        docs = {m for text in texts for m in UNSHIPPED_DOC.findall(text)} - {"README.md"}
        need(not docs, f"{field}.chain")
        need([h.get("checked_by") for h in t.get("hypotheses", [])] == HYPOTHESES[c["id"]], f"{field}.hypotheses")
        for h in t.get("hypotheses", []):
            pending = h["checked_by"].startswith("deferred.reveal.") and cert["deferred"]["reveal"] is None
            need(pending or resolve(cert, h["checked_by"]), f"{field}.hypotheses")


def check_reveal(n, r, b):
    p, q = int(r.get("p", "0")), int(r.get("q", "0"))
    need(p * q == n and p < q, "deferred.reveal.p")
    need(bpsw(p), "deferred.reveal.p")
    need(bpsw(q), "deferred.reveal.q")
    need(r.get("primality_test") == "mr25-fixed-bases", "deferred.reveal.primality_test")
    status = "mr25-probable"
    if "proofs" in r:
        try:
            ok = all(check_certificate(f, r["proofs"][k]) == "proved" for k, f in (("p", p), ("q", q)))
        except (Refused, KeyError, TypeError):
            ok = False
        need(ok, "deferred.reveal.proofs")
        status = "proved"
    need(r.get("factors_status") == status, "deferred.reveal.factors_status")
    check_witness(r.get("g1", {}).get("p"), p, b, "deferred.reveal.g1.p")
    check_witness(r.get("g1", {}).get("q"), q, b, "deferred.reveal.g1.q")
    hidden = r.get("c3_hidden", {})
    need(hidden.get("layers") == ["p", "q"], "deferred.reveal.c3_hidden.layers")
    found = check_c3(hidden, [p, q], b, "deferred.reveal.c3_hidden")
    need(found["rank"] == 2, "deferred.reveal.c3_hidden.basis.rank")


def verify(raw):
    # one trailing newline is file framing and not part of the canonical bytes
    cert = certcheck.parse(raw[:-1] if raw.endswith(b"\n") else raw)
    h = cert.get("header", {})
    need(h.get("schema") == "rhworld.cert/1", "header.schema")
    n = int(h.get("params", {}).get("n", "0"))
    need(h["params"].get("bits") == n.bit_length(), "header.params.bits")
    need(h.get("contracts") == CONTRACTS, "header.contracts")
    rec = cert.get("recomputed", {})
    need(rec.get("budget_exact") is True, "recomputed.budget_exact")
    need(rec.get("primality_test") == "mr25-fixed-bases", "recomputed.primality_test")
    b = certcheck.budget(n.bit_length())
    check_c1(n, rec.get("c1", {}), b)
    c3 = rec.get("c3", {})
    need(c3.get("layers") == ["n"] and c3.get("lcm_bits") == n.bit_length(), "recomputed.c3.layers")
    check_c3(c3, [n], b, "recomputed.c3")
    check_theorem(cert)
    need(cert.get("not_proved") == NOT_PROVED, "not_proved")
    if cert.get("deferred", {}).get("reveal") is not None:
        check_reveal(n, cert["deferred"]["reveal"], b)
    need(cert.get("world_id") == world_id(cert), "world_id")
    return world_id(cert)
