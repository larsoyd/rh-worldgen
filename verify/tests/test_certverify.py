import copy
import hashlib
import math
import random
import unittest

from rhworld.gm import forced_moduli
from rhworld.arith import is_probable_prime
from verify import certcheck
from verify.certverify import ADMISSIBLE, G2, NOT_PROVED, verify
from verify.reveal import Refused, build_certificate

P521 = 2 ** 521 - 1
P607 = 2 ** 607 - 1


def provable_prime(bits, seed):
    m, ls = forced_moduli(bits)
    rng = random.Random(seed)
    lo, hi = (3 << (bits - 2)) // m + 1, (1 << bits) // m - 1
    p = next(c for c in (1 + m * rng.randrange(lo, hi) for _ in range(100000)) if is_probable_prime(c))
    return p, build_certificate(p, [(2, 3)] + [(l, 1) for l in ls])


def sample(n, reveal=None):
    k = n.bit_length()
    b = certcheck.budget(k)
    s = math.isqrt(b - 1) + 1
    w = certcheck.least_jacobi_minus_one(n, b)
    header = {"schema": "rhworld.cert/1", "engine": "test", "params": {"n": str(n), "k": k},
              "contracts": [{"id": ADMISSIBLE, "version": 1}, {"id": G2, "version": 1}]}
    recomputed = {
        ADMISSIBLE: {"outcome": "ok", "trial_division_limit": max(b, s * s), "square_check": "not a square",
                     "probable_prime_test": "mr25-fixed-bases", "probable_prime": False},
        G2: {"bound": b, "witness": w, "steps": w, "budget_exact": True, "claim_scope": "prefix 1..B"},
    }
    theorem = {G2: {"lean_name": "LFunction_ne_zero_of_half_lt_re", "paper_theorem": "LLS 2015 Thm 1.1 (2)",
                    "hypothesis_instance": {"no prime factor below (ln n)^2": f"recomputed.{ADMISSIBLE}.trial_division_limit",
                                            "H = ker (./n) proper": f"recomputed.{ADMISSIBLE}.square_check"}}}
    cert = {"header": header, "recomputed": recomputed, "theorem": theorem,
            "deferred": {"factors_status": "unrevealed", "reveal": reveal}, "not_proved": list(NOT_PROVED)}
    cert["world_id"] = hashlib.sha256(certcheck.dump({"header": header, "recomputed": recomputed})).hexdigest()
    return cert


class Verify(unittest.TestCase):
    def refused(self, cert, reason):
        with self.assertRaises(Refused) as cm:
            verify(certcheck.dump(cert))
        self.assertEqual(str(cm.exception), f"refused: {reason}")

    def test_accepts_sample(self):
        self.assertEqual(verify(certcheck.dump(sample(P521 * P607))), "ok")

    def test_each_mutated_field_is_named(self):
        base = sample(P521 * P607)
        cases = [
            (("header", "schema"), "rhworld.cert/2", "header.schema"),
            (("header", "params", "k"), 1129, "header.params.k"),
            (("recomputed", G2, "bound"), 1, f"recomputed.{G2}.bound"),
            (("recomputed", G2, "witness"), 2, f"recomputed.{G2}.witness"),
            (("recomputed", G2, "steps"), 99, f"recomputed.{G2}.steps"),
            (("recomputed", ADMISSIBLE, "trial_division_limit"), 1000, f"recomputed.{ADMISSIBLE}.trial_division_limit"),
            (("recomputed", ADMISSIBLE, "probable_prime_test"), "bpsw", f"recomputed.{ADMISSIBLE}.probable_prime_test"),
            (("recomputed", G2, "claim_scope"), "everywhere", f"recomputed.{G2}.claim_scope"),
            (("world_id",), "0" * 64, "world_id"),
            (("deferred", "factors_status"), "proved", "deferred.factors_status"),
        ]
        for path, value, field in cases:
            cert = copy.deepcopy(base)
            node = cert
            for key in path[:-1]:
                node = node[key]
            node[path[-1]] = value
            if path[0] == "recomputed" or path[0] == "header":
                if field != "world_id":
                    cert["world_id"] = hashlib.sha256(certcheck.dump({"header": cert["header"],
                                                                      "recomputed": cert["recomputed"]})).hexdigest()
            with self.subTest(field=field):
                self.refused(cert, f"bad field {field}")

    def test_dangling_hypothesis_pointer(self):
        cert = sample(P521 * P607)
        cert["theorem"][G2]["hypothesis_instance"]["H = ker (./n) proper"] = "recomputed.nowhere"
        self.refused(cert, f"bad field theorem.{G2}.hypothesis_instance")

    def test_missing_disclaimer(self):
        cert = sample(P521 * P607)
        cert["not_proved"] = cert["not_proved"][1:]
        self.refused(cert, "bad field not_proved")

    def test_inadmissible_modulus(self):
        cert = sample(P607 * P607 * 3)
        self.refused(cert, f"bad field recomputed.{ADMISSIBLE}.outcome")


class Reveal(unittest.TestCase):
    def test_proved_reveal(self):
        (p, cp), (q, cq) = provable_prime(512, 1), provable_prime(512, 2)
        reveal = {"p": str(min(p, q)), "evidence": {str(p): cp, str(q): cq}}
        cert = sample(p * q, reveal)
        cert["deferred"]["factors_status"] = "proved"
        self.assertEqual(verify(certcheck.dump(cert)), "ok")
        cert["deferred"]["factors_status"] = "bpsw-probable"
        with self.assertRaises(Refused) as cm:
            verify(certcheck.dump(cert))
        self.assertEqual(str(cm.exception), "refused: bad field deferred.factors_status")

    def test_reveal_without_evidence_is_bpsw(self):
        cert = sample(P521 * P607, {"p": str(P521), "evidence": {}})
        cert["deferred"]["factors_status"] = "bpsw-probable"
        self.assertEqual(verify(certcheck.dump(cert)), "ok")

    def test_reveal_of_non_canonical_factor(self):
        cert = sample(P521 * P607, {"p": str(P607), "evidence": {}})
        cert["deferred"]["factors_status"] = "bpsw-probable"
        with self.assertRaises(Refused) as cm:
            verify(certcheck.dump(cert))
        self.assertEqual(str(cm.exception), "refused: bad field deferred.reveal.p")


if __name__ == "__main__":
    unittest.main()
