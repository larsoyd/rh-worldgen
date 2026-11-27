import numpy as np
from PIL import Image


def save_heightmap(heights, path):
    h = np.asarray(heights, dtype=float)
    if not np.isfinite(h).all():
        raise ValueError(f"non-finite heights count={int((~np.isfinite(h)).sum())}")
    span = h.max() - h.min()
    scaled = (h - h.min()) / span if span > 0 else np.zeros_like(h)
    Image.fromarray(np.round(scaled * 255).astype(np.uint8), mode="L").save(path)
