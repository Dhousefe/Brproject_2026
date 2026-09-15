/**
 * First-party mod: crypta-data (Phase 3 TRUE) — Deepl + PlayerEmailManager.
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
    implementation(files(rootLibs.resolve("deepl-java-1.6.0.jar")))
    implementation(files(rootLibs.resolve("DeepL.jar")))
    // jsoup may be inside DeepL stack; pull from Maven if not local
    implementation("org.jsoup:jsoup:1.17.2")
}

tasks.jar {
    archiveBaseName.set("brproject-mod-crypta")
}

tasks.named<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
}
