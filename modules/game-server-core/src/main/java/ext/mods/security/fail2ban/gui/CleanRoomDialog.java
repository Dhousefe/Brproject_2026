/*
 * Copyleft © 2024-2026 L2Brproject
 */
package ext.mods.security.fail2ban.gui;

import ext.mods.commons.gui.ModernUI;
import ext.mods.security.fail2ban.simd.CleanRoomManager;
import ext.mods.security.fail2ban.simd.PacketVector128;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Clean Room (Area Limpa) & Golden Profile Management Modal.
 * Allows administrators to designate verified IPs, monitor packet collection,
 * freeze the baseline model against data poisoning, and test SIMD anomaly scores.
 */
public class CleanRoomDialog extends JDialog {
    private final CleanRoomManager cleanRoom = CleanRoomManager.getInstance();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss dd/MM");

    private JLabel statusBadge;
    private JLabel samplesLabel;
    private JLabel anomaliesLabel;
    private DefaultTableModel ipTableModel;
    private JTable ipTable;
    private JTextField newIpField;
    private JTextField notesField;
    private JTextField testIpField;
    private JLabel testResultLabel;

    public CleanRoomDialog(JFrame parent, String prefilledIp) {
        super(parent, "Area Limpa & Golden Profile (SIMD AVX2)", true);
        setSize(880, 620);
        setLocationRelativeTo(parent);
        getContentPane().setBackground(ModernUI.BG_DARK);
        setLayout(new BorderLayout(10, 10));

        // Top Header Banner
        add(createHeaderPanel(), BorderLayout.NORTH);

        // Center: IP Management & Baseline Telemetry
        add(createCenterPanel(), BorderLayout.CENTER);

        // Bottom: Action Controls
        add(createBottomPanel(), BorderLayout.SOUTH);

        if (prefilledIp != null && !prefilledIp.isBlank()) {
            newIpField.setText(prefilledIp.trim());
            testIpField.setText(prefilledIp.trim());
        }

        refreshData();
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(ModernUI.BG_PANEL);
        panel.setBorder(new EmptyBorder(12, 16, 12, 16));

        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titlePanel.setOpaque(false);

        JLabel title = new JLabel("Area Limpa (Clean Room) & Perfil Dourado");
        title.setFont(new Font("Segoe UI", Font.BOLD, 15));
        title.setForeground(ModernUI.NEON_CYAN);
        titlePanel.add(title);

        panel.add(titlePanel, BorderLayout.WEST);

        // Status & Stats
        JPanel statsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        statsPanel.setOpaque(false);

        statusBadge = new JLabel("Status: Calibrando");
        statusBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        statusBadge.setForeground(ModernUI.NEON_PURPLE);
        statsPanel.add(statusBadge);

        samplesLabel = new JLabel("Amostras: 0");
        samplesLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        samplesLabel.setForeground(ModernUI.TEXT_WHITE);
        statsPanel.add(samplesLabel);

        anomaliesLabel = new JLabel("Anomalias: 0");
        anomaliesLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        anomaliesLabel.setForeground(new Color(255, 100, 120));
        statsPanel.add(anomaliesLabel);

        panel.add(statsPanel, BorderLayout.EAST);
        return panel;
    }

