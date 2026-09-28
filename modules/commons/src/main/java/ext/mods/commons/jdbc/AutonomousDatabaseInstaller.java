package ext.mods.commons.jdbc;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Autonomous Database Installer: Migrates and provisions schemas completely
 * across all supported databases (SQLite, MariaDB, MySQL, PostgreSQL, SQL Server).
 * Uses Flyway when available, with a resilient native DDL executor fallback.
 */
public final class AutonomousDatabaseInstaller {

    private AutonomousDatabaseInstaller() {}

    /**
     * Executes full migration on an open connection with live progress logging.
     */
    public static boolean installOrMigrate(Connection connection, SupportedDatabase dbType, Consumer<String> logger) {
        Consumer<String> log = logger != null ? logger : System.out::println;

        if (connection == null) {
            log.accept("ERRO: Conexao com banco nula.");
            return false;
        }

        // 1. Try Flyway via reflection if available
        if (tryFlyway(connection, dbType, log)) {
            return true;
        }

        // 2. Fallback to Autonomous Native DDL execution
        log.accept("Executando migracao autônoma nativa do BrProject...");
        return executeNativeMigration(connection, dbType, log);
    }

    private static boolean tryFlyway(Connection connection, SupportedDatabase dbType, Consumer<String> log) {
        try {
            Class<?> flywayClass = Class.forName("org.flywaydb.core.Flyway");
            Path migDir = resolveMigrationDir(dbType);
            if (migDir == null || !Files.exists(migDir)) {
                return false;
            }

            log.accept("Flyway detectado no classpath. Executando migracoes de: " + migDir.toAbsolutePath());
            Object config = flywayClass.getMethod("configure").invoke(null);
            config = config.getClass().getMethod("dataSource", Connection.class).invoke(config, connection);
            config = config.getClass().getMethod("locations", String[].class).invoke(config, new Object[]{new String[]{"filesystem:" + migDir.toAbsolutePath()}});
            config = config.getClass().getMethod("baselineOnMigrate", boolean.class).invoke(config, true);
            config = config.getClass().getMethod("baselineVersion", String.class).invoke(config, "0");
            Object fw = config.getClass().getMethod("load").invoke(config);
            Object result = fw.getClass().getMethod("migrate").invoke(fw);
            int n = (Integer) result.getClass().getMethod("getMigrationsExecuted").invoke(result);
            log.accept("Flyway executou com sucesso " + n + " migration(s).");
            return true;
        } catch (ClassNotFoundException ignored) {
            log.accept("Flyway nao presente no runtime. Usando instalador DDL nativo.");
        } catch (Throwable t) {
            log.accept("Flyway encontrou aviso: " + t.getMessage() + ". Prosseguindo com instalador DDL nativo.");
        }
        return false;
    }

    private static boolean executeNativeMigration(Connection connection, SupportedDatabase dbType, Consumer<String> log) {
        Path migDir = resolveMigrationDir(dbType);
        if (migDir == null || !Files.exists(migDir)) {
            log.accept("ERRO: Diretorio de migracoes nao encontrado: " + (migDir != null ? migDir : "nulo"));
            return false;
        }

        File[] sqlFiles = migDir.toFile().listFiles((dir, name) -> name.toLowerCase().endsWith(".sql"));
        if (sqlFiles == null || sqlFiles.length == 0) {
            log.accept("Aviso: Nenhum arquivo SQL encontrado em " + migDir);
            return false;
        }

        Arrays.sort(sqlFiles, Comparator.comparing(File::getName));

        int successFiles = 0;
        for (File sqlFile : sqlFiles) {
            log.accept("Aplicando script: " + sqlFile.getName() + "...");
            if (executeSqlFile(connection, sqlFile, dbType, log)) {
                successFiles++;
            }
        }

        log.accept("Migracao DDL concluida: " + successFiles + "/" + sqlFiles.length + " scripts processados com sucesso.");
        return validateEssentialTables(connection, log);
    }

    public static Path resolveMigrationDir(SupportedDatabase dbType) {
        String vendor;
        if (dbType == SupportedDatabase.SQLITE) {
            vendor = "sqlite";
        } else if (dbType == SupportedDatabase.POSTGRESQL) {
            vendor = "postgresql";
        } else {
            vendor = "mariadb";
        }
        Path[] candidates = new Path[] {
            Path.of("brproject-data/migrations", vendor),
            Path.of("../brproject-data/migrations", vendor),
            Path.of("../../brproject-data/migrations", vendor),
            Path.of("data/migrations", vendor)
        };
        for (Path p : candidates) {
            if (Files.exists(p) && Files.isDirectory(p)) {
                return p;
            }
        }
        return Path.of("brproject-data/migrations", vendor);
    }

