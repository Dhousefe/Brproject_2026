package ext.mods.config;

import ext.mods.security.fail2ban.core.Fail2BanConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ProxyIsolationConfigTest {

    private boolean origProxyGame;
    private int origPortGame;
    private int origInternalPortGame;
    private boolean origFail2BanGame;
    private boolean origFirewallGame;

    private boolean origProxyLogin;
    private int origPortLogin;
    private int origInternalPortLogin;
    private boolean origFail2BanLogin;

    @BeforeEach
    public void setUp() {
        origProxyGame = ConfigServer.ENABLE_NATIVE_PROXY;
        origPortGame = ConfigServer.GAMESERVER_PORT;
        origInternalPortGame = ConfigServer.GAMESERVER_INTERNAL_PORT;
        origFail2BanGame = ConfigServer.ENABLE_FAIL2BAN;
        origFirewallGame = ConfigServer.FAIL2BAN_FIREWALL;

        origProxyLogin = ConfigLogin.ENABLE_NATIVE_PROXY;
        origPortLogin = ConfigLogin.LOGINSERVER_PORT;
        origInternalPortLogin = ConfigLogin.LOGINSERVER_INTERNAL_PORT;
        origFail2BanLogin = ConfigLogin.ENABLE_FAIL2BAN;
    }

    @AfterEach
    public void tearDown() {
        ConfigServer.ENABLE_NATIVE_PROXY = origProxyGame;
        ConfigServer.GAMESERVER_PORT = origPortGame;
        ConfigServer.GAMESERVER_INTERNAL_PORT = origInternalPortGame;
        ConfigServer.ENABLE_FAIL2BAN = origFail2BanGame;
        ConfigServer.FAIL2BAN_FIREWALL = origFirewallGame;

        ConfigLogin.ENABLE_NATIVE_PROXY = origProxyLogin;
        ConfigLogin.LOGINSERVER_PORT = origPortLogin;
        ConfigLogin.LOGINSERVER_INTERNAL_PORT = origInternalPortLogin;
        ConfigLogin.ENABLE_FAIL2BAN = origFail2BanLogin;
    }

    @Test
    public void testGameServerIsolationByDefault() {
        ConfigServer.GAMESERVER_PORT = 7777;
        ConfigServer.GAMESERVER_INTERNAL_PORT = 7778;
        ConfigServer.ENABLE_NATIVE_PROXY = false;

        assertEquals(7777, ConfigServer.getEffectiveGameServerPort(), "Default mode must bind directly to public port 7777 without proxy redirection");
    }

    @Test
    public void testGameServerSwitchesToInternalPortWhenProxyActive() {
        ConfigServer.GAMESERVER_PORT = 7777;
        ConfigServer.GAMESERVER_INTERNAL_PORT = 7778;
        ConfigServer.ENABLE_NATIVE_PROXY = true;

        assertEquals(7778, ConfigServer.getEffectiveGameServerPort(), "Active proxy mode must bind GameServer to internal port 7778");
    }

    @Test
    public void testLoginServerIsolationByDefault() {
        ConfigLogin.LOGINSERVER_PORT = 2106;
        ConfigLogin.LOGINSERVER_INTERNAL_PORT = 2107;
        ConfigLogin.ENABLE_NATIVE_PROXY = false;

        assertEquals(2106, ConfigLogin.getEffectiveLoginServerPort(), "Default mode must bind LoginServer directly to public port 2106");
    }

    @Test
    public void testLoginServerSwitchesToInternalPortWhenProxyActive() {
        ConfigLogin.LOGINSERVER_PORT = 2106;
        ConfigLogin.LOGINSERVER_INTERNAL_PORT = 2107;
        ConfigLogin.ENABLE_NATIVE_PROXY = true;

        assertEquals(2107, ConfigLogin.getEffectiveLoginServerPort(), "Active proxy mode must bind LoginServer to internal port 2107");
    }

    @Test
    public void testFail2BanConfigRespectsFeatureFlag() {
        ConfigServer.ENABLE_FAIL2BAN = false;
        Fail2BanConfig disabledConfig = new Fail2BanConfig();
        assertFalse(disabledConfig.isEnabled(), "Fail2BanConfig must be disabled when ConfigServer.ENABLE_FAIL2BAN = false");

        ConfigServer.ENABLE_FAIL2BAN = true;
        ConfigServer.FAIL2BAN_FIREWALL = false;
        Fail2BanConfig noFirewallConfig = new Fail2BanConfig();
        assertTrue(noFirewallConfig.isEnabled(), "Fail2BanConfig must be enabled when ConfigServer.ENABLE_FAIL2BAN = true");
        assertFalse(noFirewallConfig.isFirewallEnabled(), "Firewall must be disabled when ConfigServer.FAIL2BAN_FIREWALL = false");
    }
}
