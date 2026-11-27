import math
from itertools import combinations

from rhworld.arith import jacobi
from rhworld.fog import Refused
from rhworld.reveal import exact_budget
from rhworld.spectral import small_primes

CONTRACT = "RHW-C3 v1"
MAX_LAYERS = 8


def state_of(x, layers):
    return sum(1 << i for i, m in enumerate(layers) if jacobi(x, m) == -1)


def check_layers(layers):
    if not layers:
        raise Refused("no layers")
    if len(layers) > MAX_LAYERS:
        raise Refused("too many layers")
    if any(m < 3 or m % 2 == 0 for m in layers):
        raise Refused("modulus too small or even")
    for r in range(1, len(layers) + 1):
        for subset in combinations(layers, r):
            product = math.prod(subset)
            if math.isqrt(product) ** 2 == product:
                raise Refused("dependent layers")
    lcm = math.lcm(*layers)
    if lcm < 3000:
        raise Refused("lcm below 3000")
    budget = exact_budget(lcm.bit_length())
    for p in small_primes(budget):
        if any(m % p == 0 for m in layers):
            raise Refused("small factor")
    return budget


def prime_basis(layers, budget=None):
    limit = check_layers(layers)
    budget = limit if budget is None else budget
    span, primes, vectors, scanned = {0}, [], [], 0
    for l in small_primes(budget):
        scanned += 1
        v = state_of(l, layers)
        if v not in span:
            span |= {s ^ v for s in span}
            primes.append(l)
            vectors.append(v)
            if len(primes) == len(layers):
                return {"contract": CONTRACT, "budget": limit, "primes": primes, "vectors": vectors, "scanned": scanned}
    raise Refused("budget exceeded")


def route(source, target, basis):
    want = source ^ target
    vectors = basis["vectors"]
    for mask in range(1 << len(vectors)):
        got = 0
        for j, v in enumerate(vectors):
            if mask >> j & 1:
                got ^= v
        if got == want:
            return [l for j, l in enumerate(basis["primes"]) if mask >> j & 1]
    raise Refused("state unreachable")


def gate_state(e, alphabet, layers):
    state = 0
    for i, l in enumerate(alphabet):
        if e >> i & 1:
            state ^= state_of(l, layers)
    return state
