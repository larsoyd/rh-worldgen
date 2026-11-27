import json
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from rhworld.arith import is_probable_prime, jacobi
from rhworld.fog import Refused, budget, check_modulus, g1_witness, g2_witness, side
from rhworld.gm import make_gm


def jacobi_cases(rng):
    out = []
    for bits in (3, 8, 31, 32, 33, 63, 64, 65, 127, 512, 1024, 2048):
        for _ in range(60):
            n = rng.getrandbits(bits) | 1 | (1 << (bits - 1))
            a = rng.choice([rng.getrandbits(bits + 40), rng.randrange(0, 2000), rng.getrandbits(62)])
            out.append([str(a), str(n), jacobi(a, n)])
    return out


def prime_cases(rng):
    nums = list(range(0, 3000)) + [561, 1105, 1729, 2047, 3215031751, 3825123056546413051, 318665857834031151167461]
    nums += [2 ** 127 - 1, 2 ** 521 - 1, (2 ** 127 - 1) * (2 ** 89 - 1)]
    for bits in (64, 128, 512, 1024):
        for _ in range(40):
            nums.append(rng.getrandbits(bits) | 1)
    return [[str(n), is_probable_prime(n)] for n in nums]


def modulus_case(label, n, factors=None):
    case = {"label": label, "n": str(n)}
    try:
        case["budget"] = check_modulus(n)
        case["side"] = side(n)
        case["g2"] = g2_witness(n)
        if factors:
            case["g1"] = [g1_witness(f, n) for f in factors]
    except Refused as e:
        case["refused"] = str(e)
    return case


def modulus_cases(rng):
    out = []
    for bits in (512, 1024):
        for seed in range(3):
            p, q = make_gm(bits, seed)
            out.append(modulus_case(f"random-{bits}x2-{seed}", p * q, [p, q]))
        p, q = make_gm(bits, 0, rigged=True)
        out.append(modulus_case(f"rigged-{bits}x2-0", p * q, [p, q]))
    p, q = make_gm(1024, 7)
    out.append(modulus_case("square", p * p))
    out.append(modulus_case("prime", p))
    out.append(modulus_case("even", 2 * p * q))
    small_p, small_q = make_gm(500, 0)
    out.append(modulus_case("small", small_p * small_q))
    out.append(modulus_case("small-factor", 504101 * make_gm(1024, 8)[0]))
    return out


def main():
    rng = random.Random(20261127)
    data = {
        "budget": [[k, budget(1 << (k - 1))] for k in range(2, 20001)],
        "jacobi": jacobi_cases(rng),
        "prime": prime_cases(rng),
        "modulus": modulus_cases(rng),
    }
    Path(sys.argv[1]).write_text(json.dumps(data, separators=(",", ":")) + "\n")


main()
