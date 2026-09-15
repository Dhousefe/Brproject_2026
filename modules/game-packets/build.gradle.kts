/**
 * game-packets — Inter-server protocol packets and shared network constants.
 *
 * Phase 1 partial extraction: contains login↔game server protocol packets,
 * NpcStringId (12K LOC string registry), and SessionKey.
 *
 * The full client/server packet classes (serverpackets/, clientpackets/) remain
 * in game-server-core due to heavy coupling to model/data/skills (Phase 2 blocker).
 *
 * Dependency policy:
 *   - Depends on :commons (CLogger, AttributeType)
 *   - Does NOT depend on :game-server-core
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
    api(project(":commons"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    archiveBaseName.set("brproject-game-packets")
    manifest {
        attributes("Implementation-Title" to "BrProject Game Packets (Inter-Server Protocol)")
    }
}

tasks.test {
    useJUnitPlatform()
}
