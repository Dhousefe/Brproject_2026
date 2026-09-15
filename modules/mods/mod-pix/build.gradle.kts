/**
 * First-party mod: pix-donation (Phase 3 TRUE + Phase 6 tests).
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
    implementation(files(rootLibs.resolve("ApiPix.jar")))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    archiveBaseName.set("brproject-mod-pix")
}

tasks.named<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}
