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
package ext.mods.security.gui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Image;
import java.util.prefs.Preferences;
import java.io.File;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;

import ext.mods.commons.gui.ThemeManager;
import ext.mods.commons.gui.ModernUI; 
import ext.mods.commons.gui.CustomTopPanel;
import ext.mods.commons.gui.ConfigGS;
import ext.mods.commons.gui.DBMonitorConsole;
import ext.mods.security.services.AuthService;
import ext.mods.security.services.DatabaseManager;
import ext.mods.commons.gui.services.ProcessManagerService;
import ext.mods.commons.services.IRamAllocationService;
import ext.mods.commons.services.RamAllocationService;
import ext.mods.commons.util.JavaProcessInspector;
import ext.mods.security.services.IRatesManager;
import ext.mods.security.services.RatesManager;

public class MainFrame {

    private JFrame frame;
    private JPanel mainPanel;
    private CustomTopPanel topPanel;
    private LoginPanel loginPanel;
    private PrepararAmbientePanel prepararPanel;
    private DashboardPanel dashboardPanel;

    private final Preferences prefs = Preferences.userRoot().node("project_dashboard");
    
    private final ProcessManagerService processManagerService = ProcessManagerService.getInstance();
    private final AuthService authService = new AuthService();
    private final DatabaseManager databaseManager = DatabaseManager.getInstance();
    private final IRamAllocationService ramAllocationService = new RamAllocationService();
    private final IRatesManager ratesManager = new RatesManager();

    public MainFrame() {
        applyTheme();
        initialize();
    }

