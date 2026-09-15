/*
* Copyleft © 2024-2026 L2Brproject
* * This file is part of L2Brproject derived from aCis409/RusaCis3.8
* * L2Brproject is free software: you can redistribute it and/or modify it
* under the terms of the GNU General Public License as published by the
* Free Software Foundation, either version 3 of the License.
* * L2Brproject is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* General Public License for more details.
* * You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
* Our main Developers, Dhousefe-L2JBR, Agazes33, Ban-L2jDev, Warman, SrEli.
* Our special thanks, Nattan Felipe, Diego Fonseca, Junin, ColdPlay, Denky, MecBew, Localhost, MundvayneHELLBOY, 
* SonecaL2, Eduardo.SilvaL2J, biLL, xpower, xTech, kakuzo, Tiagorosendo, Schuster, LucasStark, damedd
* as a contribution for the forum L2JBrasil.com
 */
package ext.mods.security.services;

import java.awt.Color;
import java.awt.GridLayout;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.*;

import ext.mods.commons.gui.ThemeManager;
import ext.mods.commons.jdbc.SupportedDatabase;

/**
 * Gerencia persistência de UI e Banco de Dados.
 * Versão Final: Edição cirúrgica de arquivos (Zero alteração de formatação).
 *
 * - Multi-DB: MariaDB / MySQL / PostgreSQL / SQLServer / SQLite.
 * - Seletor explícito no diálogo "Database" do DashboardPanel (JComboBox).
 * - Fallback automático para SQLite embutido quando a conexão escolhida falha.
 */
public class DatabaseManager {

    /** Tipo de banco relacional suportado pelo botão Database do dashboard. */
    public enum DbType {
        MARIADB("MariaDB", "jdbc:mariadb://", "org.mariadb.jdbc.Driver", 3306, "root", ""),
        MYSQL("MySQL", "jdbc:mysql://", "com.mysql.cj.jdbc.Driver", 3306, "root", ""),
        POSTGRESQL("PostgreSQL", "jdbc:postgresql://", "org.postgresql.Driver", 5432, "postgres", ""),
        SQLSERVER("SQL Server", "jdbc:sqlserver://", "com.microsoft.sqlserver.jdbc.SQLServerDriver", 1433, "sa", ""),
        SQLITE("SQLite (embarcado)", "jdbc:sqlite:", "org.sqlite.JDBC", 0, "", "");

        public final String displayName;
        public final String urlPrefix;
        public final String driverClass;
        public final int defaultPort;
        public final String defaultUser;
        public final String defaultPassword;

        DbType(String displayName, String urlPrefix, String driverClass, int defaultPort, String defaultUser, String defaultPassword) {
            this.displayName = displayName;
            this.urlPrefix = urlPrefix;
            this.driverClass = driverClass;
            this.defaultPort = defaultPort;
            this.defaultUser = defaultUser;
            this.defaultPassword = defaultPassword;
        }

        public boolean isEmbedded() { return this == SQLITE; }
    }

    /** Caminho padrão do arquivo SQLite embutido. */
    private static final String DEFAULT_SQLITE_PATH = "data/brproject.sqlite";

    private static final String PREFS_ROOT = "dashboard_settings";
    private static final String SERVER_PROPERTIES_PATH = "./game/config/server.properties";
    private static final String GEOENGINE_PROPERTIES_PATH = "./game/config/geoengine.properties";
    private static final String LOGINSERVER_PROPERTIES_PATH = "./login/config/loginserver.properties";
    
    private static DatabaseManager instance;
    private final Preferences prefs;
    private boolean lightModeEnabled;
    private boolean developerModeEnabled;
    
    private DatabaseManager() {
        this.prefs = Preferences.userRoot().node(PREFS_ROOT);
        // Nao carregar .properties no construtor: isso roda durante a criacao da GUI.
        // Os getters abaixo continuam lendo os arquivos quando realmente necessarios.
    }

    public static DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    private void loadPreferences() {
        this.lightModeEnabled = isLightModeEnabled();
        this.developerModeEnabled = isDeveloperModeEnabled();
    }
    

    public String getServerHostname() {
        return loadProperty(SERVER_PROPERTIES_PATH, "Hostname", "*");
    }

