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
*/
package ext.mods.security.gui;

import ext.mods.commons.gui.CustomTopPanel;
import ext.mods.commons.gui.ModernUI;
import ext.mods.commons.gui.ModernUI.NeonButton;
import ext.mods.commons.gui.services.SiteKtorLogManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;

/**
 * Java Swing Log Viewer for the Site Ktor Web Module.
 * Displays real-time HTTP, API, startup, Netty and system logs with filtering, auto-refresh and clipboard copy.
 */
public class SiteKtorLogViewerDialog {

    private static SiteKtorLogViewerDialog instance;

    private JFrame frame;
    private JTextPane logTextPane;
    private JLabel countLabel;
    private JComboBox<String> levelFilterCombo;
    private JTextField searchField;
    private JCheckBox autoRefreshCheckBox;
    private Timer autoRefreshTimer;

    private final List<LogEntry> cachedLogs = new ArrayList<>();

    public static class LogEntry {
        public long id;
        public String timestamp;
        public String level;
        public String source;
        public String message;

        public LogEntry(long id, String timestamp, String level, String source, String message) {
            this.id = id;
            this.timestamp = timestamp;
            this.level = level;
            this.source = source;
            this.message = message;
        }
    }

    private SiteKtorLogViewerDialog() {}

    public static synchronized SiteKtorLogViewerDialog getInstance() {
        if (instance == null) {
            instance = new SiteKtorLogViewerDialog();
        }
        return instance;
    }

