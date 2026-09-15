/**
 * First-party mod: mod-global-drop (Phase 3 TRUE).
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
    implementation(project(":mod-farm-event"))
}

tasks.jar {
    archiveBaseName.set("brproject-mod-global-drop")
}

tasks.named<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
}
