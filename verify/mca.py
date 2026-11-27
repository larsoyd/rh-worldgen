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
HUB_CLEARANCE = 16


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
    return [(longs[i // per] >> (i % per * bits)) & mask for i in range(count)]


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


def shrine_index(state):
    match = re.search(r"prime_index=(\d+)", state)
    return int(match[1]) if match else None


def shrine_mismatches(blocks, pieces):
    out = []
    for piece in pieces:
        if piece["id"] != "rhworld:shrine":
            continue
        found = [shrine_index(b[3]) for b in blocks
                 if b[3].startswith("rhworld:shrine") and inside(b, piece["BB"])]
        if found != [piece["prime_index"]]:
            out.append((piece["prime_index"], found))
    return out


def java_round(v):
    return math.floor(v + 0.5)


def ring_position(i, radius=48, count=25):
    a = 2 * math.pi * i / count
    return java_round(radius * math.cos(a)), java_round(radius * math.sin(a))


def pad_top(blocks):
    column = [y for x, y, z, st in blocks if st == "rhworld:hub_stone" and x == 0 and z == 0]
    return max(column) + 1 if column else None


def foreign_starts(starts, radius):
    return sorted((cx, cz, key) for (cx, cz), found in starts.items() for key, start in found.items()
                  if not key.startswith(NAMESPACE) and start.get("id") != "INVALID"
                  and abs(cx) <= radius and abs(cz) <= radius)


def column_ok(ys, top, depth=24):
    return bool(ys) and max(ys) == top and len(ys) == top - min(ys) + 1 and len(ys) <= depth


def foundation_errors(blocks):
    stone = {}
    for x, y, z, st in blocks:
        if st == "rhworld:hub_stone":
            stone.setdefault((x, z), set()).add(y)
    errors = []
    oy = pad_top(blocks)
    for x in range(-9, 10):
        for z in range(-9, 10):
            ys = {y for y in stone.get((x, z), ()) if oy is not None and y < oy}
            if not column_ok(ys, oy - 1 if oy else None):
                errors.append(f"hub column ({x}, {z}) not contiguous to {oy - 1 if oy else None}")
    for sx, sy, sz, st in blocks:
        if st.startswith("rhworld:shrine"):
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    ys = {y for y in stone.get((sx + dx, sz + dz), ()) if y < sy}
                    if not column_ok(ys, sy - 1):
                        errors.append(f"shrine {shrine_index(st)} column ({sx + dx}, {sz + dz}) not contiguous to {sy - 1}")
    return errors


def layout_errors(blocks):
    errors = []
    oy = pad_top(blocks)
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
            i = shrine_index(st)
            want = ring_position(i) if i is not None else None
            if (x, z) != want:
                errors.append(f"shrine {i} at ({x}, {z}) expected {want}")
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
        every, pieces, starts = [], [], {}
        for f in sorted(folder.glob("r.*.mca")):
            for chunk in read_region(f.read_bytes()):
                blocks = rhworld_blocks(chunk)
                every += blocks
                found = chunk.get("structures", {}).get("starts", {})
                starts[(chunk["xPos"], chunk["zPos"])] = found
                for key, start in found.items():
                    if key.startswith(NAMESPACE):
                        pieces += start.get("Children", [])
                if blocks:
                    out[(level, chunk["xPos"], chunk["zPos"])] = (len(blocks), chunk_hash(blocks))
                else:
                    empty += 1
        placement[level] = (len(outside(every, [p["BB"] for p in pieces])), shrine_mismatches(every, pieces),
                            layout_errors(every) + foundation_errors(every), foreign_starts(starts, HUB_CLEARANCE))
    return out, empty, placement


def dump_hashes(log):
    pattern = re.compile(r"\[rhworld-chunk\] level=(\S+) cx=(-?\d+) cz=(-?\d+) blocks=(\d+) sha256=([0-9a-f]{64})")
    return {(m[1], int(m[2]), int(m[3])): (int(m[4]), m[5]) for m in pattern.finditer(Path(log).read_text())}


def provenance(world):
    path = Path(world) / "rhworld-provenance.txt"
    if not path.is_file():
        return "refused: no provenance file"
    fields = dict(re.findall(r"(\w+)=(\S+)", path.read_text()))
    if "rev" not in fields:
        return "refused: provenance without rev"
    if fields.get("dirty") != "no":
        return f"refused: world built from a dirty tree rev={fields['rev']}"
    return f"rev={fields['rev']}"


def main(world, log):
    origin = provenance(world)
    print(f"[mca] provenance {origin}")
    if origin.startswith("refused"):
        return 1
    mine, empty, placement = world_hashes(world)
    for level, (loose, wrong, layout, foreign) in placement.items():
        print(f"[mca] level={level} blocks_outside_pieces={loose} shrine_mismatches={len(wrong)} first={wrong[:4]} "
              f"layout_errors={len(layout)} first_layout={layout[:2]} foreign_starts={len(foreign)} first_foreign={foreign[:2]}")
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
