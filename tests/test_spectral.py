import math
import unittest

from rhworld.arith import jacobi
from rhworld.fog import Refused
from rhworld.spectral import certify, eigen_bound, log_bound, principal_floor, prime_power_mass, state_floor

L1024 = 1024 * math.log(2)
L2048 = 2048 * math.log(2)


def sieve(n):
    spf = list(range(n + 1))
    for i in range(2, math.isqrt(n) + 1):
        if spf[i] == i:
            for j in range(i * i, n + 1, i):
                if spf[j] == j:
                    spf[j] = i
    return spf


SPF = sieve(40000)
PRIMES = [p for p in range(3, len(SPF)) if SPF[p] == p]


def von_mangoldt(n):
    p, m = SPF[n], n
    while m % p == 0:
        m //= p
    return math.log(p) if m == 1 else 0.0


def weighted(x, chi, primes_only=False):
    total = 0.0
    for n in range(2, x + 1):
        w = von_mangoldt(n)
        if w and not (primes_only and SPF[n] != n):
            total += w * math.log(x / n) * chi(n)
    return total


def states(moduli, x):
    mass = {}
    for p in range(2, x + 1):
        if SPF[p] == p:
            key = tuple(jacobi(p, m) for m in moduli)
            mass[key] = mass.get(key, 0.0) + math.log(p) * math.log(x / p)
    return mass


class Formula(unittest.TestCase):
    def test_half_gap_crosses_where_computed(self):
        self.assertGreater(eigen_bound(L1024, 2_100_000), 0.5)
        self.assertLess(eigen_bound(L1024, 2_300_000), 0.5)
        self.assertGreater(eigen_bound(L2048, 8_300_000), 0.5)
        self.assertLess(eigen_bound(L2048, 8_800_000), 0.5)

    def test_four_state_cover_crosses_where_computed(self):
        self.assertLess(state_floor(L1024, 4, 4_900_000), 0)
        self.assertGreater(state_floor(L1024, 4, 5_100_000), 0)
        self.assertLess(state_floor(L2048, 16, 4.6e8), 0)
        self.assertGreater(state_floor(L2048, 16, 4.9e8), 0)

    def test_principal_floor_below_exact_sum(self):
        for x in (100, 1000, 30000):
            exact = weighted(x, lambda n: 1)
            self.assertLess(principal_floor(x), exact)
            self.assertGreater(principal_floor(x), exact - 2 * math.log(x) ** 2)

    def test_prime_power_mass_exact(self):
        for x in (50, 1000, 30000):
            exact = weighted(x, lambda n: 1) - weighted(x, lambda n: 1, primes_only=True)
            self.assertAlmostEqual(prime_power_mass(x), exact, delta=1e-6 * exact)

    def test_log_bound_covers_bit_length(self):
        for n in (3, 1000003, 2 ** 1024 - 1, 3 ** 700):
            self.assertGreaterEqual(log_bound([n]), math.log(n))
        self.assertGreaterEqual(log_bound([2 ** 512 - 1, 2 ** 607 - 1]), math.log(2 ** 512 - 1) + math.log(2 ** 607 - 1))


class Exhaustive(unittest.TestCase):
    def test_single_character_never_exceeds_bound(self):
        worst = 0.0
        checked = 0
        for q in PRIMES:
            if q < 60:
                continue
            for x in (59, 200, 1000, 5000, q - 1):
                if x >= q or x > 20000:
                    continue
                b = eigen_bound(math.log(q), x)
                if b >= 1:
                    continue
                lam = weighted(x, lambda n: jacobi(n, q)) / weighted(x, lambda n: 1)
                self.assertLessEqual(abs(lam), b, f"q={q} x={x} lambda={lam} bound={b}")
                worst = max(worst, abs(lam) / b)
                checked += 1
        print(f"[spectral] checked={checked} worst_ratio={worst:.3f}")
        self.assertGreater(checked, 3000)
        self.assertGreater(worst, 0.3)

    def test_every_state_reaches_its_floor(self):
        checked = 0
        for i, p in enumerate(PRIMES):
            if p < 25000:
                continue
            for q in PRIMES[i + 1:i + 40]:
                moduli = [p, q]
                x = 25000
                floor = state_floor(log_bound(moduli), 4, x)
                if floor <= 0:
                    continue
                mass = states(moduli, x)
                self.assertEqual(len(mass), 4, moduli)
                self.assertGreaterEqual(min(mass.values()), floor, moduli)
                checked += 1
            if checked >= 400:
                break
        print(f"[spectral] pairs={checked}")
        self.assertGreaterEqual(checked, 400)


class Certify(unittest.TestCase):
    def test_refusals(self):
        cases = [
            ([10403, 8], 1000, "modulus too small or even"),
            ([1], 1000, "modulus too small or even"),
            ([10007 ** 2], 1000, "dependent layers"),
            ([10007 * 10009, 10007 * 10037, 10009 * 10037], 1000, "dependent layers"),
            ([10007, 991 * 10009], 1000, "small factor"),
            ([], 1000, "no layers"),
        ]
        for moduli, x, reason in cases:
            with self.assertRaises(Refused) as ctx:
                certify(moduli, x)
            self.assertEqual(str(ctx.exception), f"refused: {reason}", moduli)

    def test_certificate_fields(self):
        c = certify([2 ** 521 - 1, 2 ** 607 - 1], 7_000_000)
        self.assertEqual(c["states"], 4)
        self.assertLess(c["lambda"], 1 / 3)
        self.assertGreater(c["floor"], 0)
        self.assertEqual(c["contract"], "RHW-C2 v1")

    def test_large_x_needs_trial_division_of_every_layer(self):
        with self.assertRaises(Refused):
            certify([2 ** 521 - 1, (2 ** 607 - 1) * 1000003], 2_000_000)


if __name__ == "__main__":
    unittest.main()
