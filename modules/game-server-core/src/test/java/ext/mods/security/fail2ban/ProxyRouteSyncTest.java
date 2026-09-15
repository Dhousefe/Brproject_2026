package ext.mods.security.fail2ban;

import ext.mods.config.ConfigLogin;
import ext.mods.config.ConfigServer;
import ext.mods.security.fail2ban.core.SecurityConfigManager;
import ext.mods.security.fail2ban.core.SecurityConfigManager.ProxyRouteDefinition;
import ext.mods.security.fail2ban.core.SecurityConfigManager.RouteSyncResult;
import ext.mods.security.fail2ban.core.SecurityConfigManager.XmlValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ProxyRouteSyncTest {

    @Test
    public void testDetectTargetService() {
        assertEquals("GameServer", SecurityConfigManager.detectTargetService("l2-game", "TCP", 7777, 7778));
        assertEquals("GameServer", SecurityConfigManager.detectTargetService("gameserver-proxy", "TCP", 7776, 7779));
        assertEquals("LoginServer", SecurityConfigManager.detectTargetService("l2-login", "TCP", 2106, 2107));
        assertEquals("LoginServer", SecurityConfigManager.detectTargetService("auth-login", "TCP", 2100, 2107));
        assertEquals("Site Ktor", SecurityConfigManager.detectTargetService("site", "HTTP", 80, 8080));
        assertEquals("Site Ktor", SecurityConfigManager.detectTargetService("web-portal", "HTTP", 443, 8080));
        assertEquals("Custom", SecurityConfigManager.detectTargetService("custom-relay", "TCP", 9999, 9998));
    }

    @Test
    public void testParseProxyRoutes() {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <proxyConfig>
                <routes>
                    <route name="l2-login" type="tcp" enabled="true" bindHost="0.0.0.0" bindPort="2106" targetHost="127.0.0.1" targetPort="2107">
                        <rateLimiter maxConnections="30" maxRequests="100" />
                    </route>
                    <route name="l2-game" type="tcp" enabled="true" bindHost="0.0.0.0" bindPort="7777" targetHost="127.0.0.1" targetPort="7778">
                        <rateLimiter maxConnections="60" maxRequests="200" />
                    </route>
                    <route name="site" type="http" enabled="true" bindHost="0.0.0.0" bindPort="80" targetHost="127.0.0.1" targetPort="8080" />
                </routes>
            </proxyConfig>
            """;

        List<ProxyRouteDefinition> routes = SecurityConfigManager.parseProxyRoutes(xml);
        assertEquals(3, routes.size(), "Should parse exactly 3 routes");

        ProxyRouteDefinition r0 = routes.get(0);
        assertEquals("l2-login", r0.name());
        assertEquals("LoginServer", r0.targetService());
        assertEquals(2106, r0.bindPort());
        assertEquals(2107, r0.targetPort());
        assertEquals(30, r0.maxConnections());

        ProxyRouteDefinition r1 = routes.get(1);
        assertEquals("l2-game", r1.name());
        assertEquals("GameServer", r1.targetService());
        assertEquals(7777, r1.bindPort());
        assertEquals(7778, r1.targetPort());
        assertEquals(60, r1.maxConnections());

        ProxyRouteDefinition r2 = routes.get(2);
        assertEquals("site", r2.name());
        assertEquals("Site Ktor", r2.targetService());
        assertEquals(80, r2.bindPort());
        assertEquals(8080, r2.targetPort());
    }

    @Test
    public void testFatalPortCollisionDetection() {
        // Fatal collision: bindPort == targetPort on local host
        String badXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <proxyConfig>
                <routes>
                    <route name="l2-game" type="tcp" enabled="true" bindHost="0.0.0.0" bindPort="7777" targetHost="127.0.0.1" targetPort="7777" />
                </routes>
            </proxyConfig>
            """;

        XmlValidationResult badResult = SecurityConfigManager.validateXmlSyntax(badXml);
        assertFalse(badResult.isValid(), "Validation must fail when bindPort == targetPort on local host");
        assertTrue(badResult.errorMessage().contains("Conflito Fatal de Porta"), "Error message should specifically flag fatal port conflict");

        // Safe config: distinct ports
        String goodXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <proxyConfig>
                <routes>
                    <route name="l2-game" type="tcp" enabled="true" bindHost="0.0.0.0" bindPort="7777" targetHost="127.0.0.1" targetPort="7778" />
                </routes>
            </proxyConfig>
            """;

        XmlValidationResult goodResult = SecurityConfigManager.validateXmlSyntax(goodXml);
        assertTrue(goodResult.isValid(), "Validation must pass with distinct ports");
    }

    @Test
    public void testWritePropertiesSafeBatch(@TempDir Path tempDir) throws Exception {
        Path propFile = tempDir.resolve("server.properties");
        String initialContent = """
            # Section Header Comment
            GameserverHostname = *
            GameserverPort = 7777
            # Internal Port Config
            GameServerInternalPort = 7778
            EnableNativeProxy = false
            """;
        Files.writeString(propFile, initialContent);

        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("GameServerInternalPort", "7788");
        updates.put("EnableNativeProxy", "true");
        updates.put("SiteBindPort", "8080");

        boolean ok = SecurityConfigManager.writePropertiesSafe(propFile, updates);
        assertTrue(ok, "writePropertiesSafe should succeed");

        String updatedContent = Files.readString(propFile);
        assertTrue(updatedContent.contains("GameServerInternalPort = 7788"), "Existing key must be updated");
        assertTrue(updatedContent.contains("EnableNativeProxy = true"), "Existing boolean key must be updated");
        assertTrue(updatedContent.contains("SiteBindPort = 8080"), "New key must be appended");
        assertTrue(updatedContent.contains("# Section Header Comment"), "Comments must be preserved");
        assertTrue(updatedContent.contains("# Internal Port Config"), "Inline comments must be preserved");
    }

    @Test
    public void testLiveRouteSynchronization() throws Exception {
        SecurityConfigManager scm = SecurityConfigManager.getInstance();
        RouteSyncResult res = scm.syncProxyRoutesWithProperties();

        assertTrue(res.success(), "syncProxyRoutesWithProperties should succeed on default proxy.xml");
        assertNotNull(res.updatedProperties());
        assertFalse(res.updatedProperties().isEmpty(), "Should report updated routes");

        // Verify in-memory configs are updated
        assertTrue(ConfigServer.GAMESERVER_PORT > 0, "ConfigServer.GAMESERVER_PORT should be configured");
        assertTrue(ConfigServer.GAMESERVER_INTERNAL_PORT > 0, "ConfigServer.GAMESERVER_INTERNAL_PORT should be configured");
        assertNotEquals(ConfigServer.GAMESERVER_PORT, ConfigServer.GAMESERVER_INTERNAL_PORT, "Public and internal ports must never be equal");

        assertTrue(ConfigLogin.LOGINSERVER_PORT > 0, "ConfigLogin.LOGINSERVER_PORT should be configured");
        assertTrue(ConfigLogin.LOGINSERVER_INTERNAL_PORT > 0, "ConfigLogin.LOGINSERVER_INTERNAL_PORT should be configured");
        List<SecurityConfigManager.ProxyRouteDefinition> routes = SecurityConfigManager.parseProxyRoutes(scm.readProxyXmlContent());
        SecurityConfigManager.ProxyRouteDefinition loginRoute = routes.stream()
            .filter(r -> "LoginServer".equals(r.targetService())).findFirst().orElse(null);
        assertNotNull(loginRoute, "LoginServer route must be present in proxy.xml");
        assertEquals(loginRoute.bindPort(), ConfigLogin.LOGINSERVER_PORT, "Public LoginserverPort must match proxy bindPort");
        assertEquals(loginRoute.targetPort(), ConfigLogin.LOGINSERVER_INTERNAL_PORT, "Internal port must match targetPort");
    }

    @Test
    public void testLoginPortIsolation() throws Exception {
        // Symmetric Port Synchronization Test:
        // bindPort updates LoginserverPort (public edge), targetPort updates LoginServerInternalPort (backend)
        Path tempLoginProp = Files.createTempFile("loginserver", ".properties");
        String initialContent = """
            LoginserverHostname = *
            LoginserverPort = 2106
            EnableNativeProxy = true
            LoginServerInternalPort = 2107
            """;
        Files.writeString(tempLoginProp, initialContent);

        // Simulate Proxy route with bindPort 2108 (proxy public edge) and targetPort 2109 (internal backend)
        Map<String, String> loginUpdates = new LinkedHashMap<>();
        loginUpdates.put("LoginserverPort", "2108");
        loginUpdates.put("LoginServerInternalPort", "2109");

        boolean ok = SecurityConfigManager.writePropertiesSafe(tempLoginProp, loginUpdates);
        assertTrue(ok, "writePropertiesSafe should succeed for loginserver");

        String content = Files.readString(tempLoginProp);
        assertTrue(content.contains("LoginserverPort = 2108"), "LoginserverPort must be updated from proxy bindPort");
        assertTrue(content.contains("LoginServerInternalPort = 2109"), "LoginServerInternalPort must be updated from targetPort");

        // Verify runtime port dispatch
        ConfigLogin.ENABLE_NATIVE_PROXY = true;
        ConfigLogin.LOGINSERVER_PORT = 2108;
        ConfigLogin.LOGINSERVER_INTERNAL_PORT = 2109;
        assertEquals(2109, ConfigLogin.getEffectiveLoginServerPort(), "When proxy is enabled, login must bind to internal port");

        ConfigLogin.ENABLE_NATIVE_PROXY = false;
        assertEquals(2108, ConfigLogin.getEffectiveLoginServerPort(), "When proxy is disabled, login must bind to public port");
    }
}
