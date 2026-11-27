import gzip
import json
import os
import struct
import sys
import zlib


def forms(value):
    raw = value.to_bytes((value.bit_length() + 7) // 8, "big")
    return {"decimal": str(value).encode(), "hex": format(value, "x").encode(),
            "HEX": format(value, "X").encode(), "bytes": raw, "bytes_le": raw[::-1]}


def region_payloads(blob):
    for i in range(1024):
        loc = struct.unpack(">I", blob[4 * i:4 * i + 4])[0]
        offset, sectors = (loc >> 8) * 4096, loc & 0xFF
        if not sectors or offset + 5 > len(blob):
            continue
        length, kind = struct.unpack(">IB", blob[offset:offset + 5])
        data = blob[offset + 5:offset + 4 + length]
        if kind == 1:
            yield gzip.decompress(data)
        elif kind == 2:
            yield zlib.decompress(data)
        elif kind == 3:
            yield data
        else:
            raise ValueError("compression %d" % kind)


def payloads(path):
    with open(path, "rb") as f:
        blob = f.read()
    yield blob
    if path.endswith(".mca") and len(blob) >= 8192:
        yield from region_payloads(blob)
    elif blob[:2] == b"\x1f\x8b":
        yield gzip.decompress(blob)


def walk(paths):
    for root in paths:
        if os.path.isfile(root):
            yield root
        for dirpath, _, names in os.walk(root):
            for name in sorted(names):
                yield os.path.join(dirpath, name)


def main(key_path, roots):
    try:
        with open(key_path) as f:
            key = json.load(f)
        factors = {"p": int(key["p"]), "q": int(key["q"])}
    except (OSError, ValueError, KeyError) as e:
        print("[rhworld-leak] refused: unreadable key reason=%s" % type(e).__name__)
        return 2
    missing = [r for r in roots if not os.path.exists(r)]
    if missing:
        print("[rhworld-leak] refused: no such path %s" % missing[0])
        return 2
    needles = [(name, form, b) for name, v in factors.items() for form, b in forms(v).items()]
    files = hits = 0
    for path in walk(roots):
        files += 1
        try:
            blobs = list(payloads(path))
        except (OSError, ValueError, EOFError, zlib.error) as e:
            print("[rhworld-leak] unreadable file=%s reason=%s" % (path, type(e).__name__))
            hits += 1
            continue
        for factor, form, needle in needles:
            if any(needle in b for b in blobs):
                hits += 1
                print("[rhworld-leak] hit file=%s factor=%s form=%s" % (path, factor, form.lower().replace("_le", "")))
    print("[rhworld-leak] verdict=%s files=%d hits=%d" % ("fail" if hits else "pass", files, hits))
    return 1 if hits else 0


if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("[rhworld-leak] refused: usage leakscan.py <key.json> <path>...")
        sys.exit(2)
    sys.exit(main(sys.argv[1], sys.argv[2:]))
