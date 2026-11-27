from typing import NamedTuple

import numpy as np
from PIL import Image

from rhworld.arith import jacobi


class Terrain(NamedTuple):
    name: str
    rgb: tuple


CLASSES = (
    Terrain("plains", (196, 210, 120)),
    Terrain("forest", (46, 110, 52)),
    Terrain("shallows", (120, 180, 220)),
    Terrain("deep water", (24, 60, 130)),
)
PLAYER = (Terrain("land", (176, 168, 140)), Terrain("water", (96, 120, 150)))


def tile(row, col, s):
    return 1 + row * s + col


def layer(m, s):
    values = [jacobi(x, m) for x in range(1, s * s + 1)]
    return np.array(values, dtype=np.int8).reshape(s, s)


def class_map(v, h):
    return ((v == -1) * 2 + (h == -1)).astype(np.uint8)


def paint(index, palette, marks):
    rgb = np.array([t.rgb for t in palette], dtype=np.uint8)[index]
    rows, cols = np.indices(index.shape)
    for x, colour, radius in marks:
        r, c = divmod(x - 1, index.shape[1])
        rgb[np.maximum(abs(rows - r), abs(cols - c)) == radius] = colour
    return Image.fromarray(rgb)


def render_gm(v, h, marks):
    return paint(class_map(v, h), CLASSES, marks)


def render_player(v, marks):
    return paint((v == -1).astype(np.uint8), PLAYER, marks)


def zoom(img, x, half=40, scale=8):
    w, h = img.size
    r, c = divmod(x - 1, w)
    top = min(max(r - half, 0), max(h - 2 * half, 0))
    left = min(max(c - half, 0), max(w - 2 * half, 0))
    box = (left, top, min(left + 2 * half, w), min(top + 2 * half, h))
    crop = img.crop(box)
    return crop.resize((crop.width * scale, crop.height * scale), Image.Resampling.NEAREST)
