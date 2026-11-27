import math
import unittest

import mpmath

from rhworld.fog import Refused, budget
from rhworld.reveal import exact_budget, verify_reveal

P521 = 2 ** 521 - 1
P607 = 2 ** 607 - 1
P127 = 2 ** 127 - 1


class ExactBudget(unittest.TestCase):
    def test_float_disagreements_fixed(self):
        for k in (96893, 167966, 193786):
            with mpmath.workdps(80):
                exact = int(mpmath.floor((k * mpmath.log(2)) ** 2))
            self.assertEqual(exact_budget(k), exact, k)
            self.assertEqual(budget(2 ** (k - 1) + 1), exact + 1, k)

    def test_known_values(self):
        self.assertEqual(exact_budget(1024), 503791)
        self.assertEqual(exact_budget(2048), 2015165)

    def test_matches_float_where_float_is_safe(self):
        for k in range(1000, 3000):
            v = (k * math.log(2)) ** 2
            if abs(v - round(v)) > 1e-6:
                self.assertEqual(exact_budget(k), math.floor(v), k)


class Reveal(unittest.TestCase):
    def test_accepts_two_distinct_primes_in_canonical_order(self):
        self.assertEqual(verify_reveal(P521 * P607, P607), (P521, P607))

    def test_refusals(self):
        cases = [
            (P521 * P607, P127, "factor does not divide"),
            (P521 * P607, 1, "factor does not divide"),
            (P521 * P607, P521 * P607, "factor does not divide"),
            (P127 ** 3, P127, "cofactor not prime"),
            (P127 * P521 ** 2, P127, "cofactor not prime"),
            (P127 * P521 ** 2, P521, "cofactor not prime"),
            (P521 ** 2, P521, "equal factors"),
            (P127 * P521 * P607, P127 * P521, "factor not prime"),
        ]
        for n, p, reason in cases:
            with self.assertRaises(Refused) as ctx:
                verify_reveal(n, p)
            self.assertEqual(str(ctx.exception), f"refused: {reason}", (n.bit_length(), p.bit_length()))


if __name__ == "__main__":
    unittest.main()