    public boolean isLightModeEnabled() {
        return Boolean.parseBoolean(loadProperty(GEOENGINE_PROPERTIES_PATH, "UseMinimalGeoOnly", "false"));
    }

    public boolean isDeveloperModeEnabled() {
        return Boolean.parseBoolean(loadProperty(SERVER_PROPERTIES_PATH, "Developer", "false"));
    }



    public void setLightMode(boolean isEnabled, JFrame frame) {
        updateProperty(GEOENGINE_PROPERTIES_PATH, "UseMinimalGeoOnly", String.valueOf(isEnabled));
        this.lightModeEnabled = isEnabled;
        JOptionPane.showMessageDialog(frame, "Light Mode " + (isEnabled ? "ativado." : "desativado.") + "\n(Reinicie o servidor).", "Configuração", JOptionPane.INFORMATION_MESSAGE);
    }

    public void setDeveloperMode(boolean isEnabled, JFrame frame) {
        updateProperty(SERVER_PROPERTIES_PATH, "Developer", String.valueOf(isEnabled));
        this.developerModeEnabled = isEnabled;
        System.out.println("[CONFIG] Developer Mode " + (isEnabled ? "ativado" : "desativado"));
        JOptionPane.showMessageDialog(frame, "Developer Mode " + (isEnabled ? "ativado." : "desativado."), "Configuração", JOptionPane.INFORMATION_MESSAGE);
    }
    

