/*
* Copyleft © 2024-2026 L2Brproject
*/
package ext.mods.security.fail2ban.gui;

import ext.mods.commons.gui.ModernUI;
import ext.mods.security.fail2ban.core.BanManager;
import ext.mods.security.fail2ban.core.BanRecord;
import ext.mods.security.fail2ban.core.GeoLocationService;
import ext.mods.security.fail2ban.firewall.FirewallAdapter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Modern modal dialog showing complete event telemetry, IP inspection,
 * payload sample and instant contextual Ban / Unban actions.
 */
public class EventDetailsDialog extends JDialog {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public EventDetailsDialog(JFrame parent,
                              String timestamp,
                              String type,
                              String ip,
                              String ipVersion,
                              String status,
                              String location,
                              String jailOrRoute,
                              String detail,
                              String payloadSample,
                              BanManager banManager,
                              GeoLocationService geoService,
                              FirewallAdapter firewall,
                              Runnable onActionCompleted) {
        super(parent, "Event Inspection and Target Control - " + ip, true);
        setSize(680, 560);
        setLocationRelativeTo(parent);
        setResizable(false);
        getContentPane().setBackground(ModernUI.BG_DARK);
        setLayout(new BorderLayout(0, 10));

        // Header Panel
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(20, 18, 30));
        header.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel titleLbl = new JLabel("Target Security Inspection: " + ip);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 15));
        titleLbl.setForeground(ModernUI.TEXT_WHITE);
        header.add(titleLbl, BorderLayout.WEST);

        JLabel verBadge = new JLabel(" [" + ipVersion + "] ");
        verBadge.setFont(new Font("Monospaced", Font.BOLD, 13));
        verBadge.setForeground(ModernUI.NEON_CYAN);
        header.add(verBadge, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // Body Panel
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(10, 16, 10, 16));

        // 1. Grid of Metadata
        JPanel metaGrid = new JPanel(new GridLayout(0, 2, 12, 8));
        metaGrid.setOpaque(false);

        metaGrid.add(createFieldPair("Timestamp:", timestamp));
        metaGrid.add(createFieldPair("Event Type:", type));
        metaGrid.add(createFieldPair("Target IP:", ip + " (" + ipVersion + ")"));
        metaGrid.add(createFieldPair("Current Status:", status));
        metaGrid.add(createFieldPair("Location / ISP:", location));
        metaGrid.add(createFieldPair("Jail / Route:", jailOrRoute));

        // Verification and Effectiveness Check
        boolean isBannedInManager = banManager != null && banManager.isBanned(ip);
        BanRecord banRecord = isBannedInManager ? banManager.getBan(ip) : null;
        String banStatusStr = isBannedInManager 
            ? "[BANNED] ACTIVE BAN (Expires: " + (banRecord != null && banRecord.expireTime() < 0 ? "PERMANENT" : (banRecord != null ? DATE_FORMAT.format(new Date(banRecord.expireTime())) : "ACTIVE")) + ")"
            : "[CLEAN] NOT BANNED (Clean Status)";

        metaGrid.add(createFieldPair("Ban Manager Status:", banStatusStr));
        metaGrid.add(createFieldPair("Edge Proxy Cache:", isBannedInManager ? "BLOCKED O(1) in RAM" : "FORWARD / ALLOW"));
        metaGrid.add(createFieldPair("Firewall Defense:", firewall != null ? firewall.name() + " (Active)" : "Internal Only"));
        metaGrid.add(createFieldPair("Cloudflare / IPv6:", ip.contains(":") ? "Native IPv6 Subnet (/64)" : "IPv4 Host / Real-IP"));

        body.add(metaGrid);
        body.add(Box.createVerticalStrut(12));

        // 2. Details and Rule Text Area
        body.add(createSectionLabel("Event Rule and Execution Context:"));
        JTextArea detailArea = createMonospacedArea(detail != null && !detail.isBlank() ? detail : "Nenhum detalhe adicional fornecido para este evento.", 3);
        body.add(new JScrollPane(detailArea));
        body.add(Box.createVerticalStrut(10));

        // 3. Payload / Request Snippet Area
        body.add(createSectionLabel("Proxy Request Payload / Path Snippet:"));
        JTextArea payloadArea = createMonospacedArea(payloadSample != null && !payloadSample.isBlank() ? payloadSample : "[Sem payload de requisição capturado / Conexão direta TCP]", 4);
        body.add(new JScrollPane(payloadArea));

        add(body, BorderLayout.CENTER);

        // Actions Footer Panel
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        footer.setBackground(ModernUI.BG_PANEL);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(40, 35, 55)));

        if (isBannedInManager) {
            JButton unbanBtn = createActionButton("Desbanir IP Agora", new Color(20, 120, 80), Color.WHITE);
            unbanBtn.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(this,
                    "Deseja realmente desbanir o IP: " + ip + "?\nIsso reativara o acesso imediatamente no Proxy e Core.",
                    "Confirmar Desbanimento",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
                if (confirm == JOptionPane.YES_OPTION) {
                    banManager.unban(ip);
                    JOptionPane.showMessageDialog(this, "IP " + ip + " desbanido com sucesso!");
                    if (onActionCompleted != null) onActionCompleted.run();
                    dispose();
                }
            });
            footer.add(unbanBtn);
        } else {
            // Duration selector
            JComboBox<String> durationCombo = new JComboBox<>(new String[]{"1 Hora", "12 Horas", "24 Horas", "7 Dias", "Permanente"});
            durationCombo.setBackground(ModernUI.BG_DARK);
            durationCombo.setForeground(ModernUI.TEXT_WHITE);
            durationCombo.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            footer.add(new JLabel("Duracao:"));
            footer.add(durationCombo);

            JButton banBtn = createActionButton("Banir IP Agora", new Color(180, 40, 60), Color.WHITE);
            banBtn.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(this,
                    "Confirma o banimento do IP: " + ip + "?\nO bloqueio entrara em vigor em menos de 0.2ms no Proxy e GameServer.",
                    "Confirmar Banimento",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
                if (confirm == JOptionPane.YES_OPTION) {
                    int selected = durationCombo.getSelectedIndex();
                    long durationMs = switch (selected) {
                        case 0 -> 3600_000L;
                        case 1 -> 43200_000L;
                        case 2 -> 86400_000L;
                        case 3 -> 604800_000L;
                        default -> -1L;
                    };
                    banManager.ban(ip, "manual_operator", "Manual ban from Event Details Inspector", durationMs);
                    JOptionPane.showMessageDialog(this, "IP " + ip + " bloqueado com sucesso!");
                    if (onActionCompleted != null) onActionCompleted.run();
                    dispose();
                }
            });
            footer.add(banBtn);
        }

        JButton closeBtn = createActionButton("Fechar", new Color(30, 28, 42), ModernUI.NEON_CYAN);
        closeBtn.addActionListener(e -> dispose());
        footer.add(closeBtn);

        add(footer, BorderLayout.SOUTH);
    }

    private JPanel createFieldPair(String label, String value) {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.setOpaque(false);
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lbl.setForeground(new Color(170, 160, 185));
        p.add(lbl, BorderLayout.WEST);

        JLabel val = new JLabel(value != null ? value : "-");
        val.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        val.setForeground(ModernUI.TEXT_WHITE);
        p.add(val, BorderLayout.CENTER);
        return p;
    }

    private JLabel createSectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 11));
        l.setForeground(ModernUI.NEON_CYAN);
        l.setBorder(new EmptyBorder(4, 0, 4, 0));
        return l;
    }

    private JTextArea createMonospacedArea(String content, int rows) {
        JTextArea area = new JTextArea(content, rows, 40);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font("Monospaced", Font.PLAIN, 11));
        area.setBackground(new Color(14, 12, 22));
        area.setForeground(new Color(220, 220, 235));
        area.setCaretColor(ModernUI.NEON_CYAN);
        area.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        return area;
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
