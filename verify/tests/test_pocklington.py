import random
import unittest

from rhworld.arith import is_probable_prime
from rhworld.gm import forced_moduli
from verify.reveal import Refused, build_certificate, check_certificate

BITS = 512


def rigged_with_certificate(seed):
    # p = 1 mod 8 prod l is rigged too since 1 is a square mod every l and n-1 comes half factored
    m, ls = forced_moduli(BITS)
    rng = random.Random(seed)
    lo, hi = (3 << (BITS - 2)) // m + 1, (1 << BITS) // m - 1
    p = next(c for c in (1 + m * rng.randrange(lo, hi) for _ in range(100000)) if is_probable_prime(c))
    return p, build_certificate(p, [(2, 3)] + [(l, 1) for l in ls])


class Pocklington(unittest.TestCase):
    def refused(self, n, cert, reason):
        with self.assertRaises(Refused) as cm:
            check_certificate(n, cert)
        self.assertEqual(str(cm.exception), f"refused: {reason}")

    def test_rigged_prime_is_proved(self):
        p, cert = rigged_with_certificate(1)
        self.assertEqual(check_certificate(p, cert), "proved")

    def test_part_too_small(self):
        p, cert = rigged_with_certificate(2)
        cert["factors"] = cert["factors"][:5]
        self.refused(p, cert, "factored part too small")

    def test_factor_not_dividing(self):
        p, cert = rigged_with_certificate(3)
        cert["factors"][1] = [cert["factors"][1][0], 7]
        self.refused(p, cert, "factored part does not divide n-1")

    def test_bad_witness(self):
        p, cert = rigged_with_certificate(4)
        cert["witnesses"][str(cert["factors"][0][0])] = 1
        self.refused(p, cert, "witness fails for factor 2")

    def test_composite_listed_factor(self):
        p, cert = rigged_with_certificate(5)
        q, _ = cert["factors"][-1]
        cert["factors"].append([q * 0 + 4, 1])
        self.refused(p, cert, "listed factor 4 not proved prime")

    def test_zero_factor(self):
        p, cert = rigged_with_certificate(7)
        cert["factors"].append([0, 1])
        self.refused(p, cert, "listed factor 0 not proved prime")

    def test_composite_n_cannot_be_certified(self):
        p, cert = rigged_with_certificate(6)
        self.refused(p * 3, cert, "factored part does not divide n-1")


if __name__ == "__main__":
    unittest.main()
