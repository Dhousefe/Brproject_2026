/**
 * First-party mod: player-god (Phase 3 TRUE).
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
    implementation(project(":commons"))
}

tasks.jar {
    archiveBaseName.set("brproject-mod-player-god")
    manifest {
        attributes(
            "Implementation-Title" to "BrProject player-god Mod",
            "Extension-Id" to "player-god",
        )
    }
}

tasks.named<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
}
