package br.project.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe in-memory cache of banned IPs for ultra-low latency (O(1)) edge checking.
 * Periodically synchronizes active bans from fail2ban.sqlite in WAL mode.
 */
public final class ProxyBanCache implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ProxyBanCache.class);
    private static final ProxyBanCache INSTANCE = new ProxyBanCache();

    public static ProxyBanCache getInstance() {
        return INSTANCE;
    }

    // IP -> Expire timestamp in millis (<= 0 means permanent)
    private final ConcurrentHashMap<String, Long> activeBans = new ConcurrentHashMap<>();
    private ScheduledExecutorService syncExecutor;
    private String resolvedDbPath;
    private java.net.DatagramSocket ipcSocket;
    private Thread ipcListenerThread;
    private volatile boolean running = false;

    private ProxyBanCache() {
        resolveDbPath();
    }

    private void resolveDbPath() {
        String[] candidates = {
            "data/fail2ban.sqlite",
            "game/data/fail2ban.sqlite",
            "../data/fail2ban.sqlite"
        };
        for (String c : candidates) {
            if (Files.exists(Path.of(c))) {
                this.resolvedDbPath = c;
                break;
            }
        }
        if (resolvedDbPath == null) {
            resolvedDbPath = "data/fail2ban.sqlite";
        }
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        syncFromDatabase();
        syncExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "proxy-bancache-sync");
            t.setDaemon(true);
            return t;
        });
        syncExecutor.scheduleWithFixedDelay(this::syncFromDatabase, 30, 30, TimeUnit.SECONDS);
        startIpcListener();
    }

    private void startIpcListener() {
        try {
            // Bind to loopback 127.0.0.1 on port 19998
            java.net.InetAddress loopback = java.net.InetAddress.getByName("127.0.0.1");
            ipcSocket = new java.net.DatagramSocket(19998, loopback);
            ipcListenerThread = new Thread(this::listenIpcLoop, "proxy-ipc-listener");
            ipcListenerThread.setDaemon(true);
            ipcListenerThread.start();
            LOG.info("[proxy/bancache] IPC socket listener active on 127.0.0.1:19998 (<0.2ms latency)");
        } catch (Exception e) {
            LOG.warn("[proxy/bancache] Could not bind IPC socket on 127.0.0.1:19998 (fallback to SQLite WAL sync): {}", e.getMessage());
        }
    }

    private void listenIpcLoop() {
        byte[] buffer = new byte[512];
        while (running && ipcSocket != null && !ipcSocket.isClosed()) {
            try {
                java.net.DatagramPacket packet = new java.net.DatagramPacket(buffer, buffer.length);
                ipcSocket.receive(packet);
                String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(), java.nio.charset.StandardCharsets.UTF_8).trim();
                handleIpcMessage(msg);
            } catch (java.net.SocketException se) {
                // Expected when socket is closed on shutdown
                break;
            } catch (Throwable t) {
                if (running) {
                    LOG.debug("[proxy/bancache] IPC receive error: {}", t.getMessage());
                }
            }
        }
    }

    public void handleIpcMessage(String msg) {
        if (msg == null || msg.isBlank()) {
            return;
        }
        String[] parts = msg.split("\\s+");
        if (parts.length >= 2) {
            String cmd = parts[0].toUpperCase(java.util.Locale.ROOT);
            String ip = parts[1].trim();
            if ("BAN".equals(cmd)) {
                long expire = 0L;
                if (parts.length >= 3) {
                    try {
                        expire = Long.parseLong(parts[2]);
                    } catch (NumberFormatException ignored) {}
                }
                addBan(ip, expire);
                LOG.info("[proxy/bancache] Instant IPC BAN applied for IP: {} (expires: {})", ip, expire);
            } else if ("UNBAN".equals(cmd)) {
                removeBan(ip);
                LOG.info("[proxy/bancache] Instant IPC UNBAN applied for IP: {}", ip);
            }
        }
    }

    private static final String[] LOOPBACK_VARIANTS = {"127.0.0.1", "0:0:0:0:0:0:0:1", "::1", "localhost"};

    private static boolean isLoopback(String ip) {
        return "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip) || "localhost".equalsIgnoreCase(ip);
    }

    public boolean isBanned(String ip) {
        if (ip == null || ip.isEmpty() || activeBans.isEmpty()) {
            return false;
        }
        Long expireTime = activeBans.get(ip);
        if (expireTime == null && isLoopback(ip)) {
            for (String v : LOOPBACK_VARIANTS) {
                expireTime = activeBans.get(v);
                if (expireTime != null) break;
            }
        }
        if (expireTime == null) {
            return false;
        }
        if (expireTime > 0 && System.currentTimeMillis() > expireTime) {
            activeBans.remove(ip);
            return false;
        }
        return true;
    }

    public void addBan(String ip, long expireTimeMillis) {
        if (ip != null && !ip.isBlank()) {
            activeBans.put(ip, expireTimeMillis);
            if (isLoopback(ip)) {
                for (String v : LOOPBACK_VARIANTS) {
                    activeBans.put(v, expireTimeMillis);
                }
            }
        }
    }

    public void removeBan(String ip) {
        if (ip != null) {
            activeBans.remove(ip);
            if (isLoopback(ip)) {
                for (String v : LOOPBACK_VARIANTS) {
                    activeBans.remove(v);
                }
            }
        }
    }

    public int activeBansCount() {
        return activeBans.size();
    }

    public void syncFromDatabase() {
        try {
            resolveDbPath();
            Path dbPath = Path.of(resolvedDbPath);
            if (!Files.exists(dbPath)) {
                return;
            }

            // Explicit driver load (failsafe)
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException ignored) {
                // SQLite driver might not be on proxy standalone classpath if minimal
                return;
            }

            long now = System.currentTimeMillis();
            String url = "jdbc:sqlite:" + resolvedDbPath;
            try (Connection conn = DriverManager.getConnection(url);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT ip, expire_time FROM fail2ban_bans WHERE status = 'ACTIVE'")) {

                ConcurrentHashMap<String, Long> fresh = new ConcurrentHashMap<>();
                while (rs.next()) {
                    String ip = rs.getString("ip");
                    long expire = rs.getLong("expire_time");
                    if (expire <= 0 || expire > now) {
                        fresh.put(ip, expire);
                    }
                }
                activeBans.clear();
                activeBans.putAll(fresh);
                LOG.debug("[proxy/bancache] Synced {} active bans from {}", activeBans.size(), resolvedDbPath);
            }
        } catch (Throwable t) {
            LOG.debug("[proxy/bancache] SQLite sync skipped or unavailable: {}", t.getMessage());
        }
    }

    @Override
    public synchronized void close() {
        running = false;
        if (ipcSocket != null && !ipcSocket.isClosed()) {
            ipcSocket.close();
            ipcSocket = null;
        }
        if (ipcListenerThread != null) {
            ipcListenerThread.interrupt();
            ipcListenerThread = null;
        }
        if (syncExecutor != null) {
            syncExecutor.shutdownNow();
            syncExecutor = null;
        }
    }
}
