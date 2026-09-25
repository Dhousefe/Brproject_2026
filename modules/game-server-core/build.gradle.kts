plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("application")
}

val rootLibs = rootProject.file("libs")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

kotlin {
    jvmToolchain(25)
}

// Phase 3 TRUE: feature packages compile in their own Gradle modules (modules/mods/*).
// game-server-core no longer adds feature source roots.

// Feature handlers live in mod modules (same package for AbstractHandler scan).
// Keep marker stubs out of core compilation so fat-jar can pick mod classes.
sourceSets {
    main {
        java {
            exclude(
                "**/handler/admincommandhandlers/AdminFarmEvent.java",
                "**/handler/voicedcommandhandlers/skins.java",
                "**/handler/voicedcommandhandlers/VoicedTour.java",
                "**/handler/voicedcommandhandlers/VoicedTournamentRank.java",
                "**/handler/voicedcommandhandlers/Email.java",
                "**/handler/voicedcommandhandlers/RouletteVoiced.java",
                "**/handler/voicedcommandhandlers/VoicedBossBattle.java",
                "**/handler/voicedcommandhandlers/FarmZoneTeleport.java",
                "**/handler/bypasshandlers/FarmZoneTeleportBypass.java",
                "**/handler/itemhandlers/CapsuleBox_System.java",
                "**/handler/itemhandlers/ItemMonsterSummon.java",
            )
        }
    }
}

val kotlinJavaRoots = file("src/main/java").absolutePath

dependencies {
    api(project(":commons"))
    // S2.2 L1: loginserver enums/crypt/auth live in :login-server
    api(project(":login-server"))
    api(project(":extensions-spi"))
    // Phase 1: network transport + inter-server protocol packets
    api(project(":game-network"))
    api(project(":game-packets"))
    // Phase 2A: standalone enums (no model/network/skills deps) live in :game-enums
    api(project(":game-enums"))
    // Phase 2B: pure value objects, DTOs, locations
    api(project(":game-model-api"))
    implementation(project(":game-api"))

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.stdlib.jdk8)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.mariadb.java.client)
    implementation(libs.postgresql.jdbc)
    implementation(libs.hikaricp)
    implementation(libs.capnproto.runtime)
    implementation(libs.zstd.jni)
    implementation(libs.fastutil.core)
    implementation(libs.netty.all)
    // Native transports for Netty (epoll Linux, kqueue macOS) — required at runtime for NIO→Netty migration
    runtimeOnly("io.netty:netty-transport-native-epoll:4.2.16.Final:linux-x86_64")
    runtimeOnly("io.netty:netty-transport-native-kqueue:4.2.16.Final:osx-x86_64")
    implementation(libs.disruptor)
    implementation(libs.gson)
    implementation(libs.slf4j.api)
    implementation(libs.micrometer.core)
    implementation(libs.micrometer.prometheus)

    // Vendored JARs (no Maven artifact available)
    implementation(
        fileTree(rootLibs) {
            include(
                "DeepL.jar",
                "ApiPix.jar",
                "interface.ext.jar",
                "Kamaloka.ext.jar",
                "jna-5.13.0.jar",
                "jna-platform-5.13.0.jar",
                "deepl-java-1.6.0.jar",
                "runtime-0.1.16.jar",
            )
        }
    )

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}


tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin") {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
        freeCompilerArgs.addAll(
            "-Xno-call-assertions",
            "-Xno-param-assertions",
            "-Xno-receiver-assertions",
            "-Xjava-source-roots=$kotlinJavaRoots",
        )
    }
}

tasks.named<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
    dependsOn(tasks.named("compileKotlin"))
    val kotlinCompile = tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin")
    classpath = files(kotlinCompile.map { it.destinationDirectory }) + classpath
}

application {
    mainClass.set("ext.mods.gameserver.GameServer")
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8", "-Xms512m", "-Xmx2g")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.file("game")
    group = "application"
    description = "Starts GameServer (cwd=game/)."
}

tasks.jar {
    archiveBaseName.set("brproject-game-server-core")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(tasks.named("classes"))
    from(sourceSets.main.get().output)
    manifest {
        attributes(
            "Implementation-Title" to "BrProject GameServer Core",
            "Main-Class" to "ext.mods.gameserver.GameServer",
        )
    }
}

tasks.named("classes") {
    dependsOn("compileKotlin", "compileJava")
}

val unifiedClasses = layout.buildDirectory.dir("unified-classes")
tasks.register<Copy>("unifyClasses") {
    dependsOn(tasks.named("classes"))
    into(unifiedClasses)
    from(sourceSets.main.get().output)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named("distTar") { enabled = false }
tasks.named("distZip") { enabled = false }
tasks.withType<Tar>().configureEach { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
tasks.withType<Zip>().configureEach { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }

tasks.test {
    useJUnitPlatform()
}
