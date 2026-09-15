/**
 * First-party mod: mod-email (Phase 3 TRUE).
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
    implementation(project(":mod-crypta"))
}

tasks.jar {
    archiveBaseName.set("brproject-mod-email")
}

tasks.named<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
}
