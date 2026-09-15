package ext.mods.security.fail2ban.gui;

import ext.mods.commons.gui.ComponentResizer;
import ext.mods.commons.gui.ModernUI;
import ext.mods.security.fail2ban.simd.CleanRoomManager;
import ext.mods.security.fail2ban.simd.CleanRoomManager.CapturedPacketSample;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Interactive inspection and management dialog for packets captured by the Netty Reverse Proxy and Game Server.
 * Allows security administrators to audit captured traffic, select which samples serve as the Golden Profile
 * baseline, remove anomalous/noisy packets, and recalculate model centroids in real-time.
 */
public class CapturedPacketsDialog extends JDialog {

    private final DefaultTableModel tableModel;
    private final JTable table;
    private final TableRowSorter<DefaultTableModel> rowSorter;
    private final JTextField searchField;
    private final JLabel countLabel;
    private final Runnable onModelUpdated;

    public CapturedPacketsDialog(Window parent, Runnable onModelUpdated) {
        super(parent, "Inspecao de Pacotes Capturados - Golden Profile", ModalityType.APPLICATION_MODAL);
        this.onModelUpdated = onModelUpdated;

        setSize(980, 600);
        setMinimumSize(new Dimension(800, 450));
        setLocationRelativeTo(parent);
        setUndecorated(true);

        // Allow user mouse resizing
        new ComponentResizer(this);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ModernUI.BG_DARK);
        root.setBorder(BorderFactory.createLineBorder(new Color(60, 45, 90), 1));
        setContentPane(root);

        // Header Panel with title and close button
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(ModernUI.BG_PANEL);
        headerPanel.setBorder(new EmptyBorder(10, 14, 10, 14));

        JLabel titleLabel = new JLabel("[Area Limpa] Auditoria de Pacotes Capturados");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setForeground(ModernUI.NEON_CYAN);
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JButton closeBtn = new JButton("X");
        closeBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        closeBtn.setForeground(Color.LIGHT_GRAY);
        closeBtn.setBackground(new Color(40, 35, 55));
        closeBtn.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        closeBtn.setFocusPainted(false);
        closeBtn.addActionListener(e -> dispose());
        headerPanel.add(closeBtn, BorderLayout.EAST);

        root.add(headerPanel, BorderLayout.NORTH);

        // Center: Search bar + Table
        JPanel centerPanel = new JPanel(new BorderLayout(0, 8));
        centerPanel.setOpaque(false);
        centerPanel.setBorder(new EmptyBorder(10, 14, 10, 14));

        // Toolbar: Search filter + Count label
        JPanel toolBar = new JPanel(new BorderLayout(8, 0));
        toolBar.setOpaque(false);

        JPanel searchBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        searchBox.setOpaque(false);
        JLabel searchLbl = new JLabel("Filtrar:");
        searchLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        searchLbl.setForeground(ModernUI.TEXT_WHITE);
        searchBox.add(searchLbl);

