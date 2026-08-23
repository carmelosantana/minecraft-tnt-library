# TNT Library — Art Direction & ComfyUI Texture Pipeline

Reusable prompting template, per-bomb art direction, and the AI-to-16×16 pipeline for the six
custom explosives in **tnt-library**.

## 0. Target format (non-negotiable, inherited from `redstone-stuff`)

Every shipped texture is a **16×16 RGBA PNG**, square, power-of-two. Reference:
`redstone-stuff/tools/generate_textures.py`. Minecraft enforces these rules — a single
non-power-of-two texture degrades mipmapping pack-wide, and a "transparent" background must be
true alpha 0, not white.

A block needs **three faces**: `top`, `side`, `bottom`. Vanilla TNT is the baseline — a red body
with a "TNT" label band on the side, and darker banded top/bottom caps. Ours must read instantly
as "Minecraft TNT" but be **signature-colored per bomb**.

AI models do **not** paint clean 16×16 art directly. The pipeline is always:

> **Generate large & stylized → downscale nearest-neighbor to 16×16 → quantize the palette → hand-clean.**

The AI output is a *concept/reference* pass. The final asset is a small, deliberate palette. For
faces that are essentially flat bands (most tops/bottoms), hand-authoring an ASCII pixel-map in the
`generate_textures.py` style is often faster and cleaner than AI — use AI for the "hero" side face
and the busy motifs (circuitry, ripples, void motes).

---

## A. Reusable ComfyUI prompting template

### A.1 Positive prompt skeleton

Fill the `{{SLOTS}}`. Keep the leading and trailing style anchors verbatim — they are what force
the flat, game-ready look.

```
minecraft block texture, flat pixel art, single {{FACE}} face of a cube tile,
orthographic {{VIEW}} view, {{BOMB_NAME}} theme,
{{PALETTE_WORDS}} color palette,
{{MOTIF}},
{{EDGE_TREATMENT}},
crisp clean blocky pixels, bold dark outline edges, retro 16-bit game texture,
high contrast, centered filling the frame, no perspective
```

| Slot | Meaning | Example values |
|---|---|---|
| `{{FACE}}` | which cube face | `top`, `side`, `bottom` |
| `{{VIEW}}` | camera implied by the face | top→`top-down`, side→`front`, bottom→`bottom` |
| `{{BOMB_NAME}}` | short theme handle | `water bomb`, `gravity bomb`, `command-block smart bomb` |
| `{{PALETTE_WORDS}}` | 2–3 plain color words matching the hex palette | `aqua and deep blue`, `pure white and pale grey` |
| `{{MOTIF}}` | the one visual idea on this face | `rippling water surface`, `glowing circuit traces around a core` |
| `{{EDGE_TREATMENT}}` | how the border reads (echoes vanilla TNT banding) | `dark navy banded border like vanilla TNT top` |

### A.2 Recommended negative prompt (use on every generation)

```
blurry, soft focus, gradient banding, photorealistic, 3d render, antialiasing, noise,
jpeg artifacts, text, watermark, signature, drop shadow, perspective, glossy reflection,
realistic lighting, multiple objects, hands, ornate frame
```

For the White Out block add: `, colorful, saturated colors`.
When you do **not** want a literal "TNT" wordmark rendered by the model, keeping `text` in the
negative is important — author real labels by hand in the 16×16 pass instead.

### A.3 Model / checkpoint suggestions

Ranked for this task (all confirmed present on the box, RTX 3090):

1. **SDXL base** (`SDXL/sd_xl_base_1.0.safetensors`) — best balance. Strong "pixel art / game
   texture" priors, fast (~15 s @ 1024²), high contrast. **Default choice, used for the mocks.**
2. **FLUX.1 dev fp8** (`FLUX1/flux1-dev-fp8.safetensors`) — cleaner edges and better prompt
   adherence for busy motifs (circuitry, text-like glyphs), but slower and heavier. Use for the
   Smart Bomb / F-Bomb hero faces. Note FLUX ignores CFG (use `guidance` / distilled settings).
3. **SD 1.5** (`SD1.5/v1-5-pruned-emaonly.ckpt`) — only if pairing a dedicated *pixel-art LoRA*.
   None is installed today; if you want maximal "true pixel" output, download a pixel-art LoRA
   (e.g. a SDXL "Pixel Art XL" style LoRA) via `download_civitai_model` and stack it.

