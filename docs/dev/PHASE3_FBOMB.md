# Phase 3 — The F-Bomb (SDD contract)

> Owner-settled design (2026-08-25). This is the spec the F-Bomb SDD track implements, on top of the
> real-block architecture (`docs/dev/PHASE1B.md`) and the Phase 2 patterns. The F-Bomb is the
> framework's showcase of a **fake-boss spectacle**: igniting it summons a menacing Wither-like
> apparition that threatens nearby players for a few seconds and then detonates in a signature blast —
> all without ever spawning a real hostile mob.

## Concept — "menace then blast" (owner-chosen)

On ignition the bomb stages a short cinematic:

1. **Summon** a fake Wither at a safe offset from the blast center; a boss bar appears for in-range
   players.
2. **Menace** for the fuse window: the apparition hovers and "roars", firing a bounded volley of
   `WitherSkull` projectiles toward nearby players (telegraphed, survivable).
3. **Blast**: the detonation carves the signature crater, the apparition despawns, and any terrain the
   display rig displaced is restored.

Short, high-drama, and gone — this is a detonation with a face, **not** a persistent encounter.

## The fake-Wither rig (the load-bearing constraint)

Build the boss from **`BlockDisplay` + `Interaction` entities driven by a custom state machine and a
single `runTaskTimer` + tick-counter — NOT a real `EntityType.WITHER`.** This is the *fake-boss
display rig* pattern (reference plugin `tuesday-twister`): Geyser-safe, needs **no client resource
pack for the mob**, and cannot wander off or grief. Reusable pieces from that reference:

- `SpawnPlacement.forSummoner(origin, distance, height)` yaw math to place the rig at a safe offset and
  face the igniter. **Prefer open air:** pick the offset direction with the most clear space around the
  bomb and bias upward so the apparition floats above the blast; the offset-selection/yaw math stays
  pure, only the air-probe is Bukkit-edge.
- **PDC source-tagging** so the detonation's `EntityExplodeEvent` (or tagged-`TNTPrimed` blast) is
  attributed to the F-Bomb for crater accounting / region protection.
- **BossBar range-viewer management** — add players entering range, remove those leaving, clear on end.
- **`WitherSkull` projectile config** for the menace volley (bounded count + cadence; modest, no
  runaway barrage).

**No terrain manipulation.** A `BlockDisplay`/`Interaction` rig replaces no world blocks and renders
through solid blocks (occlusion does not hide it), so there is **no `WorldSnapshot`/pocket-clear** — a
partly-embedded apparition is a purely cosmetic, ~3-second edge case handled by the open-air spawn
preference above, not by carving and restoring terrain. The apparition takes **no damage and deals no
melee**; its only offense is the telegraphed skull volley. It exists purely for the seconds between
ignition and blast.

## Block & identity

- Claims `instrument=pling,note=22,powered=false` (the reserved `fbomb` slot in `BombBlocks`),
  reskinned to its approved cube (Java model + Bedrock/Geyser mapping; texture generated via
  `tools/generate_textures.py`). **Orchestrator owns this reskin pass.**
- Item: base `Material.TNT` + `item_model tnt_library:fbomb` + `TNT_ID` PDC = `fbomb`.

## Ignition & lifecycle

- Igniting a placed F-Bomb (flint & steel / fire / redstone via the shared ignition path) starts the
  cinematic instead of an immediate vanilla-style blast. Reuse `BombFuse`-style bookkeeping: **one
  bounded scheduled task** drives the whole rig; cancel and fully tear down (despawn rig, clear boss
  bar, restore snapshot) on break / plugin disable / detonate so nothing leaks.
- **detonate(ctx):** explode with the configured `radius` via the tagged-`TNTPrimed` path (like the
  Water Bomb blast, no water fill) so region protection is respected consistently. The rig despawn
  happens here (or immediately after), before control returns.
- Every entity/boss-bar/task the rig creates must be tracked and removed on **every** exit path,
  including a server stop mid-cinematic — no orphaned `BlockDisplay`/`Interaction` entities, ever. This
  is satisfied by a PDC-tagged (`fbomb_rig`) entity orphan-sweep on enable plus `endAll()` on disable;
  no block-state persistence is required (there is none to restore).

## Config (`bombs.fbomb`)

`enabled` **defaults false** (dangerous bomb). Provide `radius`, `fuse-ticks` (**60**), and F-Bomb
keys: `spawn-distance` + `spawn-height` (rig offset), `bossbar-range`, `skull-count`, `skull-cadence-ticks`,
and a `menace-ticks` window if the show should outlast the raw fuse. Update `BombType`, `config.yml`,
and `TntLibraryConfigTest` pinning. **(Orchestrator owns the config/`BombType`/test wiring; the
implementer consumes an injected params record.)**

## Permission

`tntlibrary.use.fbomb` defaults **op/false** (dangerous bomb) — orchestrator declares it in `plugin.yml`.

## Dependencies

None beyond the Paper API — `BlockDisplay`, `Interaction`, `BossBar`, and `WitherSkull` are all Paper.
No new shaded or provided deps, no Floodgate. Geyser-safe **by construction** (no real mob, no
mob-specific client pack); do not add a client-pack requirement for the apparition.

## Test obligations

- **Unit (headless):** the cinematic state machine as a pure function (tick-counter → phase:
  summon → menace → blast → cleanup); `SpawnPlacement.forSummoner` yaw/offset math (incl. the open-air
  direction preference); the skull-volley schedule (count + cadence → fire ticks) as a pure fn; the
  params codec/validation. Keep every Bukkit touch at the edge so the core is headless-testable, as the
  Twins and Smart Bomb packages do.
- **Gate-12 (client, on `play.xpfarm.org`):** the apparition renders as a Wither-like display on
  **Java and Bedrock**; the boss bar appears for in-range players and clears for those who leave;
  the skull volley fires and is survivable; the detonation craters at the configured size and is
  attributed to the F-Bomb; the rig despawns cleanly; nothing is orphaned on a mid-cinematic
  reload/stop.

## What the orchestrator wires (not the logic implementer)

`BombBlocks` (fbomb=22 already reserved), `config.yml` / `BombType` / `TntLibraryConfig` pinning,
`plugin.yml` (`tntlibrary.use.fbomb` op/false; the `fbomb` command/permission placeholders exist),
recipe + `TntLibraryPlugin` registration, and the resource-pack/Geyser pass (fbomb cube texture,
model, blockstate override, Geyser mapping). The SDD implementer delivers the **isolated F-Bomb logic
package** (rig + state machine, spawn placement, skull volley, boss-bar range viewer, detonate/cleanup)
+ tests against this contract, with **zero shared-file edits** — the same isolation the Twins and Smart
Bomb tracks achieved.