    public Properties loadDatabaseConfig() {
        Properties dbProps = new Properties();
        dbProps.setProperty("host", "localhost");
        dbProps.setProperty("dbName", "l2jdb");
        dbProps.setProperty("user", "root");
        dbProps.setProperty("pass", "");

        File propsFile = new File(SERVER_PROPERTIES_PATH);
        if (propsFile.exists()) {
            try (FileInputStream fis = new FileInputStream(propsFile)) {
                Properties serverProps = new Properties();
                serverProps.load(fis);

                dbProps.setProperty("user", serverProps.getProperty("Login", serverProps.getProperty("sql.login", "root")));
                dbProps.setProperty("pass", serverProps.getProperty("Password", serverProps.getProperty("sql.password", "")));
                String url = serverProps.getProperty("URL", serverProps.getProperty("sql.url", ""));
                
                dbProps.setProperty("type", detectDbTypeFromUrl(url).name());
                dbProps.setProperty("url", url);

                Pattern patternJdbcHostDb = Pattern.compile("jdbc:(?:mariadb|mysql|postgresql)://([^:/]+)(?::\\d+)?/([^?]+)");
                Pattern patternSqlserver = Pattern.compile("jdbc:sqlserver://([^;:]+)(?::\\d+)?;databaseName=([^;]+)");
                Pattern patternSqlite = Pattern.compile("jdbc:sqlite:(.+)");

                Matcher matcher = patternJdbcHostDb.matcher(url);
                if (matcher.find()) {
                    dbProps.setProperty("host", matcher.group(1));
                    dbProps.setProperty("dbName", matcher.group(2));
                } else {
                    matcher = patternSqlserver.matcher(url);
                    if (matcher.find()) {
                        dbProps.setProperty("host", matcher.group(1));
                        dbProps.setProperty("dbName", matcher.group(2));
                    } else {
                        matcher = patternSqlite.matcher(url);
                        if (matcher.find()) {
                            dbProps.setProperty("host", matcher.group(1));
                            dbProps.setProperty("dbName", "brproject");
                            dbProps.setProperty("user", "");
                            dbProps.setProperty("pass", "");
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Erro ao carregar config DB: " + e.getMessage());
            }
        }
        return dbProps;
    }
    
    public void configurarBanco(JFrame parent) {
        Properties dbConfig = loadDatabaseConfig();

        JComboBox<DbType> cmbDbType = new JComboBox<>(DbType.values());
        cmbDbType.setRenderer((list, value, index, isSelected, cellHasFocus) -> new JLabel(value.displayName));
        cmbDbType.setSelectedItem(detectDbTypeFromConfig(dbConfig));

        JTextField txtHost = new JTextField(dbConfig.getProperty("host"));
        JTextField txtUser = new JTextField(dbConfig.getProperty("user"));
        JPasswordField txtSenha = new JPasswordField(dbConfig.getProperty("pass"));
        JTextField txtDatabase = new JTextField(dbConfig.getProperty("dbName"));

        cmbDbType.addActionListener(e -> applyDbTypeDefaults((DbType) cmbDbType.getSelectedItem(), txtHost, txtDatabase, txtUser, txtSenha));
        applyDbTypeDefaults((DbType) cmbDbType.getSelectedItem(), txtHost, txtDatabase, txtUser, txtSenha);

        JPanel panel = new JPanel(new GridLayout(5, 2, 5, 5));
        panel.add(new JLabel("Tipo do Banco:")); panel.add(cmbDbType);
        panel.add(new JLabel("Host/IP do Banco:")); panel.add(txtHost);
        panel.add(new JLabel("Nome do Database:")); panel.add(txtDatabase);
        panel.add(new JLabel("Usuário DB:")); panel.add(txtUser);
        panel.add(new JLabel("Senha DB:")); panel.add(txtSenha);

        int result = JOptionPane.showConfirmDialog(parent, panel, "Configurar Conexão Banco de Dados", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            DbType requestedType = (DbType) cmbDbType.getSelectedItem();
            String host = txtHost.getText().trim();
            String user = txtUser.getText().trim();
            String pass = new String(txtSenha.getPassword());
            String dbName = txtDatabase.getText().trim();

            Connection conn = null;
            try {
                DbConnectionChoice choice = openSelectedOrSqliteFallback(requestedType, host, dbName, user, pass, parent);
                conn = choice.connection;
                host = choice.host;
                dbName = choice.database;
                user = choice.user;
                pass = choice.password;

                JOptionPane.showMessageDialog(parent, "✅ Conexão " + choice.type.displayName + " com '" + dbName + "' bem-sucedida!");

                if (verificarTabelasExistentes(conn)) {
                    int escolha = JOptionPane.showOptionDialog(parent,
                        "⚠️ O banco de dados '" + dbName + "' já possui tabelas!\nO que deseja fazer?",
                        "Banco de Dados Existente",
                        JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null,
                        new String[] {"Limpar e Reinstalar Tudo", "Apenas Salvar Config", "Cancelar"}, "Cancelar");

                    if (escolha == 0) {
                        if (JOptionPane.showConfirmDialog(parent, "TEM CERTEZA?\nIsso apagará TODAS as tabelas!", "Confirmação Final", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
                            dropAllTables(conn, dbName);
                            try { executarSQL(conn, parent); } catch (Exception e) { System.err.println("Erro SQL (não crítico): " + e.getMessage()); }
                            generateAndRegisterHexId(conn);
                            atualizarArquivosProperties(choice.type, host, user, pass, dbName, parent);
                            JOptionPane.showMessageDialog(parent, "✅ Reinstalação concluída!");
                        }
                    } else if (escolha == 1) {
                        sincronizarHexIdExistente(conn);
                        atualizarArquivosProperties(choice.type, host, user, pass, dbName, parent);
                        JOptionPane.showMessageDialog(parent, "✅ Configurações salvas!");
                    }
                } else {
                    try { executarSQL(conn, parent); } catch (Exception e) { System.err.println("Erro SQL (não crítico): " + e.getMessage()); }
                    generateAndRegisterHexId(conn);
                    atualizarArquivosProperties(choice.type, host, user, pass, dbName, parent);
                    JOptionPane.showMessageDialog(parent, "✅ Instalação concluída!");
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(parent, "❌ Erro: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            } finally {
                if (conn != null) try { conn.close(); } catch (SQLException ex) {}
            }
        }
    }

    private record DbConnectionChoice(DbType type, Connection connection, String host, String database, String user, String password) { }

    private DbType detectDbTypeFromConfig(Properties dbConfig) {
        try { return DbType.valueOf(dbConfig.getProperty("type", "MARIADB")); } catch (Exception ignored) { return DbType.MARIADB; }
    }

    private DbType detectDbTypeFromUrl(String url) {
        if (url == null || url.isBlank()) return DbType.MARIADB;
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("jdbc:mysql:")) return DbType.MYSQL;
        if (lower.startsWith("jdbc:postgresql:")) return DbType.POSTGRESQL;
        if (lower.startsWith("jdbc:sqlserver:")) return DbType.SQLSERVER;
        if (lower.startsWith("jdbc:sqlite:")) return DbType.SQLITE;
        return DbType.MARIADB;
    }

    private void applyDbTypeDefaults(DbType type, JTextField txtHost, JTextField txtDatabase, JTextField txtUser, JPasswordField txtSenha) {
        if (type == null) return;
        boolean sqlite = type.isEmbedded();
        txtHost.setEnabled(!sqlite);
        txtDatabase.setEnabled(!sqlite);
        txtUser.setEnabled(!sqlite);
        txtSenha.setEnabled(!sqlite);
        if (sqlite) {
            txtHost.setText(DEFAULT_SQLITE_PATH);
            txtDatabase.setText("brproject");
            txtUser.setText("");
            txtSenha.setText("");
        } else {
            if (txtHost.getText().trim().isEmpty() || txtHost.getText().contains(".sqlite") || txtHost.getText().contains(".db")) txtHost.setText("127.0.0.1");
            if (txtDatabase.getText().trim().isEmpty()) txtDatabase.setText("l2jdb");
            if (txtUser.getText().trim().isEmpty()) txtUser.setText(type.defaultUser);
        }
    }

    private DbConnectionChoice openSelectedOrSqliteFallback(DbType type, String host, String dbName, String user, String pass, JFrame parent) throws Exception {
        try {
            return openDb(type, host, dbName, user, pass);
        } catch (Exception ex) {
            if (type == DbType.SQLITE) throw ex;
            System.err.println("Falha conectando em " + type.displayName + ": " + ex.getMessage() + ". Fallback para SQLite.");
            if (parent != null) {
                JOptionPane.showMessageDialog(parent,
                    "Não foi possível conectar em " + type.displayName + ":\n" + ex.getMessage() + "\n\nUsando SQLite embutido como fallback.",
                    "Fallback SQLite", JOptionPane.WARNING_MESSAGE);
            }
            return openDb(DbType.SQLITE, DEFAULT_SQLITE_PATH, "brproject", "", "");
        }
    }

    private DbConnectionChoice openDb(DbType type, String host, String dbName, String user, String pass) throws Exception {
        if (type == null) type = DbType.MARIADB;
        Class.forName(type.driverClass);
        if (type == DbType.SQLITE) new File("data").mkdirs();
        String jdbcUrl = buildJdbcUrl(type, host, dbName);
        System.out.println("Conectando a: " + jdbcUrl);
        Connection conn = type == DbType.SQLITE ? DriverManager.getConnection(jdbcUrl) : DriverManager.getConnection(jdbcUrl, user, pass);
        conn.setAutoCommit(true);
        applyConnectionPragmasIfSqlite(conn);
        return new DbConnectionChoice(type, conn, type == DbType.SQLITE ? normalizeSqlitePath(host) : host, type == DbType.SQLITE ? "brproject" : dbName, type == DbType.SQLITE ? "" : user, type == DbType.SQLITE ? "" : pass);
    }

    private String buildJdbcUrl(DbType type, String host, String dbName) {
        if (type == DbType.SQLITE) return "jdbc:sqlite:" + normalizeSqlitePath(host);
        if (type == DbType.POSTGRESQL) return "jdbc:postgresql://" + host + ":" + type.defaultPort + "/" + dbName;
        if (type == DbType.SQLSERVER) return "jdbc:sqlserver://" + host + ":" + type.defaultPort + ";databaseName=" + dbName + ";encrypt=false;trustServerCertificate=true";
        String proto = type == DbType.MYSQL ? "mysql" : "mariadb";
        return "jdbc:" + proto + "://" + host + ":" + type.defaultPort + "/" + dbName
            + "?useUnicode=true&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC"
            + (type == DbType.MARIADB ? "&disabledAuthenticationPlugins=GSSAPI" : "");
    }

    private String normalizeSqlitePath(String path) {
        String raw = path;
        if (raw == null || raw.isBlank() || "localhost".equalsIgnoreCase(raw) || "127.0.0.1".equals(raw)) raw = DEFAULT_SQLITE_PATH;
        if (raw.startsWith("jdbc:sqlite:")) raw = raw.substring("jdbc:sqlite:".length());
        File sqliteFile = new File(raw.trim());
        if (!sqliteFile.isAbsolute()) sqliteFile = sqliteFile.getAbsoluteFile();
        return sqliteFile.toPath().normalize().toString().replace('\\', '/');
    }

    private void applyConnectionPragmasIfSqlite(Connection conn) {
        try {
            if (!isSqlite(conn)) return;
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
                stmt.execute("PRAGMA cache_size=-64000");
                stmt.execute("PRAGMA temp_store=MEMORY");
                stmt.execute("PRAGMA mmap_size=268435456");
                stmt.execute("PRAGMA busy_timeout=10000");
            }
        } catch (SQLException e) {
            System.err.println("Aviso SQLite PRAGMA: " + e.getMessage());
        }
    }

    
    private void executarSQL(Connection conn, JFrame frame) throws IOException, SQLException {
        File pastaSQL = new File("./tools/sql");
        File[] arquivos = pastaSQL.listFiles((dir, name) -> name.toLowerCase().endsWith(".sql"));
        if (arquivos == null || arquivos.length == 0) return;

        java.util.Arrays.sort(arquivos);
        try (Statement stmt = conn.createStatement()) {
            for (File sqlFile : arquivos) {
                StringBuilder sqlBuilder = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new FileReader(sqlFile, StandardCharsets.UTF_8))) {
                    String linha;
                    while ((linha = br.readLine()) != null) {
                        String trimmedLine = linha.trim();
                        if (!trimmedLine.isEmpty() && !trimmedLine.startsWith("--") && !trimmedLine.startsWith("#")) {
                            sqlBuilder.append(linha).append("\n");
                        }
                    }
                }
                String[] comandos = sqlBuilder.toString().split(";\\s*(\\n|$)");
                for (String cmd : comandos) {
                    if (!cmd.trim().isEmpty()) {
                        try { stmt.execute(cmd.trim()); } catch (SQLException e) { System.err.println("Aviso SQL: " + e.getMessage()); }
                    }
                }
            }
        }
    }

    private void generateAndRegisterHexId(Connection conn) throws IOException, SQLException {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        String hexId = new BigInteger(1, bytes).toString(16).toUpperCase();
        int serverId = 1;

        saveHexIdToFile("./game/config/hexid.txt", serverId, hexId);
        saveHexIdToFile("./login/config/hexid.txt", serverId, hexId);

        ensureGameserversTable(conn);
        upsertGameServer(conn, serverId, hexId, normalizeGameServerHost());
    }

    private void saveHexIdToFile(String filePath, int serverId, String hexId) throws IOException {
        String hexIdUpper = (hexId != null) ? hexId.toUpperCase() : "";
        
        File file = new File(filePath);
        file.getParentFile().mkdirs();
        file.createNewFile();
        
        Properties hexSetting = new Properties();
        hexSetting.setProperty("ServerID", String.valueOf(serverId));
        hexSetting.setProperty("HexID", hexIdUpper);
        
        try (FileOutputStream out = new FileOutputStream(file)) {
            hexSetting.store(out, "the hexID to auth into login");
        }
    }

    /**
     * Sincroniza o hexid existente entre arquivos e banco de dados, sem gerar um novo.
     * Preserva o hexid atual se já existir.
     */
    private void sincronizarHexIdExistente(Connection conn) throws IOException, SQLException {
        int serverId = 1;
        String hexId = null;
        
        File gameHexFile = new File("./game/config/hexid.txt");
        File loginHexFile = new File("./login/config/hexid.txt");
        
        if (gameHexFile.exists()) {
            Properties hexProps = new Properties();
            try (FileInputStream fis = new FileInputStream(gameHexFile)) {
                hexProps.load(fis);
                hexId = hexProps.getProperty("HexID");
                String serverIdStr = hexProps.getProperty("ServerID");
                if (serverIdStr != null) {
                    try {
                        serverId = Integer.parseInt(serverIdStr);
                    } catch (NumberFormatException e) {
                    }
                }
            } catch (IOException e) {
                System.err.println("Erro ao ler hexid.txt: " + e.getMessage());
            }
        }
        
        if ((hexId == null || hexId.trim().isEmpty()) && loginHexFile.exists()) {
            Properties hexProps = new Properties();
            try (FileInputStream fis = new FileInputStream(loginHexFile)) {
                hexProps.load(fis);
                hexId = hexProps.getProperty("HexID");
                String serverIdStr = hexProps.getProperty("ServerID");
                if (serverIdStr != null) {
                    try {
                        serverId = Integer.parseInt(serverIdStr);
                    } catch (NumberFormatException e) {
                    }
                }
            } catch (IOException e) {
                System.err.println("Erro ao ler hexid.txt: " + e.getMessage());
            }
        }
        
        if ((hexId == null || hexId.trim().isEmpty())) {
            try {
                ensureGameserversTable(conn);
                try (PreparedStatement ps = conn.prepareStatement("SELECT hexid FROM gameservers WHERE server_id = ?")) {
                    ps.setInt(1, serverId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String dbHexId = rs.getString("hexid");
                            if (dbHexId != null && !dbHexId.trim().isEmpty()) {
                                try {
                                    new BigInteger(dbHexId.trim(), 16);
                                    hexId = dbHexId;
                                } catch (NumberFormatException e) {
                                    System.err.println("HexID inválido encontrado no banco. Será gerado um novo.");
                                }
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                System.err.println("Erro ao ler hexid do banco: " + e.getMessage());
            }
        }
        
        if (hexId != null && !hexId.trim().isEmpty()) {
            hexId = hexId.trim().toUpperCase();
            
            try {
                new BigInteger(hexId, 16);
            } catch (NumberFormatException e) {
                System.err.println("HexID inválido encontrado: " + hexId + ". Gerando novo hexid...");
                hexId = null;
            }
        }
        
        if (hexId != null && !hexId.trim().isEmpty()) {
            ensureGameserversTable(conn);
            upsertGameServer(conn, serverId, hexId, normalizeGameServerHost());

            saveHexIdToFile("./game/config/hexid.txt", serverId, hexId);
            saveHexIdToFile("./login/config/hexid.txt", serverId, hexId);
        } else {
            System.out.println("Nenhum hexid existente encontrado. Gerando novo hexid...");
            generateAndRegisterHexId(conn);
        }
    }

    private boolean verificarTabelasExistentes(Connection conn) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) return true;
        }
        return false;
    }
    
    private void dropAllTables(Connection conn, String dbName) throws SQLException {
        List<String> tabelas = new ArrayList<>();
        DbType type = detectDbType(conn);
        try (ResultSet rs = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String table = rs.getString("TABLE_NAME");
                if (table != null && !table.equalsIgnoreCase("sqlite_sequence")) tabelas.add(table);
            }
        }
        try (Statement stmt = conn.createStatement()) {
            try { if (type == DbType.MARIADB || type == DbType.MYSQL) stmt.execute("SET FOREIGN_KEY_CHECKS = 0"); } catch (SQLException e) {}
            try { if (type == DbType.POSTGRESQL) stmt.execute("SET session_replication_role = replica"); } catch (SQLException e) {}
            for (String tabela : tabelas) {
                try { stmt.executeUpdate("DROP TABLE IF EXISTS " + quoteIdentifier(type, tabela)); } catch (SQLException e) { System.err.println("Aviso drop " + tabela + ": " + e.getMessage()); }
            }
            try { if (type == DbType.POSTGRESQL) stmt.execute("SET session_replication_role = DEFAULT"); } catch (SQLException e) {}
            try { if (type == DbType.MARIADB || type == DbType.MYSQL) stmt.execute("SET FOREIGN_KEY_CHECKS = 1"); } catch (SQLException e) {}
        }
    }


    private void atualizarArquivosProperties(DbType type, String host, String user, String pass, String dbName, JFrame parent) throws IOException {
        System.out.println("Atualizando arquivos de configuração do DB...");
        
        File gameServerProps = new File(SERVER_PROPERTIES_PATH);
        File loginServerProps = new File(LOGINSERVER_PROPERTIES_PATH);

        String jdbcUrl = buildJdbcUrl(type, host, dbName);
        atualizarDBProperties(gameServerProps, jdbcUrl, user, pass, "sql.url", "sql.login", "sql.password");
        atualizarDBProperties(loginServerProps, jdbcUrl, user, pass, "sql.url", "sql.login", "sql.password");
        
        updateProperty(SERVER_PROPERTIES_PATH, "Hostname", type == DbType.SQLITE ? "127.0.0.1" : host);

        System.out.println("Atualização dos arquivos de configuração concluída.");
    }


    /**
     * Atualiza uma chave simples em um arquivo .properties preservando TUDO.
     */
    private void updateProperty(String filePath, String key, String newValue) {
        File file = new File(filePath);
        if (!file.exists()) return;

        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            List<String> newLines = new ArrayList<>();
            boolean keyFound = false;
            
            String regex = "^(\\s*" + Pattern.quote(key) + "\\s*)([=:])(.*)$";
            Pattern pattern = Pattern.compile(regex);

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("#") || trimmed.startsWith("!")) {
                    newLines.add(line);
                    continue;
                }

                Matcher m = pattern.matcher(line);
                if (m.matches()) {
                    String prefix = m.group(1);
                    String separator = m.group(2);
                    String oldTail = m.group(3);
                    
                    String comment = "";
                    int commentIndex = findInlineCommentIndex(oldTail);
                    if (commentIndex >= 0) {
                        comment = oldTail.substring(commentIndex);
                    }
                    
                    newLines.add(prefix + separator + " " + newValue + comment);
                    keyFound = true;
                } else {
                    newLines.add(line);
                }
            }

