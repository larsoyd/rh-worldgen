import random
import unittest

from rhworld.arith import is_probable_prime, jacobi

SMALL_PRIMES = [p for p in range(3, 400, 2) if all(p % d for d in range(3, int(p ** 0.5) + 1, 2))]


def legendre_by_euler(a, p):
    r = pow(a, (p - 1) // 2, p)
    return -1 if r == p - 1 else r


def factor(n):
    out, d = [], 3
    while d * d <= n:
        while n % d == 0:
            out.append(d)
            n //= d
        d += 2
    return out + ([n] if n > 1 else [])


class Jacobi(unittest.TestCase):
    def test_matches_euler_on_primes(self):
        for p in SMALL_PRIMES:
            for a in range(0, 2 * p):
                self.assertEqual(jacobi(a, p), legendre_by_euler(a % p, p), (a, p))

    def test_is_product_of_legendre_on_composites(self):
        for n in range(3, 2000, 2):
            for a in range(0, 60):
                want = 1
                for p in factor(n):
                    want *= legendre_by_euler(a % p, p)
                self.assertEqual(jacobi(a, n), want, (a, n))

    def test_big_modulus_multiplicative(self):
        rng = random.Random(5)
        n = rng.getrandbits(1024) | 1
        for _ in range(200):
            a, b = rng.getrandbits(1100), rng.getrandbits(40)
            self.assertEqual(jacobi(a * b, n), jacobi(a, n) * jacobi(b, n))

    def test_refuses_even_modulus(self):
        with self.assertRaisesRegex(ValueError, "odd positive"):
            jacobi(3, 10)


class ProbablePrime(unittest.TestCase):
    def test_small_range_exact(self):
        for n in range(-5, 5000):
            want = n > 1 and all(n % d for d in range(2, int(n ** 0.5) + 1))
            self.assertEqual(is_probable_prime(n), want, n)

    def test_carmichael_and_strong_pseudoprimes(self):
        for n in [561, 1105, 1729, 2047, 3215031751, 3825123056546413051, 318665857834031151167461]:
            self.assertFalse(is_probable_prime(n), n)

    def test_known_large_primes(self):
        for n in [2 ** 127 - 1, 2 ** 521 - 1, 2 ** 607 - 1]:
            self.assertTrue(is_probable_prime(n))
        self.assertFalse(is_probable_prime((2 ** 127 - 1) * (2 ** 89 - 1)))
