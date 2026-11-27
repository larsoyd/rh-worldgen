import mpmath

from rhworld.arith import is_probable_prime
from rhworld.fog import Refused


def exact_budget(k):
    with mpmath.workdps(40 + 2 * len(str(k))):
        v = (k * mpmath.log(2)) ** 2
        b = int(mpmath.floor(v))
        if min(v - b, b + 1 - v) < mpmath.mpf(10) ** -30:
            raise Refused("budget tie")
    return b


def verify_reveal(n, p):
    if p <= 1 or p >= n or n % p:
        raise Refused("factor does not divide")
    r = n // p
    if p == r:
        raise Refused("equal factors")
    if not is_probable_prime(p):
        raise Refused("factor not prime")
    if not is_probable_prime(r):
        raise Refused("cofactor not prime")
    return min(p, r), max(p, r)
