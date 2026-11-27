import math

SMALL = (2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37)


class Refused(Exception):
    def __str__(self):
        return f"refused: {self.args[0]}"


def kronecker_odd(a, n):
    # written apart from the oracle on purpose so a shared bug cannot hide
    result, a = 1, a % n
    while a:
        twos = (a & -a).bit_length() - 1
        a >>= twos
        if twos % 2 and n % 8 in (3, 5):
            result = -result
        if a % 4 == 3 and n % 4 == 3:
            result = -result
        a, n = n % a, a
    return result if n == 1 else 0


def strong_base2(n):
    d, s = n - 1, 0
    while d % 2 == 0:
        d, s = d // 2, s + 1
    x = pow(2, d, n)
    if x in (1, n - 1):
        return True
    for _ in range(s - 1):
        x = x * x % n
        if x == n - 1:
            return True
    return False


def selfridge(n):
    d = 5
    for _ in range(10000):
        k = kronecker_odd(d, n)
        if k == -1:
            return d
        if k == 0 and abs(d) != n:
            return None
        d = -d - 2 if d > 0 else -d + 2
    raise RuntimeError(f"no selfridge parameter for n={n}")


def strong_lucas(n):
    d = selfridge(n)
    if d is None:
        return False
    p, q = 1, (1 - d) // 4
    k, s = n + 1, 0
    while k % 2 == 0:
        k, s = k // 2, s + 1
    half = pow(2, -1, n)
    u, v, qk = 1, p, q % n
    for bit in bin(k)[3:]:
        u, v = u * v % n, (v * v - 2 * qk) % n
        qk = qk * qk % n
        if bit == "1":
            u, v = (p * u + v) * half % n, (d * u + p * v) * half % n
            qk = qk * q % n
    if u == 0 or v == 0:
        return True
    for _ in range(s - 1):
        v = (v * v - 2 * qk) % n
        qk = qk * qk % n
        if v == 0:
            return True
    return False


def bpsw(n):
    if n < 2:
        return False
    for p in SMALL:
        if n % p == 0:
            return n == p
    if math.isqrt(n) ** 2 == n:
        return False
    return strong_base2(n) and strong_lucas(n)


def check_reveal(n, p):
    if p <= 1 or p >= n or n % p:
        raise Refused("revealed factor does not divide n")
    q = n // p
    if p == q:
        raise Refused("factors equal")
    if not bpsw(p):
        raise Refused("revealed factor not prime")
    if not bpsw(q):
        raise Refused("cofactor not prime")
    return min(p, q), max(p, q)


TRIAL_LIMIT = 1 << 32


def proved_small(q):
    return 1 < q < TRIAL_LIMIT and all(q % d for d in range(2, math.isqrt(q) + 1))


def check_certificate(n, cert):
    # pocklington: a factored part f of n-1 with f^2 > n and a witness per prime of f proves n prime
    f = 1
    for q, e in cert["factors"]:
        sub = cert.get("sub", {}).get(str(q))
        if not (proved_small(q) or (sub and check_certificate(q, sub) == "proved")):
            raise Refused(f"listed factor {q} not proved prime")
        f *= q ** e
    if (n - 1) % f:
        raise Refused("factored part does not divide n-1")
    if f * f <= n:
        raise Refused("factored part too small")
    for q, _ in cert["factors"]:
        a = cert["witnesses"][str(q)]
        if pow(a, n - 1, n) != 1 or math.gcd(pow(a, (n - 1) // q, n) - 1, n) != 1:
            raise Refused(f"witness fails for factor {q}")
    return "proved"


def build_certificate(n, factors):
    witnesses = {}
    for q, _ in factors:
        witnesses[str(q)] = next(a for a in range(2, 10000)
                                 if pow(a, n - 1, n) == 1 and math.gcd(pow(a, (n - 1) // q, n) - 1, n) == 1)
    return {"factors": [[q, e] for q, e in factors], "witnesses": witnesses}
