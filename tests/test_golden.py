import json
import pathlib
import unittest

from rhworld.basis import prime_basis, state_of
from rhworld.golden import GOLDEN_PATH, c3_vectors

ROOT = pathlib.Path(__file__).resolve().parent.parent


class Golden(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = c3_vectors()

    def test_file_matches_the_oracle(self):
        stored = json.loads((ROOT / GOLDEN_PATH).read_text())
        self.assertEqual(stored, json.loads(json.dumps(self.data)))

    def test_coverage(self):
        cases = self.data["cases"]
        self.assertGreaterEqual(len(cases), 50)
        self.assertEqual({len(c["layers"]) for c in cases}, {2, 3})
        self.assertTrue(any(int(c["layers"][0]).bit_length() >= 512 for c in cases))
        reasons = {r["reason"] for r in self.data["refusals"]}
        self.assertEqual(reasons, {"no layers", "too many layers", "modulus too small or even", "dependent layers",
                                   "lcm below 3000", "small factor"})

    def test_every_route_and_fibre_is_consistent(self):
        for c in self.data["cases"]:
            layers = [int(m) for m in c["layers"]]
            r = len(layers)
            self.assertEqual(c["rank"], r)
            self.assertEqual(c["fibre_log2"], c["alphabet_size"] - r)
            self.assertEqual(len(c["routes"]), 4 ** r)
            for source, target, hops in c["routes"]:
                state = source
                for l in hops:
                    state ^= state_of(l, layers)
                self.assertEqual(state, target)
            self.assertEqual(prime_basis(layers)["primes"], c["primes"])


if __name__ == "__main__":
    unittest.main()
