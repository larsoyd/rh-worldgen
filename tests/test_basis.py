import itertools
import math
import unittest

from rhworld.arith import jacobi
from rhworld.basis import CONTRACT, MAX_LAYERS, gate_state, prime_basis, route, state_of
from rhworld.fog import Refused
from rhworld.reveal import exact_budget


def primes_upto(n):
    return [p for p in range(2, n + 1) if all(p % d for d in range(2, math.isqrt(p) + 1))]


def rank(vectors):
    span = {0}
    for v in vectors:
        span |= {s ^ v for s in span}
    return len(span).bit_length() - 1


SMALL_PRIMES = [p for p in primes_upto(400) if p > 101]
PAIRS = [(p, q) for p, q in itertools.combinations(SMALL_PRIMES, 2) if p > exact_budget((p * q).bit_length())]


class Basis(unittest.TestCase):
    def test_state_bits(self):
        self.assertEqual(state_of(1, [10007, 10009]), 0)
        self.assertEqual(state_of(5, [10007]), 0 if jacobi(5, 10007) == 1 else 1)
        bits = state_of(7, [103, 107, 109])
        for i, m in enumerate([103, 107, 109]):
            self.assertEqual(bits >> i & 1, 1 if jacobi(7, m) == -1 else 0)

    def test_every_pair_reaches_full_rank_as_least_escaping_primes(self):
        for p, q in PAIRS:
            b = prime_basis([p, q])
            self.assertEqual(b["contract"], CONTRACT)
            self.assertEqual(b["budget"], exact_budget((p * q).bit_length()))
            self.assertEqual(len(b["primes"]), 2)
            self.assertLessEqual(max(b["primes"]), b["budget"])
            self.assertEqual(rank(b["vectors"]), 2)
            scanned = [l for l in primes_upto(b["primes"][-1])]
            self.assertEqual(b["scanned"], len(scanned))
            for l in scanned:
                if l < b["primes"][0]:
                    self.assertEqual(state_of(l, [p, q]), 0, (p, q, l))
                elif b["primes"][0] < l < b["primes"][1]:
                    self.assertIn(state_of(l, [p, q]), (0, b["vectors"][0]), (p, q, l))

    def test_triples_keep_exactly_the_least_primes_outside_the_span(self):
        triples = [t for t in itertools.combinations([p for p in primes_upto(1500) if p > 450], 3)][:150]
        for layers in triples:
            b = prime_basis(list(layers))
            expected, vectors = [], []
            for l in primes_upto(b["budget"]):
                v = state_of(l, layers)
                if rank(vectors + [v]) > len(vectors):
                    expected.append(l)
                    vectors.append(v)
                    if len(vectors) == 3:
                        break
            self.assertEqual(b["primes"], expected, layers)

    def test_a_prime_in_the_span_is_skipped(self):
        self.assertEqual(prime_basis([457, 461, 479])["primes"], [2, 5, 13])
        self.assertEqual(prime_basis([457, 461, 499])["primes"], [2, 5, 17])

    def test_three_public_layers(self):
        layers = [10007 * 10009, 10037 * 10039, 10061]
        b = prime_basis(layers)
        self.assertEqual(len(b["primes"]), 3)
        self.assertEqual(rank(b["vectors"]), 3)
        self.assertEqual(b["vectors"], [state_of(l, layers) for l in b["primes"]])

    def test_refusals_in_order(self):
        cases = [
            ([], "no layers"),
            ([10007 + 2 * i for i in range(MAX_LAYERS + 1)], "too many layers"),
            ([10007, 10010], "modulus too small or even"),
            ([1, 10007], "modulus too small or even"),
            ([10007, 10007], "dependent layers"),
            ([103 * 107, 103 * 109, 107 * 109], "dependent layers"),
            ([1009], "lcm below 3000"),
            ([2999], "lcm below 3000"),
            ([71 * 73], "small factor"),
            ([10007, 10009 * 101], "small factor"),
        ]
        for layers, reason in cases:
            with self.assertRaises(Refused) as ctx:
                prime_basis(layers)
            self.assertEqual(str(ctx.exception), f"refused: {reason}", layers)

    def test_budget_exceeded_when_the_scan_is_cut(self):
        with self.assertRaises(Refused) as ctx:
            prime_basis([10007, 10009], budget=2)
        self.assertEqual(str(ctx.exception), "refused: budget exceeded")


class Route(unittest.TestCase):
    def test_every_route_on_every_pair(self):
        for p, q in PAIRS[:300]:
            b = prime_basis([p, q])
            for source, target in itertools.product(range(4), repeat=2):
                hops = route(source, target, b)
                self.assertLessEqual(len(hops), 2)
                state = source
                for l in hops:
                    state ^= state_of(l, [p, q])
                self.assertEqual(state, target, (p, q, source, target, hops))

    def test_route_from_a_tile(self):
        layers = [10007 * 10009, 10037 * 10039, 10061]
        b = prime_basis(layers)
        x = 123456789
        for target in range(8):
            prod = x
            for l in route(state_of(x, layers), target, b):
                prod *= l
            self.assertEqual(state_of(prod, layers), target)


class Gate(unittest.TestCase):
    def test_exact_uniformity_by_enumeration(self):
        alphabet = primes_upto(53)
        checked = 0
        for p, q in PAIRS:
            if max(prime_basis([p, q])["primes"]) > 53:
                continue
            counts = [0] * 4
            for e in range(1 << len(alphabet)):
                counts[gate_state(e, alphabet, [p, q])] += 1
            self.assertEqual(counts, [1 << (len(alphabet) - 2)] * 4, (p, q))
            checked += 1
            if checked == 3:
                break
        self.assertEqual(checked, 3)

    def test_exact_uniformity_over_the_full_alphabet(self):
        for p, q in PAIRS:
            counts = [1, 0, 0, 0]
            for l in primes_upto(exact_budget((p * q).bit_length())):
                v = state_of(l, [p, q])
                counts = [counts[s] + counts[s ^ v] for s in range(4)]
            self.assertEqual(len(set(counts)), 1, (p, q, counts))

    def test_gate_state_matches_the_product(self):
        layers = [10007, 10009]
        alphabet = primes_upto(60)
        for e in (0, 1, 0b1011, (1 << len(alphabet)) - 1, 0x2A5):
            prod = math.prod(l for i, l in enumerate(alphabet) if e >> i & 1)
            self.assertEqual(gate_state(e, alphabet, layers), state_of(prod, layers))


if __name__ == "__main__":
    unittest.main()
