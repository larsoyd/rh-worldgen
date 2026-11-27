import unittest

from verify import seeds
from verify.reveal import Refused, bpsw, check_reveal

P521 = 2 ** 521 - 1
P607 = 2 ** 607 - 1
CARMICHAEL = [561, 41041, 825265, 321197185, 5394826801]
STRONG_PSEUDOPRIMES_BASE2 = [2047, 3277, 4033, 4681, 8321, 3215031751]


class Primality(unittest.TestCase):
    def test_small_range_matches_trial_division(self):
        primes = [x for x in range(2, 20000) if all(x % d for d in range(2, int(x ** 0.5) + 1))]
        self.assertEqual([x for x in range(20000) if bpsw(x)], primes)

    def test_mersenne_primes_and_their_neighbours(self):
        self.assertTrue(bpsw(P521) and bpsw(P607))
        self.assertFalse(bpsw(P521 + 2) or bpsw(P521 * P607) or bpsw(P607 ** 2))

    def test_pseudoprimes_are_composite(self):
        for n in CARMICHAEL + STRONG_PSEUDOPRIMES_BASE2:
            self.assertFalse(bpsw(n), n)


class Reveal(unittest.TestCase):
    def refused(self, n, p, reason):
        with self.assertRaises(Refused) as cm:
            check_reveal(n, p)
        self.assertEqual(str(cm.exception), f"refused: {reason}")

    def test_accepts_two_distinct_primes_either_order(self):
        self.assertEqual(check_reveal(P521 * P607, P607), (P521, P607))
        self.assertEqual(check_reveal(P521 * P607, P521), (P521, P607))

    def test_cube(self):
        s = seeds.cube(1024, 1)
        self.refused(s["n"], s["factors"][0], "cofactor not prime")

    def test_square_cofactor(self):
        s = seeds.square_cofactor(1024, 1)
        self.refused(s["n"], s["factors"][0], "cofactor not prime")

    def test_square(self):
        self.refused(P607 * P607, P607, "factors equal")

    def test_not_a_divisor(self):
        self.refused(P521 * P607, P521 + 2, "revealed factor does not divide n")

    def test_composite_revealed_factor(self):
        self.refused(P521 * P607 * 3, 3 * P521, "revealed factor not prime")

    def test_trivial_factor(self):
        self.refused(P521 * P607, 1, "revealed factor does not divide n")
        self.refused(P521 * P607, P521 * P607, "revealed factor does not divide n")


if __name__ == "__main__":
    unittest.main()
