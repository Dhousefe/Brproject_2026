# `:game-server-core`

Phase 1 **owns** the full Interlude source tree:

```text
src/main/java/ext/mods/...     (~2869 .java)
src/main/kotlin/ext/mods/...   (~34 .kt)
```

## Run

```bash
# from repo root, JDK 25
./gradlew :game-server-core:run
# cwd = game/ (configs + data)
```

## Build

```bash
./gradlew :game-server-core:classes
./gradlew :app-dist:jar   # fat libs/server.jar
```

## Notes

- Packages remain `ext.mods.*` until Phase 2+ SPI.
- Login sources compile here; process entry is `:login-server:run`.
- Do **not** put sources back under root `java/` / `kotlin/`.
