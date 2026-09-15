package ext.mods.gameapi.security

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Simple fixed-window rate limiter per IP address.
 * Window = 60 seconds. Requests beyond `maxPerMinute` are rejected.
 */
class RateLimiter(private val maxPerMinute: Int) {

    private data class Window(val minuteEpoch: Long, val count: AtomicInteger)

    private val windows = ConcurrentHashMap<String, Window>(128)

    /**
     * Returns true if the request is allowed, false if rate-limited.
     */
    fun tryAcquire(ip: String): Boolean {
        val currentMinute = System.currentTimeMillis() / 60_000L
        val window = windows.compute(ip) { _, existing ->
            if (existing == null || existing.minuteEpoch != currentMinute) {
                Window(currentMinute, AtomicInteger(1))
            } else {
                existing.count.incrementAndGet()
                existing
            }
        }!!
        return window.count.get() <= maxPerMinute
    }

    /** Purge stale entries. Call periodically from a scheduled task. */
    fun gc() {
        val cutoff = System.currentTimeMillis() / 60_000L - 2
        val iter = windows.entries.iterator()
        while (iter.hasNext()) {
            if (iter.next().value.minuteEpoch < cutoff) {
                iter.remove()
            }
        }
    }
}
