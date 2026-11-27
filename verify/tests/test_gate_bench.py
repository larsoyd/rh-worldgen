import random
import unittest

from rhworld.arith import jacobi
from rhworld.gm import make_gm
from verify import gate_bench, seeds

BITS = 1024


def realm(x, p, q):
    return (jacobi(x, p) == -1, jacobi(x, q) == -1)


class Budget(unittest.TestCase):
    def test_exact_budget_matches_high_precision_where_double_is_wrong(self):
        import mpmath
        with mpmath.workdps(60):
            for k in (1024, 2048, 96893, 167966, 193786):
                self.assertEqual(gate_bench.exact_budget(k), int(mpmath.floor((k * mpmath.log(2)) ** 2)), k)
        self.assertEqual(gate_bench.exact_budget(1024), 503791)


class Span(unittest.TestCase):
    def test_rank_of_known_vectors(self):
        self.assertEqual(gate_bench.rank([]), 0)
        self.assertEqual(gate_bench.rank([(False, False)] * 5), 0)
        self.assertEqual(gate_bench.rank([(True, True), (True, True)]), 1)
        self.assertEqual(gate_bench.rank([(True, False), (True, True)]), 2)

    def test_undersized_gate_is_stuck_on_a_rigged_key(self):
        p, q = seeds.both_rigged(BITS, 0)["factors"]
        small = gate_bench.primes_upto(331)
        self.assertEqual(gate_bench.rank(gate_bench.labels(small, p, q)), 0)

    def test_all_arms_span_on_random_and_rigged_keys(self):
        keys = [make_gm(BITS, s) for s in range(2)] + [tuple(seeds.both_rigged(BITS, s)["factors"]) for s in range(2)]
        for p, q in keys:
            for arm in gate_bench.arms(p * q, BITS):
                self.assertEqual(gate_bench.rank(gate_bench.labels(arm.multipliers, p, q)), 2, arm.name)


    def test_span_prefix_on_a_rigged_key_passes_the_rigging_bound(self):
        p, q = seeds.both_rigged(BITS, 0)["factors"]
        count, last = gate_bench.span_prefix(p, q, 10000)
        self.assertGreater(last, 331)
        self.assertEqual(gate_bench.rank(gate_bench.labels(gate_bench.first_primes(count - 1), p, q)), 1)
        self.assertEqual(gate_bench.rank(gate_bench.labels(gate_bench.first_primes(count), p, q)), 2)


class Ride(unittest.TestCase):
    def test_ride_moves_realm_by_the_label_sum(self):
        p, q = seeds.both_rigged(BITS, 1)["factors"]
        n = p * q
        arm = gate_bench.arms(n, BITS)[1]
        rng = random.Random(5)
        x = 1
        for _ in range(6):
            e = [rng.getrandbits(1) for _ in arm.multipliers]
            y = gate_bench.ride(x, e, arm.multipliers, n)
            shift = [False, False]
            for bit, lab in zip(e, gate_bench.labels(arm.multipliers, p, q)):
                if bit:
                    shift = [shift[0] ^ lab[0], shift[1] ^ lab[1]]
            before = realm(x, p, q)
            self.assertEqual(realm(y, p, q), (before[0] ^ shift[0], before[1] ^ shift[1]))
            x = y


class Report(unittest.TestCase):
    def test_report_marks_identical_behaviour(self):
        p, q = seeds.both_rigged(BITS, 0)["factors"]
        rows = gate_bench.compare(p, q, BITS)
        self.assertEqual({r["arm"] for r in rows}, {"hashed128", "uncertified1000", "certified"})
        self.assertTrue(all(r["realms"] == 4 for r in rows))
        self.assertEqual(rows[-1]["multipliers"], 41816)

    def test_report_flags_an_undersized_arm(self):
        p, q = seeds.both_rigged(BITS, 0)["factors"]
        rows = gate_bench.compare(p, q, BITS, extra={"undersized331": gate_bench.primes_upto(331)})
        bad = [r for r in rows if r["arm"] == "undersized331"]
        self.assertEqual(bad[0]["realms"], 1)


if __name__ == "__main__":
    unittest.main()
