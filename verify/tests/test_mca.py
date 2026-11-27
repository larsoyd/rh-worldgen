import hashlib
import io
import struct
import unittest
import zlib

from verify import mca


def tag_string(s):
    raw = s.encode()
    return struct.pack(">H", len(raw)) + raw


def named(tag, name, payload):
    return bytes([tag]) + tag_string(name) + payload


def compound(*items):
    return b"".join(items) + b"\x00"


def pack(indices, bits):
    per = 64 // bits
    longs = []
    for start in range(0, len(indices), per):
        word = 0
        for j, v in enumerate(indices[start:start + per]):
            word |= v << (j * bits)
        longs.append(word - (1 << 64) if word >= 1 << 63 else word)
    return longs


def section(y, palette, indices):
    entries = b"".join(compound(named(8, "Name", tag_string(name)),
                                *([named(10, "Properties", compound(*[named(8, k, tag_string(v)) for k, v in props.items()]))]
                                  if props else []))
                       for name, props in palette)
    pal = named(9, "palette", bytes([10]) + struct.pack(">i", len(palette)) + entries)
    parts = [pal]
    if len(palette) > 1:
        bits = max(4, (len(palette) - 1).bit_length())
        longs = pack(indices, bits)
        parts.append(named(12, "data", struct.pack(">i", len(longs)) + b"".join(struct.pack(">q", v) for v in longs)))
    return compound(named(1, "Y", struct.pack(">b", y)), named(10, "block_states", compound(*parts)))


def chunk_nbt(cx, cz, sections):
    body = compound(named(3, "xPos", struct.pack(">i", cx)), named(3, "zPos", struct.pack(">i", cz)),
                    named(9, "sections", bytes([10]) + struct.pack(">i", len(sections)) + b"".join(sections)))
    return named(10, "", body)


def region(chunks):
    out = io.BytesIO()
    header = bytearray(8192)
    sector = 2
    payloads = []
    for (lx, lz), nbt in chunks.items():
        data = zlib.compress(nbt)
        blob = struct.pack(">I", len(data) + 1) + b"\x02" + data
        count = (len(blob) + 4095) // 4096
        header[4 * (lx + 32 * lz):4 * (lx + 32 * lz) + 4] = struct.pack(">I", sector << 8 | count)
        payloads.append(blob + b"\x00" * (count * 4096 - len(blob)))
        sector += count
    out.write(header)
    for p in payloads:
        out.write(p)
    return out.getvalue()


AIR = ("minecraft:air", {})
SHRINE = ("rhworld:shrine", {"prime_index": "3"})
GATE = ("rhworld:gate_warm", {})


class Decode(unittest.TestCase):
    def test_unpack_matches_pack_across_long_boundaries(self):
        for bits in (4, 5, 6, 7, 9):
            indices = [(i * 7 + 3) % (1 << bits) for i in range(4096)]
            self.assertEqual(mca.unpack(pack(indices, bits), bits, 4096), indices, bits)

    def test_rhworld_blocks_and_hash(self):
        indices = [0] * 4096
        indices[5 * 256 + 2 * 16 + 1] = 1
        indices[15 * 256 + 15 * 16 + 15] = 2
        nbt = chunk_nbt(-1, 2, [section(-4, [AIR, SHRINE, GATE], indices), section(0, [AIR], [])])
        raw = region({(31, 2): nbt})
        chunks = list(mca.read_region(raw))
        self.assertEqual(len(chunks), 1)
        blocks = mca.rhworld_blocks(chunks[0])
        self.assertEqual(blocks, [(-15, -64 + 5, 32 + 2, "rhworld:shrine[prime_index=3]"),
                                  (-1, -64 + 15, 32 + 15, "rhworld:gate_warm")])
        lines = "".join(sorted(f"{x} {y} {z} {s}\n" for x, y, z, s in blocks))
        self.assertEqual(mca.chunk_hash(blocks), hashlib.sha256(lines.encode()).hexdigest())

    def test_properties_sorted_in_state(self):
        self.assertEqual(mca.state_string("rhworld:x", {"b": "1", "a": "2"}), "rhworld:x[a=2,b=1]")

    def test_palette_entry_forms(self):
        self.assertEqual(mca.palette_state("minecraft:stone"), "minecraft:stone")
        self.assertEqual(mca.palette_state({"": "rhworld:gate_cold"}), "rhworld:gate_cold")
        self.assertEqual(mca.palette_state({"": "rhworld:shrine"}), "rhworld:shrine[prime_index=0]")
        self.assertEqual(mca.palette_state({"id": "rhworld:shrine", "properties": {"prime_index": "7"}}),
                         "rhworld:shrine[prime_index=7]")

    def test_blocks_outside_piece_boxes(self):
        boxes = [[-9, 61, -9, 9, 68, 9], [47, 61, -1, 49, 68, 1]]
        blocks = [(0, 61, 0, "rhworld:hub_stone"), (48, 68, 0, "rhworld:shrine[prime_index=0]"),
                  (48, 69, 0, "rhworld:shrine[prime_index=1]"), (20, 61, 0, "rhworld:sigil")]
        self.assertEqual(mca.outside(blocks, boxes), blocks[2:])

    def test_shrine_pieces_hold_their_own_prime(self):
        pieces = [{"id": "rhworld:shrine", "BB": [10 * i, 0, 0, 10 * i + 2, 7, 2], "prime_index": i} for i in range(3)]
        good = [(10 * i + 1, 3, 1, f"rhworld:shrine[prime_index={i}]") for i in range(3)]
        self.assertEqual(mca.shrine_mismatches(good, pieces), [])
        swapped = [good[0], (11, 3, 1, "rhworld:shrine[prime_index=2]"), good[2]]
        self.assertEqual(mca.shrine_mismatches(swapped, pieces), [(1, [2])])
        self.assertEqual(mca.shrine_mismatches(good[:2], pieces), [(2, [])])

    def test_absent_chunk_is_skipped(self):
        self.assertEqual(list(mca.read_region(bytes(8192))), [])


if __name__ == "__main__":
    unittest.main()
