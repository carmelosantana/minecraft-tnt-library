# Phase 2 — The Smart Bomb (SDD contract)

> Owner-settled design (2026-08-23). This is the spec the Smart Bomb SDD track implements, on top of
> the real-block architecture (`docs/dev/PHASE1B.md`). The Smart Bomb is the framework's showcase of
> *programmable* TNT: a placed block that looks like a command block and detonates on a
> player-configured trigger, with a cross-play programming UI.

## Concept

A placed block that **looks like a command block** and stores per-block programmed parameters:

- **size** — explosion radius.
- **delay** — fuse ticks from arming to detonation.
- **time-of-day trigger** — optional; detonate when the world time reaches a set value.
- **proximity sensing** — optional; detonate when a living entity enters a radius, emitting a
  **warning sound** that intensifies as the entity approaches.

## Block & identity

- Claims `instrument=pling,note=21,powered=false` (the reserved `smartbomb` slot in `BombBlocks`),
  reskinned to a command-block-style cube (Java model + Bedrock/Geyser mapping; texture from the
  approved design canvas, generated via `tools/generate_textures.py`).
- Item: base `Material.TNT` + `item_model tnt_library:smartbomb` + `TNT_ID` PDC = `smartbomb`.

## Per-block parameter store

A `note_block` is **not** a tile entity, so it carries no block PDC. Programmed params must persist in
a side store keyed by block location:

- Define `SmartBombStore` (interface) with a persistent implementation and an in-memory cache.
  **Recommended impl:** a plugin-managed flat file (YAML/JSON) mapping `world,x,y,z → params`, loaded
  on enable, saved on disable + on change (debounced). A chunk-PDC implementation is a valid
  alternative (locality + travels with the chunk) but is fiddlier to test — flat file first.
- Params record: `radius (int)`, `delayTicks (int)`, `timeTrigger (Long | null)`,
  `proximity (boolean)`, `proximityRadius (int)`. Provide a **pure `ParamCodec`** (serialize/parse)
  that is unit-tested headlessly.
- Break/detonate removes the store entry; place seeds it with config defaults.

## Programming UI — 3-tier, one handler

All three write through one handler `SmartBombProgrammer.apply(block, params)` (DRY):

1. **Command (build first — universal fallback + test harness).** `/tntlibrary smart get|set <key>
   <value>` (perm `tntlibrary.command.smart`) targeting the block the player is looking at. Use
   Paper's Brigadier `Commands` API so Java gets suggestions; Geyser translates it for Bedrock.
   Already the `plugin.yml` placeholder command.
2. **Bedrock — native Floodgate `CustomForm`.** On a non-igniting right-click of a Smart Bomb block by
   a **Bedrock** player (`FloodgateApi.isFloodgatePlayer(uuid)`), open a form: sliders (radius, delay,
   proximityRadius), toggles (time-of-day on/off, proximity on/off), input/dropdown for the trigger
   time. Submit → `apply`. **Open the form one tick later via the scheduler** to sidestep Geyser
   #5850 (post-close inventory lock). Guard every Floodgate/Cumulus call so a Floodgate-absent server
   falls through to the command/chest path.
3. **Java — chest GUI.** On a non-igniting right-click by a Java player, open a chest-menu with
   clickable buttons that adjust each param (cancel ALL item movement — no drag/take). Confirm → `apply`.

**Interaction coordination:** `IgnitionListener` already swallows non-flint&steel right-clicks on
bomb blocks (to stop note cycling). For Smart Bombs, a non-igniting right-click must additionally
**open the programmer UI**. Implement a `SmartBombListener` for that; keep flint & steel / fire /
redstone → ignite (arming) in the shared ignition path. Decide precedence so a player with flint &
steel still ignites and an empty hand opens the UI.

## Arming & triggers (the "smart" part)

- **Arming:** igniting a placed, programmed Smart Bomb *arms* it (rather than detonating immediately).
  Once armed, a per-bomb watcher applies the configured trigger:
  - **delay** — count down `delayTicks`, then detonate (the plain fuse case; if no other trigger is
    set this behaves like a timed TNT).
  - **time-of-day** — wait until `world.getTime()` reaches `timeTrigger`, then detonate.
  - **proximity** — each tick, scan for a living entity within `proximityRadius`; play a warning sound
    that gets more urgent as the nearest entity closes, and detonate when one is close enough.
  - If multiple triggers are set, define precedence (proximity/time can pre-empt the delay; document
    the rule and confirm in review).
- The watcher is one bounded scheduled task per armed Smart Bomb (reuse `BombFuse`-style bookkeeping;
  cancel on break/disable/detonate). Keep proximity scans cheap (bounded radius, throttled cadence).
- **detonate(ctx):** explode with the programmed `radius` via the tagged-`TNTPrimed` path (like the
  Water Bomb blast, no water fill), so region protection is respected consistently.

## Config (`bombs.smartbomb`)

Existing: `enabled` (true), `default-radius` (4), `default-delay-ticks` (100). Add defaults for the
optional triggers (`default-proximity-radius`, whether triggers default on/off). A freshly placed
Smart Bomb seeds its store from these; the player's programming overrides them. Update `BombType`,
`config.yml`, and `TntLibraryConfigTest` pinning.

## Dependencies

- **Floodgate** as a guarded **softdepend** (`plugin.yml: softdepend: [floodgate]`) — the plugin must
  load and fully work on a pure-Java server. Bedrock **detection** can be reflective (the ecosystem
  pattern; see redstone-stuff). Building the **CustomForm** is impractical reflectively, so add
  `org.geysermc.floodgate:api` (Cumulus) as a **`provided`-scope** compile dependency from the
  OpenCollab repo (`https://repo.opencollab.dev/main/`), never shaded, every call site guarded by a
  `getPlugin("floodgate") != null` / `ClassNotFoundException` check so the form path is inert without
  Floodgate. (Orchestrator adds the repo + dependency to `pom.xml`.)

## Test obligations

- Unit (headless): `ParamCodec` round-trip + bad-input defaults; `SmartBombStore` put/get/remove and
  persistence serialization; the command's key/value parsing + validation; trigger-precedence logic as
  a pure function; proximity warning-intensity mapping (distance → sound pitch/cadence) as a pure fn.
- Gate-12 (client): the command-block cube renders (Java+Bedrock); the Bedrock form and Java chest GUI
  open and persist params; arming + each trigger (delay/time/proximity) detonates with the programmed
  size; proximity warning sound escalates; Floodgate-absent server still programs via command/chest.

## What the orchestrator wires (not the logic implementer)

`BombBlocks` (smartbomb=21 already reserved), `config.yml`/`BombType`/`TntLibraryConfig`, `pom.xml`
(Floodgate provided dep + OpenCollab repo), `plugin.yml` (`softdepend: [floodgate]`; permissions
exist), recipe + `TntLibraryPlugin` registration, and the resource-pack/Geyser pass (command-block
texture, model, override, mapping). The SDD implementer delivers the isolated Smart Bomb logic
package (store, codec, programmer, UIs, arming/trigger watcher, detonate) + tests against this contract.