    private JPanel createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(0, 14, 0, 14));

        // Add IP Bar
        JPanel addPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        addPanel.setBackground(ModernUI.BG_PANEL);
        addPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ModernUI.NEON_PURPLE, 1),
                " Promover IP para Área Limpa ",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 11),
                ModernUI.NEON_CYAN
        ));

        addPanel.add(new JLabel("IP:") {{ setForeground(ModernUI.TEXT_WHITE); }});
        newIpField = new JTextField(14);
        styleTextField(newIpField);
        addPanel.add(newIpField);

        addPanel.add(new JLabel("Notas/Identificador:") {{ setForeground(ModernUI.TEXT_WHITE); }});
        notesField = new JTextField(18);
        styleTextField(notesField);
        addPanel.add(notesField);

        JButton addBtn = createStyledButton("Adicionar à Área Limpa", new Color(30, 120, 60), Color.WHITE);
        addBtn.addActionListener(e -> {
            String ip = newIpField.getText().trim();
            String notes = notesField.getText().trim();
            if (ip.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Informe um endereço IP válido.", "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }
            cleanRoom.addCleanIp(ip, notes.isEmpty() ? "Adicionado manualmente" : notes);
            newIpField.setText("");
            notesField.setText("");
            refreshData();
        });
        addPanel.add(addBtn);

        panel.add(addPanel, BorderLayout.NORTH);

        // Table
        String[] cols = {"IP Verificado", "Data de Inclusão", "Notas / Descrição", "Pacotes Capturados"};
        ipTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        ipTable = new JTable(ipTableModel);
        styleTable(ipTable);

        JScrollPane scroll = new JScrollPane(ipTable);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getViewport().setBackground(ModernUI.BG_DARK);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(40, 35, 55), 1));
        scroll.getVerticalScrollBar().setUI(new ModernUI.ModernScrollBarUI());

        panel.add(scroll, BorderLayout.CENTER);

        // Test Anomaly Panel
        JPanel testPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        testPanel.setBackground(ModernUI.BG_PANEL);
        testPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(60, 50, 85), 1),
                " Teste de Inferência SIMD AVX2 ",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 11),
                ModernUI.NEON_CYAN
        ));

        testPanel.add(new JLabel("Testar IP:") {{ setForeground(ModernUI.TEXT_WHITE); }});
        testIpField = new JTextField(14);
        styleTextField(testIpField);
        testPanel.add(testIpField);

        JButton testBtn = createStyledButton("Avaliar Vetor SIMD", ModernUI.NEON_PURPLE, Color.WHITE);
        testBtn.addActionListener(e -> runSimdTest());
        testPanel.add(testBtn);

        testResultLabel = new JLabel("Aguardando teste...");
        testResultLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        testResultLabel.setForeground(Color.LIGHT_GRAY);
        testPanel.add(testResultLabel);

        panel.add(testPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        panel.setBackground(ModernUI.BG_DARK);

        JButton removeBtn = createStyledButton("Remover Selecionado", new Color(120, 30, 40), Color.WHITE);
        removeBtn.addActionListener(e -> {
            int row = ipTable.getSelectedRow();
            if (row >= 0) {
                String ip = (String) ipTableModel.getValueAt(row, 0);
                cleanRoom.removeCleanIp(ip);
                refreshData();
            } else {
                JOptionPane.showMessageDialog(this, "Selecione um IP na tabela para remover.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        panel.add(removeBtn);

        JButton freezeBtn = createStyledButton(cleanRoom.isFrozen() ? "Descongelar Modelo" : "Congelar Perfil (Anti-Poisoning)", 
            new Color(80, 50, 130), Color.WHITE);
        freezeBtn.addActionListener(e -> {
            if (cleanRoom.isFrozen()) {
                cleanRoom.unfreezeProfile();
                freezeBtn.setText("Congelar Perfil (Anti-Poisoning)");
            } else {
                cleanRoom.freezeProfile();
                freezeBtn.setText("Descongelar Modelo");
            }
            refreshData();
        });
        panel.add(freezeBtn);

        JButton resetBtn = createStyledButton("Resetar Baseline", new Color(60, 60, 75), Color.LIGHT_GRAY);
        resetBtn.addActionListener(e -> {
            int res = JOptionPane.showConfirmDialog(this, 
                "Tem certeza que deseja zerar todas as amostras do Perfil Dourado?", 
                "Confirmar Reset", JOptionPane.YES_NO_OPTION);
            if (res == JOptionPane.YES_OPTION) {
                cleanRoom.resetProfile();
                refreshData();
            }
        });
        panel.add(resetBtn);

        JButton closeBtn = createStyledButton("Fechar", new Color(40, 38, 55), ModernUI.NEON_CYAN);
        closeBtn.addActionListener(e -> dispose());
        panel.add(closeBtn);

        return panel;
    }

    private void runSimdTest() {
        String ip = testIpField.getText().trim();
        if (ip.isEmpty()) {
            testResultLabel.setText("Informe um IP para testar.");
            testResultLabel.setForeground(Color.ORANGE);
            return;
        }

        // Sintetiza um pacote de teste com base na origem
        PacketVector128 testVector = PacketVector128.synthesizeTcp(ip, 128, 0x0E, 25L, 64240);
        long start = System.nanoTime();
        CleanRoomManager.AnomalyDecision dec = cleanRoom.processPacket(testVector);
        long elapsedNanos = System.nanoTime() - start;

        if (dec.isAnomaly()) {
            testResultLabel.setText(String.format("[ALERTA] ANOMALIA DETECTADA em %d ns! Z=%.2f | Dist=%.3f (%s)", 
                elapsedNanos, dec.zScore(), dec.cosineDist(), dec.reason()));
            testResultLabel.setForeground(new Color(255, 80, 100));
        } else {
            testResultLabel.setText(String.format("[OK] PADRAO NORMAL (%d ns) - Z=%.2f | Dist=%.3f (%s)", 
                elapsedNanos, dec.zScore(), dec.cosineDist(), dec.reason()));
            testResultLabel.setForeground(new Color(80, 255, 140));
        }
    }

    private void refreshData() {
        CleanRoomManager.ProfileState state = cleanRoom.getState();
        statusBadge.setText("Status: " + state.getLabel());
        if (state == CleanRoomManager.ProfileState.CALIBRATED) {
            statusBadge.setForeground(new Color(80, 255, 140));
        } else if (state == CleanRoomManager.ProfileState.FROZEN) {
            statusBadge.setForeground(ModernUI.NEON_PURPLE);
        } else {
            statusBadge.setForeground(ModernUI.NEON_CYAN);
        }

        samplesLabel.setText("Amostras: " + cleanRoom.getTotalSamplesCollected());
        anomaliesLabel.setText("Anomalias: " + cleanRoom.getTotalAnomaliesDetected());

        ipTableModel.setRowCount(0);
        List<CleanRoomManager.CleanIpEntry> ips = cleanRoom.getCleanIps();
        for (CleanRoomManager.CleanIpEntry entry : ips) {
            ipTableModel.addRow(new Object[]{
                    entry.ip(),
                    dateFormat.format(new Date(entry.addedAt())),
                    entry.notes(),
                    entry.packetCount().get()
            });
        }
    }

    private void styleTextField(JTextField tf) {
        tf.setBackground(ModernUI.BG_DARK);
        tf.setForeground(ModernUI.TEXT_WHITE);
        tf.setCaretColor(ModernUI.NEON_CYAN);
        tf.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 50, 85), 1),
                new EmptyBorder(3, 6, 3, 6)
        ));
    }

    private void styleTable(JTable table) {
        table.setBackground(ModernUI.BG_DARK);
        table.setForeground(ModernUI.TEXT_WHITE);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        table.setRowHeight(24);
        table.setGridColor(new Color(40, 35, 55));
        table.setShowVerticalLines(false);
        table.setSelectionBackground(new Color(70, 35, 120));
        table.setSelectionForeground(Color.WHITE);

        table.getTableHeader().setBackground(new Color(25, 22, 35));
        table.getTableHeader().setForeground(ModernUI.NEON_CYAN);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
        table.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ModernUI.NEON_PURPLE));
    }

    private JButton createStyledButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(bg.brighter(), 1),
                new EmptyBorder(5, 12, 5, 12)
        ));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
}
