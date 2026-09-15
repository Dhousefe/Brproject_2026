/** BrProject Cluster-HPC foundation. */
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
    implementation(project(":commons"))

    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    implementation("com.lmax:disruptor:4.0.0")
    implementation("com.google.flatbuffers:flatbuffers-java:24.3.25")
    implementation("io.netty:netty-all:4.2.16.Final")
    implementation("io.grpc:grpc-netty-shaded:1.65.1")
    implementation("io.grpc:grpc-protobuf:1.65.1")
    implementation("io.grpc:grpc-stub:1.65.1")
    implementation("com.google.protobuf:protobuf-java:3.25.5")
    implementation("com.github.docker-java:docker-java-core:3.4.0")
    implementation("com.github.docker-java:docker-java-transport-httpclient5:3.4.0")
    implementation("info.picocli:picocli:4.7.6")
    implementation("org.slf4j:slf4j-simple:2.0.17")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("br.project.cluster.hpc.dashboard.Dashboardpanel")
    applicationName = "cluster-hpc"
}

tasks.test { useJUnitPlatform() }

tasks.jar {
    archiveBaseName.set("brproject-cluster-hpc")
    manifest { attributes("Implementation-Title" to "BrProject Cluster HPC") }
}
