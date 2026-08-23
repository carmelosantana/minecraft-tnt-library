# New or Edited Plugin Checklist

Copy this file for one plugin and replace every `<...>` field. Leave an unchecked box with a short explanation when a gate is not complete; do not silently remove inapplicable checks.

- Plugin name: `TNTLibrary`
- Slug: `tnt-library`
- Repository: `carmelosantana/minecraft-tnt-library`
- Owner: `Carmelo Santana`
- Target version: `0.1.0`
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

- **`CustomTnt` definition** (interface/abstract base) + **`TntRegistry`** (id → `Supplier<CustomTnt>` map, unit-testable without a server, mirrors redstone-stuff `ItemRegistry`). Each definition carries: id, display name, `ItemStack` builder (base `Material.TNT` + custom `item_model` + PDC id), `ShapedRecipe` (shape-as-data for JUnit), placed-block appearance (BlockDisplay model + per-face textures), fuse ticks, config/permission keys, and a `detonate(Location center, Entity primer)` hook.
- **Item identity:** PDC key `tnt_library:tnt_id` (STRING). Recipes registered with `removeRecipe(key,true)` then `addRecipe(recipe,true)` (resend on reload).
- **Placed block = display-entity rig:** placing the item spawns a `BlockDisplay` (custom-textured cube, distinct top/side/bottom per bomb) + an `Interaction` entity, both PDC-tagged. Ignition sources (flint & steel, redstone current, fire/lava spread) convert the rig to a primed state that runs the bomb's `detonate` after its fuse. Geyser-safe; no client-side block model required.
- **Shared detonation services** provided to every bomb: radius entity-gathering, region-protection check (soft-depend), explosion helper (`World#createExplosion`), particle/sound helpers, and a scheduler-driven **phase runner** (one `runTaskTimer` + tick counter; interval-gated effects via `tick % n`, per tuesday-twister).

### Commands

- `/tntlibrary` (alias `/tntlib`) — root admin/util command.
  - `give <bomb> [player] [amount]` — grant a bomb item (perm `tntlibrary.command.give`). Primary test/admin path; recipes are the survival path.
  - `list` — list registered bombs and their enabled/permission state.
  - `reload` — reload `config.yml` and re-register recipes/enabled state.
  - `smart <get|set> <key> <value>` — inspect/edit the targeted Smart Bomb rig's programmed parameters (perm `tntlibrary.command.smart`); a Bedrock-safe fallback for the config UI.
  - *(Open engineering question — will confirm before gate 4, per autonomy guardrail: whether Smart Bomb programming uses a command, a sign/anvil-text input, or a Geyser-compatible form. Not assumed here.)*

### Events (Paper/Bukkit)

- `BlockPlaceEvent` — intercept placing a bomb item → spawn the display-entity rig instead of a vanilla TNT block.
- `PlayerInteractEvent` — flint & steel on a rig → ignite; also Smart Bomb programming interactions.
- `BlockIgniteEvent` / `BlockRedstoneEvent` — redstone/fire ignition of a placed rig.
- `EntityExplodeEvent` / `BlockExplodeEvent` — attribute craters to a bomb via PDC; enforce region protection; drive Water Bomb crater-fill and White Out non-destruction.
- `PrepareItemCraftEvent` / recipe events — gate crafting behind per-bomb permission + config toggle.
- `EntityDamageEvent` (cause `FALL`) — G-Bomb fall-damage accounting; White Out impact damage.
- `PlayerMoveEvent` or scheduled proximity scan — Smart Bomb proximity sensing + warning sound.
- `PlayerQuitEvent` / chunk-unload — clean up rigs, boss bars, and in-flight phase tasks.

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
9. **White Out:** loose + living entities (incl. underwater animals, boats, items, leaves) in radius are pulled to center, take fire damage, then flung outward; stone and behind-door contents are spared; no block-destroying explosion.
10. **Dual-edition assets:** one `item_model` value renders the custom art on both Java (resource pack) and Bedrock (Geyser mapping); `PluginDescriptorTest`-style checks pass; JAR ships a self-consistent pack URL+SHA-1 from CI.
11. **Bedrock/Geyser safety:** all bomb feedback (warnings, boss UI, programming) works without Java-only attributes; Bedrock players get graceful degradation where client physics differ (documented in Known limitations).

