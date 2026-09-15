/**
 * BrProject Extension SPI — pure contract, no game dependencies (Phase 2).
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

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("brproject-extensions-spi")
    manifest {
        attributes("Implementation-Title" to "BrProject Extensions SPI")
    }
}
