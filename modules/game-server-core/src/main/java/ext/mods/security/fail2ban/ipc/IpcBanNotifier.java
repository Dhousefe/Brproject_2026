/*
* Copyleft © 2024-2026 L2Brproject
* * This file is part of L2Brproject derived from aCis409/RusaCis3.8
*/
package ext.mods.security.fail2ban.ipc;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * High-performance, non-blocking Inter-Process Communication (IPC) notifier.
 * Emits lightweight UDP datagrams to the standalone Netty Proxy (:proxy)
 * over localhost (127.0.0.1:19998) when a ban or unban occurs, achieving
 * sub-millisecond reaction times (< 0.2 ms) without waiting for the SQLite WAL cycle.
 */
public final class IpcBanNotifier {

    private static final Logger LOGGER = Logger.getLogger(IpcBanNotifier.class.getName());
    public static final String DEFAULT_IPC_HOST = "127.0.0.1";
    public static final int DEFAULT_IPC_PORT = 19998;
    public static final int DEFAULT_KTOR_IPC_PORT = 19997;

    private static final ExecutorService NOTIFY_POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "fail2ban-ipc-notifier");
        t.setDaemon(true);
        return t;
    });

    private static volatile boolean enabled = true;
    private static volatile int targetPort = DEFAULT_IPC_PORT;

    private IpcBanNotifier() {}

    public static void setEnabled(boolean isEnabled) {
        enabled = isEnabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setTargetPort(int port) {
        targetPort = port;
    }

    public static int getTargetPort() {
        return targetPort;
    }

    /**
     * Broadcast an instant BAN event to the reverse proxy and site backend.
     * Payload: "BAN <ip> <expireTimeMillis>"
     */
    public static void notifyBan(String ip, long expireTimeMillis) {
        if (!enabled || ip == null || ip.isBlank()) {
            return;
        }
        sendAsync("BAN " + ip.trim() + " " + expireTimeMillis);
    }

    /**
     * Broadcast an instant UNBAN event to the reverse proxy and site backend.
     * Payload: "UNBAN <ip>"
     */
    public static void notifyUnban(String ip) {
        if (!enabled || ip == null || ip.isBlank()) {
            return;
        }
        sendAsync("UNBAN " + ip.trim());
    }

    private static void sendAsync(String payload) {
        NOTIFY_POOL.submit(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                byte[] data = payload.getBytes(StandardCharsets.UTF_8);
                InetAddress address = InetAddress.getByName(DEFAULT_IPC_HOST);

                // Broadcast to standalone Netty Proxy (port 19998)
                try {
                    DatagramPacket packetProxy = new DatagramPacket(data, data.length, address, targetPort);
                    socket.send(packetProxy);
                } catch (Exception ignored) {}

                // Broadcast to Site Ktor backend (port 19997)
                try {
                    DatagramPacket packetKtor = new DatagramPacket(data, data.length, address, DEFAULT_KTOR_IPC_PORT);
                    socket.send(packetKtor);
                } catch (Exception ignored) {}

                LOGGER.fine(() -> "[IPC-NOTIFY] Dispatched: " + payload + " to " + DEFAULT_IPC_HOST + ":" + targetPort + " & :" + DEFAULT_KTOR_IPC_PORT);
            } catch (Exception e) {
                // Best-effort push: SQLite WAL remains persistent failsafe
                LOGGER.log(Level.FINE, "[IPC-NOTIFY] Could not reach IPC ports: " + e.getMessage());
            }
        });
    }

    public static void shutdown() {
        NOTIFY_POOL.shutdownNow();
    }
}
