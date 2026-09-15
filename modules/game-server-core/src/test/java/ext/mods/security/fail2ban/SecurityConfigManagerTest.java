package ext.mods.security.fail2ban;

import ext.mods.config.ConfigLogin;
import ext.mods.config.ConfigServer;
import ext.mods.security.fail2ban.core.SecurityConfigManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SecurityConfigManagerTest {

    private boolean origFail2BanGame;
    private boolean origProxyGame;
    private boolean origFail2BanLogin;
    private boolean origProxyLogin;

    @BeforeEach
    public void setUp() {
        origFail2BanGame = ConfigServer.ENABLE_FAIL2BAN;
        origProxyGame = ConfigServer.ENABLE_NATIVE_PROXY;
        origFail2BanLogin = ConfigLogin.ENABLE_FAIL2BAN;
        origProxyLogin = ConfigLogin.ENABLE_NATIVE_PROXY;
    }

    @AfterEach
    public void tearDown() {
        ConfigServer.ENABLE_FAIL2BAN = origFail2BanGame;
        ConfigServer.ENABLE_NATIVE_PROXY = origProxyGame;
        ConfigLogin.ENABLE_FAIL2BAN = origFail2BanLogin;
        ConfigLogin.ENABLE_NATIVE_PROXY = origProxyLogin;
    }

    @Test
    public void testWritePropertySafeUpdatesExistingKeyPreservingComments(@TempDir Path tempDir) throws Exception {
        Path propFile = tempDir.resolve("server.properties");
        String initialContent = String.join(System.lineSeparator(),
            "# Header comment for testing",
            "# Another comment line",
            "EnableFail2Ban = true",
            "",
            "# Footer note",
            "GameserverPort = 7777"
        );
        Files.writeString(propFile, initialContent, StandardCharsets.UTF_8);

        // Update key to false
        boolean written = SecurityConfigManager.writePropertySafe(propFile, "EnableFail2Ban", "false");
        assertTrue(written, "writePropertySafe must return true on success");

        List<String> lines = Files.readAllLines(propFile, StandardCharsets.UTF_8);
        assertTrue(lines.contains("# Header comment for testing"), "Header comment must be preserved");
        assertTrue(lines.contains("# Another comment line"), "Second comment line must be preserved");
        assertTrue(lines.contains("EnableFail2Ban = false"), "EnableFail2Ban line must be updated to false");
        assertTrue(lines.contains("GameserverPort = 7777"), "Other properties must be preserved");
    }

    @Test
    public void testWritePropertySafeAppendsNonExistentKey(@TempDir Path tempDir) throws Exception {
        Path propFile = tempDir.resolve("loginserver.properties");
        String initialContent = String.join(System.lineSeparator(),
            "# Login server settings",
            "LoginserverPort = 2106"
        );
        Files.writeString(propFile, initialContent, StandardCharsets.UTF_8);

        // Append new key
        boolean written = SecurityConfigManager.writePropertySafe(propFile, "EnableNativeProxy", "true");
        assertTrue(written, "writePropertySafe must append new key");

        List<String> lines = Files.readAllLines(propFile, StandardCharsets.UTF_8);
        assertTrue(lines.contains("# Login server settings"), "Comment must remain");
        assertTrue(lines.contains("LoginserverPort = 2106"), "Original port property must remain");
        assertTrue(lines.contains("EnableNativeProxy = true"), "New property must be appended");
    }

    @Test
    public void testInvertFail2BanInMemoryState() {
        SecurityConfigManager scm = SecurityConfigManager.getInstance();

        scm.setFail2BanEnabled(true);
        assertTrue(ConfigServer.ENABLE_FAIL2BAN, "ConfigServer.ENABLE_FAIL2BAN must be true");
        assertTrue(ConfigLogin.ENABLE_FAIL2BAN, "ConfigLogin.ENABLE_FAIL2BAN must be true");

        scm.setFail2BanEnabled(false);
        assertFalse(ConfigServer.ENABLE_FAIL2BAN, "ConfigServer.ENABLE_FAIL2BAN must be false");
        assertFalse(ConfigLogin.ENABLE_FAIL2BAN, "ConfigLogin.ENABLE_FAIL2BAN must be false");
    }
}
