/**
 * BossZerg first-party mod (Phase 2 pilot).
 * Depends on game-server-core; core must NOT depend on this module.
 */
plugins {
    id("java-library")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    api(project(":extensions-spi"))
    implementation(project(":game-server-core"))
}

tasks.jar {
    archiveBaseName.set("brproject-mod-boss-zerg")
    manifest {
        attributes(
            "Implementation-Title" to "BrProject BossZerg Mod",
            "Extension-Id" to "boss-zerg",
        )
    }
}
