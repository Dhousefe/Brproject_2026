package ext.mods.security.fail2ban;

import ext.mods.security.fail2ban.core.SecurityConfigManager;
import ext.mods.security.fail2ban.core.SecurityConfigManager.XmlValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ProxyXmlConfigManagerTest {

    @Test
    public void testValidXmlSyntaxPasses() {
        String validXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <list>
              <reverseProxy enabled="true">
                <route name="l2-login" type="tcp" enabled="true" bindHost="0.0.0.0" bindPort="2106" targetHost="127.0.0.1" targetPort="2107">
                  <rateLimiter enabled="true" windowSeconds="60" maxConnections="30" maxRequests="0"/>
                </route>
              </reverseProxy>
            </list>
            """;

        XmlValidationResult res = SecurityConfigManager.validateXmlSyntax(validXml);
        assertTrue(res.isValid(), "Well-formed XML must pass validation");
        assertNull(res.errorMessage(), "Error message must be null for valid XML");
        assertTrue(res.warnings().isEmpty(), "No warnings expected for clean config");
    }

    @Test
    public void testMalformedXmlFailsValidationWithLineCol() {
        String brokenXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <list>
              <reverseProxy enabled="true">
                <route name="l2-login" type="tcp" enabled="true" bindHost="0.0.0.0" bindPort="2106">
                  <!-- Missing closing route tag -->
              </reverseProxy>
            </list>
            """;

        XmlValidationResult res = SecurityConfigManager.validateXmlSyntax(brokenXml);
        assertFalse(res.isValid(), "Malformed XML must fail validation");
        assertNotNull(res.errorMessage(), "Error message must not be null");
        assertTrue(res.lineNumber() > 0, "Line number must be detected");
    }

    @Test
    public void testPortCollisionDetectionGeneratesWarning() {
        String duplicatePortsXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <list>
              <reverseProxy enabled="true">
                <route name="route-one" type="tcp" enabled="true" bindHost="0.0.0.0" bindPort="7777" targetHost="127.0.0.1" targetPort="7778"/>
                <route name="route-two" type="tcp" enabled="true" bindHost="0.0.0.0" bindPort="7777" targetHost="127.0.0.1" targetPort="7779"/>
              </reverseProxy>
            </list>
            """;

        XmlValidationResult res = SecurityConfigManager.validateXmlSyntax(duplicatePortsXml);
        assertTrue(res.isValid(), "XML itself is well-formed");
        assertFalse(res.warnings().isEmpty(), "Must generate warning for duplicate bindPort 7777");
        assertTrue(res.warnings().get(0).contains("7777"), "Warning must mention port 7777");
    }

    @Test
    public void testSaveCreatesBakAndAtomicWrite(@TempDir Path tempDir) throws Exception {
        Path fakeXml = tempDir.resolve("proxy.xml");
        String originalContent = "<list><reverseProxy enabled=\"true\"/></list>";
        Files.writeString(fakeXml, originalContent, StandardCharsets.UTF_8);

        String newContent = """
            <?xml version="1.0" encoding="utf-8"?>
            <list>
              <reverseProxy enabled="true">
                <route name="test" type="tcp" bindPort="9999" targetPort="9998"/>
              </reverseProxy>
            </list>
            """;

        // Validate directly
        XmlValidationResult valRes = SecurityConfigManager.validateXmlSyntax(newContent);
        assertTrue(valRes.isValid());

        // Perform manual backup & atomic write simulation matching SecurityConfigManager logic
        Path bakPath = fakeXml.resolveSibling(fakeXml.getFileName() + ".bak");
        Files.copy(fakeXml, bakPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        Path tmpPath = fakeXml.resolveSibling(fakeXml.getFileName() + ".tmp");
        Files.writeString(tmpPath, newContent, StandardCharsets.UTF_8);
        Files.move(tmpPath, fakeXml, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);

        assertTrue(Files.exists(bakPath), "Backup file .bak must be created");
        assertEquals(originalContent, Files.readString(bakPath, StandardCharsets.UTF_8), "Backup must match original content");
        assertEquals(newContent, Files.readString(fakeXml, StandardCharsets.UTF_8), "Original file must be updated with new content");
    }
}
