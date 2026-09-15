# `:mod-boss-zerg`

Phase 2 pilot mod — anti-zerg for raid bosses (flag PvP, ally limits, heal penalty).

## SPI

- Implementation: `ext.mods.BossZerg.BossZergExtension`
- Service file: `META-INF/services/br.project.spi.Extension`
- Id: `boss-zerg`

## Config

`game/config/bosszerg.properties` (loaded by core `Config.loadBossZerg()`).

## Build

Included by default in `:app-dist` / `libs/server.jar`.

```bash
# Full build with BossZerg
./gradlew clean build

# Core without optional mods (no BossZerg on classpath of app-dist)
./gradlew clean build -PwithoutBossZerg=true
```

## Runtime flags

```bash
-Dbrproject.withoutMods=true
-Dbrproject.withoutBossZerg=true
-Dbrproject.mods=none
```