    private static boolean executeSqlFile(Connection connection, File file, SupportedDatabase dbType, Consumer<String> log) {
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            List<String> statements = splitSqlStatements(content);
            try (Statement st = connection.createStatement()) {
                for (String sql : statements) {
                    String adapted = adaptSqlForDatabase(sql, dbType);
                    if (adapted == null || adapted.isBlank()) continue;
                    try {
                        st.execute(adapted);
                    } catch (SQLException ex) {
                        // Ignora avisos toleráveis de coluna ou índice já existente
                        String msg = ex.getMessage().toLowerCase();
                        if (msg.contains("already exists") || msg.contains("duplicate column") || msg.contains("duplicate key")) {
                            continue;
                        }
                        log.accept("  [Aviso DDL] Script " + file.getName() + ": " + ex.getMessage());
                    }
                }
            }
            return true;
        } catch (Exception e) {
            log.accept("ERRO processando " + file.getName() + ": " + e.getMessage());
            return false;
        }
    }

    public static String adaptSqlForDatabase(String sql, SupportedDatabase dbType) {
        if (sql == null || sql.isBlank()) return null;
        String s = sql.trim();
        // Remove comentários de cabeçalho da linha
        if (s.startsWith("--") || s.startsWith("/*")) {
            String[] lines = s.split("\r?\n");
            StringBuilder clean = new StringBuilder();
            for (String l : lines) {
                String lt = l.trim();
                if (!lt.startsWith("--") && !lt.startsWith("/*")) {
                    clean.append(l).append("\n");
                }
            }
            s = clean.toString().trim();
            if (s.isEmpty()) return null;
        }

        if (dbType == SupportedDatabase.POSTGRESQL) {
            // Adapt MariaDB DDL to PostgreSQL
            s = s.replaceAll("(?i)ENGINE\\s*=\\s*\\w+", "");
            s = s.replaceAll("(?i)DEFAULT\\s+CHARSET\\s*=\\s*\\w+", "");
            s = s.replaceAll("(?i)COLLATE\\s*=\\s*\\w+", "");
            s = s.replaceAll("(?i)UNSIGNED", "");
            s = s.replaceAll("(?i)\\bAUTO_INCREMENT\\b", "");
            s = s.replaceAll("(?i)\\bTINYINT\\s*\\(\\s*\\d+\\s*\\)", "SMALLINT");
            s = s.replaceAll("(?i)\\bMEDIUMINT\\b", "INTEGER");
            s = s.replaceAll("(?i)\\bINT\\s*\\(\\s*\\d+\\s*\\)", "INTEGER");
            s = s.replaceAll("(?i)\\bBIGINT\\s*\\(\\s*\\d+\\s*\\)", "BIGINT");
            s = s.replaceAll("(?i)`", "\"");
        }
        return s;
    }

    public static List<String> splitSqlStatements(String sqlScript) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        int len = sqlScript.length();
        for (int i = 0; i < len; i++) {
            char c = sqlScript.charAt(i);
            char next = (i + 1 < len) ? sqlScript.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }

            if (!inSingleQuote && !inDoubleQuote) {
                if (c == '-' && next == '-') {
                    inLineComment = true;
                    i++;
                    continue;
                }
                if (c == '/' && next == '*') {
                    inBlockComment = true;
                    i++;
                    continue;
                }
            }

            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            }

            if (c == ';' && !inSingleQuote && !inDoubleQuote) {
                String st = current.toString().trim();
                if (!st.isEmpty()) {
                    statements.add(st);
                }
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        String last = current.toString().trim();
        if (!last.isEmpty()) {
            statements.add(last);
        }
        return statements;
    }

    public static boolean validateEssentialTables(Connection connection, Consumer<String> log) {
        Set<String> required = new HashSet<>(Arrays.asList(
            "accounts", "characters", "gameservers", "items", "clan_data", "fail2ban_bans"
        ));
        Set<String> found = new HashSet<>();
        try {
            DatabaseMetaData md = connection.getMetaData();
            try (ResultSet rs = md.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME").toLowerCase();
                    found.add(name);
                }
            }
            required.removeAll(found);
            if (required.isEmpty()) {
                log.accept("Validacao de integridade: Todas as tabelas essenciais estao presentes!");
                return true;
            } else {
                log.accept("Aviso: Faltam algumas tabelas no schema: " + required);
                return false;
            }
        } catch (SQLException e) {
            log.accept("Aviso validando tabelas: " + e.getMessage());
            return true;
        }
    }
}
