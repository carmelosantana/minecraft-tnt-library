# TNT Library

A custom-TNT **framework** for [xpfarm.org](https://xpfarm.org), plus a growing set of creative,
premium explosives with matching textures on Java and Bedrock clients. Each explosive is a
craftable custom block you place and ignite like vanilla TNT — but with a signature detonation.

> Status: **early development.** This repository currently holds the buildable scaffold (gate 3 of
> the plugin lifecycle). The framework and the individual bombs are implemented at the dev gate. See
> [`docs/PLUGIN_CHECKLIST.md`](docs/PLUGIN_CHECKLIST.md) for the full scope and plan.

## The explosives

| Bomb | What it does |
|---|---|
| **Water Bomb** | Explodes, then floods the crater with water. |
| **The Twins** | A paired start/stop set that detonates the line between them. |
| **F-Bomb** | A Formidi-Bomb-style lure that summons a boss. |
| **G-Bomb** | Kills gravity in the blast — victims float, then slam down for lethal fall damage. |
| **Smart Bomb** | A command-block-looking, programmable TNT (size, delay, time, proximity). |
| **White Out** | Vacuums all loose and living things to the blast center, burns them, then flings them out. |

## Playing

Join the public server at **`play.xpfarm.org`** — reachable from both Java and Bedrock editions.

## Building

Requires JDK 25 and Maven.

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

The shaded, releasable JAR is produced at `target/tnt-library-<version>.jar`.

## License

[GNU Affero General Public License v3.0 or later](LICENSE). Copyright © Carmelo Santana.
