import math
from itertools import combinations

from mpmath import euler, log, mp, mpf, pi, sqrt

from rhworld.fog import Refused

mp.dps = 30
ZETA_B = mpf("0.0230957089661210338")
CONTRACT = "RHW-C2 v1"


def log_bound(moduli):
    return math.log(2) * sum(m.bit_length() for m in moduli)


def _conductor_term(big_l):
    return max(big_l - log(pi), log(pi / 3)) / 2


def branch_bound(big_l):
    # bounds the log derivative of xi at zero for any primitive conductor below e to the L
    y = max(mpf(big_l) ** 2, mpf(100))
    mangoldt = log(y) - 1 - euler + log(2 * pi) / y + 2 * ZETA_B / sqrt(y)
    tail = log(2) + euler / 2 + (log(y) + 2) / y
    return (_conductor_term(big_l) + mangoldt + tail) / (1 - 2 / sqrt(y) - 1 / y)


def principal_floor(x):
    x = mpf(x)
    return x - log(2 * pi) * log(x) - 1 - 2 * ZETA_B * (sqrt(x) + 1)


def character_ceiling(big_l, x):
    x = mpf(x)
    lx = log(x)
    gamma_terms = lx ** 2 / 2 + (log(2) + euler / 2) * lx + pi ** 2 / 8 + 2 / x
    return branch_bound(big_l) * (2 * sqrt(x) + 2 + lx) + _conductor_term(big_l) * lx + gamma_terms


def eigen_bound(big_l, x):
    return float(character_ceiling(big_l, x) / principal_floor(x))


def prime_power_mass(x):
    total = mpf(0)
    for p in range(2, math.isqrt(int(x)) + 1):
        if all(p % d for d in range(2, math.isqrt(p) + 1)):
            pk = p * p
            while pk <= x:
                total += log(p) * log(mpf(x) / pk)
                pk *= p
    return float(total)


def state_floor(big_l, states, x):
    spread = (principal_floor(x) - (states - 1) * character_ceiling(big_l, x)) / states
    return float(spread) - prime_power_mass(x)


def _small_primes(limit):
    flags = bytearray([1]) * (limit + 1)
    flags[0:2] = b"\x00\x00"
    for i in range(2, math.isqrt(limit) + 1):
        if flags[i]:
            flags[i * i::i] = bytearray(len(flags[i * i::i]))
    return [i for i in range(2, limit + 1) if flags[i]]


def certify(moduli, x):
    if not moduli:
        raise Refused("no layers")
    if any(m < 3 or m % 2 == 0 for m in moduli):
        raise Refused("modulus too small or even")
    for r in range(1, len(moduli) + 1):
        for subset in combinations(moduli, r):
            product = math.prod(subset)
            if math.isqrt(product) ** 2 == product:
                raise Refused("dependent layers")
    for p in _small_primes(int(x)):
        if any(m % p == 0 for m in moduli):
            raise Refused("small factor")
    big_l = log_bound(moduli)
    states = 2 ** len(moduli)
    return {
        "contract": CONTRACT,
        "states": states,
        "x": int(x),
        "log_bound": big_l,
        "lambda": eigen_bound(big_l, x),
        "floor": state_floor(big_l, states, x),
    }
