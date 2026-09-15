/**
 * game-model-api — Pure value objects, DTOs, locations, and holders.
 * Zero dependency on game-server-core internals.
 * Depends only on :commons (Rnd, IntXYZ, ArraysUtil) + Java stdlib.
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
    api(project(":commons"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    archiveBaseName.set("brproject-game-model-api")
    manifest {
        attributes("Implementation-Title" to "BrProject Game Model API")
    }
}

tasks.test {
    useJUnitPlatform()
}
