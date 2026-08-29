# New or Edited Plugin Checklist

Copy this file for one plugin and replace every `<...>` field. Leave an unchecked box with a short explanation when a gate is not complete; do not silently remove inapplicable checks.

- Plugin name: `TNTLibrary`
- Slug: `tnt-library`
- Repository: `carmelosantana/minecraft-tnt-library`
- Owner: `Carmelo Santana`
- Target version: `0.4.0`
- Paper version: `26.1.2 build 74`
- Java version: `25`
- Updater destination: `tnt-library.jar`
- External services: `none` (WorldGuard / GriefPrevention are optional soft-depend *plugin* integrations, not external network services)
- Status: `active`
- Autonomy: `autonomous` — full-pipeline GitHub-push authorization granted in writing at gate 1 (repo creation → push → CI → tag → release → updater → deploy), no per-action prompts. **Guardrail from the owner:** run autonomously on outward-facing actions, but do **not** assume answers to open engineering/development questions — stop and ask instead. Evidence and fail-closed rules apply unchanged.

## 1. Scope

- [x] Status is explicitly recorded as active, experimental, or excluded. → **active** (framework + phase-1 Water Bomb are the initial releasable surface; complex bombs are sequenced by phase, not withheld gates).
- [x] Purpose, commands, events, permissions, configuration, persistence, and acceptance checks are defined. → see fields below.
- [x] Known limitations and any intentionally withheld gates are recorded. → see Known limitations below; no gates withheld (active plugin, full pipeline).

### Player-facing purpose

A custom-TNT **framework** plus a growing set of creative explosives for `play.xpfarm.org`. Each explosive is a craftable, premium-looking custom block a player places and ignites like vanilla TNT, but with a distinct signature detonation. Six bombs are planned: Water Bomb, The Twins, F-Bomb, G-Bomb, Smart Bomb, and White Out. The framework is the product; the bombs are the reference implementations that prove and exercise it.

### Design source of truth

