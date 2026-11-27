import gzip
import hashlib
import math
import re
import struct
import sys
import zlib
from pathlib import Path

NAMESPACE = "rhworld:"
DEFAULTS = {"rhworld:shrine": {"prime_index": "0"}}


class Reader:
    def __init__(self, raw):
        self.raw, self.pos = raw, 0

    def take(self, n):
        out = self.raw[self.pos:self.pos + n]
        if len(out) != n:
            raise ValueError(f"nbt truncated at pos={self.pos} need={n}")
        self.pos += n
        return out

    def fmt(self, f):
        return struct.unpack(f, self.take(struct.calcsize(f)))[0]

    def string(self):
        return self.take(self.fmt(">H")).decode("utf-8", "surrogateescape")

    def payload(self, tag):
        simple = {1: ">b", 2: ">h", 3: ">i", 4: ">q", 5: ">f", 6: ">d"}
        if tag in simple:
            return self.fmt(simple[tag])
        if tag == 7:
            return self.take(self.fmt(">i"))
        if tag == 8:
            return self.string()
        if tag == 9:
            inner, count = self.fmt(">b"), self.fmt(">i")
            return [self.payload(inner) for _ in range(count)]
        if tag == 10:
            out = {}
            for _ in range(1 << 20):
                t = self.fmt(">b")
                if t == 0:
                    return out
                key = self.string()
                out[key] = self.payload(t)
            raise ValueError("nbt compound without end")
        if tag in (11, 12):
            count = self.fmt(">i")
            return list(struct.unpack(f">{count}{'i' if tag == 11 else 'q'}", self.take(count * (4 if tag == 11 else 8))))
        raise ValueError(f"unknown nbt tag={tag} pos={self.pos}")


def parse_nbt(raw):
    r = Reader(raw)
    tag = r.fmt(">b")
    r.string()
    return r.payload(tag)


def read_region(raw):
    for slot in range(1024):
        loc = struct.unpack(">I", raw[4 * slot:4 * slot + 4])[0]
        offset = (loc >> 8) * 4096
        if not loc:
            continue
        length, kind = struct.unpack(">IB", raw[offset:offset + 5])
        data = raw[offset + 5:offset + 4 + length]
        if kind == 2:
            data = zlib.decompress(data)
        elif kind == 1:
            data = gzip.decompress(data)
        elif kind != 3:
            raise ValueError(f"unsupported chunk compression kind={kind} slot={slot}")
        yield parse_nbt(data)


