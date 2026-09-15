/**
 * First-party mod: mod-farm-event (Phase 3 TRUE).
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
    archiveBaseName.set("brproject-mod-farm-event")
}

tasks.named<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
}
