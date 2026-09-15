/**
 * First-party mod: mod-quest-recommender (Phase 3 TRUE).
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
    implementation(project(":mod-tour"))
    implementation(project(":mod-farm-event"))
    implementation(libs.fastutil.core)
}

tasks.jar {
    archiveBaseName.set("brproject-mod-quest-recommender")
}

tasks.named<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
}
