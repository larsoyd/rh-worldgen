import os
import subprocess
import sys
import tempfile
from pathlib import Path

from rhworld.fog import Refused as OracleRefused, check_modulus, g2_witness
from rhworld.gm import make_gm
from verify import certverify, seeds
from verify.reveal import Refused, check_reveal

P521, P607 = 2 ** 521 - 1, 2 ** 607 - 1


def cases(bits=1024):
    yield "good", P521 * P607, P521
    for s in range(2):
        p, q = make_gm(bits, s)
        yield f"random{s}", p * q, min(p, q)
        p, q = make_gm(bits, s, rigged=True)
        yield f"rigged{s}", p * q, min(p, q)
    for name, make in seeds.ALL.items():
        s = make(bits, 0)
        yield name, s["n"], s["factors"][0]
    yield "even", P521 * P607 + 1, None
    yield "small", P521 * 3, None
    yield "square", P607 * P607, None
    yield "prime", 2 ** 1279 - 1, None
    yield "small_factor", P521 * P607 * 503, None


def oracle(n):
    try:
        check_modulus(n)
        return f"accepted g2={g2_witness(n)}"
    except OracleRefused as e:
        return str(e)


def engine(cli, n, p, out):
    args = [cli, "certify", "--n", str(n), "--out", str(out)] + (["--p", str(p)] if p else [])
    run = subprocess.run(args, capture_output=True, text=True, timeout=120, stdin=subprocess.DEVNULL)
    last = (run.stderr.strip().splitlines() or [""])[-1]
    return run.returncode, last


def independent(n, p):
    try:
        check_reveal(n, p)
        return "reveal ok"
    except Refused as e:
        return f"reveal {e}"


def main(cli):
    failures = 0
    with tempfile.TemporaryDirectory() as tmp:
        for name, n, p in cases():
            want = oracle(n)
            code, last = engine(cli, n, None, Path(tmp) / f"{name}.json")
            agree = (code == 0) == want.startswith("accepted") and (code == 0 or last == want)
            mine = ""
            if code == 0:
                try:
                    certverify.verify((Path(tmp) / f"{name}.json").read_bytes())
                    mine = "verified"
                except Refused as e:
                    mine, agree = str(e), False
            line = f"[cross] case={name} oracle={want!r} engine_exit={code} engine_last={last!r} independent={mine!r}"
            if p and code == 0:
                rcode, rlast = engine(cli, n, p, Path(tmp) / f"{name}-reveal.json")
                verdict = independent(n, p)
                agree &= (rcode == 0) == (verdict == "reveal ok")
                if rcode == 0:
                    try:
                        certverify.verify((Path(tmp) / f"{name}-reveal.json").read_bytes())
                    except Refused as e:
                        verdict, agree = f"{verdict} cert {e}", False
                line += f" reveal_exit={rcode} reveal_last={rlast!r} reveal_independent={verdict!r}"
            failures += not agree
            print(line + f" agree={agree}", flush=True)
    print(f"[cross] verdict={'pass' if not failures else 'fail'} disagreements={failures}")
    return 1 if failures else 0


if __name__ == "__main__":
    cli = os.environ.get("RHWORLD_CLI") or (sys.argv[1] if len(sys.argv) > 1 else "")
    if not cli or not Path(cli).is_file():
        print(f"[cross] refused reason=engine cli missing cli={cli!r}")
        sys.exit(1)
    sys.exit(main(cli))
