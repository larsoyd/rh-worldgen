import hashlib
import math

from rhworld.arith import is_probable_prime, jacobi


class Refused(Exception):
    def __str__(self):
        return f"refused: {self.args[0]}"


def budget(n):
    # depends only on the bit length and is at least floor((ln n)^2) for every n of that length
    return math.floor((n.bit_length() * math.log(2)) ** 2)


def side(n):
    return math.isqrt(budget(n) - 1) + 1


def check_modulus(n, min_bits=1024):
    b = budget(n) if n > 1 else 0
    if n % 2 == 0 or n.bit_length() < min_bits:
        raise Refused("modulus too small or even")
    if math.isqrt(n) ** 2 == n:
        raise Refused("jacobi character is principal")
    if is_probable_prime(n):
        raise Refused("no hidden layer")
    limit = max(b, side(n) ** 2)
    for d in range(3, limit + 1, 2):
        if n % d == 0:
            raise Refused("small factor")
    return b


def least_witness(symbol, limit):
    for x in range(1, limit + 1):
        if symbol(x) == -1:
            return x
    raise Refused("budget exceeded")


def g2_witness(n):
    return least_witness(lambda x: jacobi(x, n), budget(n))


def g1_witness(factor, n):
    return least_witness(lambda x: jacobi(x, factor), budget(n))


def hash_value(n, i):
    raw = n.to_bytes((n.bit_length() + 7) // 8, "big")
    width = (n.bit_length() + 7) // 8 + 16
    return int.from_bytes(hashlib.shake_256(raw + i.to_bytes(8, "big")).digest(width), "big") % n


def hash_chain_index(n, limit=4096):
    for i in range(1, limit + 1):
        if jacobi(hash_value(n, i), n) == -1:
            return i
    raise Refused("hash chain exceeded")
