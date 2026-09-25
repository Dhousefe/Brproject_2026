/**
 * Distribution module — libs/server.jar (Phase 3 TRUE: core + optional mods).
 */
plugins {
    id("java")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

val modNames = listOf(
    "mod-boss-zerg",
    "mod-agathion",
    "mod-battle-boss",
    "mod-buff-shop",
    "mod-capsule-box",
    "mod-crypta",
    "mod-dressme",
    "mod-dungeon",
    "mod-email",
    "mod-farm-event",
    "mod-global-drop",
    "mod-levelup-maker",
    "mod-pix",
    "mod-player-god",
    "mod-roulette",
    "mod-safe-disconnect",
    "mod-summon-mob",
    "mod-tour",
    "mod-quest-recommender",
)

val modProjects = modNames.mapNotNull { findProject(":$it") }

dependencies {
    implementation(project(":commons"))
    implementation(project(":extensions-spi"))
    implementation(project(":game-server-core"))
    implementation(project(":game-api"))
    implementation(project(":game-network"))
    implementation(project(":game-packets"))
    implementation(project(":game-enums"))
    implementation(project(":game-model-api"))
    implementation(project(":db-migrate"))
    for (mod in modProjects) {
        implementation(mod)
    }
}

// att-ver-3.0: generate SPI from core + actually included mod projects (not a static 21-line list)
val generateSpiFile = tasks.register("generateExtensionSpi") {
    group = "build"
    description = "Merge META-INF/services/br.project.spi.Extension from core + included mods"
    val outFile = layout.buildDirectory.file("generated/spi/META-INF/services/br.project.spi.Extension")
    val coreSpi = project(":game-server-core").file("src/main/resources/META-INF/services/br.project.spi.Extension")
    inputs.file(coreSpi)
    inputs.property("includedMods", modProjects.map { it.path }.sorted().joinToString(","))
    for (mod in modProjects) {
        val modSpi = mod.file("src/main/resources/META-INF/services/br.project.spi.Extension")
        if (modSpi.exists()) {
            inputs.file(modSpi)
        }
    }
    outputs.file(outFile)
    doLast {
        val lines = linkedSetOf<String>()
        if (coreSpi.exists()) {
            coreSpi.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { lines.add(it) }
        }
        for (mod in modProjects) {
            val modSpi = mod.file("src/main/resources/META-INF/services/br.project.spi.Extension")
            if (modSpi.exists()) {
                modSpi.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { lines.add(it) }
            }
        }
        val target = outFile.get().asFile
        target.parentFile.mkdirs()
        target.writeText(lines.joinToString("\n") + if (lines.isEmpty()) "" else "\n")
        logger.lifecycle("generateExtensionSpi: ${lines.size} providers (${modProjects.size} mods included)")
    }
}

tasks.named<ProcessResources>("processResources") {
    // SPI must come only from generateExtensionSpi, not stale resources
    exclude("META-INF/services/br.project.spi.Extension")
}

tasks.jar {
    dependsOn(
        ":commons:jar",
        ":login-server:jar",
        ":extensions-spi:jar",
        ":game-network:jar",
        ":game-packets:jar",
        ":game-enums:jar",
        ":game-model-api:jar",
        ":db-migrate:jar",
        ":game-server-core:classes",
        ":game-server-core:unifyClasses",
        ":game-server-core:jar",
        ":game-api:jar",
        generateSpiFile,
    )
    for (mod in modProjects) {
        dependsOn(mod.tasks.named("jar"))
    }

    // First source wins for normal classes; SPI list is forced last (INCLUDE).
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveBaseName.set("server")
    archiveVersion.set("")
    archiveFileName.set("server.jar")
    destinationDirectory.set(rootProject.layout.projectDirectory.dir("libs"))

    val spiPath = "META-INF/services/br.project.spi.Extension"
    val commonMetaExcludes = listOf(
        "META-INF/*.SF",
        "META-INF/*.DSA",
        "META-INF/*.RSA",
        "META-INF/LICENSE*",
        "META-INF/NOTICE*",
        "META-INF/versions/**",
        spiPath, // each module has its own ServiceLoader line; we merge via generateExtensionSpi
    )

    from(project(":game-server-core").layout.buildDirectory.dir("unified-classes")) {
        exclude(commonMetaExcludes)
    }
    from(zipTree(project(":commons").tasks.named<Jar>("jar").flatMap { it.archiveFile })) {
        exclude(commonMetaExcludes)
    }
    // S2.2 L1: enums/crypt/auth live in login-server
    from(zipTree(project(":login-server").tasks.named<Jar>("jar").flatMap { it.archiveFile })) {
        exclude(commonMetaExcludes)
    }
    from(zipTree(project(":game-api").tasks.named<Jar>("jar").flatMap { it.archiveFile })) {
        exclude(commonMetaExcludes)
    }
    from(zipTree(project(":game-network").tasks.named<Jar>("jar").flatMap { it.archiveFile })) {
        exclude(commonMetaExcludes)
    }
    from(zipTree(project(":game-packets").tasks.named<Jar>("jar").flatMap { it.archiveFile })) {
        exclude(commonMetaExcludes)
    }
    from(zipTree(project(":game-enums").tasks.named<Jar>("jar").flatMap { it.archiveFile })) {
        exclude(commonMetaExcludes)
    }
    from(zipTree(project(":game-model-api").tasks.named<Jar>("jar").flatMap { it.archiveFile })) {
        exclude(commonMetaExcludes)
    }
    from(zipTree(project(":extensions-spi").tasks.named<Jar>("jar").flatMap { it.archiveFile })) {
        exclude(commonMetaExcludes)
    }
    // Flyway migration CLI used by PrepararTeste fallback SQLite/MariaDB schema setup
    from(zipTree(project(":db-migrate").tasks.named<Jar>("jar").flatMap { it.archiveFile })) {
        exclude(commonMetaExcludes)
    }
    for (mod in modProjects) {
        from(zipTree(mod.tasks.named<Jar>("jar").flatMap { it.archiveFile })) {
            exclude(commonMetaExcludes)
        }
    }

    from({
        project(":game-server-core").configurations.getByName("runtimeClasspath")
            .filter { it.name.endsWith(".jar") }
            .filter { jar ->
                val n = jar.name
                !n.startsWith("server") &&
                    !n.contains("c3p0") &&
                    !n.contains("mchange") &&
                    !n.contains("brproject-game-server-core") &&
                    !n.contains("brproject-commons") &&
                    !n.contains("brproject-login-server") &&
                    !n.contains("brproject-extensions-spi") &&
                    !n.contains("brproject-game-network") &&
                    !n.contains("brproject-game-packets") &&
                    !n.contains("brproject-game-enums") &&
                    !n.contains("brproject-game-model-api") &&
                    !n.contains("brproject-db-migrate") &&
                    !n.contains("brproject-mod-")
            }
            .map { if (it.isDirectory) it else zipTree(it) }
    }) {
        exclude(commonMetaExcludes)
    }

    // db-migrate has its own runtime dependencies (Flyway and database
    // adapters). Include them in the fat distribution so the Docker migrate
    // image can execute MigrateMain without depending on Gradle caches.
    from({
        project(":db-migrate").configurations.getByName("runtimeClasspath")
            .filter { it.name.endsWith(".jar") }
            .filter { jar -> !jar.name.startsWith("brproject-db-migrate") }
            .map { if (it.isDirectory) it else zipTree(it) }
    }) {
        exclude(commonMetaExcludes)
    }

    // Generated SPI (core + included mods only) — INCLUDE wins
    from(layout.buildDirectory.dir("generated/spi")) {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    manifest {
        attributes(
            mapOf(
                "Main-Class" to "ext.mods.gameserver.GameServer",
                "Build-Date" to System.currentTimeMillis().toString(),
                "Implementation-Title" to "BrProject",
                "Implementation-Version" to project.version,
                "BrProject-SPI" to "3",
            )
        )
    }

    group = "distribution"
    description = "Assembles libs/server.jar fat distribution (core + SPI mods)"
}

tasks.register<Copy>("syncModuleJars") {
    group = "distribution"
    description = "Copies module thin jars into libs/modules/"
    dependsOn(":commons:jar", ":extensions-spi:jar", ":game-enums:jar", ":game-server-core:jar", ":login-server:jar")
    for (mod in modProjects) {
        dependsOn(mod.tasks.named("jar"))
    }
    into(rootProject.layout.projectDirectory.dir("libs/modules"))
    from(project(":commons").tasks.named<Jar>("jar").flatMap { it.archiveFile })
    from(project(":extensions-spi").tasks.named<Jar>("jar").flatMap { it.archiveFile })
    from(project(":game-enums").tasks.named<Jar>("jar").flatMap { it.archiveFile })
    from(project(":game-server-core").tasks.named<Jar>("jar").flatMap { it.archiveFile })
    from(project(":login-server").tasks.named<Jar>("jar").flatMap { it.archiveFile })
    for (mod in modProjects) {
        from(mod.tasks.named<Jar>("jar").flatMap { it.archiveFile })
    }
}

tasks.named("build") {
    dependsOn(tasks.jar)
}
