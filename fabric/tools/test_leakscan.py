import gzip
import json
import os
import struct
import subprocess
import sys
import tempfile
import unittest
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
P = 110702446207043290462190014379
Q = 109578028768522011556722153023


def write_region(path, payload):
    data = zlib.compress(payload)
    chunk = struct.pack(">IB", len(data) + 1, 2) + data
    header = struct.pack(">I", (2 << 8) | 1) + bytes(4092) + bytes(4096)
    with open(path, "wb") as f:
        f.write(header + chunk + bytes(-len(chunk) % 4096))


class LeakScanTest(unittest.TestCase):
    def scan(self, root):
        key = os.path.join(root, "key.json")
        with open(key, "w") as f:
            json.dump({"n": str(P * Q), "p": str(P), "q": str(Q)}, f)
        world = os.path.join(root, "run")
        return subprocess.run([sys.executable, "-I", os.path.join(HERE, "leakscan.py"), key, world],
                              capture_output=True, text=True, timeout=60)

    def tree(self, root):
        os.makedirs(os.path.join(root, "run", "world", "region"))
        os.makedirs(os.path.join(root, "run", "logs"))
        with open(os.path.join(root, "run", "logs", "latest.log"), "w") as f:
            f.write("[rhworld] event=load result=ok\n")
        return os.path.join(root, "run")

    def test_clean_tree_passes(self):
        with tempfile.TemporaryDirectory() as root:
            run = self.tree(root)
            write_region(os.path.join(run, "world", "region", "r.0.0.mca"), b"nothing here")
            r = self.scan(root)
            self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
            self.assertIn("[rhworld-leak] verdict=pass", r.stdout)

    def test_decimal_in_log_is_found(self):
        with tempfile.TemporaryDirectory() as root:
            run = self.tree(root)
            with open(os.path.join(run, "logs", "latest.log"), "a") as f:
                f.write("debug q=%d\n" % Q)
            r = self.scan(root)
            self.assertEqual(r.returncode, 1, r.stdout)
            self.assertIn("factor=q form=decimal", r.stdout)

    def test_hex_inside_compressed_chunk_is_found(self):
        with tempfile.TemporaryDirectory() as root:
            run = self.tree(root)
            write_region(os.path.join(run, "world", "region", "r.0.0.mca"), ("x%xy" % P).encode())
            r = self.scan(root)
            self.assertEqual(r.returncode, 1, r.stdout)
            self.assertIn("factor=p form=hex", r.stdout)

    def test_raw_bytes_inside_gzip_dat_are_found(self):
        with tempfile.TemporaryDirectory() as root:
            run = self.tree(root)
            raw = P.to_bytes((P.bit_length() + 7) // 8, "big")
            with gzip.open(os.path.join(run, "world", "level.dat"), "wb") as f:
                f.write(b"\x0a\x00\x00" + raw)
            r = self.scan(root)
            self.assertEqual(r.returncode, 1, r.stdout)
            self.assertIn("factor=p form=bytes", r.stdout)

    def test_missing_key_is_refused(self):
        with tempfile.TemporaryDirectory() as root:
            r = subprocess.run([sys.executable, "-I", os.path.join(HERE, "leakscan.py"),
                                os.path.join(root, "nope.json"), root], capture_output=True, text=True, timeout=60)
            self.assertEqual(r.returncode, 2)
            self.assertIn("refused:", r.stdout)


if __name__ == "__main__":
    unittest.main()
