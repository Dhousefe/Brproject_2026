# modules/commons

Foundation JAR shared by login and game server.

## Wave C1 — pure utils (S2.1)

| Type | Package |
|------|---------|
| `Rnd` | `ext.mods.commons.random` |
| `ArraysUtil` | `ext.mods.commons.util` |
| `PredicateHelpers` | `ext.mods.commons.util` |
| `HexUtil` | `ext.mods.commons.lang` |
| `PrimeFinder` | `ext.mods.commons.math` |

## Wave C2 — crypto + thin logging (S2.1)

| Type | Package |
|------|---------|
| `BCrypt` | `ext.mods.commons.crypt` |
| `StringReplacer` | `ext.mods.commons.lang` |
| `CLogger` | `ext.mods.commons.logging` |
| `MasterFormatter` + pure filters/formatters/handlers | `ext.mods.commons.logging.*` |

## Wave C3 / C4 partial — pool + networking foundation

| Type | Package |
|------|---------|
| `ConnectionPool` | `ext.mods.commons.pool` — `init(url, user, pass[, poolName])` (no `Config` statics) |
| `mmocore/*` | Full stack including pure `SendablePacket` |
| `IntXYZ`, `EffectView` | Interfaces for packet helpers |
| `ServerType`, `AttributeType`, `IPv4Filter` | `ext.mods.commons.network` |

**Still in game-server-core (item-coupled):** `ItemFilter`, `DropFilter`, `ItemLogFormatter`, `DropLogFormatter`.

**Still impure in core:** `StatSet`, `MathUtil`, `StringUtil` (Creature), gui, …

Marker class: `br.project.commons.BrProjectCommons`.