**Optional speed:** `SDXL-Lightning/sdxl_lightning_4step_lora` is present — for rapid iteration
generate at 4 steps, `cfg 1.5–2`, sampler `euler`, scheduler `sgm_uniform`. Quality is lower;
use for thumbnails, then final-pass with the settings below.

### A.4 Recommended settings

| Param | Value | Notes |
|---|---|---|
| Resolution | **1024×1024** (SDXL) | Generate big; you will throw away >99% of the pixels. 512² is fine for fast passes. Always square — the source must map cleanly to a square 16×16. |
| Steps | **30** | 25–35 range; diminishing returns past 35. |
| CFG | **7** | 6–8. Higher = more literal palette adherence but harder edges. |
| Sampler | **dpmpp_2m** | Clean, low-noise. `euler` also fine. |
| Scheduler | **karras** | Smooth denoise; pairs with dpmpp_2m. |
| Seed | fixed per face | Lock a seed once you like a composition so re-runs are reproducible. |
| Batch | 4 | Generate a few, pick the cleanest to downscale. |

### A.5 Downscale to a true 16×16 + palette clean

This is the load-bearing step. A companion script is provided:
**`docs/art/downscale_to_16.py`** (Pillow, zero-config). It:

1. Center-crops the AI image to a square (defensive; our gens are already square).
2. Resizes to 16×16 using a **box/area filter** (averages each source region — better than a naive
   nearest-neighbor *downscale*, which aliases). Nearest-neighbor is for **up**scaling previews.
3. **Quantizes** to a small palette (`--colors N`, default 12) to kill gradient banding and give
   the flat Minecraft look.
4. Writes true RGBA (alpha preserved / thresholded), plus a large nearest-neighbor **preview** so
   you can eyeball it.

```
# one face:
python3 docs/art/downscale_to_16.py in/water_top_1024.png out/water_bomb_top.png --colors 10
# preview only, no palette change:
python3 docs/art/downscale_to_16.py in.png out.png --colors 0 --preview-scale 24
```

Key rules the script encodes (and you enforce by hand afterward):

- **Downscale with box/area, not bicubic** — bicubic reintroduces soft anti-aliased edges that
  read as mush at 16 px. Then **preview with NEAREST** at ×16–×24 to judge.
- **Fewer colors is more Minecraft.** Vanilla block faces typically use 4–8 tones. Aim for
  ≤ 3–4 tones per hue: a shadow, a body, a highlight, plus the border.
- **Snap the border.** After quantizing, hand-fix the 1-px outline so all four edges share one
  dark tone — the AI border is never perfectly straight. Do this in the 16×16, zoomed in.
- **Alpha:** for solid blocks keep alpha 255 everywhere. Only the item/entity sprites need
  transparency; block faces are opaque. If a face came out with a stray background, threshold
  alpha (script `--alpha-threshold`).
- **Final hand-authoring option:** once a face is nailed, transcribe it into an ASCII pixel-map +
  `PALETTE` dict exactly like `redstone-stuff/tools/generate_textures.py`. That makes the art
  reviewable in diffs, deterministic, and trivially shippable to both Java and Bedrock packs.

### A.6 Where files go (matches redstone-stuff's dual-pack shipping)

Final 16×16s ultimately live under the pack trees, same PNG written to both editions:
```
src/main/resources/pack/assets/<ns>/textures/block/<bomb>_<face>.png     # Java
src/main/resources/bedrock/textures/blocks/<bomb>_<face>.png             # Bedrock
```
Work-in-progress AI mocks and downscales stay under `docs/art/mocks/` (not shipped).

---

## B. Per-bomb art direction

Palettes are starting points — tune during the palette-quantize step. Each lists **hex tones**
(shadow → body → highlight, plus border/accent), the **top / side / bottom** treatment, and a
**filled-in prompt** (side face shown; swap `{{FACE}}`/`{{VIEW}}`/`{{MOTIF}}` for top & bottom).

Common vanilla-TNT cues to keep everywhere so the block still "reads as TNT":
banded darker caps on top & bottom, a horizontal label band across the middle of the side face,
and a 1-px darker outline on every face.

### B.1 Water Bomb — watery / aqua

