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
package ext.mods.commons.gui.services;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.prefs.Preferences;

import ext.mods.commons.config.ServerPropertiesSecretBootstrap;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import ext.mods.commons.AnsiConsole;
import ext.mods.commons.gui.ThemeManager;
import ext.mods.commons.util.JvmOptimizer;

public class ProcessManagerService {
    
    private static volatile ProcessManagerService INSTANCE;

    public static ProcessManagerService getInstance() {
        if (INSTANCE == null) {
            synchronized (ProcessManagerService.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ProcessManagerService();
                }
            }
        }
        return INSTANCE;
    }

    private static final Preferences prefs = Preferences.userRoot().node("ram_allocation_settings");
    private static final Preferences sitePrefs = Preferences.userRoot().node("site_server_settings");

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_GREEN = "\u001B[92m";
    private static final String ANSI_CYAN = "\u001B[96m";
    private static final String ANSI_YELLOW = "\u001B[93m";
    private static final String ANSI_PURPLE = "\u001B[95m";
    private static final String SITE_LOG_PREFIX = "[SITE-KTOR] ";
    private static final int SITE_BANNER_WIDTH = 117;
    private static final String[] SITE_LOGO = {
        "██████╗ ██████╗ ██████╗ ██████╗  ██████╗      ██╗███████╗ ██████╗████████╗",
        "██╔══██╗██╔══██╗██╔══██╗██╔══██╗██╔═══██╗     ██║██╔════╝██╔════╝╚══██╔══╝",
        "██████╔╝██████╔╝██████╔╝██████╔╝██║   ██║     ██║█████╗  ██║        ██║   ",
        "██╔══██╗██╔══██╗██╔═══╝ ██╔══██╗██║   ██║██   ██║██╔══╝  ██║        ██║   ",
        "██████╔╝██║  ██║██║     ██║  ██║╚██████╔╝╚█████╔╝███████╗╚██████╗   ██║   ",
        "╚═════╝ ╚═╝  ╚═╝╚═╝     ╚═╝  ╚═╝ ╚═════╝  ╚════╝ ╚══════╝ ╚═════╝   ╚═╝   "
    };
    private Process siteProcess;
    private volatile boolean stoppingSite;
    private Process BrprojectProcess;
    private volatile String BrprojectTunnelUrl;
    private Process gameServerProcess;
    private Process loginServerProcess;
    private volatile boolean isShuttingDown;

    public ProcessManagerService() {
        synchronized (ProcessManagerService.class) {
            if (INSTANCE == null) {
                INSTANCE = this;
            }
        }
        try {
            ext.mods.commons.util.ShutdownSignalWatcher.cleanSignals();
        } catch (Throwable ignored) {}
    }

    public boolean isShuttingDown() {
        return isShuttingDown;
    }

    private String getJavaExecutable() {
        String ext = System.getProperty("os.name").toLowerCase().contains("win") ? ".exe" : "";

        // 1) JAVA_HOME vindo do ambiente.
        String javaHome = System.getenv("JAVA_HOME");
        String resolved = resolveJavaFromHome(javaHome, ext);
        if (resolved != null) {
            if (resolved.startsWith("ENV:")) {
                System.err.println("[INFO] Java detectado via JAVA_HOME (" + resolved.substring(4) + ").");
            }
            return resolved.startsWith("ENV:") ? resolved.substring(4) : resolved;
        }
        System.err.println("[AVISO] JAVA_HOME nao definido. Procurando Java no sistema...");

        // 2) java.home embutido (propriedade da JVM em execucao).
        String embeddedHome = System.getProperty("java.home");
        resolved = resolveJavaFromHome(embeddedHome, ext);
        if (resolved != null) return resolved;

        // 2.5) JDK 25 Autônomo Local / GraalVM (Paridade com brproject-java.inc.bat)
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            String[] localJdkPaths = new String[] {
                "D:\\graalvm25",
                "gradle\\jdk25",
                "..\\gradle\\jdk25",
                "D:\\Projeto_Start_Brproject\\gradle\\jdk25"
            };
            for (String localJdk : localJdkPaths) {
                resolved = resolveJavaFromHome(localJdk, ext);
                if (resolved != null) {
                    System.err.println("[INFO] Java 25 local detectado: " + resolved);
                    return resolved;
                }
            }
        }

        // 3) Varredura de diretorios comuns no Windows.
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            resolved = scanCommonJavaHomes(ext);
            if (resolved != null) return resolved;
        }

        // 4) PATH via `where` (Windows) ou `which` (Linux/Mac).
        try {
            ProcessBuilder pb = new ProcessBuilder(
                System.getProperty("os.name").toLowerCase().contains("win") ? new String[]{"where", "java"} : new String[]{"sh", "-c", "command -v java"}
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (java.util.Scanner sc = new java.util.Scanner(p.getInputStream())) {
                if (sc.hasNextLine()) {
                    String fromPath = sc.nextLine().trim();
                    if (!fromPath.isEmpty() && new File(fromPath).exists()) {
                        System.err.println("[INFO] Java encontrado no PATH: " + fromPath);
                        return fromPath;
                    }
                }
            }
            p.waitFor();
        } catch (Exception ignored) {}

        // 5) Fallback: comando global. Caller lida com FileNotFoundException.
        System.err.println("[AVISO] Java nao encontrado. Tentando executar comando global 'java'.");
        return "java";
    }

    /**
     * Recebe um valor que se *parece* com JAVA_HOME e tenta extrair o
     * caminho do executavel. Aceita:
     *   - pasta do JDK            (ex.: "C:\jdk-25")
     *   - pasta com \bin incluido (ex.: "C:\jdk-25\bin")
     *   - caminho ate o java.exe  (ex.: "C:\jdk-25\bin\java.exe")
     * Retorna o caminho do executavel ou null se nao existir.
     */
    private String resolveJavaFromHome(String home, String ext) {
        if (home == null) return null;
        String h = home.trim();
        if (h.isEmpty()) return null;
        // remove aspas
        while (h.length() >= 2 && h.startsWith("\"") && h.endsWith("\"")) {
            h = h.substring(1, h.length() - 1);
        }
        // remove barra final
        while (h.endsWith("\\") || h.endsWith("/")) h = h.substring(0, h.length() - 1);

        // Se ja termina em java(.exe), retorna direto
        String lower = h.toLowerCase();
        if (lower.endsWith("java" + ext) && new File(h).exists()) return h;

        // Caso 1: h == "...\jdk-XX"        -> h\bin\java(.exe)
        // Caso 2: h == "...\jdk-XX\bin"    -> h\java(.exe)
        String c1 = h + File.separator + "bin" + File.separator + "java" + ext;
        if (new File(c1).exists()) return c1;
        String c2 = h + File.separator + "java" + ext;
        if (new File(c2).exists()) return c2;

        return null;
    }

    /**
     * Varre locais comuns onde JDKs costumam estar instalados no Windows.
     */
    private String scanCommonJavaHomes(String ext) {
        String pattern = "C:\\Program Files\\Eclipse Adoptium\\jdk-*;"
                + "C:\\Program Files\\Java\\jdk-*;"
                + "C:\\Program Files\\Microsoft\\jdk-*;"
                + "C:\\Program Files\\Zulu\\zulu-*;"
                + "C:\\Program Files\\Amazon Corretto\\jdk*;"
                + "C:\\Program Files\\BellSoft\\LibericaJDK-*;"
                + "C:\\Program Files\\OpenJDK\\jdk-*;"
                + "C:\\Program Files\\AdoptOpenJDK\\jdk-*;"
                + "C:\\jdk-*;"
                + "C:\\Program Files (x86)\\Eclipse Adoptium\\jdk-*;"
                + "C:\\Program Files (x86)\\Java\\jdk-*";

        for (String glob : pattern.split(";")) {
            File parent = new File(glob);
            File[] matches = parent.listFiles(new java.io.FilenameFilter() {
                @Override public boolean accept(File dir, String name) { return true; }
            });
            if (matches == null) continue;
            // ordena para pegar a versao mais recente
            java.util.Arrays.sort(matches, Comparator.comparing(File::getName).reversed());
            for (File m : matches) {
                if (!m.isDirectory()) continue;
                String p = m.getAbsolutePath() + "\\bin\\java" + ext;
                if (new File(p).exists()) {
                    System.err.println("[INFO] Java localizado em: " + p);
                    return p;
                }
            }
        }
        return null;
    }

    private File findSiteNativeExecutable(File projectRoot) {
        String exeExt = isWindows() ? ".exe" : "";
        File[] candidates = new File[] {
            new File(projectRoot, "bin/site-native" + exeExt),
            new File(projectRoot, "bin/brproject-site" + exeExt),
            new File("D:/Site_ktor/dist/site-native" + exeExt),
            new File("D:/Site_ktor/bin/site-native" + exeExt),
            new File("D:/Site_ktor/build/native/nativeOptimizedCompile/site-release" + exeExt),
            new File("D:/Site_ktor/build/native/nativeCompile/site-release" + exeExt),
            new File(projectRoot, "modules/site/build/native/nativeOptimizedCompile/site-release" + exeExt),
            new File(projectRoot, "modules/site/build/native/nativeCompile/site" + exeExt),
            new File("bin/site-native" + exeExt)
        };
        for (File f : candidates) {
            if (f.exists() && f.isFile()) {
                return f;
            }
        }
        return null;
    }

    public boolean isSiteRunning() {
        return siteProcess != null && siteProcess.isAlive();
    }

    public boolean startSite(JFrame frame, Runnable onStopped) {
        if (isSiteRunning()) {
            System.out.println("[SITE] Site Ktor já está em execução.");
            return true;
        }

        File projectRoot = findProjectRoot();
        File nativeExe = findSiteNativeExecutable(projectRoot);
        boolean isNative = (nativeExe != null);

        List<String> command = new ArrayList<>();
        if (isNative) {
            command.add(nativeExe.getAbsolutePath());
        } else {
            JOptionPane.showMessageDialog(frame,
                "Executável nativo do Site Ktor não encontrado.\n\n" +
                "Verifique se o arquivo 'site-native.exe' está presente em 'bin/' ou em 'D:/Site_ktor'.\n" +
                "Para compilar o binário nativo, use o repositório D:/Site_ktor.",
                "Binário Nativo não Encontrado", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        File propsFile = new File(projectRoot, "game/config/server.properties");
        if (propsFile.exists() && propsFile.isFile()) {
            try {
                ServerPropertiesSecretBootstrap.EnsureResult rotResult = ServerPropertiesSecretBootstrap.rotate(propsFile);
                if (rotResult.getChanged()) {
                    System.out.println("[SECURITY] Tokens de seguranca em server.properties rotacionados com sucesso antes do inicio do site: " + rotResult.getRegeneratedKeys());
                    SiteKtorLogManager.getInstance().addLog("INFO", "SECURITY", "Tokens de seguranca gerados e atualizados em server.properties: " + rotResult.getRegeneratedKeys());
                }
            } catch (Exception e) {
                System.err.println("[SECURITY] Aviso: Falha ao rotacionar tokens em server.properties: " + e.getMessage());
            }
        }

        Properties serverProps = loadGameServerDbProperties(projectRoot);
        String siteHost = getSiteBindHost(serverProps);
        String sitePort = getSiteBindPort(serverProps);

        printSiteStartingBanner(siteHost, sitePort, isNative);
        SiteKtorLogManager.getInstance().addLog("INFO", "LAUNCHER", "Iniciando Site NATIVO (" + nativeExe.getName() + ") em http://" + siteHost + ":" + sitePort + "...");

        stoppingSite = false;
        new Thread(() -> {
            int exitCode = -1;
            boolean readyBannerPrinted = false;
            try {
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(projectRoot);
                pb.redirectErrorStream(true);
                configureSiteEnvironment(pb.environment(), projectRoot);
                siteProcess = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(siteProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String linha;
                    while ((linha = reader.readLine()) != null) {
                        System.out.println(SITE_LOG_PREFIX + linha);
                        SiteKtorLogManager.getInstance().addLogLine(linha);
                        if (linha.contains("[KTOR-HTTP]") || linha.contains("[SECURITY]")) {
                            ext.mods.security.fail2ban.gui.ProxyLogTailer.appendLog("[SITE-KTOR] " + linha);
                            ext.mods.security.fail2ban.core.ProxySecurityBridge.processLine(linha);
                        }
                        if (!readyBannerPrinted && linha.contains("[site] listening on ")) {
                            readyBannerPrinted = true;
                            printSiteReadyBanner(siteHost, sitePort, isNative);
                            SiteKtorLogManager.getInstance().addLog("INFO", "LAUNCHER", "Site Ktor ONLINE em http://" + siteHost + ":" + sitePort + (isNative ? " [NATIVO]" : ""));
                            
                            startBrprojectTunnel(projectRoot, sitePort);
                        }
                    }
                }

                exitCode = siteProcess.waitFor();
                if (!stoppingSite && exitCode != 0) {
                    final int code = exitCode;
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame,
                        "Site Ktor finalizou com erro (Código " + code + ").\nVerifique o console para detalhes.",
                        "Erro no Site Ktor", JOptionPane.ERROR_MESSAGE));
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (!stoppingSite) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame,
                        "Falha ao iniciar Site Ktor: " + e.getMessage(),
                        "Erro no Site Ktor", JOptionPane.ERROR_MESSAGE));
                }
            } finally {
                stopBrprojectTunnel();
                siteProcess = null;
                stoppingSite = false;
                if (onStopped != null) {
                    SwingUtilities.invokeLater(onStopped);
                }
                System.out.println("[SITE] Site Ktor parado" + (exitCode >= 0 ? " (exit=" + exitCode + ")." : "."));
            }
        }, "SiteKtor-Process").start();
        return true;
    }

    public void stopSite() {
        stopSite(false);
    }

    public void stopSite(boolean waitSynchronously) {
        stopBrprojectTunnel();
        if (!isSiteRunning()) {
            System.out.println("[SITE] Site Ktor já está parado.");
            return;
        }

        System.out.println("[SITE] Parando Site Ktor" + (waitSynchronously ? " (síncrono)..." : "..."));
        SiteKtorLogManager.getInstance().addLog("WARN", "LAUNCHER", "Encerramento do processo Site Ktor solicitado...");
        stoppingSite = true;
        final Process processo = siteProcess;
        if (processo == null) {
            return;
        }

        Runnable task = () -> {
            stopProcessSafely(processo, "SITE-KTOR", 3);
            siteProcess = null;
            stoppingSite = false;
        };

        if (waitSynchronously) {
            task.run();
        } else {
            new Thread(task, "SiteKtor-Stopper").start();
        }
    }

    /**
     * Encerra um processo e toda a sua árvore de filhos com garantia determinística de waitFor.
     * Primeiro tenta graceful destroy(), aguardando gracefulTimeoutSeconds.
     * Caso ainda permaneça vivo, força killProcessTree + destroyForcibly e aguarda término real.
     */
    private boolean stopProcessSafely(Process process, String serviceName, long gracefulTimeoutSeconds) {
        if (process == null || !process.isAlive()) {
            return true;
        }

        final long pid = process.pid();
        List<Long> descendantPids = new ArrayList<>();
        try {
            descendantPids = process.descendants().map(ProcessHandle::pid).toList();
        } catch (Exception ignored) {}

        System.out.println("[" + serviceName + "] Solicitando encerramento seguro (PID=" + pid + ")...");

        // 1) Encerramento gracioso
        try {
            process.destroy();
            boolean exited = process.waitFor(gracefulTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
            if (exited && (descendantPids.isEmpty() || livePids(descendantPids).isEmpty())) {
                System.out.println("[" + serviceName + "] Processo encerrado com sucesso.");
                return true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {}

        // 2) Fallback forçado
        System.out.println("[" + serviceName + "] Timeout atingido (" + gracefulTimeoutSeconds + "s). Forçando encerramento da árvore de processos...");
        boolean killedRoot = killProcessTree(pid);
        boolean killedChildren = descendantPids.isEmpty() || killProcessTrees(descendantPids);

        try {
            if (!killedRoot || !killedChildren) {
                process.destroyForcibly();
            }
            process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignored) {}

        // Se ainda continuar vivo (ex: processo Administrador protegido contra destroyForcibly e taskkill comum)
        if (process.isAlive()) {
            System.out.println("[" + serviceName + "] Processo persiste ativo (processo Administrador). Disparando elevação administrativa...");
            killProcessTreeElevated(pid);
            try {
                process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception ignored) {}
        }

        boolean dead = !process.isAlive();
        System.out.println("[" + serviceName + "] Status pós-encerramento: " + (dead ? "CONCLUIDO" : "FALHA"));
        return dead;
    }

    private List<Long> livePids(List<Long> pids) {
        List<Long> live = new ArrayList<>();
        for (Long pid : pids) {
            if (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
                live.add(pid);
            }
        }
        return live;
    }

    private boolean killProcessTrees(List<Long> pids) {
        boolean ok = true;
        for (Long pid : pids) {
            ok &= killProcessTree(pid);
        }
        return ok;
    }

    /**
     * Encerra forçadamente um processo elevado como Administrador via PowerShell com -Verb RunAs.
     */
    private boolean killProcessTreeElevated(long pid) {
        if (!isWindows()) return false;
        try {
            System.out.println("[ELEVATED-KILL] Executando taskkill elevado para PID " + pid + "...");
            String powershellCmd = "Start-Process taskkill -ArgumentList '/F /T /PID " + pid + "' -Verb RunAs -WindowStyle Hidden -Wait";
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", powershellCmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor(8, java.util.concurrent.TimeUnit.SECONDS);
            return !ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        } catch (Exception e) {
            System.err.println("[ELEVATED-KILL] Erro ao executar taskkill elevado para PID " + pid + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Mata o processo {@code pid} e todos os seus descendentes.
     *
     * <p>No Windows, {@link Process#destroy()} envia {@code TerminateProcess}
     * apenas ao processo raiz do {@link ProcessBuilder}. Como o site Ktor é
     * iniciado via {@code gradlew.bat :site:run}, o processo raiz é o
     * {@code cmd.exe} e o {@code java.exe} pode ficar órfão, mantendo a porta
     * ocupada. Esse método usa {@code taskkill /F /T /PID <pid>} para encerrar
     * a árvore inteira.</p>
     *
     * @param pid PID do processo raiz ou de um descendente ainda vivo.
     * @return {@code true} se o comando foi executado com sucesso.
     */
    private boolean killProcessTree(long pid) {
        try {
            if (isWindows()) {
                ProcessBuilder pb = new ProcessBuilder("taskkill", "/F", "/T", "/PID", Long.toString(pid));
                pb.redirectErrorStream(true);
                Process p = pb.start();
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String linha;
                    while ((linha = r.readLine()) != null) {
                        System.out.println("[SITE][taskkill] " + linha);
                    }
                }
                boolean finished = p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
                int exitVal = p.exitValue();
                if (exitVal != 0 && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
                    System.out.println("[taskkill] Falha ao encerrar PID " + pid + " (exit=" + exitVal + "). Tentando com elevação de Administrador...");
                    return killProcessTreeElevated(pid);
                }
                return exitVal == 0;
            }

            // Linux/macOS: kill negativo de PID mata o grupo inteiro de uma vez.
            ProcessBuilder pb = new ProcessBuilder("kill", "-TERM", "-" + Long.toString(pid));
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (p.exitValue() != 0) {
                ProcessBuilder pb2 = new ProcessBuilder("kill", "-KILL", "-" + Long.toString(pid));
                pb2.redirectErrorStream(true);
                Process p2 = pb2.start();
                return p2.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            }
            return true;
        } catch (Exception e) {
            System.err.println("[SITE] Erro ao matar árvore de processos: " + e.getMessage());
            return false;
        }
    }

    private void printSiteStartingBanner(String siteHost, String sitePort, boolean isNative) {
        AnsiConsole.enable();
        printSiteBox(ANSI_CYAN, "INICIANDO SITE BRPROJECT KTOR" + (isNative ? " [NATIVO]" : ""), new String[] {
            "Status : preparando execução (" + (isNative ? "Binário Nativo GraalVM AOT" : "Processo Gradle :site:run") + ")",
            "Modo   : " + (isNative ? "GraalVM Native (PGO + SIMD + Hardened)" : "JVM / Gradle Daemon Fallback"),
            "URL    : " + siteUrl(siteHost, sitePort),
            "Host   : " + siteHost,
            "Porta  : " + sitePort,
            "Logs   : prefixados com " + SITE_LOG_PREFIX.trim()
        });
    }

    private void printSiteReadyBanner(String siteHost, String sitePort, boolean isNative) {
        printSiteBox(ANSI_GREEN, "SITE KTOR ONLINE" + (isNative ? " [NATIVO]" : ""), new String[] {
            "Status : pronto para receber conexões",
            "Modo   : " + (isNative ? "GraalVM Nativo (<20ms startup | ~30MB RAM)" : "JVM / Standard"),
            "URL    : " + siteUrl(siteHost, sitePort),
            "Painel : Dashboard > Site Ktor"
        });
    }

    private void printSiteBox(String color, String title, String[] details) {
        String border = repeat('═', SITE_BANNER_WIDTH - 2);
        System.out.println();
        System.out.println(color + "╔" + border + "╗" + ANSI_RESET);
        for (String line : SITE_LOGO) {
            printSiteLine(color, ANSI_BOLD + line + ANSI_RESET + color);
        }
        printSiteLine(color, "");
        printSiteLine(color, ANSI_BOLD + ANSI_YELLOW + title + ANSI_RESET + color);
        printSiteLine(color, "");
        for (String detail : details) {
            printSiteLine(color, ANSI_PURPLE + "• " + ANSI_RESET + color + detail);
        }
        System.out.println(color + "╚" + border + "╝" + ANSI_RESET);
        System.out.println();
    }

    private void printSiteLine(String color, String text) {
        int visibleLength = stripAnsi(text).length();
        int padding = Math.max(0, SITE_BANNER_WIDTH - 4 - visibleLength);
        System.out.println(color + "║ " + text + repeat(' ', padding) + " ║" + ANSI_RESET);
    }

    private String stripAnsi(String text) {
        return text.replaceAll("\\u001B\\[[;\\d]*m", "");
    }

    private String siteUrl(String siteHost, String sitePort) {
        return "http://" + siteHost + ":" + sitePort + "/";
    }

    private String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    private void configureSiteEnvironment(java.util.Map<String, String> env, File projectRoot) {
        Properties dbProps = loadGameServerDbProperties(projectRoot);
        String dbUrl = dbProps.getProperty("URL", dbProps.getProperty("sql.url", "jdbc:postgresql://127.0.0.1:5432/l2jdb"));
        String dbUser = dbProps.getProperty("Login", dbProps.getProperty("sql.login", "brproject"));
        String dbPassword = dbProps.getProperty("Password", dbProps.getProperty("sql.password", ""));
        String siteHost = getSiteBindHost(dbProps);
        String sitePort = getSiteBindPort(dbProps);

        String sessionSecret = dbProps.getProperty("SiteSessionSecret");
        if (sessionSecret == null || sessionSecret.trim().isEmpty()) {
            sessionSecret = getOrCreateSessionSecret();
        } else {
            sessionSecret = sessionSecret.trim();
            sitePrefs.put("SESSION_SECRET", sessionSecret);
        }

        String siteGameApiSecret = dbProps.getProperty("SiteGameApiSecret");
        if (siteGameApiSecret != null && !siteGameApiSecret.trim().isEmpty()) {
            env.put("SITE_GAME_API_SECRET", siteGameApiSecret.trim());
        }

        env.putIfAbsent("DB_URL", dbUrl);
        env.putIfAbsent("DB_USER", dbUser);
        env.putIfAbsent("DB_PASSWORD", dbPassword);
        env.put("SESSION_SECRET", sessionSecret);
        env.putIfAbsent("SITE_BIND_HOST", siteHost);
        env.putIfAbsent("SITE_BIND_PORT", sitePort);
        File siteDir = new File(projectRoot, "site");
        if (!siteDir.exists() || !siteDir.isDirectory()) {
            File externalSite = new File("D:/Site_ktor/site");
            if (externalSite.exists() && externalSite.isDirectory()) {
                siteDir = externalSite;
            }
        }
        env.putIfAbsent("SITE_ROOT", siteDir.getAbsolutePath());
        env.putIfAbsent("SITE_MIGRATIONS", new File(projectRoot, "brproject-data/migrations/mariadb").getAbsolutePath());
    }

    private Properties loadGameServerDbProperties(File projectRoot) {
        Properties props = new Properties();
        File propsFile = new File(projectRoot, "game/config/server.properties");
        if (!propsFile.exists()) {
            return props;
        }
        try (java.io.FileInputStream fis = new java.io.FileInputStream(propsFile)) {
            props.load(fis);
        } catch (Exception e) {
            System.err.println("[SITE] Falha ao ler game/config/server.properties: " + e.getMessage());
        }
        return props;
    }

    private String getOrCreateSessionSecret() {
        String secret = sitePrefs.get("SESSION_SECRET", "").trim();
        if (!secret.isEmpty()) {
            return secret;
        }
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        secret = Base64.getEncoder().encodeToString(bytes);
        sitePrefs.put("SESSION_SECRET", secret);
        return secret;
    }

    /**
     * Resolve SITE_BIND_HOST for the Ktor site subprocess.
     *
     * Precedence:
     *   1) SITE_BIND_HOST (legacy)
     *   2) KtorWebServerIp (preferred human-readable key in server.properties)
     *   3) SiteBindHost
     *   4) WebServerKtorIp
     *   5) Preferences cache
     *   6) Default: 127.0.0.1
     */
    private String getSiteBindHost(Properties serverProps) {
        String host = pickProperty(serverProps,
                "SITE_BIND_HOST",
                "KtorWebServerIp",
                "SiteBindHost",
                "WebServerKtorIp");
        if (host == null) {
            host = sitePrefs.get("SITE_BIND_HOST", "").trim();
        }
        return host.isEmpty() ? "127.0.0.1" : host;
    }

    /**
     * Resolve SITE_BIND_PORT for the Ktor site subprocess.
     *
     * Precedence:
     *   1) SITE_BIND_PORT (legacy)
     *   2) KtorWebServerPort (preferred human-readable key in server.properties)
     *   3) SiteBindPort
     *   4) WebServerKtorPort
     *   5) Preferences cache
     *   6) Default: 8080
     */
    private String getSiteBindPort(Properties serverProps) {
        String port = pickProperty(serverProps,
                "SITE_BIND_PORT",
                "KtorWebServerPort",
                "SiteBindPort",
                "WebServerKtorPort");
        if (port == null) {
            port = sitePrefs.get("SITE_BIND_PORT", "").trim();
        }
        return port.isEmpty() ? "8080" : port;
    }

    private String pickProperty(Properties props, String... keys) {
        if (props == null) return null;
        for (String k : keys) {
            String v = props.getProperty(k);
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        }
        return null;
    }

    private File findProjectRoot() {
        File dir = new File(".").getAbsoluteFile();
        while (dir != null) {
            if (new File(dir, "settings.gradle.kts").exists() && new File(dir, isWindows() ? "gradlew.bat" : "gradlew").exists()) {
                return dir;
            }
            dir = dir.getParentFile();
        }
        return new File(".").getAbsoluteFile();
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public void iniciarProcesso(String tipo, String licenseKey, String userEmail, boolean isLightModeEnabled, JFrame frame) {
        if (isShuttingDown) {
            System.out.println("[PROCESS] Bloqueado início de " + tipo + ": sistema está em processo de encerramento.");
            return;
        }
        
        int memoryMB;
        if (tipo.equalsIgnoreCase("gameserver")) {
            memoryMB = prefs.getInt("gsMemoryMB", 3072);
        } else {
            memoryMB = prefs.getInt("lsMemoryMB", 256);
        }

        System.out.println("\n============================================================");
        System.out.println("  Iniciando " + tipo.toUpperCase() + " com JVM Otimizada");
        System.out.println("============================================================");
        System.out.println("  Memoria JVM: Xms=" + memoryMB + "MB | Xmx=" + memoryMB + "MB");
        
        String caminhoJava = getJavaExecutable();

        if (!new File(caminhoJava).exists()) {
            System.err.println("[AVISO] Caminho exato do Java não encontrado: " + caminhoJava + ". Tentando executar comando global 'java'.");
            caminhoJava = "java";
        }

        File diretorioExecucao = tipo.equals("gameserver") ? new File("game") : new File("login");

        if (!diretorioExecucao.exists()) {
            JOptionPane.showMessageDialog(frame, "A pasta '" + diretorioExecucao.getAbsolutePath() + "' não existe!", "Erro Crítico", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Garante integridade e ciclo de vida do AppCDS (.gc e .jsa) antes do boot
        validateAndPrepareAppCds(diretorioExecucao, tipo);

        
        String cpString = "";
        try {
            final File libsDir = new File(diretorioExecucao, "../libs").getCanonicalFile();
            cpString = JvmOptimizer.buildRuntimeClasspath(libsDir); 
        } catch (Exception e) {
            System.err.println("[AVISO] Classpath ordenado falhou, usando libs/*: " + e.getMessage());
            cpString = ".." + File.separator + "libs" + File.separator + "*"; 
        }
        

        String mainClass = tipo.equals("gameserver") ? "ext.mods.gameserver.GameServer" : "ext.mods.loginserver.LoginServer";

        List<String> command = new ArrayList<>();
        command.add(caminhoJava);
        
        command.add("-Xms" + memoryMB + "m");
        command.add("-Xmx" + memoryMB + "m");
        
        // Forçar UTF-8 no stdout/stderr do filho para evitar corrupção de acentos/Unicode
        command.add("-Dfile.encoding=UTF-8");
        command.add("-Dsun.stdout.encoding=UTF-8");
        command.add("-Dsun.stderr.encoding=UTF-8");
        
        if ("loginserver".equalsIgnoreCase(tipo)) {
            command.add("-Dext.mods.Config.dataPath=../game/data");
        }
        
        if (ThemeManager.isSafeGraphics()) {
            command.add("-Dsun.java2d.opengl=false");
            command.add("-Dsun.java2d.d3d=false");
            command.add("-Dsun.java2d.pmoffscreen=false");
            command.add("-Dbrproject.safe.graphics=true");
        }
        
        command.add("-XX:+UseG1GC");
        command.add("-XX:MaxGCPauseMillis=200");
        if (tipo.equalsIgnoreCase("gameserver")) {
            command.add("-XX:G1HeapRegionSize=16m");
        } else {
            command.add("-XX:G1HeapRegionSize=8m");
        }
        command.add("-XX:+UseStringDeduplication");
        command.add("-XX:+UseCompressedOops");
        command.add("-XX:+UseCompactObjectHeaders");
        command.add("-XX:+TieredCompilation");
        command.add("-XX:TieredStopAtLevel=4");
        
        for (String rf : JvmOptimizer.getG1MemoryReclaimFlags()) {
            command.add(rf);
        }
        
        if (tipo.equalsIgnoreCase("gameserver") || tipo.equalsIgnoreCase("loginserver"))
        {
            command.add("-XX:+AutoCreateSharedArchive");
            command.add("-XX:SharedArchiveFile=cache/brproject_cds.jsa");
            command.add("-Xlog:cds=error");
        }

        command.add("-cp");
        command.add(cpString);
        command.add(mainClass);
        
        if (tipo.equals("gameserver")) {
            command.add(licenseKey);
            command.add(userEmail);
        }

        // Desabilitado: ocultando log do comando JVM para manter painel limpo
        // System.out.println("\n--- COMANDO JVM OTIMIZADO ---");
        // System.out.println(String.join(" ", command));
        // System.out.println("-----------------------------\n");

        new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(diretorioExecucao);
                pb.redirectErrorStream(true);
                Process processo = pb.start();
                if ("gameserver".equalsIgnoreCase(tipo)) {
                    this.gameServerProcess = processo;
                } else if ("loginserver".equalsIgnoreCase(tipo)) {
                    this.loginServerProcess = processo;
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(processo.getInputStream(), StandardCharsets.UTF_8))) {
                    String linha;
                    while ((linha = reader.readLine()) != null) {
                        System.out.println("[" + tipo.toUpperCase() + "] " + linha);
                    }
                }

                int exitCode = processo.waitFor();
                
                if (!isShuttingDown && exitCode == 2) {
                    System.out.println("Reiniciando servidor...");
                    Thread.sleep(1000);
                    if (!isShuttingDown) {
                        iniciarProcesso(tipo, licenseKey, userEmail, isLightModeEnabled, frame);
                    }
                } 
                else if (!isShuttingDown && exitCode != 0) {
                    SwingUtilities.invokeLater(() -> 
                        JOptionPane.showMessageDialog(frame, 
                            "Erro no servidor (Código " + exitCode + ").", 
                            "Erro", JOptionPane.ERROR_MESSAGE)
                    );
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if ("gameserver".equalsIgnoreCase(tipo)) {
                    this.gameServerProcess = null;
                } else if ("loginserver".equalsIgnoreCase(tipo)) {
                    this.loginServerProcess = null;
                }
            }
        }).start();
    }

    public String getBrprojectTunnelUrl() {
        return BrprojectTunnelUrl;
    }

    public boolean isBrprojectRunning() {
        return BrprojectProcess != null && BrprojectProcess.isAlive();
    }

    private File findBrprojectdExecutable(File projectRoot) {
        String exeExt = isWindows() ? ".exe" : "";
        File[] candidates = new File[] {
            new File(projectRoot, "bin/cloudflared" + exeExt),
            new File(projectRoot, "bin/Brprojectd" + exeExt),
            new File(projectRoot, "data/cloudflared" + exeExt),
            new File(projectRoot, "cloudflared" + exeExt),
            new File("bin/cloudflared" + exeExt)
        };
        for (File f : candidates) {
            if (f.exists() && f.isFile()) {
                return f;
            }
        }
        return null;
    }

    public static final java.util.regex.Pattern CLOUDFLARE_URL_PATTERN =
        java.util.regex.Pattern.compile("https://(?!api\\.)[a-zA-Z0-9-]+\\.trycloudflare\\.com", java.util.regex.Pattern.CASE_INSENSITIVE);

    public static String normalizeCustomTunnelUrl(String domainOrUrl) {
        if (domainOrUrl == null || domainOrUrl.isBlank()) {
            return null;
        }
        String clean = domainOrUrl.trim();
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1).trim();
        }
        if (clean.isEmpty()) {
            return null;
        }
        if (clean.toLowerCase().startsWith("http://")) {
            clean = "https://" + clean.substring(7);
        } else if (!clean.toLowerCase().startsWith("https://")) {
            clean = "https://" + clean;
        }
        return clean;
    }

    public static List<String> buildCloudflareCommand(String exePath, String sitePort, String token) {
        if (token != null && !token.isBlank()) {
            return List.of(exePath, "tunnel", "--no-autoupdate", "run", "--token", token.trim());
        }
        return List.of(exePath, "tunnel", "--no-autoupdate", "--url", "http://127.0.0.1:" + sitePort);
    }

    public static String extractValidCloudflareTunnelUrl(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        java.util.regex.Matcher m = CLOUDFLARE_URL_PATTERN.matcher(line);
        while (m.find()) {
            String found = m.group().trim();
            if (!found.toLowerCase().contains("api.trycloudflare.com") && !found.equalsIgnoreCase("https://trycloudflare.com")) {
                return found;
            }
        }
        return null;
    }

    public void startBrprojectTunnel(File projectRoot, String sitePort) {
        File exe = findBrprojectdExecutable(projectRoot);
        if (exe == null) {
            System.out.println("[CLOUDFLARE] Executável cloudflared.exe não encontrado em bin/. Túnel automático ignorado.");
            return;
        }

        if (isBrprojectRunning()) {
            return;
        }

        Properties serverProps = loadGameServerDbProperties(projectRoot);
        boolean tunnelEnabled = Boolean.parseBoolean(serverProps.getProperty("CloudflareTunnelEnabled", "true").trim());
        if (!tunnelEnabled) {
            SiteKtorLogManager.getInstance().addLog("INFO", "CLOUDFLARE", "Túnel Cloudflare desativado em server.properties (CloudflareTunnelEnabled=false).");
            System.out.println("[CLOUDFLARE] Túnel desativado por configuração em server.properties.");
            return;
        }

        String rawProjectName = serverProps.getProperty("SiteProjectName", "Lineage2 NewEra").trim();
        final String projectName = rawProjectName.isEmpty() ? "Lineage2 NewEra" : rawProjectName;
        final String customDomain = serverProps.getProperty("CloudflareTunnelCustomDomain", "").trim();
        final String token = serverProps.getProperty("CloudflareTunnelToken", "").trim();
        final String customUrl = normalizeCustomTunnelUrl(customDomain);

        if (customUrl != null) {
            BrprojectTunnelUrl = customUrl;
            SiteKtorLogManager.getInstance().addLog("INFO", "CLOUDFLARE", "[" + projectName + "] Site online na Internet (Domínio Oficial): " + BrprojectTunnelUrl);
            System.out.println("[CLOUDFLARE] [" + projectName + "] Domínio oficial configurado: " + BrprojectTunnelUrl);

            String endpointKey = loadVoteEndpointKey(projectRoot);
            String webhookUrl = BrprojectTunnelUrl + "/api/vote/webhook/l2jbrasil" +
                (endpointKey != null && !endpointKey.isBlank() ? "?key=" + endpointKey : "");
            SiteKtorLogManager.getInstance().addLog("INFO", "CLOUDFLARE", "[" + projectName + "] URL Webhook Top L2JBrasil: " + webhookUrl);
            System.out.println("[CLOUDFLARE] [" + projectName + "] URL Webhook Top L2JBrasil: " + webhookUrl);
        }

        new Thread(() -> {
            int maxAttempts = token.isBlank() && customUrl == null ? 3 : 1;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                if (stoppingSite) {
                    break;
                }
                if (attempt > 1) {
                    SiteKtorLogManager.getInstance().addLog("INFO", "CLOUDFLARE", "[" + projectName + "] Tentando reconectar túnel Cloudflare (tentativa " + attempt + "/" + maxAttempts + ") em 5 segundos...");
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }

                boolean tunnelEstablished = false;
                try {
                    List<String> cmd = buildCloudflareCommand(exe.getAbsolutePath(), sitePort, token);
                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    pb.directory(projectRoot);
                    pb.redirectErrorStream(true);
                    BrprojectProcess = pb.start();
                    SiteKtorLogManager.getInstance().addLog("INFO", "CLOUDFLARE", "[" + projectName + "] Iniciando túnel Cloudflare para porta " + sitePort + (token.isBlank() ? "..." : " [Zero Trust Token]..."));

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(BrprojectProcess.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.contains("ERR") || line.contains("failed to request quick Tunnel") || line.contains("error=")) {
                                SiteKtorLogManager.getInstance().addLog("WARN", "CLOUDFLARE", line);
                                if (line.contains("context deadline exceeded") || line.contains("failed to request quick Tunnel")) {
                                    SiteKtorLogManager.getInstance().addLog("WARN", "CLOUDFLARE", "A API de Quick Tunnels da Cloudflare (trycloudflare.com) atingiu timeout de 15s (instabilidade ou rate limit temporário no ISP).");
                                    SiteKtorLogManager.getInstance().addLog("INFO", "CLOUDFLARE", "Dica: Configure 'CloudflareTunnelToken' em server.properties para conexão permanente com domínio oficial sem depender de túneis efêmeros.");
                                }
                            }

                            if (customUrl == null) {
                                String validUrl = extractValidCloudflareTunnelUrl(line);
                                if (validUrl != null && !validUrl.equalsIgnoreCase(BrprojectTunnelUrl)) {
                                    BrprojectTunnelUrl = validUrl;
                                    tunnelEstablished = true;
                                    SiteKtorLogManager.getInstance().addLog("INFO", "CLOUDFLARE", "[" + projectName + "] Site online na Internet: " + BrprojectTunnelUrl);
                                    System.out.println("[CLOUDFLARE] [" + projectName + "] Túnel ativo: " + BrprojectTunnelUrl);

                                    String endpointKey = loadVoteEndpointKey(projectRoot);
                                    String webhookUrl = BrprojectTunnelUrl + "/api/vote/webhook/l2jbrasil" +
                                        (endpointKey != null && !endpointKey.isBlank() ? "?key=" + endpointKey : "");
                                    SiteKtorLogManager.getInstance().addLog("INFO", "CLOUDFLARE", "[" + projectName + "] URL Webhook Top L2JBrasil: " + webhookUrl);
                                    System.out.println("[CLOUDFLARE] [" + projectName + "] URL Webhook Top L2JBrasil: " + webhookUrl);
                                }
                            } else {
                                tunnelEstablished = true;
                            }
                        }
                    }

                    if (BrprojectProcess != null) {
                        BrprojectProcess.waitFor();
                    }
                } catch (Exception e) {
                    SiteKtorLogManager.getInstance().addLog("WARN", "CLOUDFLARE", "Erro no túnel Cloudflare: " + e.getMessage());
                } finally {
                    if (BrprojectProcess != null) {
                        try {
                            BrprojectProcess.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
                            BrprojectProcess.destroyForcibly();
                        } catch (Exception ignored) {
                        }
                    }
                    BrprojectProcess = null;
                }

                if (tunnelEstablished) {
                    break;
                }
            }

            if (customUrl == null) {
                BrprojectTunnelUrl = null;
            }
        }, "Cloudflare-Tunnel-Process").start();
    }

    private String loadVoteEndpointKey(File projectRoot) {
        try {
            String key = ext.mods.config.ConfigVoteL2JBrasil.TOP_L2JBRASIL_ENDPOINT_KEY;
            if (key != null && !key.trim().isEmpty()) {
                return key.trim();
            }
        } catch (Throwable ignored) {
        }

        File[] candidates = new File[] {
            new File(projectRoot, "game/config/votel2jbrasil.properties"),
            new File("game/config/votel2jbrasil.properties"),
            new File("config/votel2jbrasil.properties")
        };

        for (File f : candidates) {
            if (f.exists() && f.isFile()) {
                Properties props = new Properties();
                try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
                    props.load(fis);
                    String key = props.getProperty("TopL2jBrasilEndpointKey", "").trim();
                    if (!key.isEmpty()) {
                        return key;
                    }
                } catch (Exception e) {
                    System.err.println("[Brproject] Falha ao ler " + f.getPath() + ": " + e.getMessage());
                }
            }
        }
        return "";
    }

    public void stopBrprojectTunnel() {
        if (BrprojectProcess != null) {
            try {
                SiteKtorLogManager.getInstance().addLog("INFO", "CLOUDFLARE", "Encerrando túnel Cloudflare...");
                BrprojectProcess.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
                BrprojectProcess.destroy();
                if (BrprojectProcess.isAlive()) {
                    BrprojectProcess.destroyForcibly();
                }
            } catch (Exception ignored) {
            }
            BrprojectProcess = null;
            BrprojectTunnelUrl = null;
        }
    }

    private Process nativeProxyProcess;

    public boolean isNativeProxyRunning() {
        return nativeProxyProcess != null && nativeProxyProcess.isAlive();
    }

    private static File resolveProjectRoot(File baseDir) {
        if (baseDir == null) {
            baseDir = new File(".");
        }
        File candidate = baseDir.getAbsoluteFile();
        if (new File(candidate, "gradlew.bat").exists() || new File(candidate, "gradlew").exists()) {
            return candidate;
        }
        File parent = candidate.getParentFile();
        if (parent != null && (new File(parent, "gradlew.bat").exists() || new File(parent, "gradlew").exists())) {
            return parent;
        }
        return candidate;
    }

    public void startNativeProxy(File projectRoot) {
        if (isNativeProxyRunning()) {
            return;
        }

        final File effectiveRoot = resolveProjectRoot(projectRoot);

        new Thread(() -> {
            try {
                ext.mods.security.fail2ban.gui.ProxyLogTailer.appendLog("Iniciando Proxy Nativo Netty (:proxy)...");
                System.out.println("[PROXY-NETTY] Iniciando Proxy Nativo Netty...");

                List<String> cmd;
                if (isWindows()) {
                    File gradlew = new File(effectiveRoot, "gradlew.bat");
                    cmd = gradlew.exists() ? List.of(gradlew.getAbsolutePath(), ":proxy:run", "--quiet") : List.of("cmd.exe", "/c", "gradlew.bat", ":proxy:run", "--quiet");
                } else {
                    File gradlew = new File(effectiveRoot, "gradlew");
                    cmd = gradlew.exists() ? List.of(gradlew.getAbsolutePath(), ":proxy:run", "--quiet") : List.of("sh", "-c", "./gradlew :proxy:run --quiet");
                }

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(effectiveRoot);
                pb.redirectErrorStream(true);
                nativeProxyProcess = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(nativeProxyProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[PROXY] " + line);
                        ext.mods.security.fail2ban.gui.ProxyLogTailer.appendLog(line);
                        ext.mods.security.fail2ban.core.ConnectionTracker.parseAndRecordFromLog(line, "Proxy-Netty");
                        ext.mods.security.fail2ban.core.ProxySecurityBridge.processLine(line);
                    }
                }
            } catch (Exception e) {
                System.err.println("[PROXY-NETTY] Erro no Proxy Nativo Netty: " + e.getMessage());
                ext.mods.security.fail2ban.gui.ProxyLogTailer.appendLog("[WARN] Erro no Proxy Netty: " + e.getMessage());
            } finally {
                nativeProxyProcess = null;
            }
        }, "NettyProxy-Process").start();
    }

    public boolean isGameServerRunning() {
        return gameServerProcess != null && gameServerProcess.isAlive();
    }

    public boolean isLoginServerRunning() {
        return loginServerProcess != null && loginServerProcess.isAlive();
    }

    public void stopGameServer() {
        if (gameServerProcess != null) {
            try {
                ext.mods.commons.util.ShutdownSignalWatcher.sendSignal("gameserver");
            } catch (Throwable ignored) {}
            stopProcessSafely(gameServerProcess, "GAMESERVER", 10);
            gameServerProcess = null;
        }
    }

    public void stopLoginServer() {
        if (loginServerProcess != null) {
            try {
                ext.mods.commons.util.ShutdownSignalWatcher.sendSignal("loginserver");
            } catch (Throwable ignored) {}
            stopProcessSafely(loginServerProcess, "LOGINSERVER", 10);
            loginServerProcess = null;
        }
    }

    public void stopNativeProxy() {
        if (nativeProxyProcess != null) {
            ext.mods.security.fail2ban.gui.ProxyLogTailer.appendLog("Encerrando Proxy Nativo Netty...");
            stopProcessSafely(nativeProxyProcess, "PROXY-NETTY", 3);
            nativeProxyProcess = null;
        }
    }

    public synchronized void stopAllServices(boolean isForcedExit) {
        isShuttingDown = true;
        System.out.println("[SHUTDOWN] Orquestrando desligamento completo de todos os servicos do BR Project...");

        // 0. Emite sinal IPC cooperativo para encerramento gracioso (mesmo com processos elevados como Admin)
        try {
            ext.mods.commons.util.ShutdownSignalWatcher.sendSignalAll();
        } catch (Throwable ignored) {}

        // 1. Tráfego externo e túneis
        try {
            stopBrprojectTunnel();
        } catch (Throwable ignored) {}

        try {
            stopNativeProxy();
        } catch (Throwable ignored) {}

        // 2. Servidores Core
        try {
            stopGameServer();
        } catch (Throwable ignored) {}

        try {
            stopLoginServer();
        } catch (Throwable ignored) {}

        // 3. Site Ktor (síncrono no desligamento global)
        try {
            stopSite(true);
        } catch (Throwable ignored) {}

        // 4. Fail2Ban / SQLite
        try {
            ext.mods.security.fail2ban.core.BanManager bm = ext.mods.security.fail2ban.core.BanManager.getInstance();
            if (bm != null) {
                bm.shutdown();
            }
        } catch (Throwable ignored) {}

        // 5. Varredura final de segurança para garantir 0 processos órfãos
        try {
            killRemainingBrProjectProcesses();
        } catch (Throwable ignored) {}

        // 6. Limpa arquivos de sinal residual
        try {
            ext.mods.commons.util.ShutdownSignalWatcher.cleanSignals();
        } catch (Throwable ignored) {}

        System.out.println("[SHUTDOWN] Todos os servicos foram desligados com sucesso.");
    }

    public void killRemainingBrProjectProcesses() {
        try {
            java.util.List<ext.mods.commons.util.JavaProcessInspector.JavaProcessInfo> zombies =
                ext.mods.commons.util.JavaProcessInspector.findBrProjectProcesses();
            if (!zombies.isEmpty()) {
                System.out.println("[SHUTDOWN] Encontrados " + zombies.size() + " processos residuais Java. Finalizando...");
                ext.mods.commons.util.JavaProcessInspector.terminateOrphanProcesses(zombies);
            }
        } catch (Throwable t) {
            System.err.println("[SHUTDOWN] Erro ao varrer processos residuais: " + t.getMessage());
        }
    }

    /**
     * Valida e gerencia o estado dos arquivos brproject_cds.gc e brproject_cds.jsa
     * antes de invocar LoginServer ou GameServer para garantir a integridade do AppCDS.
     */
    private void validateAndPrepareAppCds(File executionDir, String tipo) {
        try {
            File cacheDir = new File(executionDir, "cache");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }

            File metaFile = new File(cacheDir, "brproject_cds.gc");
            File jsaFile = new File(cacheDir, "brproject_cds.jsa");
            File serverJar = new File(executionDir, "../libs/server.jar");

            String expectedGcMode = "G1";

            // 1. Verifica consistência do modo GC
            if (metaFile.exists()) {
                try {
                    String oldMode = java.nio.file.Files.readString(metaFile.toPath(), StandardCharsets.UTF_8).trim();
                    if (!expectedGcMode.equalsIgnoreCase(oldMode)) {
                        if (jsaFile.exists()) {
                            jsaFile.delete();
                            System.out.println("[AppCDS-" + tipo.toUpperCase() + "] Snapshot removido - modo GC alterado de " + oldMode + " para " + expectedGcMode + ".");
                        }
                    }
                } catch (Exception ignored) {}
            }
            java.nio.file.Files.writeString(metaFile.toPath(), expectedGcMode, StandardCharsets.UTF_8);

            // 2. Verifica se o snapshot .jsa está corrompido ou incompleto (< 32KB)
            if (jsaFile.exists()) {
                long size = jsaFile.length();
                if (size < 32768) {
                    jsaFile.delete();
                    System.out.println("[AppCDS-" + tipo.toUpperCase() + "] Snapshot removido - arquivo .jsa corrompido ou incompleto (" + size + " bytes).");
                } else if (serverJar.exists() && serverJar.lastModified() > jsaFile.lastModified()) {
                    jsaFile.delete();
                    System.out.println("[AppCDS-" + tipo.toUpperCase() + "] Snapshot removido - server.jar foi recompilado/atualizado.");
                } else {
                    double sizeKb = size / 1024.0;
                    System.out.println("[AppCDS-" + tipo.toUpperCase() + "] Snapshot valido detectado (" + String.format(java.util.Locale.US, "%.1f", sizeKb) + " KB). Boot acelerado <15s ATIVO!");
                }
            } else {
                System.out.println("[AppCDS-" + tipo.toUpperCase() + "] Snapshot .jsa nao encontrado. Modo de Treinamento ativo: sera gerado no shutdown gracioso.");
            }
        } catch (Exception e) {
            System.err.println("[AppCDS-" + tipo.toUpperCase() + "] Erro ao validar AppCDS: " + e.getMessage());
        }
    }
}
