# Phase 3 — The G-Bomb (SDD contract)

> Owner-settled design (2026-08-25), with the gravity research already verified against the Paper
> 26.1.2 javadocs (see `docs/PLUGIN_CHECKLIST.md` §1, "g-bomb gravity research"). This is the spec the
> G-Bomb SDD track implements. The G-Bomb is the framework's showcase of **physics as a weapon**: it
> flings everything nearby into the air, holds it there, then slams it down with a kill that lands
> regardless of client-side physics.

## Concept

On detonation, for every living entity within radius:

1. **Launch** — cut the entity's gravity and fling it upward.
2. **Hang** — hold it airborne for a fixed window (the signature "float").
3. **Slam + kill** — restore gravity, drive it downward, and apply a **guaranteed server-side finisher**
   so a full-health, unarmored player dies even on Bedrock where the float never rendered.

## Mechanics (from the verified gravity research)

- **Selection:** gather living entities within `radius` (default **20**) of the blast center (bounded,
  pure geometry — unit-testable).
- **Launch:** `Entity#setGravity(false)` + `Entity#setVelocity(Vector)` (meters/tick). Keep launch
  magnitudes **modest**, especially on Y (Paper #13270 `setVelocity` regression at large Y). Manage
  `setFallDistance(float)` **explicitly**: it does not accumulate while gravity is off and is **not**
  reset by toggling gravity, so set/track it yourself to shape the slam.
- **Hang:** hold for `hang-ticks` (default **50**) via one bounded scheduled task (reuse `BombFuse`-style
  bookkeeping).
- **Slam + guaranteed kill:** after the hang, `setGravity(true)` + a downward velocity, **and** apply
  the server-side finisher that does not depend on client physics:
  ```java
  ((Damageable) e).damage(1000.0,
      DamageSource.builder(DamageType.FALL).withDamageLocation(loc).build());
  ```
  This lands on Java and Bedrock alike — it is the contract's kill guarantee, not the launch.

## Bedrock degradation (accepted & documented)

Via Geyser, `setGravity(false)` is **not reliably translated** (the Bedrock client keeps falling) and
player velocity is **client-authoritative** (launches are weak or ignored). So on Bedrock the
float/launch *visuals* will be weak or absent — **the kill still lands** via the server-side
`DamageSource[FALL]` finisher. This experience gap is accepted and must be documented in the config
comment and the gate-12 notes, not "fixed".

## Safety — no permanently floating entities (hard requirement)

Any entity whose gravity this bomb disabled **must** have gravity restored on **every** path:
normal slam, task cancel, plugin disable, chunk/entity unload, server stop. Track the affected
entities and restore on teardown; a surviving entity (out of range of the kill, a creative/invulnerable
player, a pet) must never be left permanently gravity-off. This is the G-Bomb's #1 review gate.

## Block & identity

- Claims `instrument=pling,note=23,powered=false` (the reserved `gbomb` slot in `BombBlocks`),
  reskinned to its approved cube (Java model + Bedrock/Geyser mapping; texture generated via
  `tools/generate_textures.py`). **Orchestrator owns this reskin pass.**
- Item: base `Material.TNT` + `item_model tnt_library:gbomb` + `TNT_ID` PDC = `gbomb`.

## Ignition & lifecycle

- Igniting a placed G-Bomb (shared ignition path) starts the launch/hang/slam sequence after the fuse.
- **detonate(ctx):** the sequence *is* the detonation. If the design also wants a physical blast at the
  slam, route it through the tagged-`TNTPrimed` path so region protection is respected; otherwise the
  kill is purely the gravity finisher (confirm in review which — default is **no extra crater**, the
  spectacle is the bodies, not a hole).
- One bounded scheduled task per detonation drives launch → hang → slam → cleanup; cancel and restore
  gravity on break / disable / detonate.

## Config (`bombs.gbomb`)

`enabled` **defaults false** (dangerous bomb). Provide `radius` (**20**), `fuse-ticks` (**60**),
`hang-ticks` (**50**), plus `launch-power` (the modest upward magnitude) and `kill-damage` (default
1000). Update `BombType`, `config.yml`, and `TntLibraryConfigTest` pinning. **(Orchestrator owns the
config/`BombType`/test wiring; the implementer consumes an injected params record.)**

## Permission

`tntlibrary.use.gbomb` defaults **op/false** (dangerous bomb) — orchestrator declares it in `plugin.yml`.

## Dependencies

None beyond the Paper API — `Entity#setGravity/#setVelocity/#setFallDistance`, `Damageable#damage`,
and `DamageSource`/`DamageType` are all Paper 26.1.2. No new shaded or provided deps, no Floodgate.

## Test obligations

- **Unit (headless):** entity-in-radius selection as pure geometry; the launch/hang/slam state machine
  (tick-counter → phase) as a pure fn; the fall-distance management logic; the finisher's
  `DamageSource` construction params (type FALL, damage value, location) as a pure factory; the
  gravity-restore bookkeeping (an entity added on launch is always scheduled for restore). Keep Bukkit
  at the edge, as the Twins/Smart Bomb packages do.
- **Gate-12 (client, on `play.xpfarm.org`):** a full-health **unarmored Java** player is launched,
  hangs, slams, and **dies**; a full-health unarmored **Bedrock** player **dies** via the server-side
  `DamageSource[FALL]` finisher even with the float weak/absent; region protection respected if a
  blast is used; **no entity is left permanently gravity-off** after the sequence, a cancel, or a
  reload.

## What the orchestrator wires (not the logic implementer)

`BombBlocks` (gbomb=23 already reserved), `config.yml` / `BombType` / `TntLibraryConfig` pinning,
`plugin.yml` (`tntlibrary.use.gbomb` op/false; the `gbomb` command/permission placeholders exist),
recipe + `TntLibraryPlugin` registration, and the resource-pack/Geyser pass (gbomb cube texture,
model, blockstate override, Geyser mapping). The SDD implementer delivers the **isolated G-Bomb logic
package** (entity gather, launch/hang/slam watcher, server-side finisher, gravity-restore safety,
detonate/cleanup) + tests against this contract, with **zero shared-file edits** — the same isolation
the Twins and Smart Bomb tracks achieved.