- **Palette:** border `#0A2A4A` · shadow `#14568C` · body `#1E7FC8` · highlight `#4FC3E8` ·
  foam `#BFEFFF`
- **Top:** rippling water surface, concentric lighter-cyan ripples on blue, dark navy banded edge.
- **Side:** vertical blue body, darker top/bottom bands, a centered bubble/droplet motif where the
  TNT label sits.
- **Bottom:** calmer flat blue, faint darker ripples, dark navy band border.
- **Prompt (side):**
  ```
  minecraft block texture, flat pixel art, single side face of a cube tile,
  orthographic front view, water bomb theme, aqua and deep blue color palette,
  vertical watery body with cyan highlights and a centered bubble motif like the TNT label area,
  darker blue banded top and bottom edges, crisp clean blocky pixels, bold dark outline edges,
  retro 16-bit game texture, high contrast, centered filling the frame, no perspective
  ```

### B.2 The Twins — high-contrast monochrome pair

Two variants of the same block; opposites. Zero mid-tones — this is the point.

**Twin A (Light):**
- **Palette:** body `#F5F5F5` · soft-shadow `#D0D0D0` · band/border `#111111`
- **Top/Bottom:** near-white with a thin black banded edge.
- **Side:** white body, bold **black** middle label band.

**Twin B (Dark):**
- **Palette:** body `#111111` · soft-highlight `#333333` · band/border `#F5F5F5`
- **Top/Bottom:** near-black with a thin white banded edge.
- **Side:** black body, bold **white** middle label band.

- **Prompt (Twin A side):**
  ```
  minecraft block texture, flat pixel art, single side face of a cube tile,
  orthographic front view, monochrome twin bomb theme, pure white and black color palette,
  white body with a single bold solid black horizontal label band across the middle,
  thin black outline edges, extreme high contrast, no grey midtones,
  crisp clean blocky pixels, retro 16-bit game texture, centered filling the frame, no perspective
  ```
  For Twin B swap: `black body with a single bold solid white horizontal label band`, palette
  `black and pure white`.

> These two are the strongest candidates for **hand-authoring** — a flat fill + one band + a 1-px
> border is trivial as an ASCII pixel-map and will beat any AI output on cleanliness.

### B.3 F-Bomb — Formidi-Bomb / boss-summon lure (Wither-ish, ominous)

- **Palette:** void-black `#0B0B0F` · obsidian `#1A1526` · obsidian-purple `#2E1A47` ·
  wither-grey `#3A3A3A` · cursed-glow `#7A3FB0` (accent) · ash-highlight `#5A4A6E`
- **Top:** cracked obsidian with faint purple magma-vein glow, dark banded edge.
- **Side:** dark obsidian body, a menacing purple soul-glow motif (skull-like negative space or
  three-dot Wither hint) where the label sits, jagged dark bands.
- **Bottom:** near-black cracked stone, dim purple embers, dark band border.
- **Prompt (side):**
  ```
  minecraft block texture, flat pixel art, single side face of a cube tile,
  orthographic front view, ominous formidi-bomb boss-summon theme,
  obsidian black and dark purple color palette,
  cracked obsidian body with a menacing purple soul-glow sigil in the center label area
  and faint magma-purple veins, jagged dark banded top and bottom edges,
  crisp clean blocky pixels, bold dark outline edges, retro 16-bit game texture,
  high contrast, centered filling the frame, no perspective
  ```

### B.4 G-Bomb — gravity / anti-gravity / void

- **Palette:** void `#0D0A1A` · indigo `#241B4A` · deep-purple `#3B2A7A` · violet `#5B3FC0` ·
  mote-glow `#9B7BFF` · star-white `#E8E0FF` (sparse motes)
- **Top:** swirling void with a faint indigo vortex and a few floating light motes drifting
  upward, dark band edge.
- **Side:** deep-purple body, a central downward-then-up "anti-gravity" motif — small motes
  suspended mid-face — banded edges.
- **Bottom:** darkest, motes appearing to fall away into void, dark band border.
- **Prompt (side):**
  ```
  minecraft block texture, flat pixel art, single side face of a cube tile,
  orthographic front view, anti-gravity void bomb theme,
  deep indigo and purple color palette,
  deep purple body with small glowing violet and white motes floating suspended in the center,
  a faint void vortex, darker banded top and bottom edges,
  crisp clean blocky pixels, bold dark outline edges, retro 16-bit game texture,
  high contrast, centered filling the frame, no perspective
  ```

