# Phase 1B — Real-block re-architecture + Water Bomb assets

> Supersedes the display-entity rig from Phase 1 (T4). The placed bomb becomes a **real vanilla
> block** (a claimed `note_block` state) so it renders as a true 3D cube on **both** Java and
> Bedrock via Geyser Custom Blocks — and, being a real block, gains real-TNT ignition parity.
> `Detonator` / `DetonationListener` / `DetonationContext` / `CraterMath` / `WaterBomb.detonate`
> are **unchanged**: the detonation seam is preserved.

## Decisions (owner-confirmed 2026-08-23)

1. **Rendering:** Geyser Custom Blocks over a real vanilla blockstate (true cube on both editions).
2. **Donor block:** `minecraft:note_block` (industry-standard; mushroom_stem rejected — every face
   is always textured, so a clean single-state override is impossible and natural mushrooms break;
   Oraxen deprecated its mushroom mechanic for the same reason).
3. **Ignition:** real-TNT parity — flint & steel (right-click), fire/lava (`BlockIgniteEvent`),
   and redstone (`BlockRedstoneEvent`). Replaces the Phase-1 "flint & steel only" limitation.
4. **Geyser delivery:** the plugin **writes** the `custom_mappings` JSON + Bedrock pack into
   Geyser's plugin folder in `onLoad`, with `loadbefore: [Geyser-Spigot]` so the files exist before
   Geyser initializes. One restart on first install; no runtime API registration (Geyser bug #4177
   — vanilla-state overrides cannot be registered through the Java API, JSON-file path only).
5. **Fuse visual:** the real block stays in place through the fuse (visible on both editions);
   primed sound + smoke particles (both Geyser-visible); on fuse elapse the block is set to air and
   `Detonator.detonate` runs. No display-entity flash (Java-only, breaks cross-edition parity).

## State-claim table (the contract — code and packs MUST agree)

Donor `minecraft:note_block`. All bombs `powered=false`, `instrument=pling`, physics-locked so the
instrument cannot re-derive from the block below. Only `waterbomb` ships a model/textures this phase;
the rest are **reserved**.

| Bomb id    | Claimed note_block state                      | Java model                  | Textures (top/side/bottom)              |
|------------|-----------------------------------------------|-----------------------------|------------------------------------------|
| waterbomb  | `instrument=pling,note=19,powered=false`      | `tnt_library:block/waterbomb` | `waterbomb_top/side/bottom` (shipped)  |
| twins      | `instrument=pling,note=20,powered=false`      | `tnt_library:block/twins`     | reserved                               |
| smartbomb  | `instrument=pling,note=21,powered=false`      | `tnt_library:block/smartbomb` | reserved                               |
| fbomb      | `instrument=pling,note=22,powered=false`      | `tnt_library:block/fbomb`     | reserved                               |
| gbomb      | `instrument=pling,note=23,powered=false`      | `tnt_library:block/gbomb`     | reserved                               |
| whiteout   | `instrument=pling,note=24,powered=false`      | `tnt_library:block/whiteout`  | reserved                               |

## Architecture

### New `block` package (replaces `rig`)
- `BombBlocks` — single source of truth for the table above. Pure, unit-testable string mapping
  `bombId ↔ note_block state key`; a runtime `blockDataFor(id)` (`Bukkit.createBlockData`) and
  `bombIdOf(BlockData)` / `bombIdOf(Block)`. Constant `DONOR = "minecraft:note_block"`.

### Placement (`PlacementListener`, rewritten)
- `BlockPlaceEvent`: detect bomb item (`BombItems.idOf`), permission check (unchanged), then
  **set the target block to the claimed `note_block` state** (`block.setBlockData(BombBlocks.blockDataFor(id))`)
  instead of spawning a rig. Cancel the vanilla TNT place + consume one (existing logic reused).

### Physics lock (`NoteBlockLockListener`, new)
- `BlockPhysicsEvent` on a bomb-state block: re-assert the claimed `BlockData` so the instrument
  never re-derives from the block below (Oraxen-style). Also cancel the vanilla note interaction/sound
  **only** on bomb-state blocks (below).

