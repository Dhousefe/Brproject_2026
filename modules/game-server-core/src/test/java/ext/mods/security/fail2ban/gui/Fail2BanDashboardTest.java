package ext.mods.security.fail2ban.gui;

import ext.mods.security.fail2ban.core.BanManager;
import ext.mods.security.fail2ban.core.Fail2BanConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class Fail2BanDashboardTest {

    @Test
    public void testSingletonInstance() {
        Fail2BanDashboard d1 = Fail2BanDashboard.getInstance();
        Fail2BanDashboard d2 = Fail2BanDashboard.getInstance();
        assertNotNull(d1, "Instance should not be null");
        assertSame(d1, d2, "Should return identical singleton instance");
    }

    @Test
    public void testProxyLogTailerBuffer() {
        ProxyLogTailer.appendLog("[PROXY] Test log line from Netty proxy");
        ProxyLogTailer tailer = new ProxyLogTailer();
        String content = tailer.read();
        assertNotNull(content);
        assertTrue(content.contains("Test log line from Netty proxy"), "Log tailer should contain dynamically appended lines");

        tailer.clear();
        String afterClear = tailer.read();
        assertFalse(afterClear.contains("Test log line from Netty proxy"), "Tailer buffer should be cleared");
    }

    @Test
    public void testRouteXmlToggleAndIntegrity() {
        String originalXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <proxyConfig>
                <routes>
                    <route name="l2-login" type="tcp" enabled="true" bindHost="0.0.0.0" bindPort="2106" targetHost="127.0.0.1" targetPort="2107">
                        <rateLimiter maxConnections="30" maxRequests="100" windowSeconds="60" />
                    </route>
                </routes>
            </proxyConfig>
            """;

        var validation = ext.mods.security.fail2ban.core.SecurityConfigManager.validateXmlSyntax(originalXml);
        assertTrue(validation.isValid(), "Original sample XML should be valid");

        // Simulate route toggle logic
        String routeName = "l2-login";
        boolean newActive = false;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(<route\\b[^>]*\\bname=[\"']" + java.util.regex.Pattern.quote(routeName) + "[\"'][^>]*>)", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(originalXml);
        assertTrue(m.find(), "Route tag should be found via regex");

        String tag = m.group(1);
        String updatedTag = tag.replaceAll("enabled=[\"'][^\"']*[\"']", "enabled=\"" + newActive + "\"");
        String updatedXml = originalXml.substring(0, m.start()) + updatedTag + originalXml.substring(m.end());

        var updatedValidation = ext.mods.security.fail2ban.core.SecurityConfigManager.validateXmlSyntax(updatedXml);
        assertTrue(updatedValidation.isValid(), "Updated XML after toggle should remain 100% valid");
        assertTrue(updatedXml.contains("enabled=\"false\""), "Updated XML must contain enabled=\"false\"");
    }
}
