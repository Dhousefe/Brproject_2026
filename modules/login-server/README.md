# modules/login-server

Login process module + **S2.2 Wave L1** pure islands.

## Wave L1 — owned packages (same package names)

| Package | Contents |
|---------|----------|
| `ext.mods.loginserver.enums` | `AccountKickedReason`, `LoginClientState` |
| `ext.mods.loginserver.crypt` | Blowfish stack (`LoginCrypt`, `NewCrypt`, …) |
| `ext.mods.loginserver.auth` | `AuthRules` |

## Wave L2 partial

| Package | Contents |
|---------|----------|
| `ext.mods.loginserver.network.SessionKey` | Pure session token (SecureRandom) |

Remaining `network/*` packets stay in core until `ConfigLogin` / managers move (L3 prep).

## Dependency graph

```text
login-server  ──api──►  commons
game-server-core  ──api──►  login-server + commons
login-server:run  ──runtime──►  game-server-core  (LoginServer main + L2/L3 still in core)
```

No **compile-time** cycle. Model / network / managers / `LoginServer` remain in
`game-server-core` until Waves L2–L3 (Config / ConnectionPool decoupling).

## Run

```bash
./gradlew :login-server:run
# cwd = login/  — requires MariaDB + login/config
```

Or legacy scripts: `./StartLogin_SemDashboard.sh` / `.command`.

## Tests

```bash
./gradlew :login-server:test
```
