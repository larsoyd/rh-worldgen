import copy
import hashlib
import unittest

from verify import certcheck, rides
from verify.reveal import Refused

P521 = 2 ** 521 - 1
P607 = 2 ** 607 - 1
N = P521 * P607


def chain(top, length):
    links = [top]
    for _ in range(length):
        links.append(hashlib.sha256(links[-1]).digest())
    return links[::-1]


def build_log(colours, top=b"\x11" * 32):
    links = chain(top, len(colours))
    x, out = 1, []
    for i, colour in enumerate(colours):
        after = rides.ride(N, x, links[i + 1], i, colour)
        out.append({"after": str(after), "before": str(x), "colour": colour, "index": i, "opening": links[i + 1].hex(),
                    "reported": rides.realm(after, P521, P607)})
        x = after
    return {"commitment": links[0].hex(), "format": "rhworld.rides/1", "n": str(N), "rides": out}


class Dice(unittest.TestCase):
    def test_golden_words(self):
        bits = rides.dice(bytes(range(32)), 7, 100)
        words = [sum(b << j for j, b in enumerate(bits[k:k + 64])) for k in range(0, 100, 64)]
        signed = [w - (1 << 64) if w >= 1 << 63 else w for w in words]
        self.assertEqual(signed, [-4359226908302620812, 17293095169])

    def test_golden_full_count(self):
        bits = rides.dice(bytes(range(32)), 7, 41816)
        self.assertEqual(sum(bits), 20991)
        last = sum(b << j for j, b in enumerate(bits[41816 // 64 * 64:]))
        self.assertEqual(last, 0x31c9a4)


class Replay(unittest.TestCase):
    def refused(self, log, reason, p=None):
        with self.assertRaises(Refused) as cm:
            rides.replay(certcheck.dump(log), p)
        self.assertEqual(str(cm.exception), f"refused: {reason}")

    def test_colour_is_forced(self):
        log = build_log(["warm", "cold", "cold", "warm"])
        for r in log["rides"]:
            self.assertEqual(certcheck.kronecker_odd(int(r["after"]), N), 1 if r["colour"] == "warm" else -1)

    def test_valid_log_with_and_without_reveal(self):
        log = build_log(["warm", "cold", "warm"])
        self.assertEqual(rides.replay(certcheck.dump(log)), 3)
        self.assertEqual(rides.replay(certcheck.dump(log), P521), 3)

    def test_each_tamper_is_named(self):
        base = build_log(["warm", "cold", "warm"])
        cases = [
            (lambda g: g["rides"][1].update(index=5), "ride 1 index"),
            (lambda g: g["rides"][1].update(before="2"), "ride 1 before"),
            (lambda g: g["rides"][1].update(opening="00" * 32), "ride 1 opening"),
            (lambda g: g["rides"][2].update(after=str(int(g["rides"][2]["after"]) + 1)), "ride 2 after"),
            (lambda g: g["rides"][0].update(reported="mist" if g["rides"][0]["reported"] in ("hearth", "ember") else "hearth"),
             "ride 0 reported"),
            (lambda g: g.update(format="rhworld.rides/2"), "format"),
        ]
        for tamper, reason in cases:
            log = copy.deepcopy(base)
            tamper(log)
            with self.subTest(reason=reason):
                self.refused(log, reason)

    def test_lie_within_colour_is_caught_only_at_reveal(self):
        log = build_log(["warm"])
        r = log["rides"][0]
        r["reported"] = "ember" if r["reported"] == "hearth" else "hearth"
        self.assertEqual(rides.replay(certcheck.dump(log)), 1)
        self.refused(log, "ride 0 reported", P521)

    def test_noncanonical_log(self):
        raw = certcheck.dump(build_log(["warm"])).replace(b'"index":0', b'"index": 0')
        with self.assertRaises(Refused):
            rides.replay(raw)


if __name__ == "__main__":
    unittest.main()
