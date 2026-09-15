/*
 * Copyleft © 2024-2026 L2Brproject
 * PrepararAmbientePanel — painel unificado de primeiro acesso.
 *
 * Combina em uma única tela:
 *   • Login da Licença (e-mail/senha) — credenciais OSS pré-preenchidas
 *   • Driver do banco (MariaDB/MySQL/SQLite) + Host/Porta/Usuario/Senha/DB
 *   • Detecção automática do MariaDB local
 *   • Teste de conexão
 *   • Migração do schema (Flyway / SQL inline) + registro do HexID
 *   • Botão "Continuar" para o Dashboard
 *
 * Substitui o LoginPanel quando o arquivo flag/PrepararTeste.done ainda não existe.
 * Reaproveita o mesmo tema do LoginPanel (cores NEON_PURPLE / BASE_PURPLE).
 */
package ext.mods.security.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import ext.mods.commons.gui.ThemeManager;
import ext.mods.commons.jdbc.SupportedDatabase;
import ext.mods.security.services.AuthService;

public class PrepararAmbientePanel {

    private final JFrame parentFrame;
    private final AuthService authService; // pode ser null em modo standalone (PrepararTeste / Start*.bat)
    private final MainFrame mainFrame;     // pode ser null em modo standalone
    private final boolean standalone;
    private Runnable onComplete;           // disparado quando o usuario conclui o painel (standalone)
    private boolean finished;              // true apos onComplete rodar
    public boolean isFinished() { return finished; }

    private final Preferences prefs = Preferences.userRoot().node("project_dashboard");
    private JPanel rootPanel;

    // Login
    private JTextField txtEmail;
    private JPasswordField txtSenha;

    // Banco
    private JComboBox<DbOption> cmbDriver;
    private JTextField txtHost;
    private JTextField txtPorta;
    private JTextField txtDatabase;
    private JTextField txtDbUser;
    private JPasswordField txtDbPass;

    // Logs
    private JTextArea logArea;
    private JProgressBar progress;
    private JButton btnDetectar;
    private JButton btnTestar;
    private JButton btnPreparar;
    private JButton btnEntrar;

    private static final String MARKER = "flag/PrepararTeste.done";

    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    static {
        DEFAULTS.put("host", "127.0.0.1");
        DEFAULTS.put("port", "3306");
        DEFAULTS.put("user", "root");
        DEFAULTS.put("pass", "root");
        DEFAULTS.put("database", "l2jdb");
    }

    public PrepararAmbientePanel(JFrame parentFrame, AuthService authService, MainFrame mainFrame) {
        this.parentFrame = parentFrame;
        this.authService = authService;
        this.mainFrame = mainFrame;
        this.standalone = (authService == null && mainFrame == null);
        createPanel();
    }

    /**
     * Construtor standalone - usado por PrepararTesteEntry quando invocado
     * a partir dos .bat (StartLogin/StartGame_SemDashboard) sem dashboard.
     * Nao exige autenticacao: o usuario revisa IP/DB/driver, aperta
     * "Concluir", marker eh gravado, janela fecha e o batch segue com start.
     */
    public PrepararAmbientePanel(JFrame parentFrame) {
        this.parentFrame = parentFrame;
        this.authService = null;
        this.mainFrame = null;
        this.standalone = true;
        createPanel();
    }

    /** Define o callback a ser chamado quando o usuario clicar em "Concluir". */
    public void setOnComplete(Runnable r) { this.onComplete = r; }

    public JPanel getPanel() { return rootPanel; };

