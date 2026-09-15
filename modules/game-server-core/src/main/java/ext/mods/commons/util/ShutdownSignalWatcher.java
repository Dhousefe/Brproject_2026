/*
* Copyleft © 2024-2026 L2Brproject
* Utilitário de sinalização IPC cooperativa para encerramento gracioso
* de servidores (especialmente quando executados como Administrador).
*/
package ext.mods.commons.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ShutdownSignalWatcher {

    private static final String SIGNAL_ALL = "shutdown_all.sig";
    private static final String FLAG_DIR = "flag";

    private ShutdownSignalWatcher() {}

    /**
     * Inicia uma thread daemon leve que monitora periodicamente a existência
     * de um arquivo sentinel de encerramento em flag/ ou ../flag/.
     *
     * @param serviceName nome do servico (ex: "gameserver", "loginserver")
     * @param onShutdownTriggered acao a ser executada ao detectar o sinal
     */
    public static Thread startWatcher(String serviceName, Runnable onShutdownTriggered) {
        AtomicBoolean triggered = new AtomicBoolean(false);
        Thread watcher = new Thread(() -> {
            List<File> candidates = resolveSignalFiles(serviceName);
            while (!triggered.get()) {
                try {
                    for (File candidate : candidates) {
                        if (candidate.exists()) {
                            if (triggered.compareAndSet(false, true)) {
                                System.out.println("[SHUTDOWN-WATCHER] Sinal de parada detectado em "
                                        + candidate.getAbsolutePath() + ". Encerrando '" + serviceName + "' com segurança...");
                                try {
                                    candidate.delete();
                                } catch (Exception ignored) {}

                                try {
                                    onShutdownTriggered.run();
                                } catch (Throwable t) {
                                    System.err.println("[SHUTDOWN-WATCHER] Erro ao executar callback de shutdown: " + t.getMessage());
                                }
                                return;
                            }
                        }
                    }
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception ignored) {}
            }
        }, "BrProject-ShutdownWatcher-" + serviceName);

        watcher.setDaemon(true);
        watcher.setPriority(Thread.MIN_PRIORITY);
        watcher.start();
        return watcher;
    }

    /**
     * Emite o sinal de encerramento para todos os serviços (GameServer e LoginServer).
     */
    public static void sendSignalAll() {
        sendSignal("all");
        sendSignal("gameserver");
        sendSignal("loginserver");
    }

    /**
     * Emite o sinal de encerramento para um serviço específico gravando o arquivo .sig.
     */
    public static void sendSignal(String serviceName) {
        String fileName = "all".equalsIgnoreCase(serviceName) ? SIGNAL_ALL : ("shutdown_" + serviceName.toLowerCase() + ".sig");
        File dir = resolveFlagDirectory();
        if (dir != null) {
            try {
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File signalFile = new File(dir, fileName);
                signalFile.createNewFile();
                System.out.println("[SHUTDOWN-WATCHER] Sinal emitido em: " + signalFile.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("[SHUTDOWN-WATCHER] Falha ao criar sinal " + fileName + ": " + e.getMessage());
            }
        }
    }

    /**
     * Remove quaisquer arquivos de sinal residuais para evitar encerramento acidental na inicialização.
     */
    public static void cleanSignals() {
        File dir = resolveFlagDirectory();
        if (dir != null && dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(".sig"));
            if (files != null) {
                for (File f : files) {
                    try {
                        f.delete();
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private static File resolveFlagDirectory() {
        File dir1 = new File(FLAG_DIR);
        if (dir1.exists()) return dir1;
        File dir2 = new File(".." + File.separator + FLAG_DIR);
        if (dir2.exists()) return dir2;
        // fallback: cria na raiz atual
        dir1.mkdirs();
        return dir1;
    }

    private static List<File> resolveSignalFiles(String serviceName) {
        List<File> list = new ArrayList<>();
        String specific = "shutdown_" + serviceName.toLowerCase() + ".sig";

        list.add(new File(FLAG_DIR, SIGNAL_ALL));
        list.add(new File(".." + File.separator + FLAG_DIR, SIGNAL_ALL));

        list.add(new File(FLAG_DIR, specific));
        list.add(new File(".." + File.separator + FLAG_DIR, specific));

        return list;
    }
}
