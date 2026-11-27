import unittest

from verify import certcheck
from verify.certcheck import Refused

P521 = 2 ** 521 - 1
P607 = 2 ** 607 - 1


class Canonical(unittest.TestCase):
    def refused(self, raw, reason):
        with self.assertRaises(Refused) as cm:
            certcheck.parse(raw)
        self.assertEqual(str(cm.exception), f"refused: {reason}")

    def test_round_trip(self):
        raw = b'{"a":[1,"x",true,null],"b":{"c":-5}}'
        self.assertEqual(certcheck.parse(raw), {"a": [1, "x", True, None], "b": {"c": -5}})
        self.assertEqual(certcheck.dump(certcheck.parse(raw)), raw)

    def test_rejects_noncanonical(self):
        self.refused(b'{"b":1,"a":2}', "keys not sorted")
        self.refused(b'{"a":1,"a":2}', "duplicate key")
        self.refused(b'{"a": 1}', "not canonical")
        self.refused(b'{"a":1.5}', "not canonical")
        self.refused(b'{"a":01}', "not canonical")
        self.refused(b'{"a":9007199254740993}', "integer above 2^53 must be a string")
        self.refused(b'{"a":1}x', "not canonical")

    def test_sort_is_by_utf16_units(self):
        self.assertEqual(certcheck.dump({"\U0001f600": 1, "￿": 2}), '{"\U0001f600":1,"￿":2}'.encode())


class Recompute(unittest.TestCase):
    def test_exact_budget(self):
        self.assertEqual(certcheck.budget(1024), 503791)
        self.assertEqual(certcheck.budget(96893), 4510614664)

    def test_g2_witness_is_least(self):
        n = P521 * P607
        w = certcheck.least_jacobi_minus_one(n, certcheck.budget(n.bit_length()))
        self.assertEqual(certcheck.check_g2(n, w), w)
        second = next(x for x in range(w + 1, 10 ** 4) if certcheck.kronecker_odd(x, n) == -1)
        with self.assertRaises(Refused) as cm:
            certcheck.check_g2(n, second)
        self.assertEqual(str(cm.exception), "refused: witness is not the least")

    def test_g2_claim_must_be_a_nonresidue(self):
        n = P521 * P607
        w = certcheck.least_jacobi_minus_one(n, 1000)
        with self.assertRaises(Refused) as cm:
            certcheck.check_g2(n, 1)
        self.assertEqual(str(cm.exception), "refused: witness symbol is not -1")
        self.assertGreater(w, 1)

    def test_admissible(self):
        self.assertEqual(certcheck.admissible(P521 * P607), "ok")
        for n, reason in [(P521 * P607 + 1, "modulus too small or even"), (P607 * P607, "jacobi character is principal"),
                          (P521 * 503, "modulus too small or even"), (P521 * P607 * 503, "small factor")]:
            self.assertEqual(certcheck.admissible(n), f"refused: {reason}", n)


if __name__ == "__main__":
    unittest.main()
