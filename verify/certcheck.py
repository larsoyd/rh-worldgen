import decimal
import json
import math

from verify.reveal import Refused, kronecker_odd

LIMIT53 = 1 << 53
MR25 = (2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97)


def utf16(key):
    return key.encode("utf-16-be")


def strict_object(pairs):
    keys = [k for k, _ in pairs]
    if len(set(keys)) != len(keys):
        raise Refused("duplicate key")
    if keys != sorted(keys, key=utf16):
        raise Refused("keys not sorted")
    return dict(pairs)


def no_float(_):
    raise Refused("not canonical")


def check_ints(value):
    if isinstance(value, bool) or value is None or isinstance(value, str):
        return
    if isinstance(value, int):
        if abs(value) > LIMIT53:
            raise Refused("integer above 2^53 must be a string")
    elif isinstance(value, list):
        for v in value:
            check_ints(v)
    else:
        for v in value.values():
            check_ints(v)


def dump(value):
    if isinstance(value, dict):
        items = sorted(value.items(), key=lambda kv: utf16(kv[0]))
        return b"{" + b",".join(dump(k) + b":" + dump(v) for k, v in items) + b"}"
    if isinstance(value, list):
        return b"[" + b",".join(dump(v) for v in value) + b"]"
    return json.dumps(value, ensure_ascii=False).encode()


def parse(raw):
    try:
        value = json.loads(raw.decode("utf-8"), object_pairs_hook=strict_object, parse_float=no_float,
                           parse_constant=no_float)
    except (ValueError, UnicodeDecodeError):
        raise Refused("not canonical") from None
    check_ints(value)
    if dump(value) != raw:
        raise Refused("not canonical")
    return value


def budget(k):
    with decimal.localcontext() as ctx:
        ctx.prec = 90
        exact = (k * decimal.Decimal(2).ln()) ** 2
        lo, hi = int(exact - decimal.Decimal("1e-50")), int(exact + decimal.Decimal("1e-50"))
    if lo != hi:
        raise Refused(f"budget floor ambiguous at k={k}")
    return lo


def mr25(n):
    for a in MR25:
        if n % a == 0:
            return n == a
    d, s = n - 1, 0
    while d % 2 == 0:
        d, s = d // 2, s + 1
    for a in MR25:
        x = pow(a, d, n)
        if x in (1, n - 1):
            continue
        for _ in range(s - 1):
            x = x * x % n
            if x == n - 1:
                break
        else:
            return False
    return True


def admissible(n, min_bits=1024):
    if n % 2 == 0 or n.bit_length() < min_bits:
        return "refused: modulus too small or even"
    if math.isqrt(n) ** 2 == n:
        return "refused: jacobi character is principal"
    if mr25(n):
        return "refused: no hidden layer"
    b = budget(n.bit_length())
    side = math.isqrt(b - 1) + 1
    if any(n % d == 0 for d in range(3, max(b, side * side) + 1, 2)):
        return "refused: small factor"
    return "ok"


def least_jacobi_minus_one(n, limit):
    return next((x for x in range(1, limit + 1) if kronecker_odd(x, n) == -1), None)


def check_g2(n, w):
    if not 1 <= w <= budget(n.bit_length()):
        raise Refused("witness outside budget")
    if kronecker_odd(w, n) != -1:
        raise Refused("witness symbol is not -1")
    if least_jacobi_minus_one(n, w - 1) is not None:
        raise Refused("witness is not the least")
    return w
