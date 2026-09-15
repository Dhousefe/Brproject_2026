package br.project.proxy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedWindowRateLimiterTest {

    @Test
    void allowsOnlyConfiguredNumberOfEventsInWindow() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter();

        assertTrue(limiter.tryAcquire("ip:127.0.0.1", 2, 60).allowed());
        assertTrue(limiter.tryAcquire("ip:127.0.0.1", 2, 60).allowed());
        assertFalse(limiter.tryAcquire("ip:127.0.0.1", 2, 60).allowed());
    }

    @Test
    void tracksKeysIndependently() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter();

        assertTrue(limiter.tryAcquire("a", 1, 60).allowed());
        assertFalse(limiter.tryAcquire("a", 1, 60).allowed());
        assertTrue(limiter.tryAcquire("b", 1, 60).allowed());
    }
}
