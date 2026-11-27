import random

from rhworld.arith import is_probable_prime
from rhworld.fog import side
from rhworld.gm import random_prime, rigged_prime


def rng_for(name, bits, seed):
    return random.Random(f"verify:{name}:{bits}:{seed}")


TRIES = 100000


def next_prime(x):
    x += 1 + x % 2
    for _ in range(TRIES):
        if is_probable_prime(x):
            return x
        x += 2
    raise RuntimeError(f"no prime found above x={x}")


def until(name, make):
    for _ in range(TRIES):
        out = make()
        if out:
            return out
    raise RuntimeError(f"seed {name} not built in {TRIES} tries")


def seed(name, n, factors):
    return {"name": name, "n": n, "factors": factors}


def cube(bits, s):
    p = random_prime(bits // 3 + 1, rng_for("cube", bits, s))
    return seed("cube", p ** 3, [p])


def square_cofactor(bits, s):
    rng = rng_for("square_cofactor", bits, s)

    def attempt():
        p, r = random_prime(bits // 2, rng), random_prime(bits // 4, rng)
        return (p * r * r).bit_length() == bits and seed("square_cofactor", p * r * r, [p, r])
    return until("square_cofactor", attempt)


def above_trial_bound(bits, s):
    rng = rng_for("above_trial_bound", bits, s)
    small = next_prime(side(1 << (bits - 1)) ** 2)

    def attempt():
        big = random_prime(bits - small.bit_length(), rng)
        return (small * big).bit_length() == bits and seed("above_trial_bound", small * big, [small, big])
    return until("above_trial_bound", attempt)


def near_square(bits, s):
    p = random_prime(bits // 2, rng_for("near_square", bits, s))
    q = next_prime(p)
    return seed("near_square", p * q, [p, q])


def both_rigged(bits, s):
    rng = rng_for("both_rigged", bits, s)
    p, q = rigged_prime(bits // 2, rng), rigged_prime(bits // 2, rng)
    return seed("both_rigged", p * q, [p, q])


ALL = {f.__name__: f for f in (cube, square_cofactor, above_trial_bound, near_square, both_rigged)}
