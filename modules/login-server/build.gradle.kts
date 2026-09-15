/**
 * LoginServer module — S2.2 Wave L1: pure islands live here.
 *
 * Owns: ext.mods.loginserver.{enums,crypt,auth}
 * Remaining loginserver code still compiles in :game-server-core until L2/L3.
 *
 * Dependency direction (no compile cycle):
 *   login-server → commons
 *   game-server-core → login-server (api) + commons
 *   :run uses runtimeOnly game-server-core for LoginServer main
 */
plugins {
    id("java-library")
    id("application")
    id("org.jetbrains.kotlin.jvm")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    api(project(":commons"))

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)

    // LoginServer.main + remaining packages still in core (L2/L3).
    // runtimeOnly avoids compile-time cycle with core → login-server.
    runtimeOnly(project(":game-server-core"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("ext.mods.loginserver.LoginServer")
    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Xms256m",
        "-Xmx512m",
    )
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.file("login")
    group = "application"
    description = "Starts LoginServer (cwd=login/). Requires MariaDB + configs."
    // Ensure core (LoginServer + remaining loginserver) is on the runtime classpath.
    classpath = sourceSets.main.get().runtimeClasspath +
        project(":game-server-core").configurations.getByName("runtimeClasspath")
    standardOutput = System.out
    errorOutput = System.err
}

tasks.jar {
    archiveBaseName.set("brproject-login-server")
    manifest {
        attributes(
            "Main-Class" to "ext.mods.loginserver.LoginServer",
            "Implementation-Title" to "BrProject LoginServer",
        )
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.named("distTar") { enabled = false }
tasks.named("distZip") { enabled = false }
tasks.withType<Tar>().configureEach { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
tasks.withType<Zip>().configureEach { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
