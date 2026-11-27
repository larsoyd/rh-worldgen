import functools
import hashlib

from verify import certcheck
from verify.gate_bench import primes_upto
from verify.reveal import Refused, kronecker_odd

REALMS = {(1, 1): "hearth", (-1, -1): "ember", (1, -1): "mist", (-1, 1): "frost"}
COLOUR = {"hearth": "warm", "ember": "warm", "mist": "cold", "frost": "cold"}


def need(ok, field):
    if not ok:
        raise Refused(field)


@functools.lru_cache(maxsize=4)
def primes_for(bits):
    return tuple(primes_upto(certcheck.budget(bits)))


def dice(seed, index, count):
    raw = bytearray()
    for block in range((count + 255) // 256):
        raw += hashlib.sha256(seed + index.to_bytes(8, "big", signed=True) + block.to_bytes(4, "big")).digest()
    return [(raw[j >> 3] >> (j & 7)) & 1 for j in range(count)]


def ride(n, x, opening, index, colour):
    primes = primes_for(n.bit_length())
    e = dice(opening, index, len(primes))
    y = 1
    for bit, l in zip(e, primes):
        if bit:
            y = y * l % n
    target = 1 if colour == "warm" else -1
    if kronecker_odd(x * y % n, n) != target:
        w = next(i for i, l in enumerate(primes) if kronecker_odd(l, n) == -1)
        y = y * (primes[w] if e[w] == 0 else pow(primes[w], -1, n)) % n
    return x * y % n


def realm(x, p, q):
    return REALMS[(kronecker_odd(x, p), kronecker_odd(x, q))]


def replay(raw, p=None):
    log = certcheck.parse(raw)
    need(log.get("format") == "rhworld.rides/1", "format")
    n = int(log["n"])
    link = bytes.fromhex(log["commitment"])
    x = 1
    for i, r in enumerate(log["rides"]):
        need(r.get("index") == i, f"ride {i} index")
        need(int(r["before"]) == x, f"ride {i} before")
        opening = bytes.fromhex(r["opening"])
        need(len(opening) == 32 and hashlib.sha256(opening).digest() == link, f"ride {i} opening")
        need(r["colour"] in ("warm", "cold"), f"ride {i} colour")
        need(int(r["after"]) == ride(n, x, opening, i, r["colour"]), f"ride {i} after")
        need(COLOUR.get(r["reported"]) == r["colour"], f"ride {i} reported")
        if p is not None:
            need(r["reported"] == realm(int(r["after"]), p, n // p), f"ride {i} reported")
        link, x = opening, int(r["after"])
    return len(log["rides"])
