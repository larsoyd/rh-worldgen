import argparse
import json
import sys
import time
from pathlib import Path

from rhworld.fog import Refused, check_modulus, g1_witness, g2_witness, hash_chain_index, side
from rhworld.gm import make_gm
from rhworld.world import CLASSES, class_map, layer, ledger_primes, render_gm, render_ledger, render_player, zoom

G1_MARK = (220, 30, 30)
G1_RADIUS = 3
G2_MARK = (250, 210, 0)
G2_RADIUS = 5
LEDGER_LIMIT = 1024


def log(tag, **fields):
    print(f"[{tag}] " + " ".join(f"{k}={v}" for k, v in fields.items()), flush=True)


def write_gm(args):
    p, q = make_gm(args.bits, args.seed, args.rigged)
    data = {"bits": args.bits, "seed": args.seed, "rigged": args.rigged, "p": str(p), "q": str(q), "n": str(p * q)}
    Path(args.out).write_text(json.dumps(data, indent=1) + "\n")
    log("gm", wrote=args.out, n_bits=(p * q).bit_length(), rigged=args.rigged)


def public_part(n, out):
    start = time.monotonic()
    b = check_modulus(n)
    s = side(n)
    log("check", n_bits=n.bit_length(), budget=b, side=s, ms=f"{(time.monotonic() - start) * 1000:.0f}")
    g2 = g2_witness(n)
    chain = hash_chain_index(n)
    log("g2", witness=g2, budget=b)
    log("control", hash_chain_index=chain)
    out.mkdir(parents=True, exist_ok=True)
    v = layer(n, s)
    img = render_player(v, [(g2, G2_MARK, G2_RADIUS)])
    img.save(out / "player.png")
    zoom(img, g2).save(out / "player_zoom.png")
    return {"n_bits": n.bit_length(), "budget": b, "side": s, "g2": g2, "hash_chain_index": chain}, v


def world(args):
    data = json.loads(Path(args.gm).read_text())
    p, q, n = int(data["p"]), int(data["q"]), int(data["n"])
    if p * q != n:
        raise Refused("gm file factors do not match n")
    out = Path(args.out)
    report, v = public_part(n, out)
    g1p, g1q = g1_witness(p, n), g1_witness(q, n)
    log("g1", witness_p=g1p, witness_q=g1q, budget=report["budget"])
    h = layer(p, report["side"])
    img = render_gm(v, h, [(g1p, G1_MARK, G1_RADIUS), (report["g2"], G2_MARK, G2_RADIUS)])
    img.save(out / "gm.png")
    zoom(img, g1p).save(out / "gm_zoom.png")
    primes = ledger_primes(max(LEDGER_LIMIT, g1p, report["g2"]) + 32)
    render_ledger(n, p, primes, [(g1p, G1_MARK), (report["g2"], G2_MARK)]).save(out / "ledger.png")
    counts = class_map(v, h).ravel().tolist()
    tiles = {c.name: counts.count(k) for k, c in enumerate(CLASSES)}
    report.update(g1_p=g1p, g1_q=g1q, tiles=tiles)
    (out / "report.json").write_text(json.dumps(report, indent=1) + "\n")
    log("world", wrote=out, **{k.replace(" ", "_"): t for k, t in tiles.items()})


def player(args):
    out = Path(args.out)
    report, _ = public_part(int(args.n), out)
    (out / "report.json").write_text(json.dumps(report, indent=1) + "\n")
    log("player", wrote=out)


def main(argv=None):
    ap = argparse.ArgumentParser(prog="rhworld")
    sub = ap.add_subparsers(dest="cmd", required=True)
    g = sub.add_parser("gm", help="make a sample game master key file (not for real secrets)")
    g.add_argument("--bits", type=int, default=512, help="bits per prime factor")
    g.add_argument("--seed", type=int, required=True)
    g.add_argument("--rigged", action="store_true", help="choose factors that hide small non-residues")
    g.add_argument("--out", required=True)
    g.set_defaults(func=write_gm)
    w = sub.add_parser("world", help="game master view from a key file")
    w.add_argument("--gm", required=True)
    w.add_argument("--out", required=True)
    w.set_defaults(func=world)
    pl = sub.add_parser("player", help="player view from the public modulus only")
    pl.add_argument("--n", required=True)
    pl.add_argument("--out", required=True)
    pl.set_defaults(func=player)
    args = ap.parse_args(argv)
    start = time.monotonic()
    try:
        args.func(args)
    except Refused as e:
        print(e, flush=True)
        log("done", status="refused", ms=f"{(time.monotonic() - start) * 1000:.0f}")
        return 2
    log("done", status="ok", ms=f"{(time.monotonic() - start) * 1000:.0f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
