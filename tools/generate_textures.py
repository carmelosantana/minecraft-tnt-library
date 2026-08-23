#!/usr/bin/env python3
"""
Generates tnt-library block textures.

The art is code rather than a binary blob on purpose: a 16x16 block face is small enough that a
pixel map is more reviewable in a diff than a PNG, and regenerating is deterministic. Run:

    python3 tools/generate_textures.py

Every face is written to BOTH the Java and Bedrock pack trees from a single rendered image. That
is deliberate -- the two editions must show the same art, and copying from one source removes the
chance of them drifting.

Texture rules that are not negotiable (Minecraft enforces them, not us):
  * square, power-of-two dimensions. A single non-power-of-two texture in a pack degrades or
    disables mipmapping GAME-WIDE, not just for that block.
  * RGBA. Every pixel here is opaque (alpha 255) because these are BLOCK faces -- a block face is
    never see-through. Alpha 0 is only for item sprites, which this file does not produce.

Adding another bomb: append an entry to BOMBS with three 16x16 pixel-maps (top/side/bottom) built
from PALETTE keys, then re-run. The writer creates the block/ and blocks/ directories as needed.
"""

from pathlib import Path

from PIL import Image

REPO = Path(__file__).resolve().parent.parent

# Where each edition keeps block textures. {name} is filled per face, e.g. "waterbomb_top".
JAVA_BLOCK_DIR = REPO / "src/main/resources/pack/assets/tnt_library/textures/block"
BEDROCK_BLOCK_DIR = REPO / "src/main/resources/bedrock/textures/blocks"

# Owner-approved Water Bomb palette (docs/art/ART_DIRECTION.md B.1). RGB from the spec, alpha 255
# on every tone: block faces are opaque. No transparent key exists in this file on purpose.
PALETTE = {
    "N": (0x0A, 0x2A, 0x4A, 255),  # border / outline navy
    "S": (0x14, 0x56, 0x8C, 255),  # shadow / banded cap
    "B": (0x1E, 0x7F, 0xC8, 255),  # body blue
    "H": (0x4F, 0xC3, 0xE8, 255),  # highlight cyan (ripples, bubbles)
    "F": (0xBF, 0xEF, 0xFF, 255),  # foam -- sparse accent only
}

# --- Water Bomb -------------------------------------------------------------------------------

# TOP: water surface seen from above. Body blue filled, two concentric rippled highlight rings,
# a 2x2 foam glint at the very center, 1-px navy outline on all four edges.
WATERBOMB_TOP = [
    "NNNNNNNNNNNNNNNN",
    "NBBBBBBBBBBBBBBN",
    "NBBBHHHHHHHHBBBN",
    "NBBHBBBBBBBBHBBN",
    "NBBHBBBBBBBBHBBN",
    "NBBHBBHHHHBBHBBN",
    "NBBHBHBBBBHBHBBN",
    "NBBHBHBFFBHBHBBN",
    "NBBHBHBFFBHBHBBN",
    "NBBHBHBBBBHBHBBN",
    "NBBHBBHHHHBBHBBN",
    "NBBHBBBBBBBBHBBN",
    "NBBHBBBBBBBBHBBN",
    "NBBBHHHHHHHHBBBN",
    "NBBBBBBBBBBBBBBN",
    "NNNNNNNNNNNNNNNN",
]

# SIDE: vertical blue body with darker banded caps top and bottom (the vanilla-TNT read), and a
# centered bubble cluster (highlight cyan) carrying a small foam glint where the TNT label sits.
# 1-px navy outline on all four edges.
WATERBOMB_SIDE = [
    "NNNNNNNNNNNNNNNN",
    "NSSSSSSSSSSSSSSN",
    "NSSSSSSSSSSSSSSN",
    "NSSSSSSSSSSSSSSN",
    "NBBBBBBBBBBBBBBN",
    "NBBBBBBHHBBBBBBN",
    "NBBBBBFFHHBBBBBN",
    "NBBBBHHHHHHBBBBN",
    "NBBBBBHHHHBHBBBN",
    "NBBBHBBHHBBBBBBN",
    "NBBBBBBBHBBBBBBN",
    "NBBBBBBBBBBBBBBN",
    "NSSSSSSSSSSSSSSN",
    "NSSSSSSSSSSSSSSN",
    "NSSSSSSSSSSSSSSN",
    "NNNNNNNNNNNNNNNN",
]

# BOTTOM: calmer, flatter blue with a few faint darker ripple dashes, 1-px navy band on all edges.
WATERBOMB_BOTTOM = [
    "NNNNNNNNNNNNNNNN",
    "NBBBBBBBBBBBBBBN",
    "NBBBBBBBBBBBBBBN",
    "NBBBBSSSBBBBBBBN",
    "NBBBBBBBBBBBBBBN",
    "NBBBBBBBBSSSBBBN",
    "NBBBBBBBBBBBBBBN",
    "NBBBSSBBBBBBBBBN",
    "NBBBBBBBBBBBBBBN",
    "NBBBBBBBSSSBBBBN",
    "NBBBBBBBBBBBBBBN",
    "NBBBBBSSBBBBBBBN",
    "NBBBBBBBBBBBBBBN",
    "NBBBBBBBBBBBBBBN",
    "NBBBBBBBBBBBBBBN",
    "NNNNNNNNNNNNNNNN",
]

# Registry: {bomb name: {face: pixel-map}}. Faces render to <name>_<face>.png in both trees.
BOMBS = {
    "waterbomb": {
        "top": WATERBOMB_TOP,
        "side": WATERBOMB_SIDE,
        "bottom": WATERBOMB_BOTTOM,
    },
}


def render(rows, palette):
    size = len(rows)
    if any(len(row) != size for row in rows):
        raise ValueError("texture must be square")
    if size & (size - 1):
        raise ValueError(f"texture side {size} is not a power of two")

    image = Image.new("RGBA", (size, size))
    pixels = image.load()
    for y, row in enumerate(rows):
        for x, key in enumerate(row):
            if key not in palette:
                raise KeyError(f"row {y} column {x} uses undefined palette key {key!r}")
            color = palette[key]
            if color[3] != 255:
                raise ValueError(f"block face pixel {key!r} is not opaque (alpha {color[3]})")
            pixels[x, y] = color
    return image


def main():
    for name, faces in BOMBS.items():
        for face, rows in faces.items():
            image = render(rows, PALETTE)
            filename = f"{name}_{face}.png"
            for directory in (JAVA_BLOCK_DIR, BEDROCK_BLOCK_DIR):
                directory.mkdir(parents=True, exist_ok=True)
                target = directory / filename
                image.save(target, "PNG", optimize=True)
                print(f"wrote {target.relative_to(REPO)} ({image.width}x{image.height} {image.mode})")


if __name__ == "__main__":
    main()
