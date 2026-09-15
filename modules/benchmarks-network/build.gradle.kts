plugins {
    id("java")
    id("application")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    implementation(libs.jmh.core)
    annotationProcessor(libs.jmh.generator.annprocess)
    
    implementation(libs.disruptor)
    implementation(libs.netty.all)
    implementation(libs.slf4j.api)
    
    implementation(project(":game-network"))
    implementation(project(":commons"))
    implementation(project(":game-server-core"))
    implementation(project(":game-api"))
    implementation(project(":proxy"))
    implementation(project(":mod-quest-recommender"))
    implementation(libs.fastutil.core)
    implementation(libs.gson)
    
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("ext.mods.benchmark.network.FullNetworkDiagnosticRunner")
    applicationDefaultJvmArgs = listOf("-XX:+UseSerialGC", "-Xms64m", "-Xmx256m", "-Dfile.encoding=UTF-8")
}

tasks.named<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
    jvmArgs = listOf("-Xms256m", "-Xmx1024m", "-XX:+UseG1GC")
}

tasks.register<JavaExec>("runGameApiBenchmark") {
    group = "benchmark"
    description = "Executes the JMH Game API performance benchmark."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ext.mods.benchmark.gameapi.GameApiBenchmarkRunner")
    jvmArgs = listOf("-Xms256m", "-Xmx1024m", "-Dfile.encoding=UTF-8")
}

tasks.register<JavaExec>("runProxyBenchmark") {
    group = "benchmark"
    description = "Executes the JMH Proxy & Fail2Ban Performance Benchmark."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ext.mods.benchmark.proxy.ProxyBenchmarkRunner")
    jvmArgs = listOf("-Xms256m", "-Xmx1024m", "-Dfile.encoding=UTF-8")
}

tasks.register<JavaExec>("runHtmlSendBenchmark") {
    group = "benchmark"
    description = "Executes the JMH HTML & ShowBoard Throughput Performance Benchmark."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ext.mods.benchmark.network.HtmlSendBenchmarkRunner")
    jvmArgs = listOf("-Xms256m", "-Xmx1024m", "-Dfile.encoding=UTF-8")
}

tasks.register<JavaExec>("runMonsterAiBenchmark") {
    group = "benchmark"
    description = "Executes the JMH Monster AI & Movement Performance Benchmark."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ext.mods.benchmark.ai.MonsterAiBenchmarkRunner")
    jvmArgs = listOf("-Xms256m", "-Xmx1024m", "-Dfile.encoding=UTF-8")
}

tasks.register<JavaExec>("runMonsterAggressionBenchmark") {
    group = "benchmark"
    description = "Executes the JMH Monster Aggression & forEach Loop Benchmark."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ext.mods.benchmark.ai.MonsterAggressionBenchmarkRunner")
    jvmArgs = listOf("-Xms256m", "-Xmx1024m", "-Dfile.encoding=UTF-8")
}

tasks.register<JavaExec>("runPlayerReactionBenchmark") {
    group = "benchmark"
    description = "Executes the JMH Player Movement Reaction & DEX Balance Benchmark."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ext.mods.benchmark.combat.PlayerReactionBenchmarkRunner")
    jvmArgs = listOf("-Xms256m", "-Xmx1024m", "-Dfile.encoding=UTF-8")
}

tasks.register<JavaExec>("runQuestRecommenderBenchmark") {
    group = "benchmark"
    description = "Executes the JMH Quest Recommender Vector & Mechanical Sympathy Benchmark."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ext.mods.benchmark.quest.QuestRecommenderBenchmarkRunner")
    jvmArgs = listOf("-Xms256m", "-Xmx1024m", "-Dfile.encoding=UTF-8")
}

