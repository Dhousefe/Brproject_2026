package ext.mods.gameapi.security

import ext.mods.gameapi.GameApiConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory nonce deduplication to prevent replay attacks.
 * Thread-safe via ConcurrentHashMap with amortized O(1) purge.
 */
object NonceCache {
    private val seen = ConcurrentHashMap<String, Long>(512)
    private val counter = AtomicLong(0L)
    private const val PURGE_INTERVAL_MASK = 0x1FFL // every 512 requests

    /**
     * Attempts to consume a nonce. Returns true if the nonce has NOT been seen before
     * (i.e., it's fresh). Returns false if it's a replay.
     */
    fun tryConsume(nonce: String, timestamp: Long): Boolean {
        if ((counter.incrementAndGet() and PURGE_INTERVAL_MASK) == 0L || seen.size > 2048) {
            purgeExpired()
        }
        return seen.putIfAbsent(nonce, timestamp) == null
    }

    fun purgeExpired() {
        val cutoff = System.currentTimeMillis() - GameApiConfig.nonceWindowMs
        seen.entries.removeIf { it.value < cutoff }
    }

    /** Visible for testing / shutdown cleanup. */
    fun clear() {
        counter.set(0L)
        seen.clear()
    }
}