- **Reference plugins studied (gate 1 research):**
  - `redstone-stuff` — the entire dual-edition custom-item + artwork pipeline is reusable: custom item = vanilla base `Material` wearing a custom `minecraft:item_model` component (Paper 26.1 exposes no server-side `ITEM` registry); identity via a PDC key, never name/lore; Java `pack/` + Bedrock `bedrock/` + one Geyser mapping JSON all driven by one `item_model` value; textures generated as code (`tools/generate_textures.py`, Pillow, ASCII pixel-maps → both edition trees, single source); `pack.mcmeta` uses `min_format`/`max_format: 84` (not legacy `pack_format`); PackSquash + SHA-1 baked into the JAR in CI; Bedrock/Geyser assets installed in **`onLoad()`** (Geyser reads `custom_mappings/` during its own `onEnable`), with **no** depend/softdepend on Geyser; **no attribute modifiers** on items (breaks Bedrock display — Geyser #6266); explicit two-arg `new NamespacedKey("tnt_library", …)` to preserve the underscore; server-driven behavior + particle/sound/action-bar feedback that reads identically on both editions.
  - `tuesday-twister` — the **fake-boss display rig** pattern: build the boss from `BlockDisplay` + `Interaction` entities driven by a custom state machine and a single `runTaskTimer` + tick-counter, **not** a real `EntityType.WITHER`. Geyser-safe, needs no client resource pack for the mob. `SpawnPlacement.forSummoner(origin, distance, height)` yaw math, PDC source-tagging so `EntityExplodeEvent` can attribute craters, BossBar range-viewer management, `WitherSkull` projectile config, and a `WorldSnapshot` block-restore system are all reusable. The same `BlockDisplay`-based approach is the chosen mechanism for every bomb's **placed** appearance (see below).
- **g-bomb gravity research (gate 1, verified against Paper 26.1.2 javadocs):** `Entity#setGravity(boolean)` and `#setVelocity(Vector)` (meters/tick) exist on `Entity`, so they apply to players, mobs, items, `TNTPrimed`, boats, falling blocks alike. **Critical limitation:** on Bedrock via Geyser, `setGravity(false)` is not reliably translated (Bedrock keeps falling) and player velocity is client-authoritative (launches are weak/ignored). The **guaranteed kill must be server-side**: `((Damageable)e).damage(1000.0, DamageSource.builder(DamageType.FALL).withDamageLocation(loc).build())` lands regardless of client physics. `setFallDistance(float)` (no double overload) does not accumulate while gravity is off and is not reset by toggling gravity — manage it explicitly. Keep launch magnitudes modest (Paper #13270 `setVelocity` regression at large Y). Reference plugins: Anti-Gravity V4, iGravity, Blackhole (Spigot #66175).

### Framework architecture

- **`CustomTnt` definition** (interface/abstract base) + **`TntRegistry`** (id → `Supplier<CustomTnt>` map, unit-testable without a server, mirrors redstone-stuff `ItemRegistry`). Each definition carries: id, display name, `ItemStack` builder (base `Material.TNT` + custom `item_model` + PDC id), `ShapedRecipe` (shape-as-data for JUnit), fuse ticks, config/permission keys, and a `detonate(DetonationContext)` hook.
- **Item identity:** PDC key `tnt_library:tnt_id` (STRING). Recipes registered with `removeRecipe(key,true)` then `addRecipe(recipe,true)` (resend on reload).
- **Placed block = real vanilla blockstate + Geyser Custom Blocks** (re-architected 2026-08-23; see `docs/dev/PHASE1B.md`). Placing the item rewrites the block to a claimed `note_block` state (`instrument=pling`, distinct `note` per bomb, `powered=false`; physics-locked so the instrument can't re-derive). A Java resource pack reskins that state to the bomb's `cube_bottom_top` model; a Geyser `custom_mappings` override maps the same state to a Bedrock custom block (`unit_cube`), so the cube renders as a **true 3D block on both editions** (display entities are invisible to Bedrock — the reason for this change). `org.xpfarm.tntlibrary.block.BombBlocks` is the single source of truth for the state↔id table. Being a real block, it gains full real-TNT ignition parity (flint & steel, fire/lava, redstone). Geyser assets are written into Geyser's folder in `onLoad` (`loadbefore: [Geyser-Spigot]`); Geyser requires `gameplay.enable-custom-content: true` (operator-set).
- **Shared detonation services** provided to every bomb: radius entity-gathering, region-protection check (soft-depend), explosion helper (`World#createExplosion`), particle/sound helpers, and a scheduler-driven **phase runner** (one `runTaskTimer` + tick counter; interval-gated effects via `tick % n`, per tuesday-twister).

### Commands

- `/tntlibrary` (alias `/tntlib`) — root admin/util command.
  - `give <bomb> [player] [amount]` — grant a bomb item (perm `tntlibrary.command.give`). Primary test/admin path; recipes are the survival path.
  - `list` — list registered bombs and their enabled/permission state.
  - `reload` — reload `config.yml` and re-register recipes/enabled state.
  - `smart <get|set> <key> <value>` — inspect/edit the targeted Smart Bomb rig's programmed parameters (perm `tntlibrary.command.smart`); a Bedrock-safe fallback for the config UI.
  - *(Open engineering question — will confirm before gate 4, per autonomy guardrail: whether Smart Bomb programming uses a command, a sign/anvil-text input, or a Geyser-compatible form. Not assumed here.)*

### Events (Paper/Bukkit)

- `BlockPlaceEvent` — placing a bomb item rewrites the block to the claimed `note_block` state (not a vanilla TNT block).
- `PlayerInteractEvent` — flint & steel on a bomb block → ignite; any other interaction is swallowed so the note never cycles; also Smart Bomb programming interactions.
- `BlockIgniteEvent` / `BlockRedstoneEvent` — fire/lava/redstone ignition of a placed bomb block (real-TNT parity).
- `BlockPhysicsEvent` / `NotePlayEvent` / `BlockBreakEvent` — keep the bomb block locked (instrument can't re-derive), silent, and dropping the bomb item on break.
- `EntityExplodeEvent` / `BlockExplodeEvent` — attribute craters to a bomb via PDC; enforce region protection; drive Water Bomb crater-fill and White Out non-cratering / transform-not-remove (surface is reskinned to white_concrete, never destroyed).
- `PrepareItemCraftEvent` / recipe events — gate crafting behind per-bomb permission + config toggle.
- `EntityDamageEvent` — G-Bomb fall-damage accounting (cause `FALL`); White Out collapse kill (cause `FREEZE`, the server-side finisher).
- `PlayerMoveEvent` or scheduled proximity scan — Smart Bomb proximity sensing + warning sound.
- `PlayerQuitEvent` / chunk-unload — clean up boss bars and in-flight phase tasks (placed bombs are real blocks and persist; no rig cleanup needed).

### Permissions

- `tntlibrary.use.<bomb>` — craft/ignite a specific bomb. Dangerous bombs (`whiteout`, `fbomb`, `gbomb`) default **op/false**; tamer bombs (`waterbomb`, `smartbomb`, `twins`) default per config.
- `tntlibrary.command.give` / `.smart` / `.reload` — admin commands, default op.
- `tntlibrary.admin` — parent for all admin nodes.

### Configuration (`config.yml`)

- `enabled` (bool, default true) — master switch.
- `bombs.<bomb>.enabled` (bool) — **per-bomb toggle**; `whiteout`, `fbomb`, `gbomb` default **false**, others default true.
- `bombs.<bomb>.radius`, `.fuse-ticks`, and bomb-specific keys (e.g. `gbomb.hang-ticks`, `whiteout.pull-radius`, `smartbomb.default-delay`).
- `protection.respect-regions` (bool, default true) + `protection.provider` (`auto|worldguard|griefprevention|none`).
- `resource-pack.url` / `resource-pack.sha1` (fallback to CI-baked `pack-defaults.properties` like redstone-stuff; empty = delivery disabled, INFO not error).
- Config is parsed into an immutable record, never throws on bad values (degrades to defaults + WARNING naming the key), per redstone-stuff `RedstoneStuffConfig`.

### Persistence

- **PDC** on the placed `BlockDisplay`/`Interaction` rig: bomb id, fuse state, and (Smart Bomb) programmed parameters; (Twins) marker role start/stop + pair linkage.
- **PDC** on the item: `tnt_library:tnt_id`.
- **Twins registry:** in-memory index of active marker rigs (rebuilt from PDC-tagged entities on load) for nearest-partner lookup; no external DB.
- No flat-file/SQL persistence planned for phase 1 beyond `config.yml` and entity PDC. *(Open question flagged for gate 4: whether Twins pairing needs cross-restart durability beyond PDC re-scan.)*

### Dependencies

- **Hard:** Paper API `26.1.2.build.74-stable` (provided). Only compile dependency (redstone-stuff proved Geyser/Floodgate need no compile dep).
- **Soft:** WorldGuard, GriefPrevention (region protection — reflective/optional, no compile dep); Geyser/Floodgate detected reflectively for Bedrock-aware delivery (`onLoad()` asset install, **no** `plugin.yml` depend/softdepend on Geyser — wrong load phase).
- Load order: Bedrock/Geyser assets written in `onLoad()`; behavior in `onEnable()`.

### External integrations

`none` — no Ollama, Umami, or other outside-service network calls. (Region-protection integrations are in-process plugin APIs, not network services.)

### Acceptance checks (basis for gate 6 unit tests + gate 7a runtime verification)

1. **Framework:** `TntRegistry` returns a distinct definition per bomb id; each builds an `ItemStack` with the correct `item_model` and PDC id, and a valid `ShapedRecipe` (all assertable without a server).
2. **Item identity:** a bomb is recognized only by PDC id, never by renamed/loremodified copies.
3. **Place → ignite → detonate:** placing a Water Bomb item spawns a blue-topped display rig (not a vanilla TNT block); igniting it detonates after the configured fuse and floods the crater cavity with water, skipping protected regions.
4. **Config/permission gating:** a bomb disabled in config cannot be crafted or ignited; a player without `tntlibrary.use.<bomb>` cannot craft/ignite it; `whiteout`/`fbomb`/`gbomb` are off by default.
5. **The Twins:** a single marker cannot detonate without a registered opposite marker in range; with a valid pair, the line between them is affected; nearest valid partner wins.
6. **F-Bomb:** ignition spawns the fake-Wither display rig at a safe offset from the blast center (no real `EntityType.WITHER`); boss bar appears for in-range players.
7. **G-Bomb:** entities in radius have gravity toggled and are launched, then slammed; a full-health unarmored **Java and Bedrock** player both die, the Bedrock kill coming from the server-side `DamageSource[FALL]` finisher even without client float.
8. **Smart Bomb:** programmable size/delay/time-of-day/proximity honored; proximity mode plays a warning sound before triggering.
9. **White Out:** loose + living entities in radius are pulled inward with escalating velocity and hit with blindness/slowness/freeze during the pull window, then at the collapse every caught entity is killed by a guaranteed server-side `DamageSource[FREEZE]` finisher (lands on Bedrock even without client physics, like the G-Bomb); the ground is ring-swept into a **permanent white_concrete scar** — non-cratering / transform-not-remove, so region-protected columns, other bombs' blocks, and containers/tile-entities are skipped rather than destroyed. Region gating is inert until a real region-aware `ProtectionService` is wired (ecosystem item), so today it converts/affects everything.
10. **Dual-edition assets:** one `item_model` value renders the custom art on both Java (resource pack) and Bedrock (Geyser mapping); `PluginDescriptorTest`-style checks pass; JAR ships a self-consistent pack URL+SHA-1 from CI.
11. **Bedrock/Geyser safety:** all bomb feedback (warnings, boss UI, programming) works without Java-only attributes; Bedrock players get graceful degradation where client physics differ (documented in Known limitations).

### Known limitations (recorded honestly; cheap to fix later, expensive to discover at release)

- **G-Bomb on Bedrock:** the float/launch visuals will be weak or absent (Geyser doesn't translate `setGravity(false)`; player velocity is client-authoritative). The kill still lands via the server-side `DamageSource[FALL]` finisher, but the *experience* differs from Java. Accepted and documented.
- **White Out fidelity:** "suck in everything not stone or behind a door" needs a concrete spare-list ruleset; exact block/entity classification (what counts as "loose", how doors shelter contents) is an **open engineering question** to settle at gate 4, not assumed now.
- **Smart Bomb programming UI:** command vs. sign/anvil vs. Geyser form is an **open question** (see Commands) — Bedrock form support must be verified before committing.
- **Placed-block appearance** is a real vanilla `note_block` reskinned via a Java resource pack + Geyser Custom Blocks (re-architected 2026-08-23; superseded the display-entity rig, which was invisible on Bedrock). Two consequences: (a) Geyser custom-**block** components are upstream-unstable (Geyser wiki) — re-test the cubes after any Geyser/Bedrock version bump; (b) **identity is the blockstate** — a player who hand-tunes a real note block to `instrument=pling,note=19,powered=false` on a pling-supporting block would be treated as a bomb block. Very low probability; a location index (`PlacedBombIndex`, v0.2.1) now heals drift but is in-memory and self-populated from the same blockstate, so the collision surface is unchanged (persistent chunk-PDC tagging still deferred).
- **Twins line-explosion shape** and **Water Bomb crater-cavity detection** algorithms are specified by intent, not yet by exact geometry — to be pinned at gate 4.
- **Phase 2 deferred minors (v0.2.0, accepted — surfaced by the Smart Bomb whole-branch review):** (a) a Smart Bomb or a placed Twin destroyed by a *neighboring* explosion (no `BlockBreakEvent`) orphans its store/index entry — harmless stale data until overwritten; a load-time prune is a nice-to-have, not a blocker (both the Smart Bomb store and the Twin index share this drift, treated consistently). (b) `/tntlibrary reload` disarms live *armed* Smart Bombs while Water-Bomb fuses survive — a documented inconsistency; reload is a rare admin action and disarming is the safe direction. (c) the Smart Bomb watcher's unseeded fallback uses `SmartBombParams.DEFAULT` vs the config `seedParams` (they coincide on the shipped config). (d) a Floodgate-present-but-null-instance path leaves a Bedrock player without a chest fallback (documented can't-happen). (e) an admin `/time set` backward is read as a forward advance by the time trigger (admin-only edge). None block v0.2.0.
- **v0.2.1 placed-bomb bug fixes (2026-08-25, from live-server reports):** three defects in shipped v0.2.0, all root-caused on the Legendary stack and fixed. (1) **Bedrock rendered the missing texture** — `GeyserAssetInstaller` wrote the Bedrock pack as a loose `packs/tnt_library/` folder, which Geyser ignores (only `.zip`/`.mcpack` archives load); now ships a deterministic `packs/tnt_library.mcpack` and removes the legacy folder. (2) **placed bombs reverted to note blocks** and (3) **could be hit for a note sound** — same root cause: on Paper 26.1.2 a note block re-derives `instrument` in `Block.updateShape()`, a path **not** gated by the cancellable `BlockPhysicsEvent`, so the Oraxen-style physics lock could not hold `pling` (proven: event fires, is cancelled, instrument still drifts). Fixed with a self-populating location index (`PlacedBombIndex`) that heals a drifted block to its claimed state within ~1 tick and keeps guards/mute working mid-drift. +6 unit tests (271 total). **Gate-12 real-client checks:** the Bedrock cube actually renders from the `.mcpack`, and punching a placed bomb produces no note on either edition.
- **Phase 3 (v0.3.0, 2026-08-25) — F-Bomb + G-Bomb integrated.** Both developed via subagent-driven development (opus implementer + reviewer per task) as isolated zero-shared-edit packages, then orchestrator-wired. **F-Bomb** (note=22, disabled by default): the owner-chosen "menace then blast" cinematic — a fake-Wither `BlockDisplay`/`Interaction` rig (never a real `EntityType.WITHER`) summoned at an open-air offset with a boss bar, a bounded `WitherSkull` volley, then the signature blast; ignition is diverted to the feature's director; the "no orphaned entities" guarantee is a PDC-tagged (`fbomb_rig`) sweep on enable + `endAll()` on disable (no `WorldSnapshot` — a display rig alters no terrain). **G-Bomb** (note=23, disabled by default): launch (gravity off + up velocity) → hang → slam + a guaranteed server-side `DamageSource[FALL]` finisher that kills on Bedrock even without client float; reads `launch-power`/`kill-damage` in-package (`GBombDefaults`); the #1 gate is `GravityLedger` restoring gravity on every path (slam/cancel/disable/unload). Both cubes reskinned for Java + Bedrock (soul-lit wither skull / magenta launch-arrow + gravity vortex). **Phase 3 accepted minors (gate-12):** (a) F-Bomb `probeOpenness` does ~288 `getBlockAt` per summon — fine at the default spawn-distance, a far distance could cause a one-off main-thread hitch; (b) F-Bomb `WitherSkull.setYield(1f)` lightly craters unprotected terrain by design (survivable menace, region-safe via `EntityExplodeEvent`); (c) the ignition→launch/cinematic→kill chain is unit-tested + gate-12/RCON-harness-verified, not headless (setblock bypasses the ignition events).
- **Phase 4 (v0.4.0, 2026-08-29) — White Out integrated.** Charted design-first via the wayfinder skill (7 decision tickets), decomposed into a 12-task TDD plan, then built in a fresh-context chip via subagent-driven development (opus implementer + reviewer per task) as an isolated zero-shared-edit package (26 files, independently re-verified: empty shared-file diff), then orchestrator-wired mirroring the G-Bomb (whole effect in `detonate(ctx)`, **no** `IgnitionListener` divert). **White Out** (note=24, disabled by default): an implosion — escalating inward pull + blindness/slowness/freeze during the pull window, then at the collapse a guaranteed server-side `DamageSource[FREEZE]` finisher that kills on Bedrock even without client physics (the G-Bomb inverted), leaving a **permanent ring-swept white_concrete scar** (non-cratering / transform-not-remove: region-protected columns, other bombs' blocks, and containers/tile-entities are skipped, never destroyed). Reads `pull-power`/`pull-ticks`/`kill-damage`/`effect-ticks` in-package (`WhiteoutDefaults`); the #1 gate is the `EffectLedger` clearing every caught entity's debuffs on every abort path (break/disable/chunk-unload before collapse). A registry-free name/EnumSet `SurfacePredicate` (not `Material.isSolid()`/`isOccluding()`, which throw `No RegistryAccess` headlessly) keeps the skinnable-surface check pure and unit-tested. Recipe `SPS/PTP/SPS` (snow_block/packed_ice/TNT). Cube reskinned for Java + Bedrock (icy frost-vortex). **Phase 4 accepted minors / deferrals (gate-12):** (a) the pull/effect/scar detonation chain is unit-tested but not headless (setblock bypasses ignition), so its no-leak paths — plugin disable mid-storm, chunk-unload mid-storm, a totem/invulnerable survivor cleared at DONE, two overlapping vortices, sweep completeness at `RADIUS_MAX` — await a real client or the RCON harness; (b) region gating routes through the correct `ProtectionService` seam (`canPlace` per-column, `canBreak` per-entity) but is **inert until a real region-aware provider is wired** (ecosystem item, gates all six bombs), so today White Out converts/affects everything.
- No gates are intentionally withheld (active plugin, full pipeline). Complex bombs are **phased**, not withheld: Phase 1 framework + Water Bomb; Phase 2 Smart Bomb + Twins; Phase 3 F-Bomb + G-Bomb (done); Phase 4 White Out.

### Naming chain (established here; scaffold confirms consistency)

`slug: tnt-library` → repo `carmelosantana/minecraft-tnt-library` → Maven `artifactId: tnt-library` (group `org.xpfarm`) → shaded JAR `tnt-library-<version>.jar` → updater destination `tnt-library.jar` → `plugin.yml name: TNTLibrary` → main class `org.xpfarm.tntlibrary.TntLibraryPlugin`, package `org.xpfarm.tntlibrary`, permission prefix `tntlibrary`, resource-pack namespace `tnt_library` (underscore preserved via explicit two-arg `NamespacedKey`).

## 2. Repository

- [x] Repository is `carmelosantana/minecraft-tnt-library` with an SSH `origin` and `main` branch. → created public 2026-08-23 (`git@github.com:carmelosantana/minecraft-tnt-library.git`), `main` tracks `origin/main`, commit `f5bcf09`.
- [x] Existing user-owned worktree changes were identified and preserved. → none; directory was empty before scaffold.
- [x] No `herobrinesystems` references remain in source, metadata, workflows, remotes, or documentation. → scan clean across all real surfaces; the only match is this checklist's own rule statement on the line below (template text, not an obsolete-owner reference).

## 3. Metadata

- [x] AGPL-3.0-or-later `LICENSE` and Maven license metadata are present and consistent. → full AGPL `LICENSE`; `pom.xml <licenses>` names "GNU Affero General Public License v3.0 or later".
- [x] `https://xpfarm.org` metadata and Carmelo Santana author metadata are present. → `pom.xml` `<url>`/`<developers>`, `plugin.yml` `author`/`website`.
- [x] `play.xpfarm.org` is recorded as the public Minecraft server hostname where server identity is documented. → `README.md` "Playing" section.
- [x] New work uses the `org.xpfarm` Maven group, or an existing-coordinate compatibility decision is documented. → `org.xpfarm:tnt-library:0.1.0` (new work, no carve-out).
- [x] Repository slug, artifact, releasable JAR, updater destination, and `plugin.yml` names are consistent. → slug `tnt-library` = `artifactId` = JAR base (`tnt-library-0.1.0.jar`, verified built) = updater dest `tnt-library.jar`; `plugin.yml name: TNTLibrary`.
- [x] No secrets committed in source, defaults, tests, logs, history, or documentation. → reviewed; config resource-pack url/sha1 empty, no tokens/endpoints.

## 4. Compatibility

- [x] Java 25/Paper 26.1.2 build 74 compile succeeds and `plugin.yml` uses `api-version: '26.1'`, matching the API compiled against. → `mvn clean verify` green (Java 25, paper-api 26.1.2.build.74); embedded `plugin.yml` `api-version: '26.1'`; runtime Paper 26.1.2 loaded the plugin green.
- [x] Hard dependencies, soft dependencies, optional APIs, and load ordering were reviewed and declared. → only compile dep is `paper-api` (provided). `softdepend: [WorldGuard, GriefPrevention]`; `loadbefore: [Geyser-Spigot]` so the `onLoad` Geyser-asset install lands before Geyser reads its mappings. Geyser/Floodgate are **not** a hard/soft *depend* by design — the plugin enables normally on a Java-only server (installer no-ops when no Geyser is detected). No hard deps.
- [x] Geyser/Floodgate/ViaVersion review covers Bedrock-safe input, UI, inventory, identity, and protocol behavior. → placed bomb is a **real vanilla `note_block` reskinned via a Java resource pack + Geyser Custom Blocks** (`unit_cube`), so it renders as a true 3D cube on **both** editions (re-architected from the Bedrock-invisible display-entity rig). All effects server-authoritative (explosion/block changes); feedback via action-bar `Component` + sounds + particles (all Geyser-translated; no Java-only chat-input, no custom attributes). Runtime: floodgate, Geyser-Spigot, ViaVersion booted **green** alongside TNTLibrary; the `onLoad` installer wrote the mapping + Bedrock pack into Geyser's folder and **Geyser parsed it with `enable-custom-content: true` and registered custom blocks with no error**. **Per-bomb cube render on a real Bedrock/Java client is not headlessly verifiable — deferred to the gate-12 play-test (see §7).**

## 5. External services

- [x] External integrations are disabled by default or require explicit configuration and have bounded timeouts. → **none** — no Ollama/Umami/network calls. WorldGuard/GriefPrevention are optional in-process plugin soft-depends, not network services; Phase-1 `ProtectionService` is `AllowAllProtection` and protection is inherited via native explosion filtering.
- [x] Ollama/Umami-style external endpoints are optional and failure-tolerant when applicable. → N/A, no external endpoints.
- [x] Endpoint failure cannot fail server/plugin startup, and diagnostics redact secrets. → no endpoints; runtime log scan showed no secrets and no errors on enable.

## 6. Tests and build

- [x] Unit tests cover separable logic, configuration, serialization, permissions, and failure paths where applicable. → **118 tests** across core (registry/keys/recipe-spec), config (never-throws parsing, bad-value defaults, provider, **`PackDefaults` classpath load + `${...}`-placeholder coercion, and the full `resource-pack.url`/`.sha1` resolution order in `ResourcePackResolutionTest`**), item (recipe shape/id), block (the note_block state-claim table: completeness vs. `BombType`, distinct notes, round-trip, canonical key), detonation (crater/rim math), command (subcommand routing, amount parsing, permission constants), and **delivery (the join-time Adventure `ResourcePackRequest` factory, the pure `PackDeliveryDecision`, and the reflective `BedrockDetector`)**. **v0.2.0 adds Phase 2 — 265 tests; v0.2.1 adds `PlacedBombIndex` — 271 tests; v0.3.0 adds the isolated F-Bomb + G-Bomb packages — 348 tests; v0.4.0 adds the isolated White Out package — 412 tests total**, with the isolated, headless-tested **Twins** package (`TwinColor`/variant mapping, `PlacedTwinIndex`, `TwinsPairing`, `TwinsBeam`, `TwinsPlan`, `TheTwins`) and **Smart Bomb** package (`SmartBombParams`/`ParamCodec`, `YamlSmartBombStore`, `TriggerEvaluator` incl. the time reach/cross + proximity inner-threshold regression pins, `ProximityWarning`, `SmartBombDefaults`), both developed via subagent-driven development (opus implementer + reviewer per task). (v0.1.1 added the resource-pack delivery + PackSquash pipeline on top of the v0.1.0 real-block re-architecture.)
- [x] `PluginDescriptorTest` parses `plugin.yml` and `config.yml` with SnakeYAML and asserts `name`, `main`, a `String`-typed `api-version`, a fully-substituted `version`, every command the code looks up, every permission the code checks, and the declared soft dependencies. → present; asserts `tntlibrary` command and `tntlibrary.admin` / `.command.give` / `.command.reload` / `.use.waterbomb` (the nodes the command + listeners check) and `WorldGuard` softdepend.
- [x] `mvn --batch-mode --no-transfer-progress clean verify` succeeds. → BUILD SUCCESS, **412 tests**, 0 failures (v0.4.0). Verified green under all three pack-hash conditions: empty (plain local build), the literal `${tnt.pack.sha1}` placeholder (the environment quirk that broke CI), and a real baked hash via `-Dtnt.pack.sha1=<hash>` (the CI/release path).
- [x] The shaded releasable JAR and embedded `plugin.yml` were inspected; `original-*` JARs are excluded. → `target/tnt-library-0.1.0.jar`; embedded `plugin.yml` `version: '0.1.0'` (substituted), correct main/api-version/commands/permissions/`loadbefore`; **0** `org/bukkit` or `io/papermc` classes bundled (provided scope correct); the Java resource pack (`pack/**`) and Geyser assets (`geyser/**`, unfiltered so PNGs/JSON ship byte-for-byte) are bundled; 33 plugin classes, rig package gone; `original-tnt-library-0.1.0.jar` is the pre-shade intermediate, not a release asset.

## 7. Matrix

### 7a — single-plugin runtime verification (dev gate) — DONE

Booted a fresh disposable Legendary stack on `target/tnt-library-0.1.0.jar` via `scripts/test-stack.sh up` (exit 0 — Paper logged `Done (15.084s)`, the Java port served a real Minecraft handshake at protocol 775 / Paper 26.1.2, and RCON `plugins` listed **`TNTLibrary` green** alongside **floodgate, Geyser-Spigot, ViaVersion** — all green, whole cross-play stack up together). Exercised over RCON:

- `/tntlibrary list` → `Registered bombs (1): waterbomb [enabled]`.
- `/tntlibrary reload` → `reloaded; 1 bomb(s) registered [waterbomb]` (config re-read path works).
- `/tntlibrary give waterbomb` (no player) → graceful console error.
- **`onLoad` Geyser installer:** logged `Installed/updated 6 Geyser custom-block asset file(s) in Geyser-Spigot`; verified the mapping + Bedrock pack (6 files) landed at `/minecraft/plugins/Geyser-Spigot/{custom_mappings,packs/tnt_library}`; Geyser (with `enable-custom-content: true`) parsed the mapping and registered custom blocks with **no error** about our file.
- Log scan: **no exceptions, no SEVERE, no leaked secrets**; clean `Loading`/`Enabling`/`enabled — 1 bomb(s) registered` lines. Torn down with `down` (slot released, no leak).

**v0.1.1 re-verification (resource-pack delivery delta) — DONE.** Rebooted a fresh disposable stack on `target/tnt-library-0.1.1.jar`, built with a real placeholder hash (`-Dtnt.pack.sha1=…`) so the join-time delivery path was **armed** (`packDeliveryEnabled` true). `up` exit 0 — Paper `Done (14.123s)`, Java port served protocol 775, and RCON `plugins` listed **`TNTLibrary` v0.1.1 green** alongside floodgate, Geyser-Spigot, ViaVersion. The new `ResourcePackDeliveryListener` registered in `onEnable` with **no exception**, and the absence of the "resource-pack … not configured yet" INFO confirms delivery was armed rather than silently disabled. `/tntlibrary list` and `/tntlibrary reload` worked; the `onLoad` Geyser installer wrote its 6 asset files; log scan clean (no exceptions/SEVERE/secrets). Torn down cleanly. **Still gate-12:** the actual pack **download by a real joining client** (Java accepts, Bedrock is skipped) is not headlessly reachable — no client joins the RCON stack — so it stays on the play-test obligation below.

**Behaviors NOT reachable headlessly — gate-12 play-test obligation (real Java + Bedrock client on `play.xpfarm.org`, with Geyser `enable-custom-content: true`):**
- Crafting the Water Bomb (TNT + 4 water buckets) actually yields the item, and the item shows as the **3D cube in inventory** (item_model → block model).
- Placing the item yields the claimed `note_block` state (no vanilla TNT) and the cube **renders on Java AND Bedrock** (the whole reason for the Custom Blocks re-architecture — must confirm the Bedrock cube actually draws).
- **Real-TNT ignition parity:** flint & steel, fire/lava spread, and redstone each light the fuse → smoke+primed cue → explosion → **crater floods with permanent water sources to the rim** (Nether skips the fill); WG/GriefPrevention spare protected regions.
- **Physics lock** holds (the note-block instrument never re-derives from the block below), the block is **silent**, and **breaking it returns the Water Bomb item**.
- Edge case: a hand-tuned real note block at `instrument=pling,note=19,powered=false` would be treated as a bomb (documented low-probability limitation) — spot-check acceptability.
- **Resource-pack delivery (v0.1.1):** a joining **Java** player is offered/receives the CI-published pack over the baked URL+SHA-1 and, on accept, sees the custom block/item textures; a **Bedrock** player is skipped (Geyser serves the Bedrock pack) and is not disconnected; a `resource-pack.required: true` server behaves as configured.

### 7a — v0.4.0 Phase 4 re-verification (White Out) — DONE

Booted a fresh disposable stack (throwaway `bombs.whiteout.enabled: true` build, reverted to disabled-by-default for the shipped artifact). Plugin loaded **green** alongside floodgate, Geyser-Spigot, ViaVersion; **412 tests** pass in `mvn verify`. Verified:

- Plugin **enables green with the White Out feature wired** (`WhiteoutFeature`, mirroring `GBombFeature` — built before registration, registry holds the runtime-bound `whiteout()`). With whiteout enabled: **5 bomb(s) registered [waterbomb, twins_white, twins_black, smartbomb, whiteout]**.
- **Recipe registered with no exception or crafting conflict** — `SPS/PTP/SPS` (snow_block/packed_ice/TNT) deconflicts against the other five (the F-Bomb shares the ring *shape* but with wither-skull/gunpowder, different materials).
- **Reload cycles the feature cleanly** — `WhiteoutFeature.disable()` runs `WhiteoutRuntime.cancelAll()` (cancels in-flight sequences + clears every tracked entity's freeze/debuffs, the mandatory no-leak path) then unregisters the cleanup listener — **no exceptions, no SEVERE**; whiteout still registered after reload.
- **Embedded assets confirmed in the shaded jar:** note=24 blockstate variant + Geyser `custom_mappings` state_override, the icy-frost model/item, and all six whiteout PNGs in both trees; shipped `config.yml` carries whiteout **disabled**.

**Still gate-12 for v0.4.0 (real client on `play.xpfarm.org`):** the whiteout (note=24) cube renders on Java + Bedrock; the no-leak detonation paths — plugin disable mid-storm, chunk-unload mid-storm, a totem/invulnerable survivor's debuffs cleared at collapse, two overlapping vortices, and scar-sweep completeness at `RADIUS_MAX` — plus the guaranteed server-side `DamageSource[FREEZE]` kill on a full-health Java **and** Bedrock player, and the permanent white_concrete scar sparing region-protected columns / other bombs' blocks / containers. The pull→collapse chain can't be triggered headlessly (setblock bypasses the ignition events), so it awaits a real client or the RCON harness. Region gating is inert until a real region-aware `ProtectionService` is wired (ecosystem item).

### 7a — v0.3.0 Phase 3 re-verification (F-Bomb + G-Bomb) — DONE

Booted a fresh disposable stack on `target/tnt-library-0.3.0.jar`. Plugin loaded **green** alongside floodgate, Geyser-Spigot, ViaVersion; **348 tests** pass in `mvn verify`. Verified:

- Plugin **enables green with both new features wired** (`GBombFeature` + `FBombFeature`), 4 bombs registered by default (both Phase 3 bombs ship disabled). Enabling `bombs.fbomb`/`bombs.gbomb` + `/tntlibrary reload` → **6 bomb(s) registered [waterbomb, twins_white, twins_black, smartbomb, gbomb, fbomb]**; `/tntlibrary list` shows both.
- **Reload cycles both features cleanly** — `GBombFeature.disable()` runs `cancelAll → restoreAll` (the mandatory gravity-restore path) and `FBombFeature.disable()` tears down any in-flight cinematic + sweeps its rig entities — **no exceptions, no SEVERE**.
- **onLoad Geyser installer** wrote the updated `custom_mappings` (now 6 `state_overrides`, notes 18–23) + the `.mcpack`; Geyser parsed it cleanly (no error about the donor or overrides), and the shipped `tnt_library.mcpack` **carries all six new fbomb/gbomb textures** (20 entries). The block-drift heal (v0.2.1) and the `.mcpack` packaging (v0.2.1) both remain in force.
- Note-slot allocation confirmed at runtime: a `note=22`/`note=23` state is recognized as `fbomb`/`gbomb` by `BombBlocks`.

**Still gate-12 for v0.3.0 (real client on `play.xpfarm.org`):** the fbomb (note=22) + gbomb (note=23) cubes actually render on Java + Bedrock; **F-Bomb** — igniting a placed F-Bomb summons the fake-Wither rig at an open-air offset, the boss bar adds/removes by range, the `WitherSkull` volley fires and is survivable, the blast craters and the rig despawns with no orphaned display entities (incl. a mid-cinematic reload/stop); watch a far `spawn-distance` for the `probeOpenness` main-thread hitch. **G-Bomb** — igniting it launches/hangs/slams entities in radius; a full-health **unarmored Java** player dies, a **Bedrock** player dies via the server-side `DamageSource[FALL]` finisher even with weak/absent float, and — the #1 acceptance — **no entity is left gravity-off** after the slam or a cancel/reload mid-hang (`/data get entity <id> NoGravity` absent/0b), while an invulnerable/creative player survives but still has gravity restored. The ignition→sequence chain can't be triggered headlessly (setblock bypasses the ignition events), so it awaits a real client or the RCON harness.

### 7a — v0.2.0 combined re-verification (Twins + Smart Bomb) — DONE

Booted a fresh disposable stack on `target/tnt-library-0.2.0.jar` (built with a real placeholder hash so delivery stays armed). `up` exit 0 — Paper `Done (15.136s)`, Java port served protocol 775, and RCON `plugins` listed **`TNTLibrary` v0.2.0 green** alongside floodgate, Geyser-Spigot, ViaVersion. Verified:

- `/tntlibrary list` → **4 bomb(s) registered [waterbomb, twins_white, twins_black, smartbomb]** — both Twin variants and the Smart Bomb register from `bombs.twins` / `bombs.smartbomb`.
- **onLoad Geyser installer** wrote **15** custom-block asset files (up from 6); **Geyser parsed the multi-state `custom_mappings` cleanly** — "Registered 8 custom blocks", "Registered 203 custom block overrides", **no error** about the `note_block` donor or the four `state_overrides`. This is the empirical validation that multiple vanilla-state overrides on one `note_block` donor work.
- `/tntlibrary reload` → 4 bombs re-registered. `/tntlibrary smart get radius` from console → graceful `Only a player can program a Smart Bomb.` (the SMART subcommand is wired and permission/player-gated).
- Log scan: **no exceptions, no SEVERE, no ClassNotFound/NoClassDefFound** (Floodgate present on the stack; the guarded Cumulus form path did not classload-crash), **no leaked secrets**. Clean teardown, slot released.

**Still gate-12 for v0.2.0 (real client on `play.xpfarm.org`):** the twins_white/twins_black inverse cubes and the smartbomb command-block cube actually render on Java + Bedrock; igniting one Twin carves the trench to its nearest opposite and removes both, a lone Twin fizzles and drops its item, the range cap holds; the Smart Bomb's Bedrock Floodgate form and Java chest GUI open and persist params, arming + each trigger (delay/time/proximity) detonates with the programmed size, and the proximity warning escalates before detonating at ~2 blocks.

### 7b — full-roster matrix — DONE (2026-08-29, v0.4.0, on explicit request after the updater pass)

`xpfarm-test-stack matrix up --from-releases` booted the whole roster via the **published updater image** (which installs each plugin from its latest release, so TNTLibrary resolved to the just-published **v0.4.0** by auto-follow). Result: **MATRIX PASSED: 23/23 expectation(s) met** — `TNTLibrary  PRESENT` (loaded green) alongside every other roster entry; zero ABSENT, zero RED. Stack torn down and slot released cleanly (`matrix down`). Out-of-band ecosystem-coherence evidence; it did not gate the already-shipped v0.4.0 release. The roster is now 23 entries (was 22 at v0.3.0). Other plugins' checklists were not backfilled with this row in this standalone pass.

### 7b — full-roster matrix — DONE (2026-08-25, on explicit request after the v0.3.0 updater pass)

- [x] Fresh-volume [Legendary Java Minecraft Geyser Floodgate stack](https://github.com/TheRemote/Legendary-Java-Minecraft-Geyser-Floodgate) test covers every updater-managed plugin. → `xpfarm-test-stack matrix up --from-releases` booted the full roster from published releases; Paper `Done (16.059s)`, Java port served protocol 775. **MATRIX PASSED: 22/22 expectation(s) met.**
- [x] Each updater-managed plugin's manifest `enabled` value, default state, and expected fresh-volume behavior are recorded separately. → all **22** manifest entries are `enabled` (absent flag = true) with no `pin` (cross-checked `plugins.json` ↔ `CURRENT_STATE.md`, no discrepancy; excluded Agent Steve/GeoBrot/Solar Power correctly absent from both). Every entry is expected to install + enable, and every one did — the rig printed one `PRESENT` row per plugin, **TNTLibrary** included. No disabled entries this roster, so no intentional-absence rows.
- [x] Paper, Geyser, Floodgate, and ViaVersion start successfully together. → all four green in RCON `plugins` (26/26 plugin entries green `§a`, zero red `§c`, no `Disabling`/failed-load). The only log anomaly was a **benign caught soft-depend probe** — MagicCarpet's `ClassNotFoundException: com.sk89q.worldguard.WorldGuard` (WorldGuard absent from the base stack), which it handles with a WARN and degrades to "ignore WorldGuard regions". `play.xpfarm.org` resolves (168.231.74.113) and its Java entry point is TCP-reachable.
- [~] Affected commands, permissions, persistence, and configuration reload were exercised over RCON with no server-wide hot reload. → the **7a** rows already exercise TNTLibrary's own commands/permissions/reload single-plugin; the 7b pass verifies whole-roster coexistence (present + enabled, no cross-plugin conflict), not a re-run of every plugin's command surface. Per-plugin command exercise stays each plugin's 7a job.
- [x] Ollama and Umami unavailable-endpoint tests keep the server and plugins available when applicable. → both enabled and **degraded gracefully** on the fresh volume: `Umami analytics is disabled; no tracking listeners or network clients were started` and `Ollama integration is disabled; no API client or listeners were started` (Llama companion dormant). No leaked secrets in any log line; server + all plugins stayed up.

> **Method note:** verified via the rig's built-in per-plugin `PRESENT`/expectation table (22/22) plus a bounded stack-wide log/color scan by the primary agent, rather than 22 separate per-plugin subagents — proportionate to a clean full-pass. This 7b run is out-of-band ecosystem-coherence evidence; it does not gate the already-shipped v0.3.0 release. The other 21 plugins' checklists were not backfilled with this row in this standalone pass.

## 8. CI/CD

- [x] Identical standard plugin Actions workflow is installed with the required triggers, Temurin 25 build, artifact, checksum, and release behavior. → `.github/workflows/build.yml` (gate 8a); triggers push `main` / tags `v*` / PR→`main` / dispatch; `actions/checkout@v7`, `setup-java@v5` Temurin 25, `mvn clean verify`, bare-filename `SHA256SUMS.txt`, `upload-artifact@v7`, `gh release` on `v*`.
- [x] Successful main Actions run is recorded before tagging. → **v0.4.0:** main run `33250866865` **completed / success** on commit `01cbeb8` (release-prep). **v0.3.0:** main run `32860764603` **completed / success** on commit `57b6447` (release-prep). **v0.2.1:** main run `32851106059` on `ecba6df`. **v0.2.0:** main run `32667062564` on `e457ac3`. (v0.1.1 was `32657520501` on `f1d7f5f`; v0.1.0 was `32654660644` on `8a9a6ed`.)
- [x] Workflow permissions contain no broader access than the documented contract. → `permissions: contents: write` only.

## 9. Release

- [x] Semantic version matches the POM, plugin metadata, and `v<version>` tag. → **v0.3.0** in `pom.xml`; `plugin.yml` `version: '${project.version}'` → embedded `0.3.0`; annotated tag `v0.3.0` on commit `57b6447`. (Prior: `v0.2.1` on `ecba6df`, `v0.2.0` on `e457ac3`, `v0.1.1` on `f1d7f5f`, `v0.1.0` on `8a9a6ed`.)
- [x] Successful tag Actions run and GitHub release are recorded. → **v0.4.0** tag run `33250921812` **completed / success** (incl. PackSquash pack + tagged-asset upload); release `v0.4.0` published (not draft/prerelease) — https://github.com/carmelosantana/minecraft-tnt-library/releases/tag/v0.4.0. **v0.3.0** tag run `32860899118` **completed / success**; release published — https://github.com/carmelosantana/minecraft-tnt-library/releases/tag/v0.3.0. (v0.2.1 tag run was `32851208914`.)
- [x] Release contains exactly one updater-matching JAR plus `SHA256SUMS.txt` and no `original-*` JAR. → **v0.4.0** assets: `tnt-library-0.4.0.jar` + `SHA256SUMS.txt` (plus the resource-pack sidecars `tnt-library-pack-0.4.0.zip` + `.zip.sha1`, which are not JARs and do not match the updater regex); `original-*` count 0.
- [x] Downloaded release assets pass `sha256sum --check SHA256SUMS.txt`. → **v0.4.0:** `tnt-library-0.4.0.jar: OK`. **Delivery integrity also verified:** the JAR's baked `pack.sha1` (`f2c8f8e9566f4a7739190660c17e71ce7e7a6824`) exactly equals the published `tnt-library-pack-0.4.0.zip` SHA-1 (the pack now carries the White Out cube too), and `pack.url` points to the v0.4.0 asset. (**v0.3.0:** `tnt-library-0.3.0.jar: OK`; baked `pack.sha1` `38b88b45…` matched its pack.)

## 10. Updater

- [x] Updater manifest/tests cover repository, destination, anchored asset regex, legacy globs, enabled state, and optional pin. → entry added to `carmelosantana/minecraft-plugin-updater` `plugins.json` (commit `60374e2`): `repo carmelosantana/minecraft-tnt-library`, `destination tnt-library.jar` (unique), `asset_regex ^tnt-library-[0-9].*\.jar$` (anchored), `legacy_globs ["tnt-library-[0-9]*.jar"]`, enabled by default (no `enabled`/`pin`, matching the roster). `json.tool` valid; 11 updater unit tests pass.
- [x] Fresh install, upgrade, no-op, legacy archival, endpoint failure, and checksum failure behaviors pass. → dry-run: `TNT Library: would install v0.1.0`. Sandbox real run: `installed v0.1.0; archived legacy JARs: tnt-library-0.0.9.jar`, old destination backed up, then `already current (v0.1.0)` on repeat. Endpoint/download/checksum-failure paths covered by the passing unit suite (`test_bad_checksum_preserves_installed_jar` et al.).
- [x] Updater dry-run uses a disposable directory and never a production plugin directory. → all runs used `/tmp` sandbox dirs (`--plugins-dir`/`--state-file`/`--backup-dir` all inside `/tmp`); production `/minecraft` never touched; sandbox discarded after.
- [x] Failure retains the installed JAR and default fail-open behavior permits Minecraft startup. → verified by `test_bad_checksum_preserves_installed_jar` (installed JAR preserved on checksum failure) and the updater's fail-open, warn-and-continue default.
- **Auto-follow confirmed (no manifest change):** the entry has no `pin`, so it tracks the latest non-prerelease release. A disposable dry-run after publishing **v0.2.0** printed `TNT Library: would install v0.2.0`, and the anchored `^tnt-library-[0-9].*\.jar$` regex correctly selected `tnt-library-0.2.0.jar` (not the `tnt-library-pack-0.2.0.zip` sidecars). No `plugins.json` edit was required (also confirmed at v0.1.1). **v0.2.1** and **v0.3.0** track identically (no pin, same regex). **v0.3.0 confirmed (2026-08-25 `minecraft-plugin-updater` pass):** `json.tool` valid; 11 updater unit tests pass; `destination tnt-library.jar` unique across all 22 manifest entries; a disposable dry-run printed `TNT Library: would install v0.3.0` (real fetch + checksum), and a sandboxed real run against a single-entry manifest showed `installed v0.3.0` (222480-byte JAR) → `already current (v0.3.0)` on repeat → legacy archival moved a planted `tnt-library-0.2.9.jar` into `--backup-dir`. Endpoint/download/checksum failure paths and installed-JAR-preservation are covered by the passing unit suite. No `plugins.json` edit was required. **v0.4.0 confirmed (2026-08-29):** `json.tool` valid; 11 updater unit tests pass; dry-run printed `TNT Library: would install v0.4.0`; a sandboxed real run showed `installed v0.4.0; archived legacy JARs: tnt-library-0.3.0.jar` (legacy moved into `--backup-dir`), and the installed `tnt-library.jar` SHA-256 `0a50d84e…` **exactly matched** the release `SHA256SUMS.txt` entry (real fetch + checksum-verified). A follow-up run hit an unauthenticated GitHub API rate-limit (HTTP 403) and correctly **failed open** — `keeping installed JAR` — which is itself a required behavior; the clean `already current` no-op is unchanged from v0.3.0 (same state-file logic). No `plugins.json` edit was required.

## 11. Deployment

Not a gate. Deployment is updater pickup: a verified release plus a correct manifest entry is all
this lifecycle owes. Leaving this section entirely unticked is the normal resting state and blocks
nothing — not release, not enrolment, not handoff.

- [ ] Enrolment confirmed live and correct: release sound, manifest entry on `origin/main`, gate 10 genuinely completed.
- [ ] Deployment evidence recorded, if and only if an operator relayed some. Otherwise note "enrolled, not known to be deployed" and leave unticked.

## 12. Handoff

- [ ] Current-state documentation refreshed with release, CI, updater, deployment, and local pending state.
- [ ] Known limitations, skipped checks, configuration or migration notes, rollback guidance, and follow-up owner are recorded.
- [ ] Evidence distinguishes source commit, published tag/release, updater state, and deployed state without exposing secrets.
- [ ] Client play-test obligation recorded with a named owner and a target date: `<owner>` / `<date>`.
- [ ] Client play-test outcome recorded once performed, covering Java join, Bedrock join, and any form, inventory, or rendered item behavior this plugin introduces. Leave unchecked with the owner and date above until the team has run it; an unchecked box here does not block a release, but an unrecorded obligation is a gate 12 failure.
- [ ] Public deployment reachability confirmed during that pass: `play.xpfarm.org` reaches the intended Java and Bedrock entry points.
