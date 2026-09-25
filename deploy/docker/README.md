# L2 NewEra — Docker + MariaDB

Este é o ambiente local reproduzível da primeira etapa de persistência. Ele
sobe somente os componentes necessários para validar o jogo: MariaDB, Flyway,
LoginServer e GameServer. Site, API/HPC e painel operacional ficam fora deste
stack até terem uma imagem e um contrato de configuração próprios.

## Pré-requisitos

- Docker Desktop com WSL 2 habilitado;
- distribuição compilada com `libs/server.jar`.

Na raiz do projeto:

```bash
./gradlew :app-dist:jar
cp .env.example .env
```

Altere as senhas de `.env` antes de expor qualquer porta fora da máquina local.

## Subir

```bash
docker compose -f deploy/docker/docker-compose.yml --env-file .env up -d --build
```

O serviço `migrate` executa as migrations MariaDB e registra o GameServer
local com `server_id=1`. O registro usa um HexID de desenvolvimento definido
por `GAME_SERVER_HEXID`; troque-o antes de qualquer implantação compartilhada.

Serviços e portas padrão:

| Serviço | Função | Porta |
|---|---|---:|
| `db` | MariaDB 11 | 3307 no host / 3306 no container |
| `migrate` | Flyway + seed do GameServer | — |
| `login-server` | LoginServer | 2106, 9014 |
| `game-server` | GameServer | 7777 |

## Diagnóstico

```bash
docker compose -f deploy/docker/docker-compose.yml ps
docker compose -f deploy/docker/docker-compose.yml logs -f login-server game-server
docker compose -f deploy/docker/docker-compose.yml logs migrate
```

Para validar a configuração sem iniciar:

```bash
docker compose -f deploy/docker/docker-compose.yml --env-file .env config
```

## Parar e resetar

```bash
docker compose -f deploy/docker/docker-compose.yml down
```

O volume MariaDB é persistente. Para apagar o banco local e recriar tudo do
zero, use `down -v`; isso remove personagens, contas e demais dados locais.
