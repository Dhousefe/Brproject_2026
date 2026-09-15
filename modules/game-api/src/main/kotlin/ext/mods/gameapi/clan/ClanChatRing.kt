package ext.mods.gameapi.clan

import ext.mods.gameapi.GameApiConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * Lock-free ring buffer for clan chat messages with push notification support.
 *
 * Design:
 *   - Each clan has its own ring buffer (bounded by `historySize`).
 *   - Seq numbers are global and monotonic.
 *   - On append, registered listeners are notified synchronously on the caller's thread.
 *     Listeners should be fast (e.g., enqueue an async HTTP call).
 *
 * Scalability:
 *   - Per-clan synchronized blocks (contention bounded to clan size, not global).
 *   - Listeners are in a CopyOnWriteArrayList (fast iteration, rare mutation).
 */
object ClanChatRing {

    data class Entry(
        val seq: Long,
        val time: Long,
        val clanId: Int,
        val characterId: Int,
        val characterName: String,
        val text: String,
        val role: String = "MEMBER",
        val channel: String = "CLAN"
    )

    /** Listener interface for push notifications on new messages. */
    fun interface OnMessageListener {
        fun onMessage(entry: Entry)
    }

    private val seqCounter = AtomicLong(0L)
    private val rings = ConcurrentHashMap<Int, ArrayDeque<Entry>>()
    private val listeners = CopyOnWriteArrayList<OnMessageListener>()

    /** Register a listener that will be called on every append (any clan). */
    fun addListener(listener: OnMessageListener) {
        listeners.add(listener)
    }

    /** Remove a previously registered listener. */
    fun removeListener(listener: OnMessageListener) {
        listeners.remove(listener)
    }

    /**
     * Fetch all messages since `sinceSeq` (exclusive).
     * If `sinceSeq` is 0, returns up to `defaultMax` latest messages.
     */
    fun fetchSince(clanId: Int, sinceSeq: Long, defaultMax: Int = 50): List<Entry> {
        val ring = rings[clanId] ?: return emptyList()
        return synchronized(ring) {
            if (sinceSeq <= 0L) {
                ring.toList().takeLast(defaultMax)
            } else {
                ring.filter { it.seq > sinceSeq }
            }
        }
    }

    /** Useful for tests / UI initialization to inspect recent history without seq filter. */
    fun latest(clanId: Int, max: Int): List<Entry> {
        val ring = rings[clanId] ?: return emptyList()
        return synchronized(ring) {
            ring.toList().takeLast(max)
        }
    }

    /** Append a new entry, evicting the oldest if the ring overflows. Returns the seq assigned. */
    @JvmOverloads
    fun append(clanId: Int, characterId: Int, characterName: String, text: String, role: String = "MEMBER", channel: String = "CLAN"): Entry {
        val ring = rings.computeIfAbsent(clanId) { ArrayDeque(GameApiConfig.clanChatHistorySize) }
        val seq = seqCounter.incrementAndGet()
        val entry = Entry(seq, System.currentTimeMillis(), clanId, characterId, characterName, text, role, channel)
        synchronized(ring) {
            ring.addLast(entry)
            while (ring.size > GameApiConfig.clanChatHistorySize) ring.removeFirst()
        }
        // Notify listeners (should be fast; async dispatch is the listener's responsibility)
        for (listener in listeners) {
            try {
                listener.onMessage(entry)
            } catch (_: Exception) {
                // Never let a bad listener crash the chat flow
            }
        }
        return entry
    }

    /** Useful for tests / admin tools. Not used by the polling path. */
    fun size(clanId: Int): Int = synchronized(rings[clanId] ?: return 0) { rings[clanId]?.size ?: 0 }

    /** Wipes all rings (e.g., on config reload). */
    fun clear() {
        rings.values.forEach { synchronized(it) { it.clear() } }
        rings.clear()
    }
}
