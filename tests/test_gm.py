import math
import random
import unittest

from rhworld.arith import is_probable_prime, jacobi
from rhworld.fog import Refused, budget, check_modulus, g1_witness, g2_witness, hash_chain_index, least_witness
from rhworld.gm import forced_bound, make_gm, random_prime, rigged_prime


def odd_primes_upto(n):
    return [x for x in range(3, n + 1, 2) if is_probable_prime(x)]


def least_qnr(p):
    return next(x for x in range(2, p) if jacobi(x, p) == -1)


class Primes(unittest.TestCase):
    def test_random_prime_has_exact_bits(self):
        rng = random.Random(1)
        for bits in (64, 512):
            p = random_prime(bits, rng)
            self.assertTrue(is_probable_prime(p))
            self.assertEqual(p.bit_length(), bits)
            self.assertEqual((p * random_prime(bits, rng)).bit_length(), 2 * bits)

    def test_rigged_prime_hides_every_small_nonresidue(self):
        rng = random.Random(2)
        for bits in (512, 1024):
            forced = forced_bound(bits)
            p = rigged_prime(bits, rng)
            self.assertTrue(is_probable_prime(p))
            self.assertEqual(p.bit_length(), bits)
            self.assertGreater(least_qnr(p), forced)
            self.assertGreater(forced, 0.6 * math.log(p), forced)
            print(f"[rigged] bits={bits} forced={forced} least_qnr={least_qnr(p)} lnp={math.log(p):.0f}")

    def test_forced_bound_uses_all_spare_bits(self):
        self.assertEqual([forced_bound(b) for b in (512, 1024)], [331, 683])
        for bits in (512, 1024):
            self.assertGreater(8 * math.prod(odd_primes_upto(forced_bound(bits) + 60)), 2 ** (bits - 64))

    def test_same_seed_same_gm(self):
        self.assertEqual(make_gm(512, 7), make_gm(512, 7))
        self.assertNotEqual(make_gm(512, 7), make_gm(512, 8))
        self.assertEqual(make_gm(512, 7, rigged=True), make_gm(512, 7, rigged=True))


class Guarantee(unittest.TestCase):
    def samples(self):
        for bits in (512, 1024):
            for seed in range(3):
                for rigged in (False, True):
                    p, q = make_gm(bits, seed, rigged)
                    yield p, q, rigged

    def test_g1_g2_and_hash_control_on_random_and_rigged_gms(self):
        count = 0
        for p, q, rigged in self.samples():
            n = p * q
            b = check_modulus(n)
            for f in (p, q):
                w = g1_witness(f, n)
                self.assertLessEqual(w, b)
                self.assertEqual(jacobi(w, f), -1)
                self.assertTrue(all(jacobi(x, f) == 1 for x in range(1, w)))
                if rigged:
                    self.assertGreater(w, forced_bound(f.bit_length()))
            w2 = g2_witness(n)
            self.assertLessEqual(w2, b)
            self.assertGreaterEqual(hash_chain_index(n), 1)
            count += 1
            print(f"[g1] bits={n.bit_length()} rigged={rigged} budget={b} g1p={g1_witness(p, n)} "
                  f"g1q={g1_witness(q, n)} g2={w2} chain={hash_chain_index(n)}")
        self.assertEqual(count, 12)

    def test_cut_budget_refuses_on_rigged_gm(self):
        for bits in (512, 1024):
            p, q = make_gm(bits, 0, rigged=True)
            n = p * q
            cut = math.floor(0.3 * math.log(n))
            with self.assertRaises(Refused):
                least_witness(lambda x: jacobi(x, p), cut)
            self.assertLess(g1_witness(p, n), budget(n))
            print(f"[cut] bits={n.bit_length()} cut={cut} g1p={g1_witness(p, n)} budget={budget(n)}")

    def test_exhaustive_small_worlds(self):
        primes = [x for x in range(3, 10 ** 4) if is_probable_prime(x)]
        checked, cut_refused = 0, []
        for i, p in enumerate(primes):
            for q in primes[i + 1:]:
                n = p * q
                if n >= 10 ** 6:
                    break
                if n < 3000:
                    continue
                try:
                    b = check_modulus(n, min_bits=1)
                except Refused:
                    continue
                self.assertGreater(p, math.log(n) ** 2)
                for f in (p, q):
                    self.assertLessEqual(least_witness(lambda x: jacobi(x, f), b), b, n)
                self.assertLessEqual(least_witness(lambda x: jacobi(x, n), b), b, n)
                try:
                    least_witness(lambda x: jacobi(x, p), math.floor(math.log(n)))
                except Refused:
                    cut_refused.append(n)
                checked += 1
        print(f"[small] checked={checked} cut_refused={len(cut_refused)} first={cut_refused[:3]}")
        self.assertGreater(checked, 1000)
        self.assertTrue(cut_refused)
