/**
 * Root aggregator — Phase 1 + Phase 2.
 */
plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.0-Beta2" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0-Beta2" apply false
    id("com.github.spotbugs") version "6.5.9" apply false
}

allprojects {
    group = "br.project"
    version = "3.1.0"
}

subprojects {
    repositories {
        mavenCentral()
        google()
        mavenLocal()
        maven { url = uri("https://artifacts.deepl.com/maven/") }
    }

    // Apply SpotBugs to any subproject that uses the Java plugin.
    // Kotlin sources are out of scope for SpotBugs (use detekt for those).
    plugins.withId("java") {
        apply(plugin = "com.github.spotbugs")

        extensions.configure<com.github.spotbugs.snom.SpotBugsExtension>("spotbugs") {
            ignoreFailures.set(true)
            showStackTraces.set(false)
            effort.set(com.github.spotbugs.snom.Effort.MAX)
            reportLevel.set(com.github.spotbugs.snom.Confidence.LOW)

            val excludeFile = rootProject.file("config/spotbugs/exclude.xml")
            if (excludeFile.exists()) {
                excludeFilter.set(excludeFile)
            }
        }

        // Wire up HTML + XML + SARIF reports so CI can publish them as artifacts.
        tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
            reports.create("html") {
                required.set(true)
                outputLocation.set(layout.buildDirectory.file("reports/spotbugs/${name}/spotbugs.html"))
            }
            reports.create("xml") {
                required.set(true)
                outputLocation.set(layout.buildDirectory.file("reports/spotbugs/${name}/spotbugs.xml"))
            }
            reports.create("sarif") {
                required.set(true)
                outputLocation.set(layout.buildDirectory.file("reports/spotbugs/${name}/spotbugs.sarif"))
            }
        }
    }
}

tasks.register<Delete>("clean") {
    group = "build"
    description = "Deletes root/subproject build outputs and libs/server.jar"
    delete(layout.buildDirectory)
    delete(subprojects.map { it.layout.buildDirectory })
    delete(layout.projectDirectory.file("libs/server.jar"))
}

tasks.register("compileAll") {
    group = "build"
    description = "Compiles core + SPI + optional mods"
    dependsOn(":extensions-spi:classes", ":game-server-core:classes", ":proxy:classes")
    if (findProject(":mod-boss-zerg") != null) {
        dependsOn(":mod-boss-zerg:classes")
    }
}


tasks.register("build") {
    group = "build"
    description = "Tests SPI + packages server.jar"
    dependsOn(":extensions-spi:test", ":game-server-core:test", ":mod-pix:test", ":proxy:test", ":app-dist:jar", ":db-migrate:compileJava")
}

tasks.register("compileJava") {
    group = "build"
    dependsOn(":game-server-core:compileJava")
}

tasks.register("compileKotlin") {
    group = "build"
    dependsOn(":game-server-core:compileKotlin")
}

tasks.register("brCompileIncremental") {
    group = "build"
    description = "Build incremental: compila o que mudou + gera libs/server.jar"
    dependsOn(
        ":commons:jar",
        ":extensions-spi:jar",
        ":login-server:jar",
        ":db-migrate:jar",
        ":game-server-core:jar",
        ":proxy:jar",
        ":app-dist:jar",
    )
}

tasks.register("brCompileCleanAfterBuild") {
    group = "build"
    description = "Build wrapper (chamado por brCompileClean via gradlew clean brCompileClean)"
    dependsOn(":app-dist:jar")
}

tasks.register("brCompileClean") {
    group = "build"
    description = "Clean + build completo: use `gradlew clean brCompileClean` (gradlew.bat ja chama nesta ordem)"
    dependsOn("brCompileCleanAfterBuild")
}

tasks.register("PrepararTeste") {
    group = "build"
    description = "Prepara ambiente (configs + GUI 1ª vez + hexid) e opcionalmente inicia o servidor"
    dependsOn(":app-dist:jar")

    doLast {
        val libsDir = rootProject.layout.projectDirectory.dir("libs").asFile
        val serverJar = libsDir.resolve("server.jar")
        if (!serverJar.exists()) {
            throw GradleException("libs/server.jar não foi gerado por :app-dist:jar")
        }

        val javaExe = project(":game-server-core").extensions
            .getByType<JavaPluginExtension>().toolchain
            .let { tc ->
                val svc = project.extensions.findByType(org.gradle.jvm.toolchain.JavaToolchainService::class.java)
                    ?: project(":game-server-core").extensions.getByType(org.gradle.jvm.toolchain.JavaToolchainService::class.java)
                svc.launcherFor(tc).get().executablePath.asFile.absolutePath
            }

        val entry = "ext.mods.prepararteste.PrepararTesteEntry"
        val args = mutableListOf("-cp", "libs/server.jar", entry)
        if (project.hasProperty("start")) args.add("--start")
        if (project.hasProperty("noGui")) args.add("--no-gui")

        val cmd = listOf(javaExe) + args
        logger.lifecycle("[PrepararTeste] exec -> ${cmd.joinToString(" ")}")

        val proc = ProcessBuilder(cmd)
            .directory(rootProject.layout.projectDirectory.asFile)
            .inheritIO()
            .start()
        val code = proc.waitFor()
        if (code != 0) {
            throw GradleException("PrepararTesteEntry terminou com exit=$code")
        }
    }
}

gradle.projectsEvaluated {
    project(":app-dist").tasks.named("jar") {
        mustRunAfter(rootProject.tasks.named("clean"))
    }
}

tasks.register("buildWithoutMods") {
    group = "build"
    description = "Limpa tudo + rebuilda libs/server.jar (alias usado por gradlew.bat br-ant-dist-test)"
    dependsOn("clean")
    dependsOn(":app-dist:jar")
}
