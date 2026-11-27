import math
import unittest

import mpmath

from rhworld.arith import jacobi
from rhworld.fog import Refused, budget, check_modulus, g2_witness, hash_chain_index, hash_value, least_witness, side

P521 = 2 ** 521 - 1
P607 = 2 ** 607 - 1
GOOD_N = P521 * P607


def exact_budget(n):
    with mpmath.workdps(60):
        return int(mpmath.floor((n.bit_length() * mpmath.log(2)) ** 2))


class Budget(unittest.TestCase):
    def test_matches_high_precision_floor(self):
        for n in [3705, 10 ** 6 + 3, 2 ** 1023 + 1, GOOD_N, 2 ** 2048 - 1]:
            self.assertEqual(budget(n), exact_budget(n), n)

    def test_known_values(self):
        self.assertEqual(budget(2 ** 1023 + 1), 503791)
        self.assertEqual(budget(2 ** 1024 - 1), 503791)
        self.assertEqual(budget(2 ** 2048 - 1), 2015165)
        self.assertEqual([side(2 ** 1023 + 1), side(2 ** 2048 - 1)], [710, 1420])

    def test_budget_covers_log_squared_of_every_n_with_that_length(self):
        for n in [3001, 2 ** 1023 + 1, 2 ** 1024 - 1, GOOD_N, 2 ** 2048 - 1]:
            self.assertGreaterEqual(budget(n), math.floor(math.log(n) ** 2))
        self.assertLess(budget(2 ** 1024 - 1), math.log(2 ** 1024 - 1) ** 2)

    def test_side_covers_budget(self):
        for n in [3705, GOOD_N, 2 ** 2048 - 1]:
            s = side(n)
            self.assertGreaterEqual(s * s, budget(n))
            self.assertLess((s - 1) * (s - 1), budget(n))


class ClientChecks(unittest.TestCase):
    def refused(self, n, reason):
        with self.assertRaises(Refused) as cm:
            check_modulus(n)
        self.assertEqual(str(cm.exception), f"refused: {reason}")

    def test_accepts_product_of_two_big_primes(self):
        self.assertEqual(check_modulus(GOOD_N), budget(GOOD_N))

    def test_even(self):
        self.refused(GOOD_N + 1, "modulus too small or even")

    def test_too_small(self):
        self.refused(P521 * 3, "modulus too small or even")

    def test_square(self):
        self.refused(P607 * P607, "jacobi character is principal")

    def test_prime(self):
        self.refused(2 ** 1279 - 1, "no hidden layer")

    def test_small_factor_below_budget(self):
        self.refused(503791 * P607 * P521, "small factor")
        self.refused(1013 * P607 * P521, "small factor")

    def test_factor_inside_patch_but_above_budget(self):
        start = budget(GOOD_N * 10 ** 6)
        f = next(x for x in range(start, start + 2000)
                 if all(x % d for d in range(2, 1100)) and budget(x * GOOD_N) <= x <= side(x * GOOD_N) ** 2)
        self.refused(f * GOOD_N, "small factor")

    def test_factor_three(self):
        self.refused(3 * GOOD_N, "small factor")

    def test_min_bits_parameter_for_small_worlds(self):
        self.assertEqual(check_modulus(1009 * 1013, min_bits=1), budget(1009 * 1013))


class Witnesses(unittest.TestCase):
    def test_least_witness_is_least(self):
        for n in range(1001, 3000, 2):
            if math.isqrt(n) ** 2 == n:
                continue
            want = next((x for x in range(1, n) if jacobi(x, n) == -1), None)
            if want is None:
                continue
            self.assertEqual(least_witness(lambda x: jacobi(x, n), n), want, n)

    def test_budget_exceeded_refuses(self):
        n = 11128425
        with self.assertRaises(Refused) as cm:
            least_witness(lambda x: jacobi(x, n), 72)
        self.assertEqual(str(cm.exception), "refused: budget exceeded")
        self.assertEqual(least_witness(lambda x: jacobi(x, n), 73), 73)

    def test_witness_exactly_at_budget_is_accepted(self):
        b = budget(GOOD_N)
        self.assertEqual(least_witness(lambda x: -1 if x == b else 1, b), b)

    def test_g2_on_big_n(self):
        w = g2_witness(GOOD_N)
        self.assertEqual(jacobi(w, GOOD_N), -1)
        self.assertTrue(all(jacobi(x, GOOD_N) == 1 for x in range(1, w)))

    def test_hash_chain_is_canonical_and_valid(self):
        i = hash_chain_index(GOOD_N)
        self.assertEqual(i, hash_chain_index(GOOD_N))
        self.assertGreaterEqual(i, 1)
        self.assertLess(i, 64)

    def test_hash_chain_value_is_a_witness_even_with_zero_symbols(self):
        n = 3 * 5 * 7 * GOOD_N
        i = hash_chain_index(n)
        self.assertEqual(jacobi(hash_value(n, i), n), -1)
        self.assertTrue(all(jacobi(hash_value(n, j), n) != -1 for j in range(1, i)))
        zeros = sum(jacobi(hash_value(n, j), n) == 0 for j in range(1, 200))
        self.assertGreater(zeros, 50)
        firsts = [hash_chain_index(n * k) for k in (1, 11, 13, 17, 19, 23)]
        self.assertTrue(all(jacobi(hash_value(n * k, i), n * k) == -1 for k, i in zip((1, 11, 13, 17, 19, 23), firsts)))

    def test_hash_value_known_answer(self):
        self.assertEqual(hash_value(10007 * 10009, 1), 52080376)
        self.assertEqual(hash_value(10007 * 10009, 2), 76059255)

    def test_hash_chain_refuses_square(self):
        with self.assertRaises(Refused) as cm:
            hash_chain_index(P607 * P607, limit=50)
        self.assertEqual(str(cm.exception), "refused: hash chain exceeded")
