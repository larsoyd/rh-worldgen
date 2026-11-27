import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

from rhworld.arith import jacobi
from rhworld.fog import g1_witness, g2_witness, side
from rhworld.gm import make_gm
from rhworld.__main__ import G1_MARK, G1_RADIUS, G2_MARK, G2_RADIUS
from rhworld.world import CLASSES, LEDGER_BACKGROUND, LEDGER_CELL, LEDGER_HEIGHT, PLAYER, class_map, layer, ledger_primes, render_gm, render_ledger, render_player, tile, zoom


def run(*args):
    return subprocess.run([sys.executable, "-m", "rhworld", *map(str, args)],
                          capture_output=True, text=True, timeout=240)


class Layers(unittest.TestCase):
    def test_tile_numbering_is_row_major_from_one(self):
        self.assertEqual(tile(0, 0, 10), 1)
        self.assertEqual(tile(0, 9, 10), 10)
        self.assertEqual(tile(3, 4, 10), 35)

    def test_layer_is_the_symbol_at_each_tile(self):
        p, q = make_gm(512, 1)
        n, s = p * q, 37
        v, h = layer(n, s), layer(p, s)
        for r in range(s):
            for c in range(s):
                x = tile(r, c, s)
                self.assertEqual(v[r, c], jacobi(x, n))
                self.assertEqual(h[r, c], jacobi(x, p))
                self.assertEqual(v[r, c] * h[r, c], jacobi(x, q))

    def test_class_map_covers_four_classes(self):
        v = np.array([[1, 1], [-1, -1]], dtype=np.int8)
        h = np.array([[1, -1], [1, -1]], dtype=np.int8)
        self.assertEqual(class_map(v, h).tolist(), [[0, 1], [2, 3]])
        self.assertEqual(len({c.rgb for c in CLASSES}), 4)
        self.assertEqual(len({c.rgb for c in PLAYER}), 2)


class Render(unittest.TestCase):
    def test_gm_view_colours_follow_classes(self):
        v = np.array([[1, 1], [-1, -1]], dtype=np.int8)
        h = np.array([[1, -1], [1, -1]], dtype=np.int8)
        px = np.asarray(render_gm(v, h, marks=[]))
        self.assertEqual([tuple(px[r, c]) for r in (0, 1) for c in (0, 1)], [c.rgb for c in CLASSES])

    def test_player_view_ignores_hidden_layer(self):
        v = np.array([[1, -1], [-1, 1]], dtype=np.int8)
        px = np.asarray(render_player(v, marks=[]))
        self.assertEqual(tuple(px[0, 0]), PLAYER[0].rgb)
        self.assertEqual(tuple(px[0, 1]), PLAYER[1].rgb)

    def test_mark_outlines_tile_without_covering_it(self):
        v = np.ones((21, 21), dtype=np.int8)
        px = np.asarray(render_player(v, marks=[(tile(10, 10, 21), (255, 0, 0), 3)]))
        self.assertEqual(tuple(px[10, 10]), PLAYER[0].rgb)
        self.assertEqual(tuple(px[7, 10]), (255, 0, 0))
        self.assertEqual(tuple(px[0, 0]), PLAYER[0].rgb)
        self.assertEqual(int((px == (255, 0, 0)).all(axis=2).sum()), 24)

    def test_two_marks_on_one_tile_both_show(self):
        v = np.ones((21, 21), dtype=np.int8)
        px = np.asarray(render_player(v, marks=[(tile(10, 10, 21), (255, 0, 0), 3), (tile(10, 10, 21), (0, 0, 255), 5)]))
        self.assertEqual(tuple(px[7, 10]), (255, 0, 0))
        self.assertEqual(tuple(px[5, 10]), (0, 0, 255))

    def test_mark_at_map_edge_keeps_tile_visible(self):
        v = np.ones((21, 21), dtype=np.int8)
        px = np.asarray(render_player(v, marks=[(2, (255, 0, 0), 3)]))
        self.assertEqual(tuple(px[0, 1]), PLAYER[0].rgb)
        self.assertEqual(tuple(px[3, 1]), (255, 0, 0))
        self.assertEqual(tuple(px[0, 4]), (255, 0, 0))