### Known limitations (recorded honestly; cheap to fix later, expensive to discover at release)

- **G-Bomb on Bedrock:** the float/launch visuals will be weak or absent (Geyser doesn't translate `setGravity(false)`; player velocity is client-authoritative). The kill still lands via the server-side `DamageSource[FALL]` finisher, but the *experience* differs from Java. Accepted and documented.
- **White Out fidelity:** "suck in everything not stone or behind a door" needs a concrete spare-list ruleset; exact block/entity classification (what counts as "loose", how doors shelter contents) is an **open engineering question** to settle at gate 4, not assumed now.
- **Smart Bomb programming UI:** command vs. sign/anvil vs. Geyser form is an **open question** (see Commands) — Bedrock form support must be verified before committing.
- **Placed-block appearance** relies on display entities; extremely high bomb counts could add entity load. Phase-1 scope keeps counts modest; revisit if it matters.
- **Twins line-explosion shape** and **Water Bomb crater-cavity detection** algorithms are specified by intent, not yet by exact geometry — to be pinned at gate 4.
- No gates are intentionally withheld (active plugin, full pipeline). Complex bombs are **phased**, not withheld: Phase 1 framework + Water Bomb; Phase 2 Smart Bomb + Twins; Phase 3 F-Bomb + G-Bomb; Phase 4 White Out.

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
- [x] Hard dependencies, soft dependencies, optional APIs, and load ordering were reviewed and declared. → only compile dep is `paper-api` (provided). `softdepend: [WorldGuard, GriefPrevention]`. Geyser/Floodgate not declared as depend/softdepend by design (asset install belongs in `onLoad`, wrong phase for a depend); Phase-1 Geyser/Floodgate use is reflective/none. No hard deps.
- [x] Geyser/Floodgate/ViaVersion review covers Bedrock-safe input, UI, inventory, identity, and protocol behavior. → placed bomb is a **display-entity rig** (renders on Bedrock via Geyser with no client pack); all effects server-authoritative (explosion/velocity/block changes), feedback via action-bar/chat `Component` + sounds (no Java-only chat-input, no custom attributes). Runtime: floodgate, Geyser-Spigot, ViaVersion all booted **green** alongside TNTLibrary on one stack. **Client-render of the rig/item on a real Bedrock client is not headlessly verifiable — deferred to the gate-12 play-test (see §7 note).**

## 5. External services

- [x] External integrations are disabled by default or require explicit configuration and have bounded timeouts. → **none** — no Ollama/Umami/network calls. WorldGuard/GriefPrevention are optional in-process plugin soft-depends, not network services; Phase-1 `ProtectionService` is `AllowAllProtection` and protection is inherited via native explosion filtering.
- [x] Ollama/Umami-style external endpoints are optional and failure-tolerant when applicable. → N/A, no external endpoints.
- [x] Endpoint failure cannot fail server/plugin startup, and diagnostics redact secrets. → no endpoints; runtime log scan showed no secrets and no errors on enable.

## 6. Tests and build

- [x] Unit tests cover separable logic, configuration, serialization, permissions, and failure paths where applicable. → **86 tests** across core (registry/keys/recipe-spec), config (never-throws parsing, bad-value defaults, provider), item (recipe shape/id), rig (state/geometry/handle), detonation (crater/rim math), command (subcommand routing, amount parsing, permission constants).
- [x] `PluginDescriptorTest` parses `plugin.yml` and `config.yml` with SnakeYAML and asserts `name`, `main`, a `String`-typed `api-version`, a fully-substituted `version`, every command the code looks up, every permission the code checks, and the declared soft dependencies. → present; asserts `tntlibrary` command and `tntlibrary.admin` / `.command.give` / `.command.reload` / `.use.waterbomb` (the nodes the command + listeners check) and `WorldGuard` softdepend.
- [x] `mvn --batch-mode --no-transfer-progress clean verify` succeeds. → BUILD SUCCESS, 86 tests, 0 failures.
- [x] The shaded releasable JAR and embedded `plugin.yml` were inspected; `original-*` JARs are excluded. → `target/tnt-library-0.1.0.jar`; embedded `plugin.yml` `version: '0.1.0'` (substituted), correct main/api-version/commands/permissions; **0** `org/bukkit` or `io/papermc` classes bundled (provided scope correct); `original-tnt-library-0.1.0.jar` is the pre-shade intermediate, not a release asset.

## 7. Matrix

### 7a — single-plugin runtime verification (dev gate) — DONE

Booted a fresh disposable Legendary stack on `target/tnt-library-0.1.0.jar` via `scripts/test-stack.sh up` (exit 0 — Paper logged `Done (15.084s)`, the Java port served a real Minecraft handshake at protocol 775 / Paper 26.1.2, and RCON `plugins` listed **`TNTLibrary` green** alongside **floodgate, Geyser-Spigot, ViaVersion** — all green, whole cross-play stack up together). Exercised over RCON:

- `/tntlibrary list` → `Registered bombs (1): waterbomb [enabled]`.
- `/tntlibrary reload` → `reloaded; 1 bomb(s) registered [waterbomb]` (config re-read path works).
- `/tntlibrary give waterbomb` (no player) → graceful console error; `give waterbomb Ghost 5` (offline) → graceful "player not found" error.
- Log scan: **no exceptions, no SEVERE, no leaked secrets**; clean `Loading`/`Enabling`/`enabled — 1 bomb(s) registered` lines. Torn down with `down` (slot released, no leak).

**Behaviors NOT reachable headlessly — gate-12 play-test obligation (real Java + Bedrock client on `play.xpfarm.org`):**
- Crafting the Water Bomb (TNT + 4 water buckets) actually yields the item.
- Placing the item spawns the display-entity rig (no vanilla TNT) and consumes one item; the rig renders on Java **and** Bedrock.
- Flint & steel right-click primes → fuse → explosion → **crater floods with permanent water sources to the rim** (and Nether skips the fill); WG/GriefPrevention actually spare protected regions.
- Custom item/rig **texture rendering** (placeholder `Material.TNT` cube this phase; real art lands with the asset track).

### 7b — full-roster matrix — NOT RUN (out-of-band, not required for this release)

- [ ] Fresh-volume [Legendary Java Minecraft Geyser Floodgate stack](https://github.com/TheRemote/Legendary-Java-Minecraft-Geyser-Floodgate) test covers every updater-managed plugin.
- [ ] Each updater-managed plugin's manifest `enabled` value, default state, and expected fresh-volume behavior are recorded separately.
- [ ] Paper, Geyser, Floodgate, and ViaVersion start successfully together.
- [ ] Affected commands, permissions, persistence, and configuration reload were exercised over RCON with no server-wide hot reload.
- [ ] Ollama and Umami unavailable-endpoint tests keep the server and plugins available when applicable.

## 8. CI/CD

- [x] Identical standard plugin Actions workflow is installed with the required triggers, Temurin 25 build, artifact, checksum, and release behavior. → `.github/workflows/build.yml` (gate 8a); triggers push `main` / tags `v*` / PR→`main` / dispatch; `actions/checkout@v7`, `setup-java@v5` Temurin 25, `mvn clean verify`, bare-filename `SHA256SUMS.txt`, `upload-artifact@v7`, `gh release` on `v*`.
- [ ] Successful main Actions run is recorded before tagging. → **release gate (8b)**, not scaffold's to tick. Scaffold push triggered run `32647139252` (in progress at hand-off); `minecraft-plugin-release` records the green run before any tag.
- [x] Workflow permissions contain no broader access than the documented contract. → `permissions: contents: write` only.

## 9. Release

- [ ] Semantic version matches the POM, plugin metadata, and `v<version>` tag.
- [ ] Successful tag Actions run and GitHub release are recorded.
- [ ] Release contains exactly one updater-matching JAR plus `SHA256SUMS.txt` and no `original-*` JAR.
- [ ] Downloaded release assets pass `sha256sum --check SHA256SUMS.txt`.

## 10. Updater

- [ ] Updater manifest/tests cover repository, destination, anchored asset regex, legacy globs, enabled state, and optional pin.
- [ ] Fresh install, upgrade, no-op, legacy archival, endpoint failure, and checksum failure behaviors pass.
- [ ] Updater dry-run uses a disposable directory and never a production plugin directory.
- [ ] Failure retains the installed JAR and default fail-open behavior permits Minecraft startup.

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
