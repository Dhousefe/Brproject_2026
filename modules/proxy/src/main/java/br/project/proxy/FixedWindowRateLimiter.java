package br.project.proxy;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight per-key fixed-window limiter, matching the site module's in-process style.
 * Key is normally routeName + clientIp + action (tcp-connect/http-request).
 */
public final class FixedWindowRateLimiter {
    private record Bucket(long windowStartMillis, AtomicInteger count) {}

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitDecision tryAcquire(String key, int limit, int windowSeconds) {
        if (limit <= 0 || windowSeconds <= 0) {
            return RateLimitDecision.allowed(Integer.MAX_VALUE, 0);
        }

        long now = System.currentTimeMillis();
        long windowMillis = windowSeconds * 1000L;
        long windowStart = now - (now % windowMillis);
        Bucket bucket = buckets.compute(key, (ignored, existing) -> {
            if (existing == null || existing.windowStartMillis != windowStart) {
                return new Bucket(windowStart, new AtomicInteger(0));
            }
            return existing;
        });

        int current = bucket.count.incrementAndGet();
        if (current <= limit) {
            return RateLimitDecision.allowed(limit, current);
        }
        long retryAfter = Math.max(1L, windowStart + windowMillis - now);
        return RateLimitDecision.denied(limit, current, retryAfter);
    }

    public void gc() {
        long cutoff = System.currentTimeMillis() - 5 * 60_000L;
        buckets.entrySet().removeIf(entry -> entry.getValue().windowStartMillis < cutoff);
    }
}
