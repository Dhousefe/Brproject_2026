package br.project.proxy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ProxyBanCacheTest {

    private ProxyBanCache cache;

    @BeforeEach
    public void setup() {
        cache = ProxyBanCache.getInstance();
        cache.removeBan("192.168.1.50");
        cache.removeBan("10.0.0.1");
    }

    @Test
    public void testAddAndCheckBan() {
        assertFalse(cache.isBanned("192.168.1.50"));

        // Permanent ban (expire <= 0)
        cache.addBan("192.168.1.50", 0);
        assertTrue(cache.isBanned("192.168.1.50"));

        cache.removeBan("192.168.1.50");
        assertFalse(cache.isBanned("192.168.1.50"));
    }

    @Test
    public void testExpiredBan() {
        long past = System.currentTimeMillis() - 1000;
        cache.addBan("10.0.0.1", past);

        // Should detect expiration and return false
        assertFalse(cache.isBanned("10.0.0.1"));
    }
}
