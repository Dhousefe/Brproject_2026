/**
 * game-enums — Phase 2A decomposition.
 *
 * Pure Java enum module extracted from game-server-core. Contains only
 * standalone enums (no model/network/skills dependencies). Anything that
 * depends on model/ network/ skills/ scripting/ stays in game-server-core.
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
    // Intentionally zero project dependencies. Enums here must compile
    // against the Java standard library only. Anything that imports from
    // model/ network/ skills/ commons/ config/ stays in game-server-core.
}

tasks.named<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
}

tasks.jar {
    archiveBaseName.set("brproject-game-enums")
}