    public void showWindow(JFrame parentFrame) {
        if (frame != null && frame.isVisible()) {
            frame.toFront();
            fetchLogs();
            return;
        }

        frame = new JFrame("Site Ktor — Console de Logs");
        frame.setSize(920, 620);
        frame.setMinimumSize(new Dimension(720, 480));
        frame.setUndecorated(true);
        frame.setLocationRelativeTo(parentFrame);
        frame.getContentPane().setBackground(ModernUI.BG_DARK);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(ModernUI.BG_DARK);

        // Header Panel with CustomTopPanel
        CustomTopPanel topPanel = new CustomTopPanel(frame, null, () -> frame.dispose(), true, null);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Content Container
        JPanel contentPanel = new JPanel(new BorderLayout(0, 8));
        contentPanel.setOpaque(false);
        contentPanel.setBorder(new EmptyBorder(10, 15, 15, 15));

        // Toolbar Panel
        JPanel toolbar = new JPanel(new BorderLayout(10, 0));
        toolbar.setOpaque(false);

        // Left Controls: Filter dropdown & Search
        JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftControls.setOpaque(false);

        JLabel lblFilter = new JLabel("Nível:");
        lblFilter.setForeground(ModernUI.TEXT_WHITE);
        lblFilter.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        levelFilterCombo = new JComboBox<>(new String[]{"TODOS OS NÍVEIS", "INFO", "WARN", "ERROR"});
        levelFilterCombo.setBackground(new Color(15, 23, 42));
        levelFilterCombo.setForeground(ModernUI.TEXT_WHITE);
        levelFilterCombo.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        levelFilterCombo.addActionListener(e -> renderLogs());

        searchField = new JTextField(16);
        searchField.setBackground(new Color(15, 23, 42));
        searchField.setForeground(ModernUI.TEXT_WHITE);
        searchField.setCaretColor(ModernUI.TEXT_WHITE);
        searchField.setFont(new Font("Consolas", Font.PLAIN, 12));
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85)),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)
        ));
        searchField.addActionListener(e -> renderLogs());
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { renderLogs(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { renderLogs(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { renderLogs(); }
        });

        leftControls.add(lblFilter);
        leftControls.add(levelFilterCombo);
        leftControls.add(new JLabel("  Busca:"));
        leftControls.add(searchField);

        // Right Controls: Refresh, Copy, Clear, Auto-Refresh
        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightControls.setOpaque(false);

        autoRefreshCheckBox = new JCheckBox("Auto-refresh (3s)", true);
        autoRefreshCheckBox.setOpaque(false);
        autoRefreshCheckBox.setForeground(ModernUI.TEXT_WHITE);
        autoRefreshCheckBox.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        autoRefreshCheckBox.addActionListener(e -> toggleAutoRefresh());

        NeonButton btnRefresh = new NeonButton("Refresh", ModernUI.NEON_CYAN, ModernUI.NEON_BLUE, true, null);
        btnRefresh.setPreferredSize(new Dimension(85, 26));
        btnRefresh.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnRefresh.addActionListener(e -> fetchLogs());

        NeonButton btnCopy = new NeonButton("Copiar", ModernUI.NEON_BLUE, ModernUI.NEON_PURPLE, true, null);
        btnCopy.setPreferredSize(new Dimension(75, 26));
        btnCopy.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnCopy.addActionListener(e -> copyLogsToClipboard());

        NeonButton btnClear = new NeonButton("Limpar", ModernUI.NEON_PURPLE, ModernUI.NEON_CYAN, true, null);
        btnClear.setPreferredSize(new Dimension(75, 26));
        btnClear.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnClear.addActionListener(e -> clearRemoteLogs());

        rightControls.add(autoRefreshCheckBox);
        rightControls.add(btnRefresh);
        rightControls.add(btnCopy);
        rightControls.add(btnClear);

        toolbar.add(leftControls, BorderLayout.WEST);
        toolbar.add(rightControls, BorderLayout.EAST);
        contentPanel.add(toolbar, BorderLayout.NORTH);

        // Log Console Area
        logTextPane = new JTextPane();
        logTextPane.setEditable(false);
        logTextPane.setBackground(new Color(2, 6, 23)); // Slate-950
        logTextPane.setFont(new Font("Consolas", Font.PLAIN, 12));
        logTextPane.setMargin(new Insets(8, 10, 8, 10));

        JScrollPane scrollPane = new JScrollPane(logTextPane);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(30, 41, 59)));
        scrollPane.getViewport().setBackground(new Color(2, 6, 23));
        scrollPane.getVerticalScrollBar().setUI(new ModernUI.ModernScrollBarUI());
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));

        contentPanel.add(scrollPane, BorderLayout.CENTER);

        // Footer Bar
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(4, 2, 0, 2));

        countLabel = new JLabel("Total de registros: 0");
        countLabel.setForeground(ModernUI.NEON_CYAN);
        countLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));

        footer.add(countLabel, BorderLayout.WEST);
        contentPanel.add(footer, BorderLayout.SOUTH);

        mainPanel.add(contentPanel, BorderLayout.CENTER);
        frame.setContentPane(mainPanel);
        frame.setVisible(true);

        fetchLogs();
        startAutoRefreshTimer();

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                stopAutoRefreshTimer();
            }
        });
    }

    private void toggleAutoRefresh() {
        if (autoRefreshCheckBox.isSelected()) {
            startAutoRefreshTimer();
        } else {
            stopAutoRefreshTimer();
        }
    }

    private void startAutoRefreshTimer() {
        stopAutoRefreshTimer();
        autoRefreshTimer = new Timer(3000, e -> fetchLogs());
        autoRefreshTimer.start();
    }

    private void stopAutoRefreshTimer() {
        if (autoRefreshTimer != null) {
            autoRefreshTimer.stop();
            autoRefreshTimer = null;
        }
    }

    private void fetchLogs() {
        SwingUtilities.invokeLater(() -> {
            try {
                List<SiteKtorLogManager.LogEntry> entries = SiteKtorLogManager.getInstance().getLogs();
                cachedLogs.clear();
                for (SiteKtorLogManager.LogEntry item : entries) {
                    cachedLogs.add(new LogEntry(item.getId(), item.getTimestamp(), item.getLevel(), item.getSource(), item.getMessage()));
                }
                renderLogs();
            } catch (Exception ignored) {}
        });
    }

    private void renderLogs() {
        if (logTextPane == null) return;

        String levelFilter = levelFilterCombo != null ? (String) levelFilterCombo.getSelectedItem() : "TODOS OS NÍVEIS";
        String searchText = searchField != null ? searchField.getText().trim().toLowerCase() : "";

        StyledDocument doc = logTextPane.getStyledDocument();
        try {
            doc.remove(0, doc.getLength());
        } catch (BadLocationException ignored) {}

        // Style Definitions
        Style defaultStyle = logTextPane.addStyle("Default", null);
        StyleConstants.setFontFamily(defaultStyle, "Consolas");
        StyleConstants.setFontSize(defaultStyle, 12);
        StyleConstants.setForeground(defaultStyle, new Color(226, 232, 240));

        Style timeStyle = logTextPane.addStyle("Time", defaultStyle);
        StyleConstants.setForeground(timeStyle, new Color(100, 116, 139));

        Style infoStyle = logTextPane.addStyle("INFO", defaultStyle);
        StyleConstants.setForeground(infoStyle, new Color(56, 189, 248)); // Cyan
        StyleConstants.setBold(infoStyle, true);

        Style warnStyle = logTextPane.addStyle("WARN", defaultStyle);
        StyleConstants.setForeground(warnStyle, new Color(251, 191, 36)); // Amber
        StyleConstants.setBold(warnStyle, true);

        Style errorStyle = logTextPane.addStyle("ERROR", defaultStyle);
        StyleConstants.setForeground(errorStyle, new Color(248, 113, 113)); // Rose Red
        StyleConstants.setBold(errorStyle, true);

        Style sourceStyle = logTextPane.addStyle("Source", defaultStyle);
        StyleConstants.setForeground(sourceStyle, new Color(148, 163, 184));
        StyleConstants.setBold(sourceStyle, true);

        int count = 0;
        for (LogEntry entry : cachedLogs) {
            if (!"TODOS OS NÍVEIS".equals(levelFilter) && !entry.level.equalsIgnoreCase(levelFilter)) {
                continue;
            }
            if (!searchText.isEmpty()) {
                String fullText = (entry.timestamp + " " + entry.level + " " + entry.source + " " + entry.message).toLowerCase();
                if (!fullText.contains(searchText)) {
                    continue;
                }
            }

            count++;
            try {
                doc.insertString(doc.getLength(), "[" + entry.timestamp + "] ", timeStyle);

                Style lvlStyle = infoStyle;
                if ("ERROR".equalsIgnoreCase(entry.level)) lvlStyle = errorStyle;
                else if ("WARN".equalsIgnoreCase(entry.level)) lvlStyle = warnStyle;

                doc.insertString(doc.getLength(), "[" + entry.level + "] ", lvlStyle);
                doc.insertString(doc.getLength(), "[" + entry.source + "] ", sourceStyle);
                doc.insertString(doc.getLength(), entry.message + "\n", defaultStyle);
            } catch (BadLocationException ignored) {}
        }

        if (countLabel != null) {
            countLabel.setText("Total de registros: " + count);
        }

        logTextPane.setCaretPosition(doc.getLength());
    }

    private void copyLogsToClipboard() {
        StringBuilder sb = new StringBuilder();
        for (LogEntry entry : cachedLogs) {
            sb.append("[").append(entry.timestamp).append("] [")
              .append(entry.level).append("] [")
              .append(entry.source).append("] ")
              .append(entry.message).append("\n");
        }
        StringSelection selection = new StringSelection(sb.toString());
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, selection);
        JOptionPane.showMessageDialog(frame, "Logs copiados para a área de transferência com sucesso!", "Logs do Ktor", JOptionPane.INFORMATION_MESSAGE);
    }

    private void clearRemoteLogs() {
        SiteKtorLogManager.getInstance().clear();
        fetchLogs();
    }
}
