/*
 * Copyleft © 2024-2026 L2Brproject
 * PrepararTesteEntry — orquestrador único de preparação do ambiente.
 *
 * Fluxo:
 *   1) Detecta primeira execução (arquivo marker ausente OU faltando hexid/configs).
 *   2) Tenta MariaDB/MySQL em 127.0.0.1:3306 (root/root); se indisponível,
 *      usa SQLite embarcado (data/brproject.db).
 *   3) Aplica migrations Flyway quando o schema ainda não existe.
 *   4) Sanitiza game/config/server.properties + login/config/loginserver.properties
 *      com IP/banco/credenciais conforme o banco detectado.
 *   5) Se primeira execução E MariaDB disponível E display presente → GUI Swing
 *      pedindo host/db/user/pass (reaproveita DatabaseManager.configurarBanco,
 *      o mesmo do botão "Banco de Dados" do painel).
 *   6) Caso contrário → fluxo silencioso (usa defaults).
 *   7) Gera e/ou sincroniza hexid:
 *      - Se existe hexid no banco (server_id=1) → reutiliza.
 *      - Senão → gera 16 bytes hex aleatórios.
 *      - Salva ./game/config/hexid.txt e ./login/config/hexid.txt.
 *      - UPSERT em gameservers (server_id, hexid, host).
 *   8) Marca flag/PrepararTeste.done para execuções futuras serem silenciosas.
 *   9) Se --start for passado, inicia LoginServer + GameServer ao final.
 *
 * Uso:
 *   java -cp libs/server.jar ext.mods.prepararteste.PrepararTesteEntry [--start] [--no-gui]
 *
 *   --start   liga LoginServer + GameServer ao final
 *   --no-gui  mesmo em primeira execução, não abre diálogo (usa defaults; útil em CI)
 */