class Zoom(unittest.TestCase):
    def test_zoom_is_an_exact_upscale_of_the_window(self):
        rng = np.random.default_rng(0)
        img = Image.fromarray(rng.integers(0, 255, (60, 200, 3), dtype=np.uint8))
        z = np.asarray(zoom(img, tile(0, 150, 200), half=20, scale=4))
        src = np.asarray(img)[0:40, 130:170]
        self.assertEqual(z.shape, (160, 160, 3))
        self.assertTrue((z == src.repeat(4, axis=0).repeat(4, axis=1)).all())

    def test_zoom_centres_on_inner_tile_and_clamps_right(self):
        rng = np.random.default_rng(1)
        arr = rng.integers(0, 255, (100, 100, 3), dtype=np.uint8)
        img = Image.fromarray(arr)
        z = np.asarray(zoom(img, tile(50, 50, 100), half=10, scale=1))
        self.assertTrue((z == arr[40:60, 40:60]).all())
        z = np.asarray(zoom(img, tile(95, 98, 100), half=10, scale=1))
        self.assertTrue((z == arr[80:100, 80:100]).all())

    def test_zoom_clips_at_left_edge(self):
        img = Image.fromarray(np.zeros((50, 50, 3), dtype=np.uint8))
        self.assertEqual(zoom(img, 3, half=10, scale=2).size, (40, 40))


