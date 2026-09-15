/**
 * Commons foundation — Wave C1 pure utils live under ext.mods.commons.*
 * (same packages as before; source root moved from game-server-core).
 */
plugins {
    id("java-library")
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
    // Phase 1: mmocore transport layer extracted to game-network; re-export for backward compat
    api(project(":game-network"))

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    // Wave C3: ConnectionPool lives here
    api(libs.hikaricp)

    // JDBC drivers — multi-database support
    runtimeOnly(libs.sqlite.jdbc)
    runtimeOnly(libs.postgresql.jdbc)
    runtimeOnly(libs.mssql.jdbc)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    archiveBaseName.set("brproject-commons")
    manifest {
        attributes("Implementation-Title" to "BrProject Commons")
    }
}

tasks.test {
    useJUnitPlatform()
}
