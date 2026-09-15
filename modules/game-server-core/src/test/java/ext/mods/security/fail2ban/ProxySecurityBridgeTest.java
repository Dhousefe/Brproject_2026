package ext.mods.security.fail2ban;

import ext.mods.security.fail2ban.core.ProxySecurityBridge;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ProxySecurityBridgeTest {

    @Test
    public void testIgnoresNonSecurityLines() {
        assertFalse(ProxySecurityBridge.processLine(null));
        assertFalse(ProxySecurityBridge.processLine(""));
        assertFalse(ProxySecurityBridge.processLine("[PROXY] Server started on port 2106"));
        assertFalse(ProxySecurityBridge.processLine("Random log message"));
    }

    @Test
    public void testProcessesRateLimitDenied() {
        String line = "[PROXY-SECURITY-EVENT] type=RATE_LIMIT_DENIED route='l2-login' ip=187.50.20.10 count=31/30";
        assertTrue(ProxySecurityBridge.processLine(line));
    }

    @Test
    public void testIgnoresLoopbackRateLimitDenied() {
        String line = "[PROXY-SECURITY-EVENT] type=RATE_LIMIT_DENIED route='l2-login' ip=127.0.0.1 count=31/30";
        assertFalse(ProxySecurityBridge.processLine(line));
    }

    @Test
    public void testProcessesBanDrop() {
        String line = "[PROXY-SECURITY-EVENT] type=BAN_DROP route='l2-game' ip=187.50.20.10 reason=BANNED_BY_FAIL2BAN";
        assertTrue(ProxySecurityBridge.processLine(line));
    }

    @Test
    public void testProcessesHttpTrafficEvent() {
        String line = "[PROXY-TRAFFIC-EVENT] proto=HTTP route='site' ip=203.0.113.195 method=GET uri='/static/hero.png' status=200 latency=5ms";
        assertTrue(ProxySecurityBridge.processLine(line));
    }

    @Test
    public void testProcessesHttpsTrafficEvent() {
        String line = "[PROXY-TRAFFIC-EVENT] proto=HTTPS route='site' ip=2001:db8::1 method=POST uri='/api/login' status=200 latency=15ms";
        assertTrue(ProxySecurityBridge.processLine(line));
    }

    @Test
    public void testProcessesWebSocketTrafficEvent() {
        String line = "[PROXY-TRAFFIC-EVENT] proto=WSS route='ws' ip=198.51.100.44 method=GET uri='/ws/notifications' status=101 latency=2ms";
        assertTrue(ProxySecurityBridge.processLine(line));
    }

    @Test
    public void testProcessesTcpTrafficEvent() {
        String line = "[PROXY-TRAFFIC-EVENT] proto=TCP route='game-server' ip=187.50.20.10 action=CONNECT targetPort=7777";
        assertTrue(ProxySecurityBridge.processLine(line));

        String disconnect = "[PROXY-TRAFFIC-EVENT] proto=TCP route='game-server' ip=187.50.20.10 action=DISCONNECT";
        assertTrue(ProxySecurityBridge.processLine(disconnect));
    }

    @Test
    public void testProcessesKtorHttpCloudflareIpv6Event() {
        String line = "[21:09:21] [INFO] [SITE-KTOR] [KTOR-LOG] [21:09:21.654] [INFO] [KTOR-HTTP] POST /api/account/clan-services (IP: 2804:d57:592c:3e00:742:a222:e8b6:7a18)";
        assertTrue(ProxySecurityBridge.processLine(line), "Must process Ktor HTTP log line with Cloudflare IPv6 address");
    }

    @Test
    public void testProcessesKtorWebSocketCloudflareIpv6Event() {
        String line = "[21:09:21] [INFO] [SITE-KTOR] [KTOR-LOG] [21:09:21.726] [INFO] [KTOR-HTTP] GET /ws/clan-chat (IP: 2804:d57:592c:3e00:742:a222:e8b6:7a18)";
        assertTrue(ProxySecurityBridge.processLine(line), "Must process Ktor WebSocket log line with Cloudflare IPv6 address");
    }

    @Test
    public void testProcessesKtorLocalHealthEvent() {
        String line = "[21:09:29] [INFO] [SITE-KTOR] [KTOR-LOG] [21:09:29.275] [INFO] [KTOR-HTTP] GET /api/site/health (IP: 127.0.0.1)";
        assertTrue(ProxySecurityBridge.processLine(line), "Must process Ktor loopback health check log");
    }
}
