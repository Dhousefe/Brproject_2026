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
    implementation(libs.netty.all)
    runtimeOnly("io.netty:netty-transport-native-epoll:4.2.16.Final:linux-x86_64")
    runtimeOnly("io.netty:netty-transport-native-epoll:4.2.16.Final:linux-aarch_64")
    implementation(libs.slf4j.api)
    // Logback for runtime logging (rotation, format, async appender)
    implementation("ch.qos.logback:logback-classic:1.5.18")
    // Bouncy Castle for Java 17/21/25 SelfSignedCertificate generator
    implementation("org.bouncycastle:bcpkix-jdk18on:1.80")
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("br.project.proxy.ProxyMain")
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8", "-Xms128m", "-Xmx512m", "--enable-native-access=ALL-UNNAMED")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    group = "application"
    description = "Starts BrProject Netty reverse proxy (cwd=project root)."
}

tasks.named<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

tasks.named("distTar") { enabled = false }
tasks.named("distZip") { enabled = false }
tasks.withType<Tar>().configureEach { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
tasks.withType<Zip>().configureEach { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
