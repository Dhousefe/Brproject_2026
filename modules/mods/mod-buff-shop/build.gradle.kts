/**
 * First-party mod: buff-shop (Phase 3 TRUE).
 */
plugins {
    id("java-library")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

val rootLibs = rootProject.file("libs")

dependencies {
    api(project(":extensions-spi"))
    implementation(project(":game-server-core"))
    implementation(project(":commons"))
    implementation(files(rootLibs.resolve("fastutil-8.5.13.jar")))
    implementation(files(rootLibs.resolve("fastutil-core-8.5.18.jar")))
}

tasks.jar {
    archiveBaseName.set("brproject-mod-buff-shop")
}

tasks.named<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
}
