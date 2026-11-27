import math
import unittest

from rhworld.arith import jacobi
from rhworld.fog import side
from rhworld.gm import forced_bound
from verify import seeds

BITS = 1024


def least_minus_one(n, limit):
    return next((x for x in range(1, limit + 1) if jacobi(x, n) == -1), None)


class Seeds(unittest.TestCase):
    def test_cube_has_visible_equal_to_hidden(self):
        s = seeds.cube(BITS, 1)
        p = s["factors"][0]
        self.assertEqual(s["n"], p ** 3)
        self.assertGreaterEqual(s["n"].bit_length(), BITS)
        self.assertTrue(all(jacobi(x, s["n"]) == jacobi(x, p) for x in range(1, 3001)))

    def test_square_cofactor_has_visible_equal_to_hidden(self):
        s = seeds.square_cofactor(BITS, 1)
        p, r = s["factors"]
        self.assertEqual(s["n"], p * r * r)
        self.assertEqual(s["n"].bit_length(), BITS)
        self.assertTrue(all(jacobi(x, s["n"]) == jacobi(x, p) for x in range(1, 3001)))

    def test_factor_just_above_trial_bound(self):
        s = seeds.above_trial_bound(BITS, 1)
        small, big = s["factors"]
        bound = side(s["n"]) ** 2
        self.assertEqual(s["n"].bit_length(), BITS)
        self.assertGreater(small, bound)
        self.assertLess(small, bound + 1000)
        self.assertEqual(small * big, s["n"])

    def test_near_square_falls_to_fermat(self):
        s = seeds.near_square(BITS, 1)
        n = s["n"]
        a = math.isqrt(n) + 1
        b = math.isqrt(a * a - n)
        self.assertEqual(b * b, a * a - n)
        self.assertEqual(sorted(s["factors"]), sorted([a - b, a + b]))

    def test_both_rigged_pushes_the_visible_witness(self):
        s = seeds.both_rigged(BITS, 1)
        p, q = s["factors"]
        self.assertEqual(p * q, s["n"])
        self.assertGreater(least_minus_one(s["n"], 5000), forced_bound(BITS // 2))

    def test_seeds_are_deterministic(self):
        for make in seeds.ALL.values():
            self.assertEqual(make(BITS, 3), make(BITS, 3), make.__name__)
        self.assertNotEqual(seeds.cube(BITS, 3)["n"], seeds.cube(BITS, 4)["n"])


if __name__ == "__main__":
    unittest.main()
