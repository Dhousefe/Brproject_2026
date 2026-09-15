/**
 * First-party mod: dressme (Phase 3 TRUE).
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
    archiveBaseName.set("brproject-mod-dressme")
    manifest {
        attributes(
            "Implementation-Title" to "BrProject dressme Mod",
            "Extension-Id" to "dressme",
        )
    }
}

tasks.named<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
}