def unpack(longs, bits, count):
    per, mask = 64 // bits, (1 << bits) - 1
    return [((longs[i // per] & (1 << 64) - 1) >> (i % per * bits)) & mask for i in range(count)]


def state_string(name, props):
    return name + ("[" + ",".join(f"{k}={props[k]}" for k in sorted(props)) + "]" if props else "")


def palette_state(entry):
    # saves omit default property values so they are filled back in here
    if isinstance(entry, str):
        name, props = entry, {}
    elif "" in entry:
        name, props = entry[""], {}
    elif "id" in entry:
        name, props = entry["id"], entry.get("properties", {})
    else:
        name, props = entry["Name"], entry.get("Properties", {})
    return state_string(name, {**DEFAULTS.get(name, {}), **props})


def rhworld_blocks(chunk):
    cx, cz = chunk["xPos"], chunk["zPos"]
    out = []
    for sec in chunk.get("sections", []):
        states = sec.get("block_states", {})
        palette = states.get("palette", [])
        names = [palette_state(p) for p in palette]
        if not any(n.startswith(NAMESPACE) for n in names):
            continue
        bits = max(4, (len(palette) - 1).bit_length())
        indices = unpack(states["data"], bits, 4096) if len(palette) > 1 else [0] * 4096
        for i, idx in enumerate(indices):
            if names[idx].startswith(NAMESPACE):
                y, z, x = i >> 8, (i >> 4) & 15, i & 15
                out.append((cx * 16 + x, sec["Y"] * 16 + y, cz * 16 + z, names[idx]))
    return out


def inside(block, box):
    x, y, z, _ = block
    return box[0] <= x <= box[3] and box[1] <= y <= box[4] and box[2] <= z <= box[5]


def outside(blocks, boxes):
    return [b for b in blocks if not any(inside(b, box) for box in boxes)]


def shrine_mismatches(blocks, pieces):
    out = []
    for piece in pieces:
        if piece["id"] != "rhworld:shrine":
            continue
        found = [int(b[3].split("prime_index=")[1].rstrip("]")) for b in blocks
                 if b[3].startswith("rhworld:shrine") and inside(b, piece["BB"])]
        if found != [piece["prime_index"]]:
            out.append((piece["prime_index"], found))
    return out


def java_round(v):
    return math.floor(v + 0.5)


def ring_position(i, radius=48, count=25):
    a = 2 * math.pi * i / count
    return java_round(radius * math.cos(a)), java_round(radius * math.sin(a))


def layout_errors(blocks):
    errors = []
    pad = [y for x, y, z, st in blocks if st == "rhworld:hub_stone" and abs(x) <= 9 and abs(z) <= 9]
    oy = min(pad) + 1 if pad else None
    frame = {(dy, dz) for dy in (1, 2, 3) for dz in (-1, 0, 1)}
    for name, gx in (("gate_warm", -6), ("gate_cold", 6)):
        gate = [(x, y, z) for x, y, z, st in blocks if st == f"rhworld:{name}"]
        if any(x != gx for x, _, _ in gate):
            errors.append(f"{name} at x != {gx}")
        elif oy is None or sorted((y - oy, z) for _, y, z in gate) != sorted(frame):
            errors.append(f"{name} frame wrong")
    sigil = [(x, y, z) for x, y, z, st in blocks if st == "rhworld:sigil"]
    if sigil != [(0, oy, 6)]:
        errors.append(f"sigil at {sigil}")
    for x, _, z, st in blocks:
        if st.startswith("rhworld:shrine"):
            i = int(st.split("prime_index=")[1].rstrip("]"))
            if (x, z) != ring_position(i):
                errors.append(f"shrine {i} at ({x}, {z}) expected {ring_position(i)}")
    return errors


def chunk_hash(blocks):
    lines = sorted(f"{x} {y} {z} {s}\n" for x, y, z, s in blocks)
    return hashlib.sha256("".join(lines).encode()).hexdigest()


def region_dirs(world):
    yield "minecraft:overworld", world / "dimensions" / "minecraft" / "overworld" / "region"
    for d in sorted((world / "dimensions" / "rhworld").iterdir()):
        yield f"rhworld:{d.name}", d / "region"


def world_hashes(world):
    out, empty, placement = {}, 0, {}
    for level, folder in region_dirs(Path(world)):
        if not folder.is_dir():
            raise FileNotFoundError(f"missing region folder level={level} path={folder}")
        every, pieces = [], []
        for f in sorted(folder.glob("r.*.mca")):
            for chunk in read_region(f.read_bytes()):
                blocks = rhworld_blocks(chunk)
                every += blocks
                for key, start in chunk.get("structures", {}).get("starts", {}).items():
                    if key.startswith(NAMESPACE):
                        pieces += start.get("Children", [])
                if blocks:
                    out[(level, chunk["xPos"], chunk["zPos"])] = (len(blocks), chunk_hash(blocks))
                else:
                    empty += 1
        placement[level] = (len(outside(every, [p["BB"] for p in pieces])), shrine_mismatches(every, pieces),
                            layout_errors(every))
    return out, empty, placement


def dump_hashes(log):
    pattern = re.compile(r"\[rhworld-chunk\] level=(\S+) cx=(-?\d+) cz=(-?\d+) blocks=(\d+) sha256=([0-9a-f]{64})")
    return {(m[1], int(m[2]), int(m[3])): (int(m[4]), m[5]) for m in pattern.finditer(Path(log).read_text())}


def main(world, log):
    mine, empty, placement = world_hashes(world)
    for level, (loose, wrong, layout) in placement.items():
        print(f"[mca] level={level} blocks_outside_pieces={loose} shrine_mismatches={len(wrong)} first={wrong[:4]} "
              f"layout_errors={len(layout)} first_layout={layout[:2]}")
    theirs = dump_hashes(log)
    missing = sorted(set(theirs) - set(mine))
    extra = sorted(set(mine) - set(theirs))
    differ = sorted(k for k in set(mine) & set(theirs) if mine[k] != theirs[k])
    for k in (missing + extra + differ)[:10]:
        print(f"[mca] mismatch level={k[0]} cx={k[1]} cz={k[2]} mine={mine.get(k)} dump={theirs.get(k)}")
    ok = not (missing or extra or differ) and theirs and not any(any(v) for v in placement.values())
    print(f"[mca] verdict={'pass' if ok else 'fail'} chunks_with_blocks={len(mine)} dumped={len(theirs)} "
          f"missing={len(missing)} extra={len(extra)} differ={len(differ)} empty_chunks={empty}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(*sys.argv[1:3]))