package ext.mods.prepararteste;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PrepararTesteEntry
{
    // ===== paths (relativos ao cwd = raiz do projeto) =====
    static final Path FLAG_DIR = Paths.get("flag");
    static final Path FLAG_DONE = FLAG_DIR.resolve("PrepararTeste.done");
    static final Path GAME_CONFIG = Paths.get("game", "config");
    static final Path LOGIN_CONFIG = Paths.get("login", "config");
    static final Path GAME_SERVER_PROPS = GAME_CONFIG.resolve("server.properties");
    static final Path LOGIN_SERVER_PROPS = LOGIN_CONFIG.resolve("loginserver.properties");
    static final Path GEOENGINE_PROPS = GAME_CONFIG.resolve("geoengine.properties");
    static final Path GAME_HEXID = GAME_CONFIG.resolve("hexid.txt");
    static final Path LOGIN_HEXID = LOGIN_CONFIG.resolve("hexid.txt");

    // ===== defaults =====
    static final String DEFAULT_HOST = "127.0.0.1";
    static final String DEFAULT_DB = "l2jdb";
    static final String DEFAULT_USER = "root";
    static final String DEFAULT_PASS = "root";
    static final String SQLITE_URL = "jdbc:sqlite:data/brproject.db";
    static final String SQLITE_PATH = "data/brproject.db";
    static final int SERVER_ID = 1;

    // ===== flags =====
    static boolean startAfterPrepare;
    static boolean skipGui;

    public static void main(String[] args) throws Exception
    {
        // Parse de argumentos
        for (String a : args) {
            if ("--start".equals(a)) startAfterPrepare = true;
            if ("--no-gui".equals(a)) skipGui = true;
        }

        // Forcar UTF-8 no console Windows (cp1252 -> UTF-8) para evitar "?" em logs
        try { System.setOut(new java.io.PrintStream(System.out, true, "UTF-8")); } catch (Throwable ignored) {}
        try { System.setErr(new java.io.PrintStream(System.err, true, "UTF-8")); } catch (Throwable ignored) {}

        log("=== BrProject - PrepararTeste ===");
        log("Java: " + System.getProperty("java.version"));
        log("cwd: " + new File(".").getAbsolutePath());

        boolean firstRun = isFirstRun();
        log("Primeira execução: " + (firstRun ? "SIM" : "NÃO"));

        // Detectar banco disponível
        DbChoice choice = detectDatabase();
        log("Banco detectado: " + choice.kind + "  (url=" + choice.url + ")");

        // Sanitizar .properties (ativa .example, fixa sql.* para o host)
        sanitizeAllConfigs(choice);
        log("Arquivos .properties sanitizados.");

        // Aplicar migrations se schema vazio
        ensureSchema(choice);

        // Gerar/registrar hexid (ou abrir GUI na 1ª vez, se aplicavel)
                boolean viaGui = false;
                boolean exampleExists = Files.exists(Paths.get("game", "config", "server.properties.example"));
                if (firstRun && !skipGui && hasDisplay() && exampleExists) {
                    log("Primeira execução + display + server.properties.example encontrado -> abrindo PrepararAmbientePanel standalone.");
                    viaGui = openPrepararAmbientePanelStandalone();
                } else if (firstRun) {
                    if (!exampleExists) log("server.properties.example ausente -> fluxo silencioso (sem GUI).");
                    if (skipGui)         log("--no-gui informado -> fluxo silencioso.");
                    if (!hasDisplay())   log("Sem display -> fluxo silencioso.");
                }

        // Após GUI (ou se não usou), registrar hexid
        try (Connection c = openConnection(choice)) {
            ensureHexIdRegistered(c, choice);
        }

        // Marcar flag
        try {
            Files.createDirectories(FLAG_DIR);
            Files.write(FLAG_DONE,
                ("PrepararTeste done at " + System.currentTimeMillis() + "\n"
                 + "host=" + choice.host + "\n"
                 + "user=" + (choice.kind == DbKind.SQLITE ? "(sqlite)" : DEFAULT_USER) + "\n"
                 + "dbKind=" + choice.kind + "\n").getBytes(StandardCharsets.UTF_8));
            log("Marker gravado em " + FLAG_DONE);
        } catch (IOException e) {
            warn("Não consegui gravar marker: " + e.getMessage());
        }

        log("PrepararTeste concluído com sucesso.");
        log("Próximo passo:");
        log("   StartLogin_SemDashboard.bat   (LoginServer)");
        log("   StartGame_SemDashboard.bat    (GameServer)");
        if (startAfterPrepare) {
            log("--start solicitado, inicializando Login + Game em background...");
            startLoginAndGameBackground();
        }
    }

    // ====================================================================
    // Primeira-execução
    // ====================================================================
    static boolean isFirstRun()
    {
        if (Files.exists(FLAG_DONE)) return false;
        if (Files.exists(GAME_HEXID) && Files.exists(GAME_SERVER_PROPS)
                && Files.exists(LOGIN_SERVER_PROPS)) return false;
        return true;
    }

    // ====================================================================
    // Detecção de banco: tenta MariaDB -> cai pra SQLite
    // ====================================================================
    enum DbKind { MARIADB, MYSQL, POSTGRESQL, SQLITE }

    static final class DbChoice {
        final DbKind kind;
        final String url;
        final String user;
        final String password;
        final String host;
        final String db;
        final int port;
        DbChoice(DbKind k, String u, String usr, String pwd, String host, String db, int port) {
            this.kind = k; this.url = u; this.user = usr; this.password = pwd;
            this.host = host; this.db = db; this.port = port;
        }
        @Override public String toString() { return kind + " " + url; }
    }

    static DbChoice detectDatabase()
    {
        // Carrega explicitamente os drivers JDBC para evitar dependência da ordem
        // do SPI (META-INF/services/java.sql.Driver dentro do fat-jar).
        try { Class.forName("org.mariadb.jdbc.Driver"); } catch (Throwable ignored) {}
        try { Class.forName("com.mysql.cj.jdbc.Driver"); } catch (Throwable ignored) {}
        try { Class.forName("org.postgresql.Driver"); } catch (Throwable ignored) {}
        try { Class.forName("org.sqlite.JDBC"); } catch (Throwable ignored) {}

        // Tenta MariaDB primeiro (mais comum em self-hosted L2J)
        for (String host : new String[]{"127.0.0.1", "localhost"}) {
            String url = "jdbc:mariadb://" + host + ":3306/" + DEFAULT_DB + "?useUnicode=true&characterEncoding=UTF-8";
            try (Connection c = DriverManager.getConnection(url, DEFAULT_USER, DEFAULT_PASS)) {
                if (c.isValid(2)) {
                    log("Conectado em MariaDB@ " + host + " como " + DEFAULT_USER);
                    return new DbChoice(DbKind.MARIADB, url, DEFAULT_USER, DEFAULT_PASS, host, DEFAULT_DB, 3306);
                }
            } catch (SQLException ignored) {
            }
        }
        // MySQL puro
        for (String host : new String[]{"127.0.0.1", "localhost"}) {
            String url = "jdbc:mysql://" + host + ":3306/" + DEFAULT_DB
                    + "?useUnicode=true&characterEncoding=UTF-8"
                    + "&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC";
            try (Connection c = DriverManager.getConnection(url, DEFAULT_USER, DEFAULT_PASS)) {
                if (c.isValid(2)) {
                    log("Conectado em MySQL@ " + host + " como " + DEFAULT_USER);
                    return new DbChoice(DbKind.MYSQL, url, DEFAULT_USER, DEFAULT_PASS, host, DEFAULT_DB, 3306);
                }
            } catch (SQLException ignored) {
            }
        }
        // PostgreSQL (5432)
        for (String host : new String[]{"127.0.0.1", "localhost"}) {
            String url = "jdbc:postgresql://" + host + ":5432/" + DEFAULT_DB;
            try (Connection c = DriverManager.getConnection(url, DEFAULT_USER, DEFAULT_PASS)) {
                if (c.isValid(2)) {
                    log("Conectado em PostgreSQL@ " + host + " como " + DEFAULT_USER);
                    return new DbChoice(DbKind.POSTGRESQL, url, DEFAULT_USER, DEFAULT_PASS, host, DEFAULT_DB, 5432);
                }
            } catch (SQLException ignored) {
            }
        }
        // Falhou -> SQLite embarcado
        log("Nenhum servidor SQL local disponivel - usando SQLite embarcado em " + SQLITE_PATH);
        return new DbChoice(DbKind.SQLITE, SQLITE_URL, "", "", DEFAULT_HOST, "brproject", 0);
    }

    static Connection openConnection(DbChoice ch) throws SQLException
    {
        try {
            switch (ch.kind) {
                case SQLITE:     Class.forName("org.sqlite.JDBC"); break;
                case MARIADB:    Class.forName("org.mariadb.jdbc.Driver"); break;
                case MYSQL:      Class.forName("com.mysql.cj.jdbc.Driver"); break;
                case POSTGRESQL: Class.forName("org.postgresql.Driver"); break;
            }
        } catch (ClassNotFoundException e) {
            throw new SQLException("driver ausente: " + e.getMessage());
        }
        Connection c = DriverManager.getConnection(ch.url, ch.user, ch.password);
        c.setAutoCommit(true);
        return c;
    }

    // ====================================================================
    // Schema
    // ====================================================================
    static void ensureSchema(DbChoice choice)
    {
        try (Connection c = openConnection(choice)) {
            if (tableExists(c, "gameservers")) {
                log("Schema já existe — pulando migrations.");
                return;
            }
            log("Schema vazio detectado, aplicando migrations Flyway...");
            applyFlywayMigrations(choice);
        } catch (SQLException e) {
            warn("Falha ao verificar/aplicar schema: " + e);
        }
    }

    static void applyFlywayMigrations(DbChoice choice)
    {
        // Reaproveita MigrateMain via reflection (mesmo JAR carrega db-migrate via app-dist).
        try {
            Class<?> cls = Class.forName("br.project.db.MigrateMain");
            String url = choice.url;
            String user = choice.user.isEmpty() ? "sa" : choice.user;
            String password = choice.password.isEmpty() ? "" : choice.password;
            String[] args = {"--url=" + url, "--user=" + user, "--password=" + password};
            log("MigrateMain --url=" + url);
            cls.getMethod("main", String[].class).invoke(null, (Object) args);
            log("Migrations aplicadas com sucesso.");
        } catch (Throwable t) {
            warn("Falha ao aplicar Flyway (" + t + "). Caindo em DDL manual.");
            applyInlineBaseline(choice);
        }
    }

    static void applyInlineBaseline(DbChoice choice)
    {
        // DDL mínimo só para não quebrar LoginServer/GameServer boot.
        // Flyway quase sempre cuida disso; isto é fallback para quando o JAR de migrations
        // nao esta disponivel no classpath.
        String ddlMysql = "CREATE TABLE IF NOT EXISTS gameservers ("
                + " server_id INT NOT NULL DEFAULT 0,"
                + " hexid VARCHAR(255) NOT NULL DEFAULT '',"
                + " host VARCHAR(255) NOT NULL DEFAULT '',"
                + " PRIMARY KEY (server_id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
        String ddlSqlite = "CREATE TABLE IF NOT EXISTS gameservers ("
                + " server_id INTEGER NOT NULL DEFAULT 0,"
                + " hexid TEXT NOT NULL DEFAULT '',"
                + " host TEXT NOT NULL DEFAULT '',"
                + " PRIMARY KEY (server_id)"
                + ");";
        String ddlPg = "CREATE TABLE IF NOT EXISTS gameservers ("
                + " server_id INTEGER NOT NULL DEFAULT 0,"
                + " hexid VARCHAR(255) NOT NULL DEFAULT '',"
                + " host VARCHAR(255) NOT NULL DEFAULT '',"
                + " PRIMARY KEY (server_id)"
                + ");";

        try (Connection c = openConnection(choice); Statement st = c.createStatement()) {
            switch (choice.kind) {
                case SQLITE:     st.execute(ddlSqlite); break;
                case POSTGRESQL: st.execute(ddlPg); break;
                default:         st.execute(ddlMysql); break;
            }
            log("DDL mínimo aplicado (gameservers, " + choice.kind + ").");
        } catch (SQLException e) {
            warn("Falha DDL mínimo: " + e);
        }
    }

    // ====================================================================
    // Sanitização de .properties
    // ====================================================================
    static void sanitizeAllConfigs(DbChoice choice)
    {
        try {
            Files.createDirectories(GAME_CONFIG);
            Files.createDirectories(LOGIN_CONFIG);
        } catch (IOException e) {
            warn("Falha criando diretórios: " + e);
        }

        // .example ainda ativos? copia pra versão sem .example quando existir só o example.
        activateExample("game/config/server.properties.example", "game/config/server.properties");
        activateExample("game/config/geoengine.properties.example", "game/config/geoengine.properties");
        activateExample("login/config/loginserver.properties.example", "login/config/loginserver.properties");

        String url = buildJdbcUrl(choice);

        // Server.properties
        try {
            if (Files.exists(GAME_SERVER_PROPS)) {
                updateKey(GAME_SERVER_PROPS, "sql.url", url);
                updateKey(GAME_SERVER_PROPS, "sql.login", choice.user);
                updateKey(GAME_SERVER_PROPS, "sql.password", choice.password);
                // Hostname (campo usado pelo dashboard)
                updateKey(GAME_SERVER_PROPS, "Hostname", choice.kind == DbKind.SQLITE ? DEFAULT_HOST : choice.host);
                log("Atualizado " + GAME_SERVER_PROPS);
            } else {
                warn("game/config/server.properties não existe — pulando.");
            }
        } catch (IOException e) {
            warn("Falha saneando server.properties: " + e);
        }

        // Loginserver.properties
        try {
            if (Files.exists(LOGIN_SERVER_PROPS)) {
                updateKey(LOGIN_SERVER_PROPS, "sql.url", url);
                updateKey(LOGIN_SERVER_PROPS, "sql.login", choice.user);
                updateKey(LOGIN_SERVER_PROPS, "sql.password", choice.password);
                log("Atualizado " + LOGIN_SERVER_PROPS);
            } else {
                warn("login/config/loginserver.properties não existe — pulando.");
            }
        } catch (IOException e) {
            warn("Falha saneando loginserver.properties: " + e);
        }
    }

    static String buildJdbcUrl(DbChoice choice)
    {
        if (choice.kind == DbKind.SQLITE) {
            // Cria data/ se faltar
            new File("data").mkdirs();
            return "jdbc:sqlite:" + SQLITE_PATH;
        }
        if (choice.kind == DbKind.POSTGRESQL) {
            return "jdbc:postgresql://" + choice.host + ":" + choice.port + "/" + choice.db;
        }
        // MariaDB / MySQL
        return "jdbc:" + (choice.kind == DbKind.MYSQL ? "mysql" : "mariadb")
                + "://" + choice.host + ":" + choice.port + "/" + choice.db
                + "?useUnicode=true&characterEncoding=UTF-8"
                + "&allowPublicKeyRetrieval=true"
                + (choice.kind == DbKind.MYSQL ? "&useSSL=false&serverTimezone=UTC" : "")
                + "&disabledAuthenticationPlugins=GSSAPI";
    }

    static void activateExample(String example, String real)
    {
        Path realP = Paths.get(real);
        Path exampleP = Paths.get(example);
        if (Files.exists(realP) || !Files.exists(exampleP)) return;
        try {
            Files.copy(exampleP, realP);
            log("Ativado " + real + " a partir de " + example + ".");
        } catch (IOException e) {
            warn("Falha ao ativar " + real + ": " + e);
        }
    }

    /**
     * Substitui o valor de uma chave (chave = valor) preservando comentários, ordem e keys ausentes.
     * Aceita separadores '=', ':' e ignora '#' e '!' como comentário.
     */
    static void updateKey(Path file, String key, String value) throws IOException
    {
        if (!Files.exists(file)) return;
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<String> out = new ArrayList<>(lines.size() + 1);
        boolean found = false;
        Pattern p = Pattern.compile("^\\s*" + Pattern.quote(key) + "\\s*[=:]\\s*(.*)$");
        for (String ln : lines) {
            String t = ln.trim();
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("!")) {
                out.add(ln);
                continue;
            }
            Matcher m = p.matcher(ln);
            if (m.matches()) {
                String prefix = ln.substring(0, ln.length() - m.group(1).length());
                // preserva comentário inline se houver
                int hash = findInlineComment(ln);
                String tail = (hash >= 0) ? " " + ln.substring(hash).trim() : "";
                out.add(prefix + value + tail);
                found = true;
            } else {
                out.add(ln);
            }
        }
        if (!found) out.add(key + " = " + value);
        Files.write(file, out, StandardCharsets.UTF_8);
    }

    static int findInlineComment(String s)
    {
        // '#' ou '!' fora do início (considerando trim anterior) — só após o =
        int eq = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '=' || c == ':') { eq = i; break; }
        }
        if (eq < 0) return -1;
        for (int i = eq + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '#' || c == '!') return i;
        }
        return -1;
    }

    // ====================================================================
    // GUI: repete o fluxo do botão "Banco de Dados" do painel
    // ====================================================================
    static boolean hasDisplay()
    {
        try {
            return !GraphicsEnvironment.isHeadless();
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean openPrepararAmbientePanelStandalone()
    {
        // Usa reflection para nao criar dependencia estatica forte com security.gui.
        // Abre uma JFrame com o PrepararAmbientePanel (modo standalone - sem dashboard, sem login).
        // Bloqueia ate o usuario clicar em "Concluir" ou fechar a janela.
        try {
            Class<?> panelCls = Class.forName("ext.mods.security.gui.PrepararAmbientePanel");
            Class<?> jfCls    = Class.forName("javax.swing.JFrame");
            Class<?> swing    = Class.forName("javax.swing.SwingUtilities");
            Class<?> winEnvCls = Class.forName("java.awt.event.WindowAdapter");

            Object frame = jfCls.getDeclaredConstructor(String.class).newInstance("BrProject - Preparar Ambiente (Primeira Execucao)");
            jfCls.getMethod("setDefaultCloseOperation", int.class).invoke(frame, 2 /* DISPOSE_ON_CLOSE */);
            jfCls.getMethod("setSize", int.class, int.class).invoke(frame, 920, 640);
            jfCls.getMethod("setLocationRelativeTo", Class.forName("java.awt.Component")).invoke(frame, (Object) null);

            // Construtor (JFrame) - modo standalone
            Object panel = panelCls.getDeclaredConstructor(jfCls).newInstance(frame);
            Object root  = panelCls.getMethod("getPanel").invoke(panel);

            jfCls.getMethod("setContentPane", Class.forName("java.awt.Container")).invoke(frame, root);

            // Bloqueia o thread atual ate o usuario fechar a janela
            final boolean[] closed = { false };
            Object adapter = java.lang.reflect.Proxy.newProxyInstance(
                    winEnvCls.getClassLoader(),
                    new Class<?>[]{ Class.forName("java.awt.event.WindowListener") },
                    (proxy, method, args) -> {
                        if ("windowClosing".equals(method.getName()) || "windowClosed".equals(method.getName())) {
                            synchronized (closed) { closed[0] = true; closed.notifyAll(); }
                        }
                        return null;
                    });
            jfCls.getMethod("addWindowListener", Class.forName("java.awt.event.WindowListener")).invoke(frame, adapter);

            // Se marker ja foi gravado durante o painel (Concluir), retorna true.
            Runnable onComplete = () -> {
                synchronized (closed) { closed[0] = true; closed.notifyAll(); }
            };
            panelCls.getMethod("setOnComplete", Runnable.class).invoke(panel, onComplete);

            // Mostra a janela no EDT
            swing.getMethod("invokeLater", Runnable.class).invoke(swing, (Runnable) () -> {
                try { jfCls.getMethod("setVisible", boolean.class).invoke(frame, true); }
                catch (Throwable t) { t.printStackTrace(); }
            });

            synchronized (closed) {
                while (!closed[0]) closed.wait();
            }
            // Tenta fechar a janela
            try { jfCls.getMethod("dispose").invoke(frame); } catch (Throwable ignored) {}

            boolean finished = (Boolean) panelCls.getMethod("isFinished").invoke(panel);
            log("PrepararAmbientePanel finalizado, finished=" + finished);
            return finished;
        } catch (Throwable t) {
            warn("Falha abrindo GUI: " + t);
            warn("Caindo em modo silencioso...");
            return false;
        }
    }

    // ====================================================================
    // HexId
    // ====================================================================
    static void ensureHexIdRegistered(Connection conn, DbChoice choice) throws SQLException, IOException
    {
        String host = choice.host;
        if (!tableExists(conn, "gameservers")) {
            log("Tabela gameservers não existe — pulando registro (hexid será gerado em runtime).");
            saveHexFile(null);
            return;
        }

        String dbHex = readHexFromDb(conn);
        if (dbHex == null || dbHex.isEmpty()) {
            dbHex = generateHex();
            log("Hexid novo gerado: " + dbHex);
        } else {
            log("Hexid do banco reaproveitado: " + dbHex);
        }

        // Upsert compativel com SQLite/MariaDB/MySQL/PostgreSQL.
//   SQLite:        INSERT OR REPLACE
//   MariaDB/MySQL: INSERT ... ON DUPLICATE KEY UPDATE
//   PostgreSQL:    INSERT ... ON CONFLICT (server_id) DO UPDATE SET ...
        String sql;
        boolean isPg = choice.kind == DbKind.POSTGRESQL;
        boolean isLite = isSqlite(conn);
        if (isLite) {
            sql = "INSERT OR REPLACE INTO gameservers (server_id, hexid, host) VALUES (?, ?, ?)";
        } else if (isPg) {
            sql = "INSERT INTO gameservers (server_id, hexid, host) VALUES (?, ?, ?) "
                + "ON CONFLICT (server_id) DO UPDATE SET hexid = EXCLUDED.hexid, host = EXCLUDED.host";
        } else {
            sql = "INSERT INTO gameservers (server_id, hexid, host) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE hexid = ?, host = ?";
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, SERVER_ID);
            ps.setString(2, dbHex);
            ps.setString(3, host);
            if (!isLite && !isPg) {
                ps.setString(4, dbHex);
                ps.setString(5, host);
            }
            ps.executeUpdate();
            log("gameservers: (server_id=" + SERVER_ID + ", hexid=" + dbHex + ", host=" + host + ") escrito.");
        }

        saveHexFile(dbHex);
    }

    static boolean isSqlite(Connection c) throws SQLException
    {
        return c.getMetaData().getURL().startsWith("jdbc:sqlite:");
    }

    static boolean tableExists(Connection c, String name) throws SQLException
    {
        try (ResultSet rs = c.getMetaData().getTables(null, null, name, null)) {
            while (rs.next()) if (name.equalsIgnoreCase(rs.getString("TABLE_NAME"))) return true;
        }
        return false;
    }

    static String readHexFromDb(Connection c) throws SQLException
    {
        String sql = "SELECT hexid FROM gameservers WHERE server_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, SERVER_ID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String h = rs.getString(1);
                    if (h != null) return h.trim().toUpperCase();
                }
            }
        }
        return null;
    }

    static String generateHex()
    {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return new BigInteger(1, bytes).toString(16).toUpperCase();
    }

    static void saveHexFile(String hex) throws IOException
    {
        Properties p = new Properties();
        p.setProperty("ServerID", String.valueOf(SERVER_ID));
        p.setProperty("HexID", hex == null ? "" : hex.toUpperCase());

        Files.createDirectories(GAME_CONFIG);
        Files.createDirectories(LOGIN_CONFIG);

        try (OutputStream a = new FileOutputStream(GAME_HEXID.toFile())) {
            p.store(a, "the hexID to auth into login (GameServer)");
        }
        try (OutputStream b = new FileOutputStream(LOGIN_HEXID.toFile())) {
            p.store(b, "the hexID to auth into login (LoginServer)");
        }
        log("hexid.txt gravado em game/ e login/");
    }

    // ====================================================================
    // Start em background
    // ====================================================================
    static void startLoginAndGameBackground()
    {
        try {
            ProcessBuilder lp = new ProcessBuilder("cmd", "/c", "StartLogin_SemDashboard.bat");
            lp.directory(new File(".")).redirectErrorStream(true);
            Process p1 = lp.start();
            log("LoginServer PID=" + p1.pid() + " iniciado.");
        } catch (IOException e) {
            warn("Não foi possível iniciar LoginServer: " + e);
        }
        try {
            Thread.sleep(4000);
            ProcessBuilder gp = new ProcessBuilder("cmd", "/c", "StartGame_SemDashboard.bat");
            gp.directory(new File(".")).redirectErrorStream(true);
            Process p2 = gp.start();
            log("GameServer PID=" + p2.pid() + " iniciado.");
        } catch (Throwable t) {
            warn("Não foi possível iniciar GameServer: " + t);
        }
    }

    // ====================================================================
    // Logging
    // ====================================================================
    static void log(String m)  { System.out.println("[PrepararTeste] " + m); }
    static void warn(String m) { System.out.println("[PrepararTeste][WARN] " + m); }
}