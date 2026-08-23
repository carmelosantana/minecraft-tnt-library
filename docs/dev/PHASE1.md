# Phase 1 — Framework + Water Bomb (dev gate task list)

> **STATUS: COMPLETE (2026-08-23).** All of T1–T6 landed; `mvn clean verify` green at **86 tests**; single-plugin runtime verification (gate 7a) passed on a disposable Legendary stack (plugin green with Geyser/Floodgate/ViaVersion). Follow-up nits: (a) `core.CustomTnt` → `detonation.DetonationContext` soft package cycle, tidy later; (b) real textures + resource-pack pipeline pending the asset track; (c) redstone/fire ignition deferred (flint & steel only this phase).

Scope: prove the whole path end-to-end — item → display-entity rig → ignite → detonate → (placeholder) assets. Later phases (Smart+Twins, F+G, White Out) are separate dev runs.

Textures are **placeholder** this phase (art-direction track runs in parallel); the framework must accept a real `item_model` + block model later without rework.

| ID | Task | Package | Depends on | Open questions |
|---|---|---|---|---|
| T1 | Framework core: `Keys` (PDC), `CustomTnt` interface, `TntRegistry`, bomb ids | `…core` | — | none |
| T2 | `TntLibraryConfig` immutable record from YAML; per-bomb + protection; never throws | `…config` | — | none |
| T3 | Water Bomb item builder (`Material.TNT` + `item_model` + PDC id) + `ShapedRecipe` (shape-as-data) | `…item` | T1 | none |
| T4 | Display-entity placement rig: place → `BlockDisplay`+`Interaction` (PDC-tagged); ignite → primed | `…rig` | T1 | none |
| T5 | Detonation framework: listeners, phase runner (1 `runTaskTimer`+tick), region-protection reflective adapter, Water Bomb `detonate` (explosion + crater water-fill) | `…detonation` | T1–T4 | ~~crater-fill geometry~~ **RESOLVED** ↓ |

**Water Bomb crater-fill (owner decision, 2026-08-23):** capture `EntityExplodeEvent.blockList()`; place water **SOURCE** blocks in every destroyed cell **at or below the surrounding terrain rim** (fill the bowl to the rim, no uphill overflow); water is **permanent** (real terrain change, no auto-cleanup); **skip protected regions**; **Nether caveat** — water evaporates there, so skip placement and note it.
| T6 | Plugin wiring + `/tntlibrary` command (give/list/reload) + `PluginDescriptorTest` assertions | root + `…command` | T1–T5 | none |

Dispatch order: T1‖T2 → T3‖T4 → T5 → T6. Each subagent creates only its own package + tests and does **not** touch `pom.xml`, `plugin.yml`, or `TntLibraryPlugin.java` (T6 wires everything).

Build toolchain (bundled): `JAVA_HOME=/home/carmelo/Projects/Minecraft/Plugins/.toolchain/jdk-25.0.3+9`, mvn at `.toolchain/apache-maven-3.9.11/bin/mvn`.