### Ignition (`IgnitionListener`, rewritten for real blocks)
- `PlayerInteractEvent` (RIGHT_CLICK_BLOCK + flint & steel + block resolves to a bomb) → ignite.
  Also cancel the vanilla note-block pitch cycle + `NotePlayEvent` on bomb-state blocks.
- `BlockIgniteEvent` (FIRE_SPREAD, LAVA, FLINT_AND_STEEL, EXPLOSION) where the target block is a bomb → ignite.
- `BlockRedstoneEvent`: when a bomb block (or a bomb among the changed block's neighbours) goes
  powered → ignite.
- Ignite = permission check → start the fuse.

### Break (`BlockBreakEvent`, in placement or a small break listener)
- Breaking a bomb-state block: identify the bomb, `setDropItems(false)`, drop the correct bomb item
  (`bomb.createItem()`) so the vanilla note-block item is never dropped.

### Fuse (`BombFuse`, new — replaces `TntRig.prime`)
- On ignite: keep the block, play `ENTITY_TNT_PRIMED`, emit periodic smoke particles (both editions),
  schedule `fuseTicks`; on elapse set the block to air and call `plugin.detonator().detonate(tnt, center, igniter)`.
- Guard against double-ignition of the same block (track in-progress fuse locations).

### `onLoad` Geyser installer (`GeyserAssetInstaller`, new)
- If `plugins/Geyser-Spigot/` exists (or create under it), write from bundled resources:
  `custom_mappings/tnt_library.json` and `packs/tnt_library.mcpack` (or folder). Write-if-changed;
  log a one-restart note on first install. Never fail enable if Geyser is absent.

### Resource-pack artifacts (authored, bundled in the JAR)
- **Java pack** (`resources/pack/`): `pack.mcmeta` (`min_format`/`max_format` 84); override
  `assets/minecraft/blockstates/note_block.json` preserving vanilla (`""` → `block/note_block`) and
  adding the claimed waterbomb state → `tnt_library:block/waterbomb`; model
  `assets/tnt_library/models/block/waterbomb.json` (`parent cube_bottom_top`); textures already at
  `assets/tnt_library/textures/block/waterbomb_{top,side,bottom}.png`. The item model
  (`tnt_library:waterbomb`) shows the cube in inventory.
- **Bedrock pack** (`resources/geyser/pack/`): `manifest.json`, `textures/terrain_texture.json`
  mapping short-names → the three PNGs (already at `resources/bedrock/textures/blocks/`), using
  `unit_cube` first (no hand-authored geometry) with per-face `material_instances`
  (`up`=top, `down`=bottom, `*`=side, `render_method: opaque`).
- **Geyser mapping** (`resources/geyser/custom_mappings/tnt_library.json`): `minecraft:note_block`,
  `only_override_states: true`, `state_overrides` for the waterbomb state → the Bedrock custom block.
- **Geyser config note:** requires `gameplay.enable-custom-content: true` in Geyser's `config.yml`
  (documented for the operator; not written by us).

## Known limitations (record at gate 12)
- Identity keys on the claimed blockstate: a player who hand-tunes a real note block to
  `pling,note=19,powered=false` on the right supporting block would be treated as a bomb block.
  Very low probability (uncommon instrument + exact note); documented, hardening (chunk-PDC location
  tracking) deferred.
- Cube rendering on both editions, `unit_cube` correctness, and the Geyser mapping can only be
  verified with a real Java + Bedrock client — a gate-12 play-test obligation, not reachable headlessly.
- Custom-block components are upstream-unstable (Geyser wiki); re-test on Geyser/Bedrock bumps.

## Dispatch
- Resource-pack + Bedrock + Geyser JSON authoring → one subagent (format-sensitive, web-verifiable),
  consuming this table.
- Java re-architecture (block package, listeners, fuse, installer, wiring, plugin.yml, rig deletion)
  → owner-implemented for coherence (tight coupling across plugin.yml + TntLibraryPlugin + the table).
- Owner runs the authoritative `mvn clean verify` and the runtime gate.
