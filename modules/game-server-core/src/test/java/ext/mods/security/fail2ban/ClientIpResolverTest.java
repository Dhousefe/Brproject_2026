package ext.mods.security.fail2ban;

import ext.mods.security.fail2ban.core.ClientIpResolver;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ClientIpResolverTest {

    @Test
    public void testDirectUntrustedPeerIgnoresHeaders() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.set("CF-Connecting-IP", "203.0.113.195");
        headers.set("X-Forwarded-For", "198.51.100.1");

        // When connecting directly from an untrusted public IP, headers must NOT be spoofed
        String realIp = ClientIpResolver.extractRealIp("189.40.10.5", headers);
        assertEquals("189.40.10.5", realIp, "Must ignore spoofed headers from untrusted direct peer");
    }

    @Test
    public void testCloudflareTunnelLocalhostExtractsCfIp() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.set("CF-Connecting-IP", "177.18.25.100");

        // Connecting via cloudflared on loopback (127.0.0.1)
        String realIp = ClientIpResolver.extractRealIp("127.0.0.1", headers);
        assertEquals("177.18.25.100", realIp, "Must extract CF-Connecting-IP when peer is 127.0.0.1");
    }

    @Test
    public void testCloudflareTunnelIpv6ClientAddress() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.set("CF-Connecting-IP", "2804:14d:5c60:8b00::1");

        String realIp = ClientIpResolver.extractRealIp("::1", headers);
        assertEquals("2804:14d:5c60:8b00::1", realIp, "Must support IPv6 client IP through Cloudflare");
    }

    @Test
    public void testTrueClientIpFallback() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.set("True-Client-IP", "189.50.60.70");

        String realIp = ClientIpResolver.extractRealIp("127.0.0.1", headers);
        assertEquals("189.50.60.70", realIp, "Must extract True-Client-IP when peer is 127.0.0.1");
    }

    @Test
    public void testXRealIpFallbackWhenNoCfHeader() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.set("X-Real-IP", "187.60.40.2");

        String realIp = ClientIpResolver.extractRealIp("127.0.0.1", headers);
        assertEquals("187.60.40.2", realIp, "Must fallback to X-Real-IP");
    }

    @Test
    public void testXForwardedForChain() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.set("X-Forwarded-For", "190.20.10.5, 127.0.0.1");

        String realIp = ClientIpResolver.extractRealIp("127.0.0.1", headers);
        assertEquals("190.20.10.5", realIp, "Must extract the first untrusted client hop from X-Forwarded-For");
    }

    @Test
    public void testInvalidOrMaliciousHeadersFallbackToPeer() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.set("CF-Connecting-IP", "<script>alert(1)</script>");

        String realIp = ClientIpResolver.extractRealIp("127.0.0.1", headers);
        assertEquals("127.0.0.1", realIp, "Invalid IP syntax in header must fallback safely without throwing");
    }

    @Test
    public void testDirectCloudflareEdgeIpExtractsCfIp() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.set("CF-Connecting-IP", "189.40.10.5");

        // When connection comes from official Cloudflare edge IP range (e.g. 173.245.48.10 or 104.16.1.1)
        String realIp = ClientIpResolver.extractRealIp("173.245.48.10", headers);
        assertEquals("189.40.10.5", realIp, "Must trust official Cloudflare edge IP ranges as trusted proxy");

        String realIp2 = ClientIpResolver.extractRealIp("104.16.20.30", headers);
        assertEquals("189.40.10.5", realIp2, "Must trust Cloudflare 104.16.0.0/13 CIDR block");
    }

    @Test
    public void testTrustedProxyChecks() {
        assertTrue(ClientIpResolver.isTrustedProxy("127.0.0.1"));
        assertTrue(ClientIpResolver.isTrustedProxy("::1"));
        assertTrue(ClientIpResolver.isTrustedProxy("173.245.48.1"));
        assertTrue(ClientIpResolver.isTrustedProxy("104.16.0.1"));
        assertFalse(ClientIpResolver.isTrustedProxy("189.40.10.5"));
        assertFalse(ClientIpResolver.isTrustedProxy("8.8.8.8"));
        assertFalse(ClientIpResolver.isTrustedProxy(null));
        assertFalse(ClientIpResolver.isTrustedProxy(""));
    }

    @Test
    public void testIpValidation() {
        assertTrue(ClientIpResolver.isValidIp("127.0.0.1"));
        assertTrue(ClientIpResolver.isValidIp("192.168.1.1"));
        assertTrue(ClientIpResolver.isValidIp("2001:0db8:85a3:0000:0000:8a2e:0370:7334"));
        assertFalse(ClientIpResolver.isValidIp("999.999.999.999"));
        assertFalse(ClientIpResolver.isValidIp("not-an-ip"));
        assertFalse(ClientIpResolver.isValidIp(null));
    }
}
