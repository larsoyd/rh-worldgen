import subprocess
import sys
import tempfile
import unittest
import warnings
from pathlib import Path

import numpy as np
from PIL import Image

from rhworld.image import save_heightmap


class SaveHeightmap(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def test_png_keeps_shape_and_order(self):
        h = np.arange(12, dtype=float).reshape(3, 4)
        out = self.dir / "h.png"
        save_heightmap(h, out)
        with Image.open(out) as im:
            px = np.asarray(im)
        self.assertEqual(px.shape, (3, 4))
        self.assertEqual(px.dtype, np.uint8)
        self.assertEqual((px.min(), px.max()), (0, 255))
        self.assertTrue(np.all(np.diff(px.ravel().astype(int)) > 0), px)

    def test_flat_map_is_black_not_nan(self):
        out = self.dir / "flat.png"
        with warnings.catch_warnings():
            warnings.simplefilter("error")
            save_heightmap(np.full((2, 2), 7.0), out)
        with Image.open(out) as im:
            self.assertEqual(np.asarray(im).tolist(), [[0, 0], [0, 0]])

    def test_refuses_non_finite(self):
        with self.assertRaisesRegex(ValueError, "non-finite"):
            save_heightmap(np.array([[0.0, np.nan]]), self.dir / "bad.png")

    def test_cli_renders_npy(self):
        src, out = self.dir / "h.npy", self.dir / "h.png"
        np.save(src, np.arange(6, dtype=float).reshape(2, 3))
        r = subprocess.run([sys.executable, "-m", "rhworld", "render", str(src), str(out)],
                           capture_output=True, text=True, timeout=60)
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertIn("[render] wrote", r.stdout)
        with Image.open(out) as im:
            self.assertEqual(im.size, (3, 2))