class Ledger(unittest.TestCase):
    def test_primes_in_order_up_to_limit(self):
        self.assertEqual(ledger_primes(30), [2, 3, 5, 7, 11, 13, 17, 19, 23, 29])

    def test_each_prime_column_has_its_class_colour_and_witnesses_are_marked(self):
        p, q = make_gm(512, 0, rigged=True)
        n = p * q
        primes = ledger_primes(400)
        g1, g2 = g1_witness(p, n), g2_witness(n)
        self.assertNotEqual(g1, g2)
        px = np.asarray(render_ledger(n, p, primes, [(g1, G1_MARK), (g2, G2_MARK)]))
        board = np.asarray(render_gm(layer(n, 20), layer(p, 20), marks=[]))
        cell, height = LEDGER_CELL, LEDGER_HEIGHT
        self.assertEqual(px.shape, (height + 2 * cell, cell * len(primes), 3))
        for k, l in enumerate(primes):
            want = tuple(board[divmod(l - 1, 20)])
            self.assertEqual(tuple(px[height // 2, k * cell + cell // 2]), want, l)
            row1 = tuple(px[height + cell // 2, k * cell + cell // 2])
            row2 = tuple(px[height + cell + cell // 2, k * cell + cell // 2])
            self.assertEqual(row1, G1_MARK if l == g1 else LEDGER_BACKGROUND, l)
            self.assertEqual(row2, G2_MARK if l == g2 else LEDGER_BACKGROUND, l)

    def test_two_marks_on_one_prime_both_show(self):
        px = np.asarray(render_ledger(15015 * (2 ** 521 - 1), 2 ** 521 - 1, [2, 3], [(3, G1_MARK), (3, G2_MARK)]))
        self.assertEqual(tuple(px[LEDGER_HEIGHT + 1, LEDGER_CELL + 1]), G1_MARK)
        self.assertEqual(tuple(px[LEDGER_HEIGHT + LEDGER_CELL + 1, LEDGER_CELL + 1]), G2_MARK)


class Cli(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def make(self, name, *flags):
        gm = self.dir / f"{name}.json"
        r = run("gm", "--bits", 512, "--seed", 3, *flags, "--out", gm)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        return gm

    def test_world_writes_maps_and_report_deterministically(self):
        gm = self.make("gm", "--rigged")
        outs = []
        for k in range(2):
            out = self.dir / f"w{k}"
            r = run("world", "--gm", gm, "--out", out)
            self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
            self.assertIn("[world] wrote", r.stdout)
            outs.append(out)
        for name in ("player.png", "gm.png", "gm_zoom.png", "player_zoom.png", "ledger.png", "report.json"):
            self.assertEqual((outs[0] / name).read_bytes(), (outs[1] / name).read_bytes(), name)
        rep = json.loads((outs[0] / "report.json").read_text())
        data = json.loads(gm.read_text())
        p, q, n = int(data["p"]), int(data["q"]), int(data["n"])
        self.assertEqual(rep["n_bits"], n.bit_length())
        self.assertEqual(rep["side"], side(n))
        self.assertLess(rep["g1_p"], rep["budget"])
        self.assertEqual(jacobi(rep["g1_p"], p), -1)
        self.assertEqual(jacobi(rep["g1_q"], q), -1)
        self.assertEqual(jacobi(rep["g2"], n), -1)
        self.assertGreaterEqual(rep["hash_chain_index"], 1)
        self.assertEqual(sum(rep["tiles"].values()), side(n) ** 2)
        self.assertNotIn(str(p), (outs[0] / "report.json").read_text())
        s = side(n)
        with Image.open(outs[0] / "gm.png") as im:
            self.assertEqual(im.size, (s, s))
            px = np.asarray(im)
        want = class_map(layer(n, s), layer(p, s))
        palette = np.array([c.rgb for c in CLASSES], dtype=np.uint8)
        rows, cols = np.indices((s, s))
        ring = np.zeros((s, s), dtype=bool)
        for x, radius in ((rep["g1_p"], G1_RADIUS), (rep["g2"], G2_RADIUS)):
            r, c = divmod(x - 1, s)
            ring |= np.maximum(abs(rows - r), abs(cols - c)) == radius
        self.assertTrue((px[~ring] == palette[want][~ring]).all())
        self.assertTrue(np.isin(px[ring].tolist(), [G1_MARK, G2_MARK]).all())
        counts = want.ravel().tolist()
        self.assertEqual(rep["tiles"], {c.name: counts.count(k) for k, c in enumerate(CLASSES)})

    def test_player_view_from_modulus_alone_matches(self):
        gm = self.make("gm")
        n = json.loads(gm.read_text())["n"]
        r1 = run("world", "--gm", gm, "--out", self.dir / "w")
        r2 = run("player", "--n", n, "--out", self.dir / "p")
        self.assertEqual(r1.returncode, 0, r1.stderr)
        self.assertEqual(r2.returncode, 0, r2.stderr)
        self.assertEqual((self.dir / "w" / "player.png").read_bytes(), (self.dir / "p" / "player.png").read_bytes())
        self.assertFalse((self.dir / "p" / "gm.png").exists())

    def test_refusal_is_reported_and_exit_code_two(self):
        gm = self.dir / "bad.json"
        p = 2 ** 607 - 1
        gm.write_text(json.dumps({"p": str(p), "q": str(p), "n": str(p * p)}))
        r = run("world", "--gm", gm, "--out", self.dir / "w")
        self.assertEqual(r.returncode, 2, r.stdout + r.stderr)
        self.assertIn("refused: jacobi character is principal", r.stdout)
        self.assertFalse((self.dir / "w").exists())

    def test_composite_gm_factor_refused_before_any_scan_or_file(self):
        gm = self.dir / "bad.json"
        p, q = (2 ** 521 - 1) ** 2, 2 ** 607 - 1
        gm.write_text(json.dumps({"p": str(p), "q": str(q), "n": str(p * q)}))
        r = run("world", "--gm", gm, "--out", self.dir / "w")
        self.assertEqual(r.returncode, 2, r.stdout + r.stderr)
        self.assertIn("refused: gm factor not prime", r.stdout)
        self.assertNotIn("[check]", r.stdout)
        self.assertFalse((self.dir / "w").exists())

    def test_late_refusal_writes_no_files(self):
        gm = self.make("gm")
        r = subprocess.run([sys.executable, "-c",
                            "import sys, rhworld.fog as f, rhworld.__main__ as m\n"
                            "f.g1_witness = lambda *a: (_ for _ in ()).throw(f.Refused('budget exceeded'))\n"
                            "m.g1_witness = f.g1_witness\n"
                            "sys.exit(m.main(sys.argv[1:]))",
                            "world", "--gm", str(gm), "--out", str(self.dir / "w")],
                           capture_output=True, text=True, timeout=240)
        self.assertEqual(r.returncode, 2, r.stdout + r.stderr)
        self.assertIn("refused: budget exceeded", r.stdout)
        self.assertFalse((self.dir / "w").exists())

    def test_gm_file_must_match_modulus(self):
        gm = self.make("gm")
        data = json.loads(gm.read_text())
        data["n"] = str(int(data["n"]) + 2)
        gm.write_text(json.dumps(data))
        r = run("world", "--gm", gm, "--out", self.dir / "w")
        self.assertEqual(r.returncode, 2)
        self.assertIn("refused: gm file factors do not match n", r.stdout)
