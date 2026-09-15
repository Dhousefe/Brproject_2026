/**
 * game-network — NIO/MMO network transport layer.
 *
 * Contains the mmocore selector/connection primitives (SelectorThread, MMOConnection, etc.)
 * extracted from the commons module so they can evolve independently of utilities.
 *
 * Dependency policy:
 *   - ZERO first-party module deps (only Java stdlib)
 *   - commons re-exports these classes via api(project(":game-network")) for backward compat
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
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    archiveBaseName.set("brproject-game-network")
    manifest {
        attributes("Implementation-Title" to "BrProject Game Network Transport")
    }
}

tasks.test {
    useJUnitPlatform()
}
