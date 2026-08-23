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
# Bedrock textures live under the Geyser pack tree (geyser/pack/**), which is what the plugin bundles
# and installs into Geyser-Spigot -- NOT the old top-level bedrock/ tree from before the Geyser
# Custom Blocks re-architecture.
JAVA_BLOCK_DIR = REPO / "src/main/resources/pack/assets/tnt_library/textures/block"
BEDROCK_BLOCK_DIR = REPO / "src/main/resources/geyser/pack/textures/blocks"

# All palette tones across every bomb, RGB from docs/art/ART_DIRECTION.md, alpha 255 on every tone:
# block faces are opaque. No transparent key exists in this file on purpose. Keys are unique across
# bombs so one flat PALETTE serves every pixel-map.
PALETTE = {
    # --- Water Bomb (ART_DIRECTION B.1) ---
    "N": (0x0A, 0x2A, 0x4A, 255),  # border / outline navy
    "S": (0x14, 0x56, 0x8C, 255),  # shadow / banded cap
    "B": (0x1E, 0x7F, 0xC8, 255),  # body blue
    "H": (0x4F, 0xC3, 0xE8, 255),  # highlight cyan (ripples, bubbles)
    "F": (0xBF, 0xEF, 0xFF, 255),  # foam -- sparse accent only
    # --- The Twins (ART_DIRECTION B.2): pure monochrome, zero mid-tones. K/W are shared by both
    # variants (black body == other's band); D/G are the one soft tone each variant carries. ---
    "W": (0xF5, 0xF5, 0xF5, 255),  # white body / white band
    "K": (0x11, 0x11, 0x11, 255),  # black body / black band
    "D": (0xD0, 0xD0, 0xD0, 255),  # White Twin soft shadow (inner bevel)
    "G": (0x33, 0x33, 0x33, 255),  # Black Twin soft highlight (inner bevel)
    # --- Smart Bomb (ART_DIRECTION B.5): command-block teal + lit core ---
    "Q": (0x0E, 0x2E, 0x2E, 255),  # dark-teal border / band
    "T": (0x1C, 0x5C, 0x56, 255),  # teal plate
    "C": (0x2E, 0x8B, 0x7F, 255),  # circuit-teal trace
    "P": (0x4A, 0x54, 0x58, 255),  # grey plate (rivets, underside)
    "E": (0x79, 0xC7, 0xB8, 255),  # etched-line highlight
    "O": (0xB6, 0xFF, 0xEE, 255),  # core glow
    "A": (0xFF, 0xC2, 0x4D, 255),  # lit core amber
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

# --- The Twins: White (twins_white) -----------------------------------------------------------
# White body, black label band on the side; near-white caps with a black banded edge and a thin
# soft-grey inner bevel so the cube still reads with depth. Extreme contrast, no mid-tones.

TWINS_WHITE_TOP = [
    "KKKKKKKKKKKKKKKK",
    "KDDDDDDDDDDDDDDK",
    "KDWWWWWWWWWWWWDK",
    "KDWWWWWWWWWWWWDK",
    "KDWWWWWWWWWWWWDK",
    "KDWWWWWWWWWWWWDK",
    "KDWWWWWWWWWWWWDK",
    "KDWWWWWWWWWWWWDK",
    "KDWWWWWWWWWWWWDK",
    "KDWWWWWWWWWWWWDK",
    "KDWWWWWWWWWWWWDK",
    "KDWWWWWWWWWWWWDK",
    "KDWWWWWWWWWWWWDK",
    "KDWWWWWWWWWWWWDK",
    "KDDDDDDDDDDDDDDK",
    "KKKKKKKKKKKKKKKK",
]

TWINS_WHITE_SIDE = [
    "KKKKKKKKKKKKKKKK",
    "KWWWWWWWWWWWWWWK",
    "KWWWWWWWWWWWWWWK",
    "KWWWWWWWWWWWWWWK",
    "KWWWWWWWWWWWWWWK",
    "KWWWWWWWWWWWWWWK",
    "KKKKKKKKKKKKKKKK",
    "KKKKKKKKKKKKKKKK",
    "KKKKKKKKKKKKKKKK",
    "KKKKKKKKKKKKKKKK",
    "KWWWWWWWWWWWWWWK",
    "KWWWWWWWWWWWWWWK",
    "KWWWWWWWWWWWWWWK",
    "KWWWWWWWWWWWWWWK",
    "KWWWWWWWWWWWWWWK",
    "KKKKKKKKKKKKKKKK",
]

TWINS_WHITE_BOTTOM = [
    "KKKKKKKKKKKKKKKK",
    "KWWWWWWWWWWWWWWK",
    "KWWWWWWWWWWWWWWK",
    "KWWWWWWWWWWWWWWK",
    "KWWWWWWWWWWWWWWK",
    "KWWWWWWWWWWWWWWK",
    "KWWWWWWWWWWWWWWK",
    "KWWWWWWWWWWWWWWK",
    "KWWWWWWWWWWWWWWK",
    "KWWWWWWWWWWWWWWK",
    "KWWWWWWWWWWWWWWK",
    "KWWWWWWWWWWWWWWK",
    "KWWWWWWWWWWWWWWK",
    "KWWWWWWWWWWWWWWK",
    "KWWWWWWWWWWWWWWK",
    "KKKKKKKKKKKKKKKK",
]

# --- The Twins: Black (twins_black) -- exact inverse of White ----------------------------------

TWINS_BLACK_TOP = [
    "WWWWWWWWWWWWWWWW",
    "WGGGGGGGGGGGGGGW",
    "WGKKKKKKKKKKKKGW",
    "WGKKKKKKKKKKKKGW",
    "WGKKKKKKKKKKKKGW",
    "WGKKKKKKKKKKKKGW",
    "WGKKKKKKKKKKKKGW",
    "WGKKKKKKKKKKKKGW",
    "WGKKKKKKKKKKKKGW",
    "WGKKKKKKKKKKKKGW",
    "WGKKKKKKKKKKKKGW",
    "WGKKKKKKKKKKKKGW",
    "WGKKKKKKKKKKKKGW",
    "WGKKKKKKKKKKKKGW",
    "WGGGGGGGGGGGGGGW",
    "WWWWWWWWWWWWWWWW",
]

TWINS_BLACK_SIDE = [
    "WWWWWWWWWWWWWWWW",
    "WKKKKKKKKKKKKKKW",
    "WKKKKKKKKKKKKKKW",
    "WKKKKKKKKKKKKKKW",
    "WKKKKKKKKKKKKKKW",
    "WKKKKKKKKKKKKKKW",
    "WWWWWWWWWWWWWWWW",
    "WWWWWWWWWWWWWWWW",
    "WWWWWWWWWWWWWWWW",
    "WWWWWWWWWWWWWWWW",
    "WKKKKKKKKKKKKKKW",
    "WKKKKKKKKKKKKKKW",
    "WKKKKKKKKKKKKKKW",
    "WKKKKKKKKKKKKKKW",
    "WKKKKKKKKKKKKKKW",
    "WWWWWWWWWWWWWWWW",
]

TWINS_BLACK_BOTTOM = [
    "WWWWWWWWWWWWWWWW",
    "WKKKKKKKKKKKKKKW",
    "WKKKKKKKKKKKKKKW",
    "WKKKKKKKKKKKKKKW",
    "WKKKKKKKKKKKKKKW",
    "WKKKKKKKKKKKKKKW",
    "WKKKKKKKKKKKKKKW",
    "WKKKKKKKKKKKKKKW",
    "WKKKKKKKKKKKKKKW",
    "WKKKKKKKKKKKKKKW",
    "WKKKKKKKKKKKKKKW",
    "WKKKKKKKKKKKKKKW",
    "WKKKKKKKKKKKKKKW",
    "WKKKKKKKKKKKKKKW",
    "WKKKKKKKKKKKKKKW",
    "WWWWWWWWWWWWWWWW",
]

# --- Smart Bomb (smartbomb): command-block look -----------------------------------------------
# Teal plate with etched circuit traces converging on a lit central core; grey corner rivets and a
# dark-teal banded border. TOP radiates a bright node; SIDE carries the amber-lit core in the label
# area; BOTTOM is a darker, sparser underside.

SMARTBOMB_TOP = [
    "QQQQQQQQQQQQQQQQ",
    "QPTTTTTTTTTTTTPQ",
    "QTTTTTTEETTTTTTQ",
    "QTTTTTTEETTTTTTQ",
    "QTTTTTTEETTTTTTQ",
    "QTTTTTTEETTTTTTQ",
    "QTTTTTOOOOTTTTTQ",
    "QTEEEEOOOOEEEETQ",
    "QTEEEEOOOOEEEETQ",
    "QTTTTTOOOOTTTTTQ",
    "QTTTTTTEETTTTTTQ",
    "QTTTTTTEETTTTTTQ",
    "QTTTTTTEETTTTTTQ",
    "QTTTTTTEETTTTTTQ",
    "QPTTTTTTTTTTTTPQ",
    "QQQQQQQQQQQQQQQQ",
]

SMARTBOMB_SIDE = [
    "QQQQQQQQQQQQQQQQ",
    "QPTTTTTTTTTTTTPQ",
    "QTTTTTTCCTTTTTTQ",
    "QTTTTTTCCTTTTTTQ",
    "QTTTTTTCCTTTTTTQ",
    "QTTTTTTEETTTTTTQ",
    "QTTTTTOOOOTTTTTQ",
    "QTCCCEOAAOECCCTQ",
    "QTCCCEOAAOECCCTQ",
    "QTTTTTOOOOTTTTTQ",
    "QTTTTTTEETTTTTTQ",
    "QTTTTTTCCTTTTTTQ",
    "QTTTTTTCCTTTTTTQ",
    "QTTTTTTCCTTTTTTQ",
    "QPTTTTTTTTTTTTPQ",
    "QQQQQQQQQQQQQQQQ",
]

SMARTBOMB_BOTTOM = [
    "QQQQQQQQQQQQQQQQ",
    "QPPPPPPPPPPPPPPQ",
    "QPPPPPPPPPPPPPPQ",
    "QPPPPPPCCPPPPPPQ",
    "QPPPPPPCCPPPPPPQ",
    "QPPPPPPPPPPPPPPQ",
    "QPPPPPPPPPPPPPPQ",
    "QPPPPPCCCCPPPPPQ",
    "QPPPPPCCCCPPPPPQ",
    "QPPPPPPPPPPPPPPQ",
    "QPPPPPPPPPPPPPPQ",
    "QPPPPPPCCPPPPPPQ",
    "QPPPPPPCCPPPPPPQ",
    "QPPPPPPPPPPPPPPQ",
    "QPPPPPPPPPPPPPPQ",
    "QQQQQQQQQQQQQQQQ",
]

# Registry: {bomb name: {face: pixel-map}}. Faces render to <name>_<face>.png in both trees.
BOMBS = {
    "waterbomb": {
        "top": WATERBOMB_TOP,
        "side": WATERBOMB_SIDE,
        "bottom": WATERBOMB_BOTTOM,
    },
    "twins_white": {
        "top": TWINS_WHITE_TOP,
        "side": TWINS_WHITE_SIDE,
        "bottom": TWINS_WHITE_BOTTOM,
    },
    "twins_black": {
        "top": TWINS_BLACK_TOP,
        "side": TWINS_BLACK_SIDE,
        "bottom": TWINS_BLACK_BOTTOM,
    },
    "smartbomb": {
        "top": SMARTBOMB_TOP,
        "side": SMARTBOMB_SIDE,
        "bottom": SMARTBOMB_BOTTOM,
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