        searchField = new JTextField(20);
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        searchField.setBackground(new Color(30, 25, 45));
        searchField.setForeground(Color.WHITE);
        searchField.setCaretColor(Color.WHITE);
        searchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(70, 55, 100), 1),
            new EmptyBorder(3, 6, 3, 6)
        ));
        searchBox.add(searchField);
        toolBar.add(searchBox, BorderLayout.WEST);

        countLabel = new JLabel("Total de Pacotes: 0");
        countLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        countLabel.setForeground(ModernUI.NEON_CYAN);
        toolBar.add(countLabel, BorderLayout.EAST);

        centerPanel.add(toolBar, BorderLayout.NORTH);

        // Table
        String[] columnNames = {"[X] Modelo", "ID", "Horario", "IP Origem", "Protocolo", "Detalhes / URI / Opcode"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 0) return Boolean.class;
                if (columnIndex == 1) return Long.class;
                return String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0; // Only checkbox is editable
            }
        };

        table = new JTable(tableModel);
        rowSorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(rowSorter);

        // Table Styling
        table.setBackground(ModernUI.BG_DARK);
        table.setForeground(ModernUI.TEXT_WHITE);
        table.setGridColor(new Color(40, 35, 55));
        table.setRowHeight(24);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        table.setSelectionBackground(new Color(55, 40, 85));
        table.setSelectionForeground(Color.WHITE);
        table.getTableHeader().setBackground(new Color(30, 25, 45));
        table.getTableHeader().setForeground(ModernUI.NEON_CYAN);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
        table.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(70, 55, 100)));

        // Column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(0).setMaxWidth(90);
        table.getColumnModel().getColumn(1).setPreferredWidth(60);
        table.getColumnModel().getColumn(1).setMaxWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);
        table.getColumnModel().getColumn(2).setMaxWidth(120);
        table.getColumnModel().getColumn(3).setPreferredWidth(180);
        table.getColumnModel().getColumn(4).setPreferredWidth(80);
        table.getColumnModel().getColumn(4).setMaxWidth(100);
        table.getColumnModel().getColumn(5).setPreferredWidth(450);

        // Sync checkbox changes to CleanRoomManager
        tableModel.addTableModelListener(e -> {
            if (e.getColumn() == 0 && e.getFirstRow() >= 0) {
                int modelRow = e.getFirstRow();
                if (modelRow < tableModel.getRowCount()) {
                    Boolean val = (Boolean) tableModel.getValueAt(modelRow, 0);
                    Long id = (Long) tableModel.getValueAt(modelRow, 1);
                    if (id != null && val != null) {
                        CleanRoomManager.getInstance().setPacketSelected(id, val);
                    }
                }
            }
        });

        // Search Filter listener
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                String text = searchField.getText().trim();
                if (text.isEmpty()) {
                    rowSorter.setRowFilter(null);
                } else {
                    rowSorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text)));
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBackground(ModernUI.BG_DARK);
        scrollPane.getViewport().setBackground(ModernUI.BG_DARK);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(50, 40, 75), 1));
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        root.add(centerPanel, BorderLayout.CENTER);

        // Bottom Action Controls
        JPanel bottomBar = new JPanel(new BorderLayout(10, 0));
        bottomBar.setBackground(ModernUI.BG_PANEL);
        bottomBar.setBorder(new EmptyBorder(10, 14, 10, 14));

        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftActions.setOpaque(false);

        JButton selectAllBtn = createButton("[+] Selecionar Todos", new Color(45, 40, 65));
        selectAllBtn.addActionListener(e -> {
            CleanRoomManager.getInstance().selectAllPackets(true);
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                tableModel.setValueAt(true, i, 0);
            }
        });
        leftActions.add(selectAllBtn);

        JButton deselectAllBtn = createButton("[-] Desmarcar Todos", new Color(45, 40, 65));
        deselectAllBtn.addActionListener(e -> {
            CleanRoomManager.getInstance().selectAllPackets(false);
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                tableModel.setValueAt(false, i, 0);
            }
        });
        leftActions.add(deselectAllBtn);

        JButton removeSelectedBtn = createButton("[Remover] Remover Selecionados", new Color(120, 30, 40));
        removeSelectedBtn.addActionListener(e -> removeSelectedPackets());
        leftActions.add(removeSelectedBtn);

        JButton refreshBtn = createButton("[Atualizar] Recarregar", new Color(45, 40, 65));
        refreshBtn.addActionListener(e -> reloadTableData());
        leftActions.add(refreshBtn);

        bottomBar.add(leftActions, BorderLayout.WEST);

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightActions.setOpaque(false);

        JButton applyModelBtn = createButton("[Recalcular] Aplicar ao Modelo Golden Profile", ModernUI.NEON_PURPLE);
        applyModelBtn.addActionListener(e -> applySelectedToModel());
        rightActions.add(applyModelBtn);

        JButton closeActionBtn = createButton("Fechar", new Color(60, 55, 75));
        closeActionBtn.addActionListener(e -> dispose());
        rightActions.add(closeActionBtn);

        bottomBar.add(rightActions, BorderLayout.EAST);
        root.add(bottomBar, BorderLayout.SOUTH);

        // Initial table load
        reloadTableData();
    }

    private void reloadTableData() {
        tableModel.setRowCount(0);
        List<CapturedPacketSample> samples = CleanRoomManager.getInstance().getCapturedPackets();
        for (CapturedPacketSample s : samples) {
            tableModel.addRow(new Object[]{
                s.selectedForModel().get(),
                s.id(),
                s.formattedTime(),
                s.ip(),
                s.proto(),
                s.summary()
            });
        }
        countLabel.setText("Total de Pacotes: " + samples.size());
    }

    private void removeSelectedPackets() {
        List<Long> idsToRemove = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Boolean selected = (Boolean) tableModel.getValueAt(i, 0);
            if (Boolean.TRUE.equals(selected)) {
                Long id = (Long) tableModel.getValueAt(i, 1);
                if (id != null) {
                    idsToRemove.add(id);
                }
            }
        }

        if (idsToRemove.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nenhum pacote selecionado para remocao.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
            "Deseja remover " + idsToRemove.size() + " pacote(s) do historico de captura?",
            "Confirmar Remocao", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            CleanRoomManager.getInstance().removeCapturedPackets(idsToRemove);
            reloadTableData();
            JOptionPane.showMessageDialog(this, idsToRemove.size() + " pacote(s) removido(s) com sucesso.", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void applySelectedToModel() {
        int selectedCount = 0;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Boolean selected = (Boolean) tableModel.getValueAt(i, 0);
            if (Boolean.TRUE.equals(selected)) {
                selectedCount++;
            }
        }

        int confirm = JOptionPane.showConfirmDialog(this,
            "Deseja recalcular a baseline do modelo Golden Profile com base nas " + selectedCount + " amostra(s) selecionada(s)?\n" +
            "Todas as amostras desmarcadas serao ignoradas no calculo das medias vetoriais.",
            "Recalcular Golden Profile", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            CleanRoomManager.getInstance().recalculateProfileFromSelectedSamples();
            if (onModelUpdated != null) {
                onModelUpdated.run();
            }
            JOptionPane.showMessageDialog(this,
                "Modelo Golden Profile recalculado com sucesso com " + selectedCount + " amostras selecionadas!",
                "Modelo Atualizado", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static JButton createButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(bg.brighter(), 1),
            new EmptyBorder(6, 12, 6, 12)
        ));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
}
