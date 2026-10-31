"""Cut the diamond visualizers out of the design mocks into transparent PNGs for the app.

    venv/bin/python design/tools/extract_diamonds.py

Background removal: estimate the dark background from the crop border, keep only
the light above it as alpha (so the glow composites correctly on any dark bg),
and feather the crop edges so no rectangle shows.
"""
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
MOCKS = ROOT / "design" / "ui-designs-mocks"
OUT = ROOT / "app-android" / "app" / "src" / "main" / "res" / "drawable-nodpi"

# name: (mock file, (left, top, right, bottom)) in mock pixels (852 x 1846)
CROPS = {
    "diamond_idle": ("5.png", (96, 300, 756, 960)),
    "diamond_listening": ("6.png", (40, 230, 812, 930)),
    "diamond_thinking": ("7.png", (70, 240, 782, 880)),
    "diamond_speaking": ("8.png", (20, 230, 832, 810)),
    "diamond_paused": ("9.png", (96, 280, 756, 880)),
    "diamond_error": ("12.png", (86, 290, 766, 870)),
    "diamond_welcome": ("1.png", (96, 280, 756, 960)),
    "diamond_small": ("2.png", (176, 190, 676, 520)),
}


def extract(src: Path, box: tuple[int, int, int, int]) -> Image.Image:
    img = np.asarray(Image.open(src).convert("RGB").crop(box)).astype(np.float32)
    h, w, _ = img.shape
    border = np.concatenate([img[:6].reshape(-1, 3), img[-6:].reshape(-1, 3), img[:, :6].reshape(-1, 3), img[:, -6:].reshape(-1, 3)])
    bg = np.percentile(border, 40, axis=0)
    diff = np.clip(img - bg, 0, None)
    alpha = np.clip(diff.max(axis=2) / (255 - bg.max()), 0, 1)
    alpha = np.clip(alpha * 1.15, 0, 1) ** 0.9  # keep faint glow visible
    # feather the crop edges
    fy = np.minimum(np.arange(h), np.arange(h)[::-1]) / (0.12 * h)
    fx = np.minimum(np.arange(w), np.arange(w)[::-1]) / (0.12 * w)
    feather = np.clip(np.minimum.outer(fy, fx), 0, 1)
    feather = feather * feather * (3 - 2 * feather)  # smoothstep
    alpha *= feather
    rgb = np.where(alpha[..., None] > 1e-3, diff / np.maximum(alpha[..., None], 1e-3), 0)
    rgb = np.clip(rgb, 0, 255)
    rgba = np.dstack([rgb, alpha * 255]).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (file, box) in CROPS.items():
        im = extract(MOCKS / file, box)
        im.save(OUT / f"{name}.png", optimize=True)
        print(f"{name}.png {im.size} {(OUT / f'{name}.png').stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
