# Phase 2 — The Twins (SDD contract)

> Owner-settled design (2026-08-23). This is the spec the Twins SDD track implements. Build on the
> real-block architecture from `docs/dev/PHASE1B.md` (a placed bomb is a claimed `note_block` state
> reskinned via Java pack + Geyser Custom Blocks). Detonation reuses the existing seam; the Twins add
> a bomb-specific line effect and a pairing lookup.

## Concept

Two placed bombs mark the start and stop of a range; igniting either detonates **the straight line
between them** as a 3D beam/trench. They are an **inverse pair**: a **White Twin** and a **Black
Twin**. A Twin only fires when it can find its **nearest valid opposite-color partner within a
bounded range**; alone, it fizzles.

## Owner decisions (locked)

1. **Pairing:** opposite-color only — White pairs with Black. Igniting either finds the nearest placed
   **opposite** Twin.
2. **Effect:** a straight **3D beam/trench** of explosions from Twin A's centre to Twin B's centre,
   with a **configurable thickness** (radius around the line).
3. **Range:** **bounded** by a configurable max pairing distance; beyond it there is no valid partner
   and ignition **fizzles** with feedback (no explosion).

## Two variants, one bomb type

The Twins ship as **two `CustomTnt` variants** with distinct identities but a shared config/permission:

- Registry ids: `twins_white`, `twins_black` (each its own item, claimed block state, texture/model).
- Both resolve to **`BombType.TWINS`** for config (`bombs.twins`) and to the **`tntlibrary.use.twins`**
  permission, via a **variant→base** mapping. Add that mapping once (e.g. `BombType.baseId(String)`
  or a small map in the item layer) so `Permissions.use(id)` and the config lookup strip the variant.
- `BombItems.idOf` returns the variant id; the item's `TNT_ID` PDC carries the variant id.

### Block-state allocation (extends `BombBlocks`)

Keep the shipped Water Bomb (`note=19`) and the reserved primaries (`smartbomb=21`, `fbomb=22`,
`gbomb=23`, `whiteout=24`) unchanged. Allocate the two Twin states as:

- `twins_white` → `instrument=pling,note=20,powered=false` (the existing `twins` slot)
- `twins_black` → `instrument=pling,note=18,powered=false` (a secondary slot below the primaries)

Generalise `BombBlocks` so a bomb id maps to a claimed state even when it is a variant (the pure
string table + tests extend the same way). The resource pack and Geyser mapping gain one override per
variant state.

## Ignition & detonation flow

- Ignition reuses the shared `IgnitionListener` + `BombFuse` (flint & steel / fire / redstone → fuse).
  The fuse clears the ignited block to air and calls `Detonator.detonate(thisVariant, center, igniter)`
  as usual.
- The **variant instance carries its colour**, so `TheTwins(WHITE).detonate(ctx)` searches for the
  nearest **Black** Twin block (and vice-versa) — the ignited block is already air by detonation time,
  so colour must come from the instance, not the block.
- **Partner search:** scan placed blocks for the opposite variant's claimed state within the config
  max distance of `ctx.center`, in the same world; pick the nearest. Bound the scan (max distance)
  for performance — iterate candidate block positions or track placed Twins in a lightweight in-memory
  index updated by place/break (index preferred; a blind block scan over a 64-block cube is 2M blocks).
  **Recommendation:** maintain a per-world set of placed Twin locations (populated on place, pruned on
  break/detonate), so partner lookup is a bounded distance filter over a small set.
- **No valid partner:** fizzle — a small smoke/extinguish puff + `ENTITY_TNT_PRIMED` cancel feel, an
  action-bar note ("no matching Twin in range"), and the ignited Twin is **restored** (do not consume
  it) OR consumed per owner taste — default: leave the partner search to detonation time and, on
  fizzle, drop the Twin item back so it isn't lost. (Spec decision: restore is friendlier; confirm in
  review.)
- **On a valid pair:** carve the beam and **remove both** Twin blocks (the pair is spent). Drop
  nothing (they detonated).

## The beam/trench

- Sample points along the segment A→B at ~1-block spacing (`floor(distance)` samples).
- At each sample, produce an explosion of the configured **thickness** radius, respecting protection
  (WorldGuard/GriefPrevention) exactly like the Water Bomb path — reuse the tagged-`TNTPrimed` +
  `DetonationListener` machinery where possible, or a protection-filtered `createExplosion` per sample.
  Prefer reusing the existing tagged-explosion path so region protection and the `DETONATION_ID` tag
  behaviour stay consistent. **Do not** spawn hundreds of `TNTPrimed` at once for a long line — cap the
  sample count and/or stagger, and clamp per-sample power. Config bounds the max distance, which bounds
  the sample count.
- Not incendiary (the Twins carve, they don't burn) unless owner wants otherwise — default off.

## Config (`bombs.twins`)

Existing: `enabled` (true), `radius` (repurposed as **beam thickness**, default 3), `fuse-ticks` (80).
Add: `max-pair-distance` (blocks, default 64). Update `BombType.TWINS` + `config.yml` + the
`TntLibraryConfigTest` pinning accordingly (BombSettings may need a fourth tunable or reuse `hang`).

## Items, recipe, art

- Two items (White/Black Twin), base `Material.TNT` + per-variant `item_model`
  (`tnt_library:twins_white` / `tnt_library:twins_black`) + `TNT_ID` PDC = variant id. No attribute
  modifiers.
- Recipe: they "only work in pairs" — craft each variant (shapes must not collide with vanilla or
  each other or the Water Bomb's plus-shape). Propose two distinct shaped recipes using black/white
  dyes (e.g. white/black wool or dye + TNT) to signal the inverse pair. Confirm shapes in review.
- Art: white and black **inverse** cubes (per the approved design canvas). Textures generated via
  `tools/generate_textures.py` (add `twins_white` / `twins_black` face maps), then the Java pack
  (two block models + two `note_block.json` state overrides) + Bedrock/Geyser mapping per variant —
  authored the same way as the Water Bomb (orchestrator-run pack pass, not the logic implementer).

## Test obligations

- Unit (headless): the variant→base id mapping; `BombBlocks` claimed states for both variants
  (distinct, round-trip); pairing selection math (nearest opposite within max distance) as a pure
  function over a candidate set; beam sample-point generation for a segment (count, spacing, endpoints).
- Gate-12 (client): both cubes render (Java+Bedrock); igniting one carves the trench to its partner
  and removes both; a lone Twin fizzles; range cap respected; protection spared.

## What the orchestrator wires (not the logic implementer)

`BombBlocks` extension, `config.yml`/`BombType`/`TntLibraryConfig`, `plugin.yml` (permission already
exists), recipe registration + `TntLibraryPlugin` registration of both variants, and the full
resource-pack/Geyser pass (textures, models, overrides, mappings). The SDD implementer delivers the
isolated Twins logic package (`item`/effect/pairing) + tests against this contract.
