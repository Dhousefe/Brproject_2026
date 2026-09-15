package ext.mods.security.gui;

import ext.mods.commons.gui.services.ProcessManagerService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SubsystemShutdownLifecycleTest {

    @Test
    public void testStopAllServicesGracefulWhenIdle() {
        ProcessManagerService pms = ProcessManagerService.getInstance();
        assertNotNull(pms, "ProcessManagerService instance must not be null");

        assertFalse(pms.isGameServerRunning(), "GameServer should not be running in tests");
        assertFalse(pms.isLoginServerRunning(), "LoginServer should not be running in tests");
        assertFalse(pms.isSiteRunning(), "Site Ktor should not be running in tests");
        assertFalse(pms.isNativeProxyRunning(), "Native Proxy should not be running in tests");

        // Calling stopAllServices when idle must complete cleanly without any exception
        assertDoesNotThrow(() -> pms.stopAllServices(false), "stopAllServices should be safe when idle");
    }

    @Test
    public void testStopAllServicesForcedExit() {
        ProcessManagerService pms = ProcessManagerService.getInstance();
        assertDoesNotThrow(() -> pms.stopAllServices(true), "stopAllServices forced mode should execute cleanly");
        assertTrue(pms.isShuttingDown(), "isShuttingDown should be true after stopAllServices");
    }

    @Test
    public void testStopSiteSynchronousWhenIdle() {
        ProcessManagerService pms = ProcessManagerService.getInstance();
        assertDoesNotThrow(() -> pms.stopSite(true), "stopSite synchronous should be safe when idle");
        assertFalse(pms.isSiteRunning(), "Site Ktor should remain stopped");
    }

    @Test
    public void testJavaProcessInspectorNeverContainsCurrentPid() {
        java.util.List<ext.mods.commons.util.JavaProcessInspector.JavaProcessInfo> processes =
            ext.mods.commons.util.JavaProcessInspector.findBrProjectProcesses();
        long currentPid = ProcessHandle.current().pid();
        for (ext.mods.commons.util.JavaProcessInspector.JavaProcessInfo info : processes) {
            assertNotEquals(currentPid, (long) info.pid, "findBrProjectProcesses must never include the active JVM PID");
        }
    }

    @Test
    public void testKillRemainingProcessesIdempotent() {
        ProcessManagerService pms = ProcessManagerService.getInstance();
        assertDoesNotThrow(() -> pms.killRemainingBrProjectProcesses(), "killRemainingBrProjectProcesses should be safe");
    }

    @Test
    public void testShutdownSignalWatcherEmitAndClean() throws Exception {
        ext.mods.commons.util.ShutdownSignalWatcher.cleanSignals();
        java.util.concurrent.atomic.AtomicBoolean callbackTriggered = new java.util.concurrent.atomic.AtomicBoolean(false);

        Thread watcher = ext.mods.commons.util.ShutdownSignalWatcher.startWatcher("test_service", () -> {
            callbackTriggered.set(true);
        });

        ext.mods.commons.util.ShutdownSignalWatcher.sendSignal("test_service");

        // Aguarda detecção no watcher (polling a cada 500ms)
        long deadline = System.currentTimeMillis() + 3000;
        while (!callbackTriggered.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }

        assertTrue(callbackTriggered.get(), "ShutdownSignalWatcher callback must trigger upon detecting signal file");

        ext.mods.commons.util.ShutdownSignalWatcher.cleanSignals();
    }

    @Test
    public void testCloudflareTunnelUrlParserIgnoresApiTryCloudflareCom() {
        String line1 = "2026-09-11T20:43:23Z INF Requesting tunnel from https://api.trycloudflare.com/tunnel ...";
        String extracted1 = ProcessManagerService.extractValidCloudflareTunnelUrl(line1);
        assertNull(extracted1, "api.trycloudflare.com must be ignored and never extracted as a tunnel URL");

        String line2 = "https://api.trycloudflare.com";
        String extracted2 = ProcessManagerService.extractValidCloudflareTunnelUrl(line2);
        assertNull(extracted2, "Direct api.trycloudflare.com URL must be ignored");

        String line3 = "ERR Get \"https://api.trycloudflare.com/tunnel\": context deadline exceeded";
        String extracted3 = ProcessManagerService.extractValidCloudflareTunnelUrl(line3);
        assertNull(extracted3, "Error lines with api.trycloudflare.com must not be extracted");
    }

    @Test
    public void testCloudflareTunnelUrlParserExtractsValidQuickTunnelUrl() {
        String lineBanner = "|  https://fuzzy-rabbit-orange.trycloudflare.com                            |";
        String extracted = ProcessManagerService.extractValidCloudflareTunnelUrl(lineBanner);
        assertEquals("https://fuzzy-rabbit-orange.trycloudflare.com", extracted);

        String lineStandard = "INF Your quick Tunnel has been created: https://random-words-123.trycloudflare.com";
        String extracted2 = ProcessManagerService.extractValidCloudflareTunnelUrl(lineStandard);
        assertEquals("https://random-words-123.trycloudflare.com", extracted2);
    }

    @Test
    public void testCloudflareTunnelUrlParserIgnoresNullOrEmpty() {
        assertNull(ProcessManagerService.extractValidCloudflareTunnelUrl(null));
        assertNull(ProcessManagerService.extractValidCloudflareTunnelUrl(""));
        assertNull(ProcessManagerService.extractValidCloudflareTunnelUrl("   "));
        assertNull(ProcessManagerService.extractValidCloudflareTunnelUrl("Just some random log line without any url"));
    }

    @Test
    public void testNormalizeCustomTunnelUrl() {
        assertEquals("https://site.l2jbrasil.com", ProcessManagerService.normalizeCustomTunnelUrl("site.l2jbrasil.com"));
        assertEquals("https://site.l2jbrasil.com", ProcessManagerService.normalizeCustomTunnelUrl("https://site.l2jbrasil.com/"));
        assertEquals("https://l2jbrasil.meuservidor.org", ProcessManagerService.normalizeCustomTunnelUrl("http://l2jbrasil.meuservidor.org"));
        assertNull(ProcessManagerService.normalizeCustomTunnelUrl(null));
        assertNull(ProcessManagerService.normalizeCustomTunnelUrl(""));
        assertNull(ProcessManagerService.normalizeCustomTunnelUrl("   "));
        assertNull(ProcessManagerService.normalizeCustomTunnelUrl("///"));
    }

    @Test
    public void testBuildCloudflareCommand() {
        java.util.List<String> quickCmd = ProcessManagerService.buildCloudflareCommand("cloudflared.exe", "8080", "");
        assertEquals(java.util.List.of("cloudflared.exe", "tunnel", "--no-autoupdate", "--url", "http://127.0.0.1:8080"), quickCmd);

        java.util.List<String> tokenCmd = ProcessManagerService.buildCloudflareCommand("cloudflared.exe", "8080", "eyJhIj12345");
        assertEquals(java.util.List.of("cloudflared.exe", "tunnel", "--no-autoupdate", "run", "--token", "eyJhIj12345"), tokenCmd);
    }
}