    private void createPanel() {
        rootPanel = new JPanel(new BorderLayout(0, 0));
        rootPanel.setBackground(ThemeManager.COMPONENT_BACKGROUND);

        // ---------- Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(20, 30, 12, 30));
        JLabel titulo = new JLabel("BrProject  -  Preparar Ambiente");
        titulo.setForeground(ThemeManager.BASE_PURPLE.brighter());
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titulo.setHorizontalAlignment(SwingConstants.LEFT);
        header.add(titulo, BorderLayout.WEST);
        JLabel subtitulo = new JLabel("<html><span style='color:#A0A0A8;'>Bem-vindo! Nesta tela voce configura a licenca e o banco de dados do servidor em um so lugar.</span></html>");
        subtitulo.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        header.add(subtitulo, BorderLayout.SOUTH);
        rootPanel.add(header, BorderLayout.NORTH);

        // ---------- Center: 2 colunas
        JPanel center = new JPanel(new GridLayout(1, 2, 14, 0));
        center.setOpaque(false);
        center.setBorder(BorderFactory.createEmptyBorder(0, 24, 0, 24));
        center.add(buildLicencaPanel());
        center.add(buildBancoPanel());
        rootPanel.add(center, BorderLayout.CENTER);

        // ---------- South: logs + acoes
        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.setOpaque(false);
        south.setBorder(BorderFactory.createEmptyBorder(8, 24, 16, 24));

        logArea = new JTextArea(7, 40);
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setBackground(new Color(15, 15, 22));
        logArea.setForeground(new Color(180, 220, 255));
        logArea.setFont(new Font("Consolas", Font.PLAIN, 11));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR),
                "Log", 0, 0, new Font("Segoe UI", Font.PLAIN, 11), ThemeManager.TEXT_COLOR));
        south.add(scroll, BorderLayout.CENTER);

        progress = new JProgressBar();
        progress.setIndeterminate(false);
        progress.setValue(0);
        progress.setStringPainted(true);
        progress.setForeground(ThemeManager.BASE_PURPLE);
        progress.setBackground(ThemeManager.COMPONENT_BACKGROUND);
        south.add(progress, BorderLayout.NORTH);

        JPanel acoes = new JPanel(new GridLayout(1, 5, 8, 0));
        acoes.setOpaque(false);
        btnDetectar = makeButton("Detectar DB", new Color(0x30, 0x60, 0xA0));
        btnTestar   = makeButton("Testar Conexao", new Color(0x40, 0x90, 0x60));
        btnPreparar = makeButton("Preparar (migrar + hexid)", ThemeManager.BASE_PURPLE);
        btnEntrar   = makeButton(standalone ? "Concluir" : "Entrar no Painel", new Color(0x60, 0x30, 0x90));
        JButton btnCancelar = makeButton("Cancelar", new Color(0x55, 0x55, 0x60));
        btnCancelar.addActionListener(e -> {
            if (standalone && parentFrame != null) parentFrame.dispose();
            else if (mainFrame != null) mainFrame.showDashboardPanel();
        });
        acoes.add(btnDetectar);
        acoes.add(btnTestar);
        acoes.add(btnPreparar);
        acoes.add(btnEntrar);
        acoes.add(btnCancelar);
        south.add(acoes, BorderLayout.SOUTH);

        rootPanel.add(south, BorderLayout.SOUTH);

        // Estado inicial: botao Entrar desabilitado ate Preparar terminar com sucesso
        btnEntrar.setEnabled(false);

        // Listeners
        btnDetectar.addActionListener(e -> onDetectar());
        btnTestar.addActionListener(e -> onTestar());
        btnPreparar.addActionListener(e -> onPreparar());
        btnEntrar.addActionListener(e -> onEntrar());

        // Mudanca de driver atualiza defaults
        cmbDriver.addActionListener(e -> onDriverChanged());

        // Pre-fill a partir das .properties se existirem
        prefillFromDisk();

        // Se marker ja existe, libera botao Entrar imediatamente
        if (new File(MARKER).exists()) {
            log("Marker PrepararTeste.done encontrado - ambiente ja preparado.");
            btnEntrar.setEnabled(true);
        }
    }

    // ====================================================================
    //  Sub-paineis
    // ====================================================================
    private JPanel buildLicencaPanel() {
        JPanel box = makeBox("Licenca  (BrProject L2J)");
        GridBagConstraints g = baseGbc();

        // Email
        g.gridx = 0; g.gridy = 0;
        box.add(label("E-mail:"), g);
        g.gridx = 1;
        String savedEmail = prefs.get("email", "brprojeto@l2jbrasil.com");
        txtEmail = new JTextField(savedEmail, 18);
        box.add(txtEmail, g);

        // Senha
        g.gridx = 0; g.gridy = 1;
        box.add(label("Senha:"), g);
        g.gridx = 1;
        txtSenha = new JPasswordField(prefs.get("senha", "12345678"), 18);
        box.add(txtSenha, g);

        // Credenciais padrao visiveis
        g.gridx = 0; g.gridy = 2; g.gridwidth = 2;
        JLabel hint = new JLabel("<html><span style='color:#888;'>Padrao OSS: brprojeto@l2jbrasil.com / 12345678</span></html>");
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        box.add(hint, g);

        // Status
        g.gridx = 0; g.gridy = 3; g.gridwidth = 2;
        JLabel status = new JLabel(" ");
        status.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        box.add(status, g);

        // Espaco elastico embaixo
        g.gridx = 0; g.gridy = 4; g.weighty = 1.0; g.gridwidth = 2;
        box.add(new JLabel(), g);

        return box;
    }

    private JPanel buildBancoPanel() {
        JPanel box = makeBox("Banco de Dados  (driver + migracao)");
        GridBagConstraints g = baseGbc();

        // Driver
        g.gridx = 0; g.gridy = 0;
        box.add(label("Driver:"), g);
        g.gridx = 1;
        cmbDriver = new JComboBox<>(new DbOption[] {
                new DbOption("MariaDB (recomendado)", "mariadb"),
                new DbOption("MySQL", "mysql"),
                new DbOption("PostgreSQL", "postgresql"),
                new DbOption("SQLite (embarcado - fallback automatico)", "sqlite"),
        });
        cmbDriver.setBackground(ThemeManager.COMPONENT_BACKGROUND);
        cmbDriver.setForeground(ThemeManager.TEXT_COLOR);
        box.add(cmbDriver, g);

        // Host
        g.gridx = 0; g.gridy = 1;
        box.add(label("Host/IP:"), g);
        g.gridx = 1;
        txtHost = new JTextField(DEFAULTS.get("host"), 18);
        box.add(txtHost, g);

        // Porta
        g.gridx = 0; g.gridy = 2;
        box.add(label("Porta:"), g);
        g.gridx = 1;
        txtPorta = new JTextField(DEFAULTS.get("port"), 18);
        box.add(txtPorta, g);

        // Database
        g.gridx = 0; g.gridy = 3;
        box.add(label("Database:"), g);
        g.gridx = 1;
        txtDatabase = new JTextField(DEFAULTS.get("database"), 18);
        box.add(txtDatabase, g);

        // User
        g.gridx = 0; g.gridy = 4;
        box.add(label("Usuario:"), g);
        g.gridx = 1;
        txtDbUser = new JTextField(DEFAULTS.get("user"), 18);
        box.add(txtDbUser, g);

        // Pass
        g.gridx = 0; g.gridy = 5;
        box.add(label("Senha:"), g);
        g.gridx = 1;
        txtDbPass = new JPasswordField(DEFAULTS.get("pass"), 18);
        box.add(txtDbPass, g);

        // Hint
        g.gridx = 0; g.gridy = 6; g.gridwidth = 2;
        JLabel hint = new JLabel("<html><span style='color:#888;'>Dica: clique em 'Detectar DB' antes - ele tenta MariaDB em 127.0.0.1 e cai pra SQLite se nao existir.</span></html>");
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        box.add(hint, g);

        // Espaco elastico
        g.gridx = 0; g.gridy = 7; g.weighty = 1.0; g.gridwidth = 2;
        box.add(new JLabel(), g);

        return box;
    }

    // ====================================================================
    //  Helpers visuais
    // ====================================================================
    private JPanel makeBox(String title) {
        JPanel box = new JPanel(new GridBagLayout());
        box.setBackground(new Color(22, 20, 28));
        box.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR),
                title, 0, 0, new Font("Segoe UI", Font.BOLD, 12), ThemeManager.TEXT_COLOR));
        box.setOpaque(true);
        return box;
    }

    private GridBagConstraints baseGbc() {
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.LINE_START;
        g.weightx = 0;
        return g;
    }

    private JLabel label(String s) {
        JLabel l = new JLabel(s);
        l.setForeground(ThemeManager.TEXT_COLOR);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        return l;
    }

    private JButton makeButton(String text, Color color) {
        JButton b = new JButton(text);
        b.setBackground(color);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(0, 32));
        return b;
    }

    // ====================================================================
    //  Pre-fill from existing .properties
    // ====================================================================
    private void prefillFromDisk() {
        Properties props = new Properties();
        File f = new File("game/config/server.properties");
        if (f.exists()) {
            try (var in = Files.newInputStream(f.toPath())) {
                props.load(in);
            } catch (Exception ignored) {}
        }
        if (!props.isEmpty()) {
            String url = props.getProperty("URL", props.getProperty("sql.url", ""));
            if (url.startsWith("jdbc:mariadb://")) {
                cmbDriver.setSelectedIndex(0);
                applyMariadbOrMysqlUrl(url);
            } else if (url.startsWith("jdbc:mysql://")) {
                cmbDriver.setSelectedIndex(1);
                applyMariadbOrMysqlUrl(url);
            } else if (url.startsWith("jdbc:postgresql://")) {
                cmbDriver.setSelectedIndex(2);
                applyPostgresUrl(url);
            } else if (url.startsWith("jdbc:sqlite:")) {
                cmbDriver.setSelectedIndex(3);
            }
            txtDbUser.setText(props.getProperty("Login", props.getProperty("sql.login", DEFAULTS.get("user"))));
            txtDbPass.setText(props.getProperty("Password", props.getProperty("sql.password", DEFAULTS.get("pass"))));
        }
    }

    private void applyPostgresUrl(String url) {
        // jdbc:postgresql://host:port/db
        try {
            String rest = url.substring(url.indexOf("//") + 2);
            int slash = rest.indexOf('/');
            String hostPort = rest.substring(0, slash);
            String db = rest.substring(slash + 1).split("\\?")[0];
            if (hostPort.contains(":")) {
                String[] hp = hostPort.split(":");
                txtHost.setText(hp[0]);
                txtPorta.setText(hp[1]);
            } else {
                txtHost.setText(hostPort);
            }
            txtDatabase.setText(db);
        } catch (Exception ignored) {}
    }

    private void applyMariadbOrMysqlUrl(String url) {
        // jdbc:mariadb://host:port/db?... ou jdbc:mysql://host:port/db?...
        try {
            String rest = url.substring(url.indexOf("//") + 2);
            int slash = rest.indexOf('/');
            String hostPort = rest.substring(0, slash);
            String db = rest.substring(slash + 1).split("\\?")[0];
            if (hostPort.contains(":")) {
                String[] hp = hostPort.split(":");
                txtHost.setText(hp[0]);
                txtPorta.setText(hp[1]);
            } else {
                txtHost.setText(hostPort);
            }
            txtDatabase.setText(db);
        } catch (Exception ignored) {}
    }

    // ====================================================================
    //  Acoes
    // ====================================================================
    private void onDriverChanged() {
        DbOption opt = (DbOption) cmbDriver.getSelectedItem();
        if (opt == null) return;
        if ("sqlite".equals(opt.key)) {
            txtHost.setEnabled(false);
            txtPorta.setEnabled(false);
            txtDatabase.setText("data/brproject.db");
        } else {
            txtHost.setEnabled(true);
            txtPorta.setEnabled(true);
            if (txtDatabase.getText().startsWith("data/")) {
                txtDatabase.setText(DEFAULTS.get("database"));
            }
        }
    }

    private void onDetectar() {
        DbOption opt = (DbOption) cmbDriver.getSelectedItem();
        if ("sqlite".equals(opt.key)) {
            log("Driver SQLite selecionado - sem deteccao remota necessaria.");
            return;
        }
        log("Detectando banco de dados em 127.0.0.1 ...");
        progress.setIndeterminate(true);
        new SwingWorkerLite<>(() -> {
            // Carrega drivers que possam estar no classpath (nao fatal se faltar)
            try { Class.forName("org.mariadb.jdbc.Driver"); } catch (Throwable ignored) {}
            try { Class.forName("com.mysql.cj.jdbc.Driver"); } catch (Throwable ignored) {}
            try { Class.forName("org.postgresql.Driver"); } catch (Throwable ignored) {}

            String[] candidates;
            int[] ports;
            String urlPrefix;
            if ("postgresql".equals(opt.key)) {
                candidates = new String[]{"127.0.0.1", "localhost"};
                ports = new int[]{5432};
                urlPrefix = "jdbc:postgresql://";
            } else {
                candidates = new String[]{"127.0.0.1", "localhost"};
                ports = new int[]{3306, 3307};
                urlPrefix = "jdbc:mariadb://";
            }
            for (String host : candidates) {
                for (int port : ports) {
                    String url = urlPrefix + host + ":" + port
                            + ("postgresql".equals(opt.key) ? "/postgres" : "/")
                            + ("postgresql".equals(opt.key) ? "?connectTimeout=2" : "?connectTimeout=2000");
                    try (Connection c = DriverManager.getConnection(url, "root", "root")) {
                        String name = c.getMetaData().getDatabaseProductName();
                        log("OK - " + name + " respondendo em " + host + ":" + port);
                        SwingUtilities.invokeLater(() -> {
                            txtHost.setText(host);
                            txtPorta.setText(String.valueOf(port));
                            progress.setIndeterminate(false);
                        });
                        return null;
                    } catch (SQLException ex) {
                        log("  " + host + ":" + port + " -> " + ex.getMessage());
                    }
                }
            }
            SwingUtilities.invokeLater(() -> progress.setIndeterminate(false));
            log("Nenhum banco remoto detectado - considere usar SQLite.");
            return null;
        }).start();
    }

    private void onTestar() {
        DbOption opt = (DbOption) cmbDriver.getSelectedItem();
        String url = buildJdbcUrl(opt);
        log("Testando conexao: " + url);
        progress.setIndeterminate(true);
        new SwingWorkerLite<>(() -> {
            try {
                Class.forName(opt.getDriverClass());
                try (Connection c = DriverManager.getConnection(url, txtDbUser.getText(), new String(txtDbPass.getPassword()))) {
                    String v = c.getMetaData().getDatabaseProductVersion();
                    log("Conexao OK - " + c.getMetaData().getDatabaseProductName() + " " + v);
                }
            } catch (Throwable t) {
                log("FALHA: " + t.getMessage());
            }
            SwingUtilities.invokeLater(() -> progress.setIndeterminate(false));
            return null;
        }).start();
    }

    private void onPreparar() {
        btnPreparar.setEnabled(false);
        progress.setIndeterminate(true);
        log("Preparando ambiente ...");
        new SwingWorkerLite<>(() -> {
            try {
                DbOption opt = (DbOption) cmbDriver.getSelectedItem();
                String url = buildJdbcUrl(opt);
                Class.forName(opt.getDriverClass());
                String user = txtDbUser.getText();
                String pass = new String(txtDbPass.getPassword());
                try (Connection c = DriverManager.getConnection(url, user, pass)) {
                    log("Conexao " + url + " aberta.");
                    // 1) migrar schema (Flyway se existir no classpath, senao SQL inline minimo)
                    if (!applyMigrations(c, opt)) {
                        log("Falha na migracao - abortando.");
                        return null;
                    }
                    // 2) gerar / sincronizar hexid
                    String hexId = ensureHexId(c);
                    log("HexID registrado: " + hexId);
                    // 3) atualizar .properties
                    updateProperties(url, user, pass);
                    // 4) gravar marker
                    writeMarker(opt, url, user);
                    log("Ambiente preparado. Clique em 'Entrar no Painel'.");
                    SwingUtilities.invokeLater(() -> btnEntrar.setEnabled(true));
                }
            } catch (Throwable t) {
                log("ERRO: " + t.getMessage());
                t.printStackTrace();
            }
            SwingUtilities.invokeLater(() -> {
                progress.setIndeterminate(false);
                btnPreparar.setEnabled(true);
            });
            return null;
        }).start();
    }

    private void onEntrar() {
        if (standalone) {
            // Modo standalone: nao ha autenticacao. Garante marker + dispara callback.
            try {
                DbOption opt = (DbOption) cmbDriver.getSelectedItem();
                String url = buildJdbcUrl(opt);
                String user = "sqlite".equals(opt.key) ? "" : (txtDbUser.getText().isBlank() ? DEFAULTS.get("user") : txtDbUser.getText());
                writeMarker(opt, url, user);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(parentFrame,
                        "Falha ao gravar marker: " + ex.getMessage(),
                        "Preparar Ambiente", JOptionPane.WARNING_MESSAGE);
                return;
            }
            finished = true;
            if (onComplete != null) {
                try { onComplete.run(); } catch (Throwable t) { /* ignora */ }
            }
            if (parentFrame != null) parentFrame.dispose();
            return;
        }

        String email = txtEmail.getText();
        String senha = new String(txtSenha.getPassword());
        if (!authService.authenticate(email, senha)) {
            JOptionPane.showMessageDialog(parentFrame,
                    "Credenciais invalidas.\nUse brprojeto@l2jbrasil.com / 12345678.",
                    "Login", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // Lembrar credenciais
        prefs.put("email", email);
        prefs.put("senha", senha);
        LauncherApp.setLoggedUserEmail(email);
        LauncherApp.setKey(authService.generateRandomKey());
        authService.loadLicenses(email);
        mainFrame.showDashboardPanel();
    }

    // ====================================================================
    //  Logica de migracao + hexid
    // ====================================================================
    private boolean applyMigrations(Connection c, DbOption opt) throws Exception {
        // Tenta Flyway via reflection (Flyway nao e dependencia direta do game-server-core;
        // quando app-dist empacota db-migrate + Flyway, ele aparece no classpath).
        try {
            Class<?> flywayClass = Class.forName("org.flywaydb.core.Flyway");
            log("Flyway detectado - usando migracao completa.");
            // Equivalente a: Flyway.configure().dataSource(conn).locations(loc).baselineOnMigrate(true).load()
            Object cfg = flywayClass.getMethod("configure").invoke(null);
            cfg = cfg.getClass().getMethod("dataSource", Connection.class).invoke(cfg, c);
            cfg = cfg.getClass().getMethod("locations", String[].class).invoke(cfg, new Object[]{new String[]{opt.flywayLocation()}});
            cfg = cfg.getClass().getMethod("baselineOnMigrate", boolean.class).invoke(cfg, true);
            Object fw = cfg.getClass().getMethod("load").invoke(cfg);
            Object result = fw.getClass().getMethod("migrate").invoke(fw);
            int n = (Integer) result.getClass().getMethod("getMigrationsExecuted").invoke(result);
            log("Flyway aplicou " + n + " migration(s).");
            return true;
        } catch (ClassNotFoundException nf) {
            log("Flyway ausente no classpath - usando SQL inline minimo.");
        } catch (Throwable t) {
            log("Flyway falhou: " + t.getMessage() + " - usando SQL inline.");
        }
        return applyInlineBaseline(c, opt);
    }

    private boolean applyInlineBaseline(Connection c, DbOption opt) {
        try (Statement st = c.createStatement()) {
            // Cria tabela gameservers basica (necessaria para o hexid)
            st.execute("CREATE TABLE IF NOT EXISTS gameservers (" +
                    "server_id INTEGER PRIMARY KEY, " +
                    "hexid VARCHAR(50) NOT NULL DEFAULT '', " +
                    "host VARCHAR(50) NOT NULL DEFAULT '')");
            log("Tabela 'gameservers' garantida.");
            return true;
        } catch (SQLException e) {
            log("Falha criando gameservers: " + e.getMessage());
            return false;
        }
    }

    private String ensureHexId(Connection c) throws Exception {
        String existing = readHexFromDb(c);
        if (existing == null || existing.isBlank()) {
            byte[] bytes = new byte[16];
            new SecureRandom().nextBytes(bytes);
            existing = new java.math.BigInteger(1, bytes).toString(16).toUpperCase();
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO gameservers (server_id, hexid, host) VALUES (1, ?, '127.0.0.1')")) {
                ps.setString(1, existing);
                ps.executeUpdate();
                log("HexID NOVO gerado e gravado no banco.");
            }
        } else {
            log("HexID existente reaproveitado do banco.");
        }
        // Tambem espelha em game/config/hexid.txt e login/config/hexid.txt
        saveHexIdFile("game/config/hexid.txt", existing);
        saveHexIdFile("login/config/hexid.txt", existing);
        return existing;
    }

    private String readHexFromDb(Connection c) {
        try (Statement st = c.createStatement();
             var rs = st.executeQuery("SELECT hexid FROM gameservers WHERE server_id=1")) {
            if (rs.next()) return rs.getString(1);
        } catch (SQLException ignored) {}
        return null;
    }

    private void saveHexIdFile(String path, String hex) throws Exception {
        File f = new File(path);
        f.getParentFile().mkdirs();
        Properties p = new Properties();
        p.setProperty("ServerID", "1");
        p.setProperty("HexID", hex.toUpperCase());
        try (var out = Files.newOutputStream(f.toPath())) {
            p.store(out, "the hexID to auth into login");
        }
    }

    private void updateProperties(String url, String user, String pass) throws Exception {
        upsertProperty("game/config/server.properties", url, user, pass);
        upsertProperty("login/config/loginserver.properties", url, user, pass);
    }

    private void upsertProperty(String path, String url, String user, String pass) throws Exception {
        File f = new File(path);
        f.getParentFile().mkdirs();
        Properties p = new Properties();
        if (f.exists()) {
            try (var in = Files.newInputStream(f.toPath())) {
                p.load(in);
            }
        }
        p.setProperty("URL", url);
        p.setProperty("sql.url", url);
        p.setProperty("Login", user);
        p.setProperty("sql.login", user);
        p.setProperty("Password", pass);
        p.setProperty("sql.password", pass);
        try (var out = Files.newOutputStream(f.toPath())) {
            p.store(out, "brproject");
        }
        log("Atualizado " + path);
    }

    private void writeMarker(DbOption opt, String url, String user) throws Exception {
        File f = new File(MARKER);
        f.getParentFile().mkdirs();
        String content = "driver=" + opt.key + "\n" +
                "url=" + url + "\n" +
                "user=" + user + "\n" +
                "host=" + txtHost.getText() + "\n" +
                "port=" + txtPorta.getText() + "\n" +
                "database=" + txtDatabase.getText() + "\n";
        Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
        log("Marker " + MARKER + " gravado.");
    }

    private String buildJdbcUrl(DbOption opt) {
        if ("sqlite".equals(opt.key)) {
            String db = txtDatabase.getText();
            if (db.isBlank()) db = "data/brproject.db";
            new File(db).getParentFile().mkdirs();
            return "jdbc:sqlite:" + db;
        }
        String host = txtHost.getText();
        String port = txtPorta.getText().isBlank()
                ? ("postgresql".equals(opt.key) ? "5432" : "3306")
                : txtPorta.getText();
        String db   = txtDatabase.getText();
        if ("postgresql".equals(opt.key)) {
            // PostgreSQL JDBC: jdbc:postgresql://host:port/db
            return "jdbc:postgresql://" + host + ":" + port + "/" + db;
        }
        return "jdbc:" + opt.key + "://" + host + ":" + port + "/" + db
                + "?useUnicode=true&characterEncoding=UTF-8"
                + "&allowPublicKeyRetrieval=true&disabledAuthenticationPlugins=GSSAPI";
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()) + "] " + msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    // ====================================================================
    //  Tipos internos
    // ====================================================================
    private static class DbOption {
        final String label;
        final String key;
        DbOption(String label, String key) { this.label = label; this.key = key; }
        String getDriverClass() {
            switch (key) {
                case "mariadb": return "org.mariadb.jdbc.Driver";
                case "mysql":   return "com.mysql.cj.jdbc.Driver";
                case "postgresql": return "org.postgresql.Driver";
                case "sqlite":  return "org.sqlite.JDBC";
            }
            return "org.mariadb.jdbc.Driver";
        }
        String flywayLocation() {
            switch (key) {
                case "sqlite":     return "classpath:brproject-data/migrations/sqlite";
                case "postgresql": return "classpath:brproject-data/migrations/postgresql";
                default:           return "classpath:brproject-data/migrations/mariadb";
            }
        }
        @Override public String toString() { return label; }
    }

    /** SwingWorker bem leve - evita dependencia de generics complicados do Java 17. */
    private static class SwingWorkerLite<T> extends javax.swing.SwingWorker<T, Void> {
        private final java.util.concurrent.Callable<T> task;
        SwingWorkerLite(java.util.concurrent.Callable<T> task) { this.task = task; }
        @Override protected T doInBackground() throws Exception { return task.call(); }
        /** Atalho: executa o SwingWorker no thread-pool do Swing e retorna. */
        SwingWorkerLite<T> start() { this.execute(); return this; }
    }
}