            if (!keyFound) {
                newLines.add(key + " = " + newValue);
            }

            Files.write(file.toPath(), newLines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        } catch (IOException e) {
            System.err.println("Erro ao atualizar propriedade '" + key + "': " + e.getMessage());
        }
    }

    /**
     * Encontra comentário inline em uma linha de properties ("#" ou "!"), preservando ordem e comentários.
     */
    private int findInlineCommentIndex(String valueWithComment) {
        if (valueWithComment == null || valueWithComment.isEmpty()) {
            return -1;
        }
        
        for (int i = 0; i < valueWithComment.length(); i++) {
            char c = valueWithComment.charAt(i);
            if (c == '#' || c == '!') {
                return i;
            }
        }
        
        return -1;
    }

    /**
     * Atualiza as chaves específicas de banco de dados (URL, User, Pass)
     */
    private void atualizarDBProperties(File file, String jdbcUrl, String user, String pass, String urlKey, String userKey, String passKey) throws IOException {
        if (!file.exists()) return;
        
        Path filePath = file.toPath();
        List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        List<String> newLines = new ArrayList<>();
        
        String regexTemplate = "^(\\s*%s\\s*[=:]\\s*)(.*)$"; 

        boolean urlFound = false, userFound = false, passFound = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#") || trimmed.startsWith("!")) {
                newLines.add(line);
                continue;
            }

            Matcher mUrl = Pattern.compile(String.format(regexTemplate, Pattern.quote(urlKey))).matcher(line);
            Matcher mUser = Pattern.compile(String.format(regexTemplate, Pattern.quote(userKey))).matcher(line);
            Matcher mPass = Pattern.compile(String.format(regexTemplate, Pattern.quote(passKey))).matcher(line);

            if (mUrl.matches()) {
                String prefix = mUrl.group(1);
                String oldValue = mUrl.group(2).trim();
                newLines.add(prefix + jdbcUrl);
                urlFound = true;
            } 
            else if (mUser.matches()) {
                String prefix = mUser.group(1);
                newLines.add(prefix + user);
                userFound = true;
            } 
            else if (mPass.matches()) {
                String prefix = mPass.group(1);
                newLines.add(prefix + pass);
                passFound = true;
            } 
            else {
                newLines.add(line);
            }
        }

        if (!urlFound) newLines.add(urlKey + " = " + jdbcUrl);
        if (!userFound) newLines.add(userKey + " = " + user);
        if (!passFound) newLines.add(passKey + " = " + pass);

        Files.write(filePath, newLines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
    
    private DbType detectDbType(Connection conn) throws SQLException {
        String url = conn.getMetaData().getURL();
        return detectDbTypeFromUrl(url);
    }

    private boolean isSqlite(Connection conn) throws SQLException {
        return detectDbType(conn) == DbType.SQLITE;
    }

    private void ensureGameserversTable(Connection conn) throws SQLException {
        DbType type = detectDbType(conn);
        String ddl;
        if (type == DbType.SQLITE) {
            ddl = "CREATE TABLE IF NOT EXISTS gameservers (server_id INTEGER NOT NULL DEFAULT 0, hexid TEXT NOT NULL DEFAULT '', host TEXT NOT NULL DEFAULT '', PRIMARY KEY (server_id))";
        } else if (type == DbType.POSTGRESQL) {
            ddl = "CREATE TABLE IF NOT EXISTS gameservers (server_id INTEGER NOT NULL DEFAULT 0, hexid VARCHAR(50) NOT NULL DEFAULT '', host VARCHAR(50) NOT NULL DEFAULT '', PRIMARY KEY (server_id))";
        } else if (type == DbType.SQLSERVER) {
            ddl = "IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='gameservers' AND xtype='U') CREATE TABLE gameservers (server_id INT NOT NULL DEFAULT 0, hexid VARCHAR(50) NOT NULL DEFAULT '', host VARCHAR(50) NOT NULL DEFAULT '', PRIMARY KEY (server_id))";
        } else {
            ddl = "CREATE TABLE IF NOT EXISTS gameservers (server_id INT NOT NULL DEFAULT 0, hexid VARCHAR(50) NOT NULL DEFAULT '', host VARCHAR(50) NOT NULL DEFAULT '', PRIMARY KEY (server_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(ddl);
        }
    }

    private void upsertGameServer(Connection conn, int serverId, String hexId, String host) throws SQLException {
        DbType type = detectDbType(conn);
        String sql;
        if (type == DbType.SQLITE) {
            sql = "INSERT OR REPLACE INTO gameservers (server_id, hexid, host) VALUES (?, ?, ?)";
        } else if (type == DbType.POSTGRESQL) {
            sql = "INSERT INTO gameservers (server_id, hexid, host) VALUES (?, ?, ?) ON CONFLICT (server_id) DO UPDATE SET hexid = EXCLUDED.hexid, host = EXCLUDED.host";
        } else if (type == DbType.SQLSERVER) {
            sql = "MERGE gameservers AS target USING (SELECT ? AS server_id, ? AS hexid, ? AS host) AS src ON target.server_id = src.server_id "
                + "WHEN MATCHED THEN UPDATE SET hexid = src.hexid, host = src.host "
                + "WHEN NOT MATCHED THEN INSERT (server_id, hexid, host) VALUES (src.server_id, src.hexid, src.host);";
        } else {
            sql = "INSERT INTO gameservers (server_id, hexid, host) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE hexid = ?, host = ?";
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, serverId);
            ps.setString(2, hexId);
            ps.setString(3, host);
            if (type == DbType.MARIADB || type == DbType.MYSQL) {
                ps.setString(4, hexId);
                ps.setString(5, host);
            }
            ps.executeUpdate();
        }
    }

    private String normalizeGameServerHost() {
        String hostname = getServerHostname();
        if (hostname == null || hostname.trim().isEmpty() || hostname.trim().equalsIgnoreCase("localhost") || hostname.equals("*")) return "127.0.0.1";
        return hostname.trim();
    }

    private String quoteIdentifier(DbType type, String identifier) {
        String safe = identifier.replace("\"", "\"\"").replace("`", "``").replace("]", "]]").trim();
        if (type == DbType.MARIADB || type == DbType.MYSQL) return "`" + safe + "`";
        if (type == DbType.SQLSERVER) return "[" + identifier.replace("]", "]]").trim() + "]";
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private String loadProperty(String filePath, String key, String defaultValue) {
        Properties props = new Properties();
        File file = new File(filePath);
        if (!file.exists()) return defaultValue;
        try (FileInputStream fis = new FileInputStream(file)) {
            props.load(fis);
            return props.getProperty(key, defaultValue);
        } catch (IOException e) {
            return defaultValue;
        }
    }
}
