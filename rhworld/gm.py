import random

from rhworld.arith import is_probable_prime

SLACK_BITS = 64


def odd_primes(limit):
    return [x for x in range(3, limit + 1, 2) if all(x % d for d in range(3, int(x ** 0.5) + 1, 2))]


def forced_moduli(bits):
    m, ls = 8, []
    for l in odd_primes(4 * bits):
        if (m * l).bit_length() > bits - SLACK_BITS:
            break
        m *= l
        ls.append(l)
    return m, ls


def forced_bound(bits):
    return forced_moduli(bits)[1][-1]


def random_prime(bits, rng):
    while True:
        p = rng.getrandbits(bits) | (3 << (bits - 2)) | 1
        if is_probable_prime(p):
            return p


def rigged_prime(bits, rng):
    # p is 1 mod 8 and a square mod each small l so by reciprocity every small x is a residue
    _, ls = forced_moduli(bits)
    m, r = 8, 1
    for l in ls:
        target = pow(rng.randrange(1, l), 2, l)
        r += m * ((target - r) * pow(m, -1, l) % l)
        m *= l
    lo, hi = (3 << (bits - 2)) // m + 1, (1 << bits) // m - 1
    while True:
        p = r + m * rng.randrange(lo, hi)
        if is_probable_prime(p):
            return p


def make_gm(bits, seed, rigged=False):
    rng = random.Random(f"{bits}:{seed}:{rigged}")
    pick = rigged_prime if rigged else random_prime
    return pick(bits, rng), pick(bits, rng)
