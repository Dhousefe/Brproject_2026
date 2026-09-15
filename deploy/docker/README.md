# Docker / Compose (Phase 5)

## Prerequisites

1. Docker + Docker Compose v2  
2. Build the distribution:

```bash
./gradlew clean :app-dist:jar
```

3. Seed configs (once):

```bash
./tools/sync-brproject-data.sh
# For Docker DB hostnames, copy docker examples over live configs (or edit manually):
cp brproject-data/config-examples/game/server.properties.docker.example game/config/server.properties
cp brproject-data/config-examples/login/loginserver.properties.docker.example login/config/loginserver.properties
```

4. Copy env:

```bash
cp .env.example .env
```

## Start stack

From **repo root**:

```bash
docker compose -f deploy/docker/docker-compose.yml --env-file .env up -d --build
```

Services:

| Service | Role | Ports |
|---------|------|-------|
| `db` | MariaDB 11 | 3306 |
| `migrate` | Flyway via Gradle (one-shot) | — |
| `login` | LoginServer | 2106, 9014 |
| `game` | GameServer | 7777 |

## Logs

```bash
docker compose -f deploy/docker/docker-compose.yml logs -f login game
```

## Migrate only (host)

```bash
export DB_URL=jdbc:mariadb://localhost:3306/l2jdb
export DB_USER=brproject
export DB_PASSWORD=brproject
./tools/migrate-db.sh
```

## Stop

```bash
docker compose -f deploy/docker/docker-compose.yml down
# keep volume: omit -v
docker compose -f deploy/docker/docker-compose.yml down -v  # wipe DB
```
