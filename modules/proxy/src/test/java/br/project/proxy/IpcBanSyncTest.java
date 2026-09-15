package br.project.proxy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class IpcBanSyncTest {

    private ProxyBanCache cache;

    @BeforeEach
    public void setup() {
        cache = ProxyBanCache.getInstance();
        cache.removeBan("198.51.100.77");
        cache.removeBan("2804:cafe:babe::1");
    }

    @AfterEach
    public void cleanup() {
        cache.removeBan("198.51.100.77");
        cache.removeBan("2804:cafe:babe::1");
    }

    @Test
    public void testHandleIpcBanMessage() {
        assertFalse(cache.isBanned("198.51.100.77"));

        // Simulate instant IPC packet received over loopback datagram
        cache.handleIpcMessage("BAN 198.51.100.77 0");
        assertTrue(cache.isBanned("198.51.100.77"), "IP should be banned in cache instantly upon IPC message");

        cache.handleIpcMessage("UNBAN 198.51.100.77");
        assertFalse(cache.isBanned("198.51.100.77"), "IP should be unbanned in cache instantly upon IPC message");
    }

    @Test
    public void testHandleIpcIpv6BanMessage() {
        assertFalse(cache.isBanned("2804:cafe:babe::1"));

        cache.handleIpcMessage("BAN 2804:cafe:babe::1 0");
        assertTrue(cache.isBanned("2804:cafe:babe::1"), "IPv6 address should be supported in instant IPC ban");

        cache.handleIpcMessage("UNBAN 2804:cafe:babe::1");
        assertFalse(cache.isBanned("2804:cafe:babe::1"));
    }
}
