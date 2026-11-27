import decimal
import random
import time
from typing import NamedTuple

from rhworld.arith import jacobi
from rhworld.fog import hash_value


class Arm(NamedTuple):
    name: str
    multipliers: list


def exact_budget(k):
    # floor((k ln 2)^2) from a 60 digit ln 2, refused when the error could move the floor
    with decimal.localcontext() as ctx:
        ctx.prec = 80
        value = (k * decimal.Decimal(2).ln()) ** 2
        lo, hi = int(value - decimal.Decimal("1e-40")), int(value + decimal.Decimal("1e-40"))
    if lo != hi:
        raise ValueError(f"budget floor ambiguous at k={k}")
    return lo


def primes_upto(limit):
    flags = bytearray([1]) * (limit + 1)
    flags[:2] = b"\x00\x00"
    for i in range(2, int(limit ** 0.5) + 1):
        if flags[i]:
            flags[i * i::i] = bytearray(len(flags[i * i::i]))
    return [i for i, f in enumerate(flags) if f]


def first_primes(count):
    limit = 16
    for _ in range(64):
        ps = primes_upto(limit)
        if len(ps) >= count:
            return ps[:count]
        limit *= 2
    raise ValueError(f"too many primes count={count}")


def arms(n, bits):
    return [
        Arm("hashed128", [hash_value(n, i) for i in range(1, 129)]),
        Arm("uncertified1000", first_primes(1000)),
        Arm("certified", primes_upto(exact_budget(bits))),
    ]


def labels(multipliers, p, q):
    return [(jacobi(m, p) == -1, jacobi(m, q) == -1) for m in multipliers]


def rank(vectors):
    basis = []
    for v in vectors:
        bits = v[0] << 1 | v[1]
        for b in basis:
            bits = min(bits, bits ^ b)
        if bits:
            basis.append(bits)
    return len(basis)


def span_prefix(p, q, limit):
    vectors = []
    for count, l in enumerate(primes_upto(limit), 1):
        vectors.append((jacobi(l, p) == -1, jacobi(l, q) == -1))
        if rank(vectors) == 2:
            return count, l
    return None


def ride(x, e, multipliers, n):
    for bit, m in zip(e, multipliers):
        if bit:
            x = x * m % n
    return x


def compare(p, q, bits, extra=None):
    n = p * q
    chosen = arms(n, bits) + [Arm(name, ms) for name, ms in (extra or {}).items()]
    rng = random.Random(f"bench:{n}")
    rows = []
    for arm in chosen:
        r = rank(labels(arm.multipliers, p, q))
        e = [rng.getrandbits(1) for _ in arm.multipliers]
        start = time.perf_counter()
        ride(1, e, arm.multipliers, n)
        ms = (time.perf_counter() - start) * 1000
        rows.append({"arm": arm.name, "multipliers": len(arm.multipliers), "rank": r, "realms": 2 ** r,
                     "steerable": any(jacobi(m, n) == -1 for m in arm.multipliers), "ride_ms": round(ms, 3)})
    return rows


def keys(bits, count):
    from rhworld.gm import make_gm, random_prime, rigged_prime
    from verify import seeds
    for s in range(count):
        yield "random", s, make_gm(bits, s)
        rng = random.Random(f"bench:rigged_p:{bits}:{s}")
        yield "rigged_p", s, (rigged_prime(bits // 2, rng), random_prime(bits // 2, rng))
        yield "both_rigged", s, tuple(seeds.both_rigged(bits, s)["factors"])


def main(bits_list=(1024, 2048), count=3):
    for bits in bits_list:
        for kind, s, (p, q) in keys(bits, count):
            start = time.perf_counter()
            needed, l = span_prefix(p, q, exact_budget(bits))
            print(f"[bench] bits={bits} key={kind} seed={s} span_prefix_primes={needed} span_prefix_last={l}")
            for row in compare(p, q, bits):
                print(f"[bench] bits={bits} key={kind} seed={s} " + " ".join(f"{k}={v}" for k, v in row.items()))
            print(f"[bench] bits={bits} key={kind} seed={s} secs={time.perf_counter() - start:.2f}")


if __name__ == "__main__":
    main()
