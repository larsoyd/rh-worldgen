import argparse
import sys
import time

import numpy as np

from rhworld.image import save_heightmap


def render(args):
    start = time.monotonic()
    heights = np.load(args.src)
    save_heightmap(heights, args.out)
    ms = (time.monotonic() - start) * 1000
    print(f"[render] wrote out={args.out} shape={heights.shape} ms={ms:.1f}")


def main(argv=None):
    ap = argparse.ArgumentParser(prog="rhworld")
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("render", help="write a heightmap array as a grayscale png")
    r.add_argument("src")
    r.add_argument("out")
    r.set_defaults(func=render)
    args = ap.parse_args(argv)
    args.func(args)
    return 0


if __name__ == "__main__":
    sys.exit(main())
