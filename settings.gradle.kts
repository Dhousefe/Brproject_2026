/**
 * BrProject multi-module (Phase 1–3 TRUE layout).
 */
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.3.0-Beta2"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenCentral()
        google()
        mavenLocal()
        maven { url = uri("https://artifacts.deepl.com/maven/") }
    }
}

rootProject.name = "brproject"

include("game-network")
include("game-packets")
include("commons")
include("extensions-spi")
include("login-server")
include("game-server-core")
include("game-api")
include("app-dist")
include("db-migrate")
include("proxy")
include("benchmarks-network")

project(":game-network").projectDir = file("modules/game-network")
project(":game-packets").projectDir = file("modules/game-packets")
project(":commons").projectDir = file("modules/commons")
project(":extensions-spi").projectDir = file("modules/extensions-spi")
project(":login-server").projectDir = file("modules/login-server")
project(":game-server-core").projectDir = file("modules/game-server-core")
project(":game-api").projectDir = file("modules/game-api")
project(":app-dist").projectDir = file("modules/app-dist")
project(":db-migrate").projectDir = file("modules/db-migrate")
project(":proxy").projectDir = file("modules/proxy")
project(":benchmarks-network").projectDir = file("modules/benchmarks-network")

// Phase 3 TRUE: all first-party mods are optional Gradle projects
val withoutMods = providers.gradleProperty("withoutMods").orElse("false").get() == "true"
val withoutBossZerg = providers.gradleProperty("withoutBossZerg").orElse("false").get() == "true"

val allMods = listOf(
    "mod-boss-zerg",
    "mod-agathion",
    "mod-battle-boss",
    "mod-buff-shop",
    "mod-capsule-box",
    "mod-crypta",
    "mod-dressme",
    "mod-dungeon",
    "mod-email",
    "mod-farm-event",
    "mod-global-drop",
    "mod-levelup-maker",
    "mod-pix",
    "mod-player-god",
    "mod-roulette",
    "mod-safe-disconnect",
    "mod-summon-mob",
    "mod-tour",
    "mod-quest-recommender",
)

if (!withoutMods) {
    for (mod in allMods) {
        if (mod == "mod-boss-zerg" && withoutBossZerg) continue
        include(mod)
        project(":$mod").projectDir = file("modules/mods/$mod")
    }
}

include("game-enums")
project(":game-enums").projectDir = file("modules/game-enums")

include("game-model-api")
project(":game-model-api").projectDir = file("modules/game-model-api")
