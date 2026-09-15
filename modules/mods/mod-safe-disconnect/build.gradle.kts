/**
 * First-party mod: safe-disconnect (Phase 3 TRUE).
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
    implementation(libs.kotlinx.coroutines.core)
}

tasks.jar {
    archiveBaseName.set("brproject-mod-safe-disconnect")
}

tasks.named<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
}
