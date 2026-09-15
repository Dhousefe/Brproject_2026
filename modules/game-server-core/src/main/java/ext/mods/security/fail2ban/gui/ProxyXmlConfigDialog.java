package ext.mods.security.fail2ban.gui;

import ext.mods.commons.gui.ModernUI;
import ext.mods.commons.gui.services.ProcessManagerService;
import ext.mods.security.fail2ban.core.SecurityConfigManager;
import ext.mods.security.fail2ban.core.SecurityConfigManager.XmlValidationResult;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.awt.*;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Modal dialog for dynamic inspection, editing, and validation of proxy.xml.
 * Provides a structured visual route inspector and a full direct XML editor with SAX validation,
 * automatic backups, port collision guard, and live proxy reload.
 */
public class ProxyXmlConfigDialog extends JDialog {

    private final JTextArea xmlEditorArea;
    private final JLabel validationLabel;
    private final DefaultTableModel routesTableModel;
    private final JTable routesTable;
    private final Path proxyXmlPath;
    private final Runnable onSaveCallback;

    public ProxyXmlConfigDialog(Frame parent, Runnable onSaveCallback) {
        super(parent, "Configurador Dinâmico de Rotas & Segurança: proxy.xml", true);
        this.onSaveCallback = onSaveCallback;
        this.proxyXmlPath = SecurityConfigManager.getInstance().getProxyXmlPath();

        setSize(920, 660);
        setLocationRelativeTo(parent);
        getContentPane().setBackground(ModernUI.BG_DARK);
        setLayout(new BorderLayout());

        // Header Panel
        JPanel header = new JPanel(new BorderLayout(10, 4));
        header.setBackground(ModernUI.BG_PANEL);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 35, 55)),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));

        JLabel title = new JLabel("GERENCIADOR DE ROTAS & FIREWALL (proxy.xml)");
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        title.setForeground(ModernUI.NEON_CYAN);
        header.add(title, BorderLayout.WEST);

        JLabel pathLabel = new JLabel("Arquivo: " + proxyXmlPath.toString());
        pathLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
        pathLabel.setForeground(new Color(160, 160, 185));
        header.add(pathLabel, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // Center: Tabs (Route Overview + Raw XML Editor)
        JTabbedPane tabs = new JTabbedPane();
        tabs.setUI(new ModernUI.ModernTabbedPaneUI());
        tabs.setBackground(ModernUI.BG_DARK);
        tabs.setForeground(ModernUI.TEXT_WHITE);
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 11));

        // Tab 1: Visual Routes Overview
        String[] columns = {"Rota", "Tipo", "Status", "Bind (Host:Porta)", "Destino (Target)", "Max Conexões/min", "Max Reqs/min"};
        routesTableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        routesTable = new JTable(routesTableModel);
        styleRoutesTable(routesTable);
        JScrollPane routesScroll = new JScrollPane(routesTable);
        routesScroll.getViewport().setBackground(ModernUI.BG_DARK);
        routesScroll.setBorder(BorderFactory.createLineBorder(new Color(40, 35, 55), 1));

        JPanel routesPanel = new JPanel(new BorderLayout(0, 8));
        routesPanel.setBackground(ModernUI.BG_DARK);
        routesPanel.setBorder(new EmptyBorder(10, 12, 10, 12));
        
        JLabel routesDesc = new JLabel("Rotas ativas e regras de mitigação extraídas do arquivo proxy.xml:");
        routesDesc.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        routesDesc.setForeground(new Color(180, 180, 200));
        routesPanel.add(routesDesc, BorderLayout.NORTH);
        routesPanel.add(routesScroll, BorderLayout.CENTER);
        tabs.addTab("  Resumo das Rotas  ", routesPanel);

        // Tab 2: Raw XML Editor
        JPanel editorPanel = new JPanel(new BorderLayout(0, 6));
        editorPanel.setBackground(ModernUI.BG_DARK);
        editorPanel.setBorder(new EmptyBorder(10, 12, 6, 12));

        xmlEditorArea = new JTextArea();
        xmlEditorArea.setBackground(new Color(13, 11, 20));
        xmlEditorArea.setForeground(new Color(226, 232, 240));
        xmlEditorArea.setCaretColor(ModernUI.NEON_CYAN);
        xmlEditorArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        xmlEditorArea.setTabSize(2);

        JScrollPane editorScroll = new JScrollPane(xmlEditorArea);
        editorScroll.getViewport().setBackground(new Color(13, 11, 20));
        editorScroll.setBorder(BorderFactory.createLineBorder(new Color(50, 45, 70), 1));
        editorPanel.add(editorScroll, BorderLayout.CENTER);

        // Validation status strip
        validationLabel = new JLabel("Clique em 'Validar XML' para verificar a integridade da sintaxe.");
        validationLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        validationLabel.setForeground(new Color(140, 140, 160));
        validationLabel.setBorder(new EmptyBorder(4, 4, 4, 4));
        editorPanel.add(validationLabel, BorderLayout.SOUTH);

        tabs.addTab("  Editor XML Direto  ", editorPanel);
        add(tabs, BorderLayout.CENTER);

        // Footer Actions
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        footer.setBackground(ModernUI.BG_PANEL);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(40, 35, 55)));

        JButton validateBtn = createActionButton("Validar XML", new Color(30, 28, 42), ModernUI.NEON_CYAN);
        validateBtn.addActionListener(e -> runValidation());
        footer.add(validateBtn);

        JButton reloadBtn = createActionButton("Recarregar do Disco", new Color(30, 28, 42), new Color(200, 200, 220));
        reloadBtn.addActionListener(e -> reloadFromDisk());
        footer.add(reloadBtn);

        JButton saveBtn = createActionButton("Salvar & Aplicar", new Color(15, 80, 50), new Color(140, 255, 180));
        saveBtn.addActionListener(e -> saveAndApply());
        footer.add(saveBtn);

        JButton closeBtn = createActionButton("Fechar", new Color(30, 28, 42), ModernUI.NEON_CYAN);
        closeBtn.addActionListener(e -> dispose());
        footer.add(closeBtn);

        add(footer, BorderLayout.SOUTH);

        // Load initial content
        reloadFromDisk();
    }

    private void reloadFromDisk() {
        try {
            String content = SecurityConfigManager.getInstance().readProxyXmlContent();
            xmlEditorArea.setText(content);
            xmlEditorArea.setCaretPosition(0);
            parseAndPopulateRoutesTable(content);
            validationLabel.setText("Conteúdo carregado com sucesso do disco.");
            validationLabel.setForeground(new Color(140, 220, 160));
        } catch (Exception e) {
            validationLabel.setText("Erro ao ler arquivo do disco: " + e.getMessage());
            validationLabel.setForeground(new Color(255, 100, 120));
        }
    }

    private boolean runValidation() {
        String xml = xmlEditorArea.getText();
        XmlValidationResult res = SecurityConfigManager.validateXmlSyntax(xml);
        if (res.isValid()) {
            StringBuilder sb = new StringBuilder("[OK] Sintaxe XML 100% Valida!");
            if (!res.warnings().isEmpty()) {
                sb.append(" (Avisos: ").append(String.join("; ", res.warnings())).append(")");
                validationLabel.setForeground(new Color(255, 183, 3));
            } else {
                validationLabel.setForeground(new Color(6, 214, 160));
            }
            validationLabel.setText(sb.toString());
            parseAndPopulateRoutesTable(xml);
            return true;
        } else {
            validationLabel.setText("[ERRO] Sintaxe Invalida (Linha " + res.lineNumber() + ", Coluna " + res.columnNumber() + "): " + res.errorMessage());
            validationLabel.setForeground(new Color(255, 77, 109));
            return false;
        }
    }

    private void saveAndApply() {
        if (!runValidation()) {
            JOptionPane.showMessageDialog(this,
                "Não é possível salvar: O XML contém erros de sintaxe!\n\n" + validationLabel.getText(),
                "Erro de Validação", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String xml = xmlEditorArea.getText();
        SecurityConfigManager scm = SecurityConfigManager.getInstance();
        XmlValidationResult saveRes = scm.saveProxyXmlContentSafe(xml);

        if (saveRes.isValid()) {
            StringBuilder msg = new StringBuilder("Configurações salvas com sucesso em:\n")
                .append(proxyXmlPath.toString())
                .append("\n\nBackup automático criado: proxy.xml.bak");

            if (!saveRes.warnings().isEmpty()) {
                msg.append("\n\nAvisos:\n");
                for (String w : saveRes.warnings()) {
                    msg.append("- ").append(w).append("\n");
                }
            }

            // Check if Native Proxy is currently running
            ProcessManagerService pms = ProcessManagerService.getInstance();
            if (pms.isNativeProxyRunning()) {
                msg.append("\n\nO Proxy Nativo está em execução. Deseja reiniciá-lo agora para aplicar as novas rotas?");
                int choice = JOptionPane.showConfirmDialog(this, msg.toString(), "Sucesso & Reinício do Proxy",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (choice == JOptionPane.YES_OPTION) {
                    scm.restartProxyIfRunning();
                    JOptionPane.showMessageDialog(this, "Processo do Proxy Nativo reiniciado em background.", "Proxy Reiniciado", JOptionPane.INFORMATION_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this, msg.toString(), "Configuração Salva", JOptionPane.INFORMATION_MESSAGE);
            }

            if (onSaveCallback != null) {
                onSaveCallback.run();
            }
        } else {
            JOptionPane.showMessageDialog(this, "Falha ao salvar XML:\n" + saveRes.errorMessage(), "Erro de Escrita", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void parseAndPopulateRoutesTable(String xml) {
        routesTableModel.setRowCount(0);
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));

            NodeList routeList = doc.getElementsByTagName("route");
            for (int i = 0; i < routeList.getLength(); i++) {
                Element r = (Element) routeList.item(i);
                String name = r.getAttribute("name");
                String type = r.getAttribute("type").toUpperCase();
                String enabledStr = r.hasAttribute("enabled") ? r.getAttribute("enabled") : "true";
                boolean enabled = Boolean.parseBoolean(enabledStr);
                String bind = r.getAttribute("bindHost") + ":" + r.getAttribute("bindPort");
                String target = r.getAttribute("targetHost") + ":" + r.getAttribute("targetPort");

                String maxConn = "—";
                String maxReq = "—";
                NodeList limiters = r.getElementsByTagName("rateLimiter");
                if (limiters.getLength() > 0) {
                    Element lim = (Element) limiters.item(0);
                    maxConn = lim.getAttribute("maxConnections");
                    maxReq = lim.getAttribute("maxRequests");
                }

                routesTableModel.addRow(new Object[]{
                    name,
                    type,
                    enabled ? "ATIVO" : "DESATIVADO",
                    bind,
                    target,
                    maxConn,
                    maxReq
                });
            }
        } catch (Exception ignored) {
        }
    }

    private void styleRoutesTable(JTable table) {
        table.setBackground(ModernUI.BG_DARK);
        table.setForeground(ModernUI.TEXT_WHITE);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        table.setRowHeight(24);
        table.setSelectionBackground(ModernUI.NEON_PURPLE);
        table.setSelectionForeground(Color.WHITE);
        table.setGridColor(new Color(30, 28, 42));

        table.getTableHeader().setBackground(ModernUI.BG_PANEL);
        table.getTableHeader().setForeground(ModernUI.NEON_CYAN);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));

        for (int col = 0; col < table.getColumnCount(); col++) {
            int alignment = (col == 1 || col == 2 || col == 5 || col == 6) ? SwingConstants.CENTER : SwingConstants.LEFT;
            DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
            renderer.setHorizontalAlignment(alignment);
            final int c = col;
            table.getColumnModel().getColumn(col).setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable tbl, Object val, boolean sel, boolean foc, int row, int column) {
                    Component comp = super.getTableCellRendererComponent(tbl, val, sel, foc, row, column);
                    setHorizontalAlignment(alignment);
                    if (sel) {
                        comp.setBackground(ModernUI.NEON_PURPLE);
                        comp.setForeground(Color.WHITE);
                    } else {
                        comp.setBackground(row % 2 == 0 ? ModernUI.BG_DARK : new Color(18, 16, 25));
                        comp.setForeground(ModernUI.TEXT_WHITE);

                        if (c == 2 && val != null) {
                            setFont(getFont().deriveFont(Font.BOLD));
                            if ("ATIVO".equals(val.toString())) {
                                comp.setForeground(new Color(6, 214, 160));
                            } else {
                                comp.setForeground(new Color(255, 77, 109));
                            }
                        } else if (c == 1 && val != null) {
                            setFont(getFont().deriveFont(Font.BOLD));
                            if (val.toString().contains("TCP")) {
                                comp.setForeground(ModernUI.NEON_CYAN);
                            } else if (val.toString().contains("HTTP")) {
                                comp.setForeground(new Color(199, 125, 255));
                            }
                        }
                    }
                    return comp;
                }
            });
        }
    }

    private JButton createActionButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(bg.brighter(), 1),
            BorderFactory.createEmptyBorder(6, 14, 6, 14)
        ));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
}
