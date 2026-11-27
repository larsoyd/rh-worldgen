import itertools
import json
import pathlib
import sys

from rhworld.basis import CONTRACT, MAX_LAYERS, prime_basis, route, state_of
from rhworld.fog import Refused
from rhworld.reveal import exact_budget
from rhworld.spectral import small_primes

GOLDEN_PATH = "samples/c3-golden.json"
ROOT = pathlib.Path(__file__).resolve().parent.parent


def _pairs(count):
    primes = [p for p in small_primes(400) if p > 101]
    pairs = [[p, q] for p, q in itertools.combinations(primes, 2) if p > exact_budget((p * q).bit_length())]
    return pairs[:count]


def _triples(count):
    primes = [p for p in small_primes(1500) if p > 450]
    return [list(t) for t in itertools.combinations(primes, 3)][:count]


def _rigged_key():
    key = json.loads((ROOT / "samples/rigged-1024/gm-key.json").read_text())
    return [int(key["p"]), int(key["q"])]


def _case(layers):
    basis = prime_basis(layers)
    alphabet = small_primes(basis["budget"])
    span = {0}
    for l in alphabet:
        v = state_of(l, layers)
        if v not in span:
            span |= {s ^ v for s in span}
    rank = len(span).bit_length() - 1
    states = range(1 << len(layers))
    return {
        "layers": [str(m) for m in layers],
        "budget": basis["budget"],
        "primes": basis["primes"],
        "vectors": basis["vectors"],
        "scanned": basis["scanned"],
        "alphabet_size": len(alphabet),
        "rank": rank,
        "fibre_log2": len(alphabet) - rank,
        "routes": [[s, t, route(s, t, basis)] for s in states for t in states],
    }


REFUSALS = [
    [],
    [10007 + 2 * i for i in range(MAX_LAYERS + 1)],
    [10007, 10010],
    [1, 10007],
    [10007, 10007],
    [103 * 107, 103 * 109, 107 * 109],
    [1009],
    [71 * 73],
    [10007, 10009 * 101],
]


def _refusal(layers):
    try:
        prime_basis(layers)
    except Refused as exc:
        return {"layers": [str(m) for m in layers], "reason": exc.args[0]}
    raise AssertionError(f"accepted {layers}")


def c3_vectors():
    layers = _pairs(40) + _triples(12) + [[10007 * 10009, 10037 * 10039, 10061], [2 ** 521 - 1, 2 ** 607 - 1], _rigged_key()]
    return {
        "contract": CONTRACT,
        "cases": [_case(m) for m in layers],
        "refusals": [_refusal(m) for m in REFUSALS],
    }


if __name__ == "__main__":
    out = ROOT / GOLDEN_PATH
    out.write_text(json.dumps(c3_vectors(), indent=1, sort_keys=True) + "\n")
    print(f"[golden] path={GOLDEN_PATH} bytes={out.stat().st_size}", file=sys.stderr)
