package ext.mods.security.fail2ban;

import ext.mods.security.fail2ban.core.PanicMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PanicModeSubnetTest {

    @Test
    public void testIpv4Subnet24Extraction() {
        PanicMode panic = PanicMode.getInstance();
        assertEquals("192.168.1", panic.getSubnetRange("192.168.1.105"));
        assertEquals("10.0.5", panic.getSubnetRange("10.0.5.20"));
    }

    @Test
    public void testIpv6Subnet64Extraction() {
        PanicMode panic = PanicMode.getInstance();
        String ipv6 = "2001:0db8:85a3:0000:0000:8a2e:0370:7334";
        String subnet64 = panic.getSubnetRange(ipv6);
        assertEquals("2001:0db8:85a3:0000", subnet64, "Must extract first 4 hextets (/64 prefix) for IPv6 subnet protection");
    }

    @Test
    public void testIpv6RangeBanAndAllowConnection() {
        PanicMode panic = PanicMode.getInstance();
        String targetIpv6 = "2804:14d:5c60:8b00:1234:5678:9abc:def0";
        String siblingIpv6 = "2804:14d:5c60:8b00:ffff:eeee:dddd:cccc";

        panic.setLevel(PanicMode.DEFENSIVE, "Unit test defensive attack");
        panic.banRange(targetIpv6, 60000);

        // In DEFENSIVE mode, all sibling IPs in the same /64 range should be rejected
        assertFalse(panic.allowConnection(targetIpv6), "Target IPv6 should be rejected under DEFENSIVE mode");
        assertFalse(panic.allowConnection(siblingIpv6), "Sibling IPv6 in same /64 should be rejected under DEFENSIVE mode");

        // IP from a different IPv6 /64 prefix should still be allowed
        String safeIpv6 = "2804:14d:9999:aaaa:1111:2222:3333:4444";
        assertTrue(panic.allowConnection(safeIpv6), "Unbanned IPv6 range should be allowed");

        // Reset to normal
        panic.setLevel(PanicMode.NORMAL, "Reset after test");
    }
}
