/**
 * Phase 5 / P3 — Flyway DB migrations via MigrateMain CLI.
 *
 * The official Flyway Gradle plugin still references JavaPluginConvention
 * (removed in Gradle 9). Prefer `:db-migrate:run` / `./tools/migrate-db.sh`.
 */
plugins {
    id("java")
    id("application")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

val dbUrl = providers.gradleProperty("dbUrl")
    .orElse("jdbc:mariadb://localhost:3306/l2jdb?useUnicode=true&characterEncoding=UTF-8")
val dbUser = providers.gradleProperty("dbUser").orElse("brproject")
val dbPassword = providers.gradleProperty("dbPassword").orElse("brproject")

dependencies {
    implementation("org.flywaydb:flyway-core:11.3.4")
    implementation("org.flywaydb:flyway-mysql:11.3.4")
    implementation("org.flywaydb:flyway-database-postgresql:11.3.4")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.3")
    runtimeOnly("org.xerial:sqlite-jdbc:3.46.1.0")
    runtimeOnly("org.postgresql:postgresql:42.7.4")
    runtimeOnly("com.microsoft.sqlserver:mssql-jdbc:12.8.1.jre11")
}

application {
    mainClass.set("br.project.db.MigrateMain")
}

tasks.named<JavaExec>("run") {
    args(
        "--url=${dbUrl.get()}",
        "--user=${dbUser.get()}",
        "--password=${dbPassword.get()}",
    )
    workingDir = rootProject.projectDir
}

/** Preferred migrate entry (Gradle 9-safe). */
tasks.register("migrate") {
    group = "database"
    description = "Apply Flyway migrations via MigrateMain (Gradle 9-safe)"
    dependsOn("run")
}

/** Back-compat alias for scripts that still call :db-migrate:flywayMigrate */
tasks.register("flywayMigrate") {
    group = "database"
    description = "Alias for migrate (Flyway plugin removed; uses MigrateMain)"
    dependsOn("migrate")
}
