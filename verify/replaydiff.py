import copy
import json
import subprocess
import sys
import tempfile
from pathlib import Path

from verify import certcheck, rides
from verify.reveal import Refused

DATA = Path(__file__).parent / "tests" / "data"


def tampered(base):
    def t(f):
        g = copy.deepcopy(base)
        f(g)
        return certcheck.dump(g)
    raw = certcheck.dump(base)
    return {
        "valid": raw,
        "index": t(lambda g: g["rides"][3].update(index=9)),
        "before": t(lambda g: g["rides"][2].update(before="5")),
        "opening": t(lambda g: g["rides"][4].update(opening="ab" * 32)),
        "after": t(lambda g: g["rides"][5].update(after=str(int(g["rides"][5]["after"]) + 2))),
        "last_after": t(lambda g: g["rides"][7].update(after=str(int(g["rides"][7]["after"]) * 4 % int(g["n"])))),
        "colour_lie": t(lambda g: g["rides"][1].update(reported="hearth")),
        "within_colour_lie": t(lambda g: g["rides"][1].update(reported="frost" if g["rides"][1]["reported"] == "mist" else "mist")),
        "colour_flip": t(lambda g: g["rides"][0].update(colour="cold")),
        "format": t(lambda g: g.update(format="rhworld.rides/2")),
        "dice_missing": t(lambda g: g.pop("dice")),
        "dice_unknown": t(lambda g: g.update(dice="server")),
        "server_held": t(lambda g: g.update(dice="server-held")),
        "truncated": t(lambda g: g.update(rides=g["rides"][:3])),
        "dropped_middle": t(lambda g: g.update(rides=g["rides"][:2] + g["rides"][3:])),
        "whitespace": raw.replace(b'":', b'": ', 1),
        "trailing_lf": raw + b"\n",
        "crlf": raw + b"\r\n",
        "upper_hex": t(lambda g: g["rides"][0].update(opening=g["rides"][0]["opening"].upper())),
        "index_string": t(lambda g: g["rides"][0].update(index="0")),
        "not_json": b"{",
    }


def mine(raw):
    try:
        return "ok " + json.dumps(rides.replay(raw))
    except (Refused, ValueError, KeyError, TypeError, AttributeError) as e:
        return f"refused {e}"


def main(cli):
    base = json.loads((DATA / "rides-rigged-1024.json").read_bytes())
    splits = 0
    with tempfile.TemporaryDirectory() as tmp:
        for name, raw in tampered(base).items():
            path = Path(tmp) / f"{name}.json"
            path.write_bytes(raw)
            ours = mine(raw)
            run = subprocess.run([cli, "replay", "--log", str(path)], capture_output=True, text=True, timeout=120,
                                 stdin=subprocess.DEVNULL)
            last = ((run.stdout + run.stderr).strip().splitlines() or [""])[-1]
            agree = (run.returncode == 0) == ours.startswith("ok")
            splits += not agree
            print(f"[replay-diff] case={name} mine={ours[:60]!r} mechanics_exit={run.returncode} "
                  f"mechanics_last={last[:70]!r} agree={agree}", flush=True)
    print(f"[replay-diff] verdict={'pass' if not splits else 'fail'} splits={splits}")
    return 1 if splits else 0


if __name__ == "__main__":
    cli = sys.argv[1] if len(sys.argv) > 1 else ""
    if not Path(cli).is_file():
        print(f"[replay-diff] refused reason=mechanics cli missing cli={cli!r}")
        sys.exit(1)
    sys.exit(main(cli))
