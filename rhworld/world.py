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
MARK_RADIUS = 3


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
    for x, colour in marks:
        r, c = divmod(x - 1, index.shape[1])
        rgb[np.maximum(abs(rows - r), abs(cols - c)) == MARK_RADIUS] = colour
    return Image.fromarray(rgb)


def render_gm(v, h, marks):
    return paint(class_map(v, h), CLASSES, marks)


def render_player(v, marks):
    return paint((v == -1).astype(np.uint8), PLAYER, marks)