    private void initialize() {
        frame = new JFrame("BR Project - GameServer Launcher");
        frame.setUndecorated(true);
        frame.setResizable(true);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        ext.mods.commons.pool.CoroutinePool.init();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                processManagerService.stopAllServices(true);
            } catch (Throwable ignored) {
            }
        }, "BrProject-ShutdownHook"));

        
        frame.setSize(650, 400); 
        frame.setLocationRelativeTo(null);
        
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleCloseAction();
            }
        });
        
        JMenuBar menuBar = createMenuBar();
        Runnable closeAction = this::handleCloseAction;
        String iconPath = "./images/16x16.png";

        topPanel = new CustomTopPanel(frame, menuBar, closeAction, false, iconPath);
        frame.add(topPanel, BorderLayout.NORTH);

        mainPanel = new JPanel(new CardLayout());
        mainPanel.setBackground(ModernUI.BG_DARK);

        loginPanel = new LoginPanel(frame, authService, this);
        prepararPanel = new PrepararAmbientePanel(frame, authService, this);
        dashboardPanel = new DashboardPanel(frame, databaseManager, processManagerService, this, ramAllocationService, ratesManager);

        mainPanel.add(prepararPanel.getPanel(), "preparar");
        mainPanel.add(loginPanel.getPanel(), "login");
        mainPanel.add(dashboardPanel.getPanel(), "dashboard");

        frame.add(mainPanel, BorderLayout.CENTER);

        loadIcons();

        // Fluxo:
        //   - Se flag/PrepararTeste.done ainda nao existe, mostra a tela unificada
        //     "Preparar Ambiente" (login + DB + migracao + hexid).
        //   - Caso contrario, tenta login automatico (lembrar-me) e segue para Dashboard.
        java.io.File marker = new java.io.File("flag/PrepararTeste.done");
        if (!marker.exists()) {
            showPrepararPanel();
        } else {
            String email = prefs.get("email", "operator@brproject.local");
            // DevAuth auto-login: use the active dev token as password when available
            String devToken = authService.getActiveDevToken();
            String senha = (devToken != null) ? devToken : prefs.get("senha", "");

            boolean success = authService.authenticate(email, senha);

            if (success) {
                playServerLoadedSoundStartAsync();
                LauncherApp.setLoggedUserEmail(email);
                LauncherApp.setKey(authService.generateRandomKey());
                authService.loadLicenses(email);
                showDashboardPanel();
                if (prefs.get("email", "").isEmpty() && prefs.get("senha", "").isEmpty()) {
                    prefs.put("email", email);
                    prefs.put("senha", senha);
                }
            } else {
                loginPanel.clearFields();
                showLoginPanel();
            }
        }

        frame.setVisible(true);
        warnAboutOrphanJavaProcessesAsync();
    }
    
    /** Atualiza o conteudo do dashboard se ele ja estiver visivel. */
    public void refreshDashboardLayout() {
        if (dashboardPanel != null) {
            dashboardPanel.updateLayout(LauncherApp.getKey());
        }
    }
    
    
    private void handleCloseAction() {
        String[] opcoes = {"Esconder", "Fechar", "Cancelar"};
        int escolha = JOptionPane.showOptionDialog(frame, 
            "O que deseja fazer?\n\n- Fechar: encerra todos os servicos (Game, Login, Site, Cloudflare, Fail2Ban, Proxy) e fecha o programa.\n- Esconder: mantem os servicos ativos e oculta a janela.", 
            "Sair do sistema", 
            JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opcoes, opcoes[0]);
        if (escolha == 0) { 
            frame.setVisible(false); 
        } else if (escolha == 1) { 
            executeShutdownWithProgress();
        }
    }

    private void executeShutdownWithProgress() {
        playServerLoadedSoundLogout();

        javax.swing.JDialog progressDialog = new javax.swing.JDialog(frame, "Encerrando Servidores", true);
        progressDialog.setUndecorated(true);
        progressDialog.setSize(380, 110);
        progressDialog.setLocationRelativeTo(frame);

        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(ModernUI.BG_DARK);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ModernUI.NEON_PURPLE, 2),
            BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));

        javax.swing.JLabel title = new javax.swing.JLabel("Encerrando BR Project...", javax.swing.JLabel.CENTER);
        title.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 15));
        title.setForeground(ModernUI.TEXT_WHITE);

        javax.swing.JLabel msg = new javax.swing.JLabel("Aguarde a finalização segura de todas as JVMs...", javax.swing.JLabel.CENTER);
        msg.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
        msg.setForeground(ModernUI.TEXT_GRAY);

        p.add(title, BorderLayout.NORTH);
        p.add(msg, BorderLayout.CENTER);
        progressDialog.setContentPane(p);

        new Thread(() -> {
            try {
                processManagerService.stopAllServices(true);
            } catch (Throwable ignored) {
            } finally {
                SwingUtilities.invokeLater(() -> {
                    try {
                        progressDialog.dispose();
                    } catch (Throwable ignored) {}
                    if (frame != null) {
                        try {
                            frame.dispose();
                        } catch (Throwable ignored) {}
                    }
                    System.exit(0);
                });
            }
        }, "BrProject-Shutdown-Worker").start();

        progressDialog.setVisible(true);
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBorder(BorderFactory.createEmptyBorder()); 

        JMenu menu = new JMenu("Painel");
        menu.setForeground(ThemeManager.TEXT_COLOR);
        
        JMenuItem itemSair = createMenuItem("Sair", e -> logout());
        menu.add(itemSair);
        
        JMenuItem itemConfig = createMenuItem("Config", e -> {
            ConfigGS configWindow = new ConfigGS(frame);
            configWindow.showWindow();
        });
        menu.add(itemConfig);
        
        JMenuItem itemDBMonitor = createMenuItem("DB Monitor", e -> {
            DBMonitorConsole.getInstance().showWindow();
        });
        menu.add(itemDBMonitor);
        
        menuBar.add(menu);
        
        JMenu menuSuporte = new JMenu("Suporte");
        menuSuporte.setForeground(ThemeManager.TEXT_COLOR);
        
        JMenuItem itemReport = createMenuItem("Report Bug", e -> JOptionPane.showMessageDialog(frame, "Não há bugs reportados."));
        menuSuporte.add(itemReport);
        menuBar.add(menuSuporte);
        
        return menuBar;
    }

    private JMenuItem createMenuItem(String title, java.awt.event.ActionListener listener) {
        JMenuItem item = new JMenuItem(title);
        if (listener != null) item.addActionListener(listener);
        return item;
    }
    
    public void showLoginPanel() {
        loginPanel.updateRememberMeState();
        CardLayout cl = (CardLayout) mainPanel.getLayout();
        cl.show(mainPanel, "login");
    }

    /** Mostra a tela unificada de primeiro acesso (login + DB + migracao + hexid). */
    public void showPrepararPanel() {
        // Recria o painel para refletir mudancas no marker (caso o usuario volte de outro lugar)
        mainPanel.remove(prepararPanel.getPanel());
        prepararPanel = new PrepararAmbientePanel(frame, authService, this);
        mainPanel.add(prepararPanel.getPanel(), "preparar");
        CardLayout cl = (CardLayout) mainPanel.getLayout();
        cl.show(mainPanel, "preparar");
    }

    public void showDashboardPanel() {
        dashboardPanel.updateLayout(LauncherApp.getKey());
        CardLayout cl = (CardLayout) mainPanel.getLayout();
        cl.show(mainPanel, "dashboard");
    }
    
    public void refreshLicenses() {
        authService.loadLicenses(LauncherApp.getLoggedUserEmail());
        showDashboardPanel();
    }

    public void logout() {
        if (JOptionPane.showConfirmDialog(frame, "Tem certeza que deseja logout?", "Confirmar", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            playServerLoadedSoundLogout();
            if (loginPanel.getChkRemember() == null || !loginPanel.getChkRemember().isSelected()) {
                prefs.remove("email");
                prefs.remove("senha");
            }
            LauncherApp.setLoggedUserEmail(null);
            LauncherApp.setKey(null);
            loginPanel.clearFields();
            showLoginPanel();
        }
    }

    private void applyTheme() {
        ThemeManager.applyTheme();
    }
    
    private void loadIcons() {
        java.util.List<Image> icons = new ArrayList<>();
        try {
            ImageIcon icon16 = new ImageIcon("./images/16x16.png");
            if (icon16.getImageLoadStatus() == java.awt.MediaTracker.COMPLETE) icons.add(icon16.getImage());
        } catch (Exception e) {}
        try {
            ImageIcon icon32 = new ImageIcon("./images/32x32.png");
            if (icon32.getImageLoadStatus() == java.awt.MediaTracker.COMPLETE) icons.add(icon32.getImage());
        } catch (Exception e) {}
        if (!icons.isEmpty()) frame.setIconImages(icons);
    }
    
    private void warnAboutOrphanJavaProcessesAsync() {
        Thread inspector = new Thread(() -> {
            try {
                java.util.List<JavaProcessInspector.JavaProcessInfo> zombies =
                    JavaProcessInspector.findBrProjectProcesses();
                if (zombies.isEmpty()) return;

                SwingUtilities.invokeLater(() -> showOrphanJavaProcessesWarning(zombies));
            } catch (Exception ignored) {
                // nunca quebra o startup por causa de inspecao de processos
            }
        }, "brproject-process-inspector");
        inspector.setDaemon(true);
        inspector.start();
    }

    private void showOrphanJavaProcessesWarning(java.util.List<JavaProcessInspector.JavaProcessInfo> zombies) {
        if (zombies == null || zombies.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='width:480px'>");
        sb.append("<b>").append(zombies.size()).append(" processo(s) Java do BR Project ja estao em execucao:</b><br><br>");
        for (JavaProcessInspector.JavaProcessInfo p : zombies) {
            sb.append("&bull; PID ").append(p.pid)
              .append(" - ").append(p.type.getLabel()).append("<br>");
        }
        sb.append("<br>Isso pode impedir a inicializacao correta de novos servidores.<br>");
        sb.append("Feche-os pelo Gerenciador de Tarefas ou execute no CMD:<br>");
        sb.append("<code>taskkill /F /IM java.exe</code>");
        sb.append("</body></html>");

        JOptionPane.showMessageDialog(frame, sb.toString(),
            "Processos Java em execucao", JOptionPane.WARNING_MESSAGE);
    }

    public void playServerLoadedSoundStart() { playSound("Start.wav"); }
    public void playServerLoadedSoundStartAsync() { playSoundAsync("Start.wav"); }
    public void playServerLoadedSoundLogout() { playSound("Shutdown.wav"); }

    private void playSoundAsync(String fileName) {
        Thread soundThread = new Thread(() -> playSound(fileName), "brproject-sound-" + fileName);
        soundThread.setDaemon(true);
        soundThread.start();
    }

    public void playSound(String fileName) {
        try {
            File soundFile = new File("./sound/" + fileName);
            if (!soundFile.exists()) return;
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(soundFile);
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            clip.start();
        } catch (Exception e) {}
    }
}