### B.5 Smart Bomb — command-block look (programmable tech)

Deliberately echoes the vanilla **command block** (teal/grey with a circuit/conduit pattern and a
central node), so players read "programmable."

- **Palette:** dark-teal `#0E2E2E` · teal `#1C5C56` · circuit-teal `#2E8B7F` · grey-plate `#4A5458` ·
  etched-line `#79C7B8` · core-glow `#B6FFEE` · core-amber `#FFC24D` (optional lit core)
- **Top:** grey/teal plate with etched circuit traces radiating from a bright central node.
- **Side:** command-block-style face — teal plate, orthogonal circuit traces, a glowing core in
  the center label area, subtle grey rivets in the corners, banded edges.
- **Bottom:** darker plate, sparser traces, dim core, dark band border.
- **Prompt (side):**
  ```
  minecraft block texture, flat pixel art, single side face of a cube tile,
  orthographic front view, command-block smart bomb theme,
  teal and grey circuit color palette,
  metal plate with etched glowing teal circuit traces converging on a bright glowing core
  in the center, small corner rivets, darker banded top and bottom edges,
  crisp clean blocky pixels, bold dark outline edges, retro 16-bit game texture,
  high contrast, centered filling the frame, no perspective
  ```
  Recommended on **FLUX** for cleaner circuit geometry.

### B.6 White Out — pure, unsettling all-white

- **Palette:** white `#FFFFFF` · pale-cool `#F2F4F7` · faint-grey `#E4E8ED` · edge-grey `#CBD2DA`
  (that's the whole palette — 3–4 near-whites and one slightly darker edge)
- **Top / Side / Bottom:** all the same — an almost featureless white surface, only the faintest
  cool-grey shading to hint at block edges, a barely-there darker outline. No label, no motif.
  The unease comes from the near-total absence of detail. Keep the outline just dark enough to
  register as a block in-world.
- **Prompt (any face):**
  ```
  minecraft block texture, flat pixel art, single face of a cube tile, orthographic view,
  white out bomb theme, pure white and pale cool grey color palette,
  minimal eerie almost featureless white surface with only the faintest cool grey pixel shading
  to suggest block edges and a subtle darker grey outline, unsettlingly blank,
  crisp clean blocky pixels, retro 16-bit game texture, centered filling the frame, no perspective
  ```
  Negative: append `, colorful, saturated colors`. Downscale with `--colors 4`.

---

## Palette quick-reference

| Bomb | Border/Band | Shadow | Body | Highlight/Accent |
|---|---|---|---|---|
| Water Bomb | `#0A2A4A` | `#14568C` | `#1E7FC8` | `#4FC3E8` / foam `#BFEFFF` |
| Twin A (Light) | `#111111` | `#D0D0D0` | `#F5F5F5` | — |
| Twin B (Dark) | `#F5F5F5` | `#333333` | `#111111` | — |
| F-Bomb | `#0B0B0F` | `#1A1526` | `#2E1A47` | glow `#7A3FB0` |
| G-Bomb | `#0D0A1A` | `#241B4A` | `#3B2A7A` | mote `#9B7BFF` / star `#E8E0FF` |
| Smart Bomb | `#0E2E2E` | `#1C5C56` | `#2E8B7F` | core `#B6FFEE` / amber `#FFC24D` |
| White Out | `#CBD2DA` | `#E4E8ED` | `#FFFFFF` | pale `#F2F4F7` |

---

## Pipeline checklist (per face)

1. Fill the template (A.1) + negative (A.2) with the bomb's row from B.
2. Generate 4× on SDXL @ 1024², steps 30, cfg 7, dpmpp_2m/karras (A.4). Pick the cleanest.
3. `downscale_to_16.py in.png out.png --colors N` (N ≈ 4–12 per B).
4. Zoom the preview; hand-fix the 1-px border and any stray pixels in the 16×16.
5. (Optional but recommended) transcribe to an ASCII pixel-map + palette dict per the
   `redstone-stuff` pattern for a deterministic, diff-reviewable, dual-pack-shippable asset.
6. Write the same PNG to the Java and Bedrock pack paths (A.6). Verify 16×16 RGBA, power-of-two.
