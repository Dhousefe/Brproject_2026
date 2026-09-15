# `:app-dist`

Assembles the fat JAR used by Docker and legacy scripts:

```bash
./gradlew :app-dist:jar
# → libs/server.jar  (Main-Class: ext.mods.gameserver.GameServer)
```

Optional:

```bash
./gradlew :app-dist:syncModuleJars
# → libs/modules/*.jar
```
