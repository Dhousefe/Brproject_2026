package ext.mods.gameapi.security

import ext.mods.gameapi.GameApiConfig
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple in-memory nonce deduplication to prevent replay attacks.
 * Each nonce is stored with its timestamp; expired entries are purged on every insert.
 *
 * Thread-safe via ConcurrentHashMap.
 */
object NonceCache {
    private val seen = ConcurrentHashMap<String, Long>(256)

    /**
     * Attempts to consume a nonce. Returns true if the nonce has NOT been seen before
     * (i.e., it's fresh). Returns false if it's a replay.
     */
    fun tryConsume(nonce: String, timestamp: Long): Boolean {
        purgeExpired()
        return seen.putIfAbsent(nonce, timestamp) == null
    }

    private fun purgeExpired() {
        val cutoff = System.currentTimeMillis() - GameApiConfig.nonceWindowMs
        val iter = seen.entries.iterator()
        while (iter.hasNext()) {
            if (iter.next().value < cutoff) {
                iter.remove()
            }
        }
    }

    /** Visible for testing / shutdown cleanup. */
    fun clear() = seen.clear()
}
