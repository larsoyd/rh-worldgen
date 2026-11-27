import copy
import json
import random
import unittest
from pathlib import Path

from rhworld.arith import is_probable_prime
from rhworld.gm import forced_moduli
from verify import certcheck
from verify.certverify import verify, world_id
from verify.reveal import Refused, build_certificate

DATA = Path(__file__).parent / "data"
SAMPLES = ("good-n-public.json", "good-n-revealed.json", "both-rigged-1024-0-revealed.json", "random-1024-public.json",
           "rigged-1024-revealed.json")


def load(name):
    return json.loads((DATA / name).read_bytes())


def provable_prime(bits, seed):
    m, ls = forced_moduli(bits)
    rng = random.Random(seed)
    lo, hi = (3 << (bits - 2)) // m + 1, (1 << bits) // m - 1
    p = next(c for c in (1 + m * rng.randrange(lo, hi) for _ in range(100000)) if is_probable_prime(c))
    return p, build_certificate(p, [(2, 3)] + [(l, 1) for l in ls])


def set_path(cert, path, value):
    node = cert
    for key in path[:-1]:
        node = node[key]
    node[path[-1]] = value


class Samples(unittest.TestCase):
    def test_engine_samples_verify(self):
        for name in SAMPLES:
            raw = (DATA / name).read_bytes()
            self.assertEqual(verify(raw), world_id(certcheck.parse(raw.rstrip(b"\n"))), name)

    def test_world_id_changes_with_n(self):
        a, b = (certcheck.parse((DATA / f).read_bytes().rstrip(b"\n")) for f in ("random-1024-public.json", "rigged-1024-revealed.json"))
        self.assertNotEqual(world_id(a), world_id(b))


class Mutations(unittest.TestCase):
    CASES = [
        (("header", "schema"), "rhworld.cert/2"),
        (("header", "params", "bits"), 1025),
        (("header", "contracts"), [{"id": "RHW-C1", "version": 2}, {"id": "RHW-C3", "version": 1}]),
        (("recomputed", "budget_exact"), False),
        (("recomputed", "primality_test"), "bpsw"),
        (("recomputed", "c1", "budget"), 503792),
        (("recomputed", "c1", "side"), 709),
        (("recomputed", "c1", "trial_limit"), 503791),
        (("recomputed", "c1", "primes_tested"), 41839),
        (("recomputed", "c1", "square"), True),
        (("recomputed", "c1", "probable_prime"), True),
        (("recomputed", "c1", "min_bits"), 512),
        (("recomputed", "c1", "g2", "witness"), 359),
        (("recomputed", "c1", "g2", "steps"), 348),
        (("recomputed", "c1", "g2", "bound"), 1000),
        (("recomputed", "c3", "basis", "primes"), [359]),
        (("recomputed", "c3", "basis", "steps"), 71),
        (("recomputed", "c3", "basis", "rank"), 2),
        (("recomputed", "c3", "independent"), False),
        (("deferred", "reveal", "factors_status"), "proved"),
        (("deferred", "reveal", "g1", "p", "witness"), 349),
        (("deferred", "reveal", "g1", "q", "steps"), 1),
        (("deferred", "reveal", "c3_hidden", "basis", "vectors"), [1, 2]),
        (("deferred", "reveal", "c3_hidden", "basis", "primes"), [349]),
        (("theorem", "RHW-C1", "claim_scope"), "everywhere"),
        (("theorem", "RHW-C1", "hypotheses", 1, "checked_by"), "recomputed.c1.nothing"),
        (("theorem", "RHW-C3", "chain", 1, "ref"), "Bach 1990"),
        (("theorem", "RHW-C3", "chain", 2, "ref"), "derivation in NOTES.md"),
        (("theorem", "RHW-C1", "chain", 2, "statement"), "see docs/proof.md"),
        (("theorem", "RHW-C3", "hypotheses", 1, "checked_by"), "recomputed.c3.bound"),
        (("recomputed", "c3", "trial_limit"), 1000),
        (("recomputed", "c3", "primes_tested"), 41815),
        (("deferred", "reveal", "c3_hidden", "trial_limit"), 1000),
        (("deferred", "reveal", "c3_hidden", "primes_tested"), 1),
        (("world_id",), "0" * 64),
    ]

    def test_each_mutated_field_is_named(self):
        base = load("rigged-1024-revealed.json")
        for path, value in self.CASES:
            cert = copy.deepcopy(base)
            set_path(cert, path, value)
            field = ".".join(str(k) for k in path[:3] if not isinstance(k, int))
            if path[0] == "theorem":
                field = ".".join(path[:2])
            with self.subTest(path=path):
                with self.assertRaises(Refused) as cm:
                    verify(certcheck.dump(cert))
                self.assertTrue(str(cm.exception).startswith(f"refused: bad field {field}"), (str(cm.exception), field))

    def test_swapped_factors(self):
        cert = load("rigged-1024-revealed.json")
        r = cert["deferred"]["reveal"]
        r["p"], r["q"] = r["q"], r["p"]
        r["g1"]["p"], r["g1"]["q"] = r["g1"]["q"], r["g1"]["p"]
        with self.assertRaises(Refused) as cm:
            verify(certcheck.dump(cert))
        self.assertEqual(str(cm.exception), "refused: bad field deferred.reveal.p")

    def test_engine_version_is_informational(self):
        cert = load("rigged-1024-revealed.json")
        cert["engine_version"] = "9.9.9"
        self.assertTrue(verify(certcheck.dump(cert)))

    def test_dropped_disclaimer(self):
        cert = load("random-1024-public.json")
        cert["not_proved"] = cert["not_proved"][:-1]
        with self.assertRaises(Refused) as cm:
            verify(certcheck.dump(cert))
        self.assertEqual(str(cm.exception), "refused: bad field not_proved")


class Basis(unittest.TestCase):
    def test_dependent_vectors_are_skipped(self):
        from verify.certverify import basis, kronecker_odd
        from verify.gate_bench import primes_upto
        for p, q in [(1019, 1031), (1009, 1013), (2003, 2011)]:
            span, kept = {0}, []
            for l in primes_upto(500):
                v = (kronecker_odd(l, p) == -1) | (kronecker_odd(l, q) == -1) << 1
                if v not in span:
                    span |= {v ^ s for s in span}
                    kept.append(l)
                if len(kept) == 2:
                    break
            self.assertEqual(basis([p, q], 500)["primes"], kept, (p, q))


class Proved(unittest.TestCase):
    def test_proofs_decide_status(self):
        cert = load("rigged-1024-revealed.json")
        (p, cp), (q, cq) = provable_prime(512, 1), provable_prime(512, 2)
        good = verify(certcheck.dump(cert))
        self.assertTrue(good)
        r = cert["deferred"]["reveal"]
        r["proofs"] = {"p": cp, "q": cq}
        with self.assertRaises(Refused) as cm:
            verify(certcheck.dump(cert))
        self.assertEqual(str(cm.exception), "refused: bad field deferred.reveal.proofs")


if __name__ == "__main__":
    unittest.main()
