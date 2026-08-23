#!/usr/bin/env python3
"""
Downscale an AI-generated concept texture to a true 16x16 RGBA game texture.

The ComfyUI models never paint a clean 16x16 directly (see docs/art/ART_DIRECTION.md). This is the
load-bearing conversion step: big stylized render -> honest 16x16 -> small flat palette.

    python3 docs/art/downscale_to_16.py in.png out.png [--size 16] [--colors 12]
                                        [--alpha-threshold 128] [--preview-scale 24] [--no-preview]

Why the choices here:
  * Downscale with a BOX/AREA filter (averages each source region). Bicubic re-introduces soft
    anti-aliased edges that read as mush at 16px; nearest-neighbor DOWNscaling aliases badly.
    Nearest-neighbor is correct only for UPscaling the preview.
  * Quantize to a small palette (--colors) to kill gradient banding and get the flat Minecraft
    look. Vanilla block faces use ~4-8 tones. --colors 0 skips quantization.
  * Emit true RGBA. Block faces are opaque (alpha 255). --alpha-threshold hard-snaps any partial
    alpha so a "transparent" background is really alpha 0, never white.

The output is a review pass, not the final word: zoom the preview and hand-fix the 1px border and
stray pixels afterward, per ART_DIRECTION.md.
"""

import argparse
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    sys.exit("Pillow is required:  pip install Pillow")


def center_square(img: Image.Image) -> Image.Image:
    w, h = img.size
    if w == h:
        return img
    side = min(w, h)
    left = (w - side) // 2
    top = (h - side) // 2
    return img.crop((left, top, left + side, top + side))


def downscale(img: Image.Image, size: int) -> Image.Image:
    # BOX averages source regions -> clean, low-noise small texture.
    return img.resize((size, size), Image.BOX)


def quantize(img: Image.Image, colors: int) -> Image.Image:
    if colors <= 0:
        return img
    # Split alpha out, quantize only RGB, then re-attach alpha (quantizing RGBA muddies edges).
    rgb = img.convert("RGB")
    q = rgb.quantize(colors=colors, method=Image.MEDIANCUT, dither=Image.NONE).convert("RGB")
    out = q.convert("RGBA")
    out.putalpha(img.getchannel("A"))
    return out


def snap_alpha(img: Image.Image, threshold: int) -> Image.Image:
    a = img.getchannel("A").point(lambda v: 255 if v >= threshold else 0)
    img.putalpha(a)
    return img


def main() -> None:
    ap = argparse.ArgumentParser(description="Downscale a concept texture to a 16x16 RGBA game texture.")
    ap.add_argument("src")
    ap.add_argument("dst")
    ap.add_argument("--size", type=int, default=16, help="output side (power of two; default 16)")
    ap.add_argument("--colors", type=int, default=12, help="palette size after downscale; 0 = skip (default 12)")
    ap.add_argument("--alpha-threshold", type=int, default=128,
                    help="alpha >= this -> 255, else 0; use 0 to leave alpha untouched (default 128)")
    ap.add_argument("--preview-scale", type=int, default=24, help="nearest-neighbor preview magnification (default 24)")
    ap.add_argument("--no-preview", action="store_true", help="do not write the *_preview.png")
    args = ap.parse_args()

    size = args.size
    if size & (size - 1):
        sys.exit(f"--size {size} is not a power of two")

    img = Image.open(args.src).convert("RGBA")
    img = center_square(img)
    img = downscale(img, size)
    img = quantize(img, args.colors)
    if args.alpha_threshold > 0:
        img = snap_alpha(img, args.alpha_threshold)

    dst = Path(args.dst)
    dst.parent.mkdir(parents=True, exist_ok=True)
    img.save(dst, "PNG", optimize=True)
    print(f"wrote {dst} ({img.width}x{img.height} RGBA, {args.colors or 'un'}quantized)")

    if not args.no_preview:
        preview = img.resize((size * args.preview_scale, size * args.preview_scale), Image.NEAREST)
        pv = dst.with_name(dst.stem + "_preview.png")
        preview.save(pv, "PNG")
        print(f"wrote {pv} ({preview.width}x{preview.height}, nearest-neighbor preview only)")


if __name__ == "__main__":
    main()
