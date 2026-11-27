import hashlib
import math

from verify import certcheck
from verify.reveal import Refused, check_certificate, check_reveal

ADMISSIBLE = "rhworld.modulus.admissible"
G2 = "rhworld.g2.least-jacobi-nonresidue"
NOT_PROVED = ("GRH is not checked by this certificate", "G1 holds before reveal only by theorem")


def need(ok, field):
    if not ok:
        raise Refused(f"bad field {field}")


def lookup(node, dotted):
    # contract ids hold dots so each step takes the longest key that matches
    while dotted:
        if not isinstance(node, dict):
            return None
        keys = [k for k in node if dotted == k or dotted.startswith(k + ".")]
        if not keys:
            return None
        key = max(keys, key=len)
        node, dotted = node[key], dotted[len(key) + 1:]
    return node


def check_header(cert):
    h = cert.get("header", {})
    need(h.get("schema") == "rhworld.cert/1", "header.schema")
    n = int(h.get("params", {}).get("n", "0"))
    need(h["params"].get("k") == n.bit_length(), "header.params.k")
    return n


def check_admissible(n, rec):
    b = certcheck.budget(n.bit_length())
    s = math.isqrt(b - 1) + 1
    need(rec.get("probable_prime_test") == "mr25-fixed-bases", f"recomputed.{ADMISSIBLE}.probable_prime_test")
    need(rec.get("trial_division_limit") == max(b, s * s), f"recomputed.{ADMISSIBLE}.trial_division_limit")
    need(rec.get("outcome") == "ok" and certcheck.admissible(n) == "ok", f"recomputed.{ADMISSIBLE}.outcome")


def check_g2(n, rec):
    b = certcheck.budget(n.bit_length())
    field = f"recomputed.{G2}"
    need(rec.get("bound") == b, f"{field}.bound")
    need(rec.get("budget_exact") is True, f"{field}.budget_exact")
    need(rec.get("claim_scope") == "prefix 1..B", f"{field}.claim_scope")
    try:
        certcheck.check_g2(n, rec.get("witness"))
    except (Refused, TypeError):
        need(False, f"{field}.witness")
    need(rec.get("steps") == rec["witness"], f"{field}.steps")


def check_theorem(cert):
    for cid, entry in cert.get("theorem", {}).items():
        pointers = entry.get("hypothesis_instance", {})
        need(pointers and all(lookup(cert, v) is not None for v in pointers.values()),
             f"theorem.{cid}.hypothesis_instance")


def check_deferred(n, d):
    reveal = d.get("reveal")
    if reveal is None:
        need(d.get("factors_status") == "unrevealed", "deferred.factors_status")
        return
    p = int(reveal.get("p", "0"))
    try:
        small, big = check_reveal(n, p)
    except Refused:
        need(False, "deferred.reveal.p")
    need(p == small, "deferred.reveal.p")
    evidence = reveal.get("evidence", {})
    try:
        proved = all(check_certificate(f, evidence[str(f)]) == "proved" for f in (small, big))
    except (Refused, KeyError):
        proved = False
    need(d.get("factors_status") == ("proved" if proved else "bpsw-probable"), "deferred.factors_status")


def verify(raw):
    cert = certcheck.parse(raw)
    n = check_header(cert)
    rec = cert.get("recomputed", {})
    check_admissible(n, rec.get(ADMISSIBLE, {}))
    check_g2(n, rec.get(G2, {}))
    body = certcheck.dump({"header": cert["header"], "recomputed": rec})
    need(cert.get("world_id") == hashlib.sha256(body).hexdigest(), "world_id")
    check_theorem(cert)
    need(all(s in cert.get("not_proved", []) for s in NOT_PROVED), "not_proved")
    check_deferred(n, cert.get("deferred", {}))
    return "ok"
