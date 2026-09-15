/*
* Copyleft © 2024-2026 L2Brproject
* Detecta processos Java rodando (zumbis/antigos) que podem impedir
* a inicializacao correta de novos LoginServer/GameServer.
*/
package ext.mods.commons.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Detecta processos Java em execucao filtrando por classpath/main class do BR Project.
 *
 * <p>Plataforma-alvo: Windows (usa tasklist + wmic). Em outras plataformas,
 * lanca {@link UnsupportedOperationException} - comportamento intencional,
 * projeto suporta apenas Windows oficialmente.</p>
 */
public final class JavaProcessInspector {

    private static final String BR_PROJECT_LOGIN_MAIN = "ext.mods.loginserver.LoginServer";
    private static final String BR_PROJECT_GAME_MAIN = "ext.mods.gameserver.GameServer";

    private JavaProcessInspector() {}

    /** Tipo de servidor detectado num processo java. */
    public enum ServerType {
        LOGIN_SERVER("LoginServer"),
        GAME_SERVER("GameServer"),
        UNKNOWN("Java (BR Project)");

        private final String label;
        ServerType(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    /** Informacao de um processo java do BR Project. */
    public static final class JavaProcessInfo {
        public final int pid;
        public final String commandLine;
        public final ServerType type;

        JavaProcessInfo(int pid, String commandLine, ServerType type) {
            this.pid = pid;
            this.commandLine = commandLine;
            this.type = type;
        }

        public boolean isBrProject() { return type != ServerType.UNKNOWN || commandLine.contains("Brproject_Distribution"); }
    }

    /**
     * Lista todos os processos java que parecem ser do BR Project
     * (LoginServer, GameServer, ou classpath contem Brproject_Distribution).
     */
    public static List<JavaProcessInfo> findBrProjectProcesses() {
        List<JavaProcessInfo> result = new ArrayList<>();
        long currentPid = ProcessHandle.current().pid();

        // 1. Varredura via ProcessHandle API (Java 9+ / Java 25 LTS)
        try {
            ProcessHandle.allProcesses().forEach(ph -> {
                if (ph.pid() == currentPid || !ph.isAlive()) return;
                ph.info().commandLine().ifPresent(cmd -> {
                    if (cmd.contains(BR_PROJECT_LOGIN_MAIN) || cmd.contains(BR_PROJECT_GAME_MAIN) || (cmd.contains("java") && cmd.contains("Brproject_Distribution"))) {
                        int pId = (int) ph.pid();
                        boolean alreadyFound = result.stream().anyMatch(i -> i.pid == pId);
                        if (!alreadyFound) {
                            ServerType type = ServerType.UNKNOWN;
                            if (cmd.contains(BR_PROJECT_LOGIN_MAIN)) type = ServerType.LOGIN_SERVER;
                            else if (cmd.contains(BR_PROJECT_GAME_MAIN)) type = ServerType.GAME_SERVER;
                            result.add(new JavaProcessInfo(pId, cmd, type));
                        }
                    }
                });
            });
        } catch (Throwable ignored) {}

        if (!isWindows()) return result;

        // 2. Complemento via WMIC no Windows (caso commandLine via ProcessHandle esteja mascarado pelo SO)
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "wmic", "process", "where",
                "name='java.exe'",
                "get", "ProcessId,CommandLine", "/FORMAT:CSV"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                boolean skipHeader = true;
                while ((line = r.readLine()) != null) {
                    if (skipHeader) { skipHeader = false; continue; }
                    if (line.isBlank()) continue;
                    JavaProcessInfo info = parseWmicLine(line);
                    if (info != null && info.isBrProject() && info.pid != currentPid) {
                        boolean already = result.stream().anyMatch(x -> x.pid == info.pid);
                        if (!already) result.add(info);
                    }
                }
            }
            p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (IOException | InterruptedException ignored) {
            // fallback silencioso
        }
        return result;
    }

    /**
     * Encerra de forma forçada e determinística processos Java órfãos do BR Project.
     */
    public static void terminateOrphanProcesses(List<JavaProcessInfo> processes) {
        if (processes == null || processes.isEmpty()) return;
        long currentPid = ProcessHandle.current().pid();
        for (JavaProcessInfo p : processes) {
            if (p.pid == currentPid) continue;
            try {
                System.out.println("[JavaProcessInspector] Encerrando processo residual PID " + p.pid + " (" + p.type.getLabel() + ")...");
                ProcessHandle.of(p.pid).ifPresent(handle -> {
                    try {
                        handle.descendants().forEach(ProcessHandle::destroyForcibly);
                    } catch (Exception ignored) {}
                    handle.destroyForcibly();
                });
                if (isWindows()) {
                    ProcessBuilder pb = new ProcessBuilder("taskkill", "/F", "/T", "/PID", Integer.toString(p.pid));
                    pb.redirectErrorStream(true);
                    Process killer = pb.start();
                    killer.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);

                    // Se o processo ainda continuar vivo (ex: processo elevado como Administrador),
                    // executa fallback com elevação administrativa via PowerShell
                    if (ProcessHandle.of(p.pid).map(ProcessHandle::isAlive).orElse(false)) {
                        System.out.println("[JavaProcessInspector] PID " + p.pid + " ainda ativo (processo Administrador). Executando encerramento com elevação...");
                        try {
                            String powershellCmd = "Start-Process taskkill -ArgumentList '/F /T /PID " + p.pid + "' -Verb RunAs -WindowStyle Hidden -Wait";
                            ProcessBuilder pbElevated = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", powershellCmd);
                            Process elevatedKiller = pbElevated.start();
                            elevatedKiller.waitFor(6, java.util.concurrent.TimeUnit.SECONDS);
                        } catch (Exception exElevated) {
                            System.err.println("[JavaProcessInspector] Falha no fallback elevado para PID " + p.pid + ": " + exElevated.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[JavaProcessInspector] Falha ao encerrar PID " + p.pid + ": " + e.getMessage());
            }
        }
    }

    private static JavaProcessInfo parseWmicLine(String csvLine) {
        // formato CSV do wmic: Node,CommandLine,ProcessId
        // CommandLine pode conter virgulas e aspas - parser simples tolerante
        int lastQuoteEnd = csvLine.lastIndexOf("\",");
        if (lastQuoteEnd < 0) return null;
        int pidStart = lastQuoteEnd + 2;
        if (pidStart >= csvLine.length()) return null;
        int pid;
        try { pid = Integer.parseInt(csvLine.substring(pidStart).trim()); }
        catch (NumberFormatException e) { return null; }

        int firstQuote = csvLine.indexOf('"');
        int secondQuote = csvLine.indexOf('"', firstQuote + 1);
        if (firstQuote < 0 || secondQuote < 0) return null;
        String cmd = csvLine.substring(firstQuote + 1, secondQuote);

        ServerType type = ServerType.UNKNOWN;
        if (cmd.contains(BR_PROJECT_LOGIN_MAIN)) type = ServerType.LOGIN_SERVER;
        else if (cmd.contains(BR_PROJECT_GAME_MAIN)) type = ServerType.GAME_SERVER;

        return new JavaProcessInfo(pid, cmd, type);
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }
}
