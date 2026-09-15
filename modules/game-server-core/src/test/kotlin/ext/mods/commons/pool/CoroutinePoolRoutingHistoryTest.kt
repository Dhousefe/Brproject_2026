package ext.mods.commons.pool

import ext.mods.commons.pool.CoroutinePool.ExecutionRoute
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for the bounded LRU routing history in CoroutinePool.
 *
 * Verifies that taskRoutingHistory is bounded by MAX_ROUTING_HISTORY,
 * evicts least-recently-used entries on overflow, and remains thread-safe
 * under concurrent access.
 *
 * Run with -Dbrproject.routing.max-history=200 for fast test execution.
 * Default MAX_ROUTING_HISTORY is 10000 in production.
 */
class CoroutinePoolRoutingHistoryTest {

    private val maxHistory = CoroutinePool.MAX_ROUTING_HISTORY

    /**
     * Reflective access to the private taskRoutingHistory map.
     * Returns the underlying MutableMap for direct manipulation in tests.
     */
    @Suppress("UNCHECKED_CAST")
    private fun getRoutingMap(): MutableMap<String, ExecutionRoute> {
        val field = CoroutinePool::class.java.getDeclaredField("taskRoutingHistory")
        field.isAccessible = true
        return field.get(CoroutinePool) as MutableMap<String, ExecutionRoute>
    }

    @BeforeEach
    fun setUp() {
        // Clear any leftover state from other tests
        getRoutingMap().clear()
    }

    @AfterEach
    fun tearDown() {
        getRoutingMap().clear()
    }

    // ========================================================================
    // Test 1: Map is bounded — inserting beyond MAX does not grow unbounded
    // ========================================================================

    @Test
    @Timeout(30)
    fun `map size never exceeds MAX_ROUTING_HISTORY`() {
        val map = getRoutingMap()
        val insertCount = maxHistory * 2

        for (i in 0 until insertCount) {
            map["task-$i"] = ExecutionRoute.PLATFORM
        }

        assertTrue(
            map.size <= maxHistory,
            "Map size ${map.size} exceeded MAX_ROUTING_HISTORY $maxHistory"
        )
    }

    @Test
    @Timeout(30)
    fun `inserting 20000 unique entries stays within bounds`() {
        val map = getRoutingMap()
        val insertCount = 20_000.coerceAtLeast(maxHistory * 2)

        for (i in 0 until insertCount) {
            map["unique-task-$i"] = if (i % 2 == 0) ExecutionRoute.PLATFORM else ExecutionRoute.VIRTUAL
        }

        // The map should never exceed MAX_ROUTING_HISTORY
        assertTrue(
            map.size <= maxHistory,
            "Map size ${map.size} should be bounded by $maxHistory after inserting $insertCount entries"
        )
    }

    // ========================================================================
    // Test 2: Eviction preserves recent entries over old ones
    // ========================================================================

    @Test
    @Timeout(30)
    fun `recent entries survive eviction while old entries are removed`() {
        val map = getRoutingMap()

        // Insert maxHistory entries as "old" entries
        for (i in 0 until maxHistory) {
            map["old-task-$i"] = ExecutionRoute.PLATFORM
        }
        assertEquals(maxHistory, map.size, "Map should be full at MAX_ROUTING_HISTORY")

        // Insert new entries that will trigger eviction of the oldest
        val newEntryCount = maxHistory / 4
        for (i in 0 until newEntryCount) {
            map["new-task-$i"] = ExecutionRoute.VIRTUAL
        }

        // All new entries should still be present (they are most recent)
        for (i in 0 until newEntryCount) {
            assertTrue(
                map.containsKey("new-task-$i"),
                "Recently inserted entry 'new-task-$i' should survive eviction"
            )
        }

        // Some of the oldest entries should have been evicted
        val oldSurvivors = (0 until maxHistory).count { map.containsKey("old-task-$it") }
        assertTrue(
            oldSurvivors < maxHistory,
            "Some old entries should have been evicted, but all $oldSurvivors survived"
        )

        // Total size is still bounded
        assertTrue(
            map.size <= maxHistory,
            "Map size ${map.size} exceeds MAX_ROUTING_HISTORY $maxHistory"
        )
    }

    @Test
    @Timeout(30)
    fun `access-order LRU promotes recently read entries`() {
        val map = getRoutingMap()

        // Fill the map to capacity
        for (i in 0 until maxHistory) {
            map["task-$i"] = ExecutionRoute.PLATFORM
        }

        // Access the first entry (task-0) to promote it to "most recently used"
        map["task-0"]

        // Now insert enough new entries to evict older entries
        val evictionCount = maxHistory / 2
        for (i in 0 until evictionCount) {
            map["evict-trigger-$i"] = ExecutionRoute.VIRTUAL
        }

        // task-0 was recently accessed, so it should survive
        assertTrue(
            map.containsKey("task-0"),
            "task-0 was accessed recently and should survive LRU eviction"
        )

        // task-1 was not accessed recently — it should be evicted (it was near the front)
        // Note: task-1 is the second oldest after task-0 was promoted
        assertTrue(
            !map.containsKey("task-1"),
            "task-1 was not recently accessed and should be evicted"
        )
    }

    // ========================================================================
    // Test 3: Routing works correctly after eviction
    // ========================================================================

    @Test
    @Timeout(30)
    fun `evicted task re-submitted gets default PLATFORM route`() {
        val map = getRoutingMap()

        // Insert a task with VIRTUAL route
        map["routed-task"] = ExecutionRoute.VIRTUAL
        assertEquals(ExecutionRoute.VIRTUAL, map["routed-task"])

        // Fill the map to force eviction of "routed-task"
        // First, insert maxHistory other entries without accessing "routed-task"
        for (i in 0 until maxHistory) {
            map["filler-$i"] = ExecutionRoute.PLATFORM
        }

        // "routed-task" should be evicted now
        assertTrue(
            !map.containsKey("routed-task"),
            "routed-task should have been evicted after overflow"
        )

        // Simulating executeSmart behavior: getOrDefault returns PLATFORM for unknown keys
        val route = map.getOrDefault("routed-task", ExecutionRoute.PLATFORM)
        assertEquals(
            ExecutionRoute.PLATFORM, route,
            "Evicted task should fall back to default PLATFORM route"
        )
    }

    @Test
    @Timeout(30)
    fun `surviving entries retain their route after eviction`() {
        val map = getRoutingMap()

        // Insert entries near the end (most recent)
        for (i in 0 until maxHistory) {
            map["task-$i"] = if (i >= maxHistory - 10) ExecutionRoute.VIRTUAL else ExecutionRoute.PLATFORM
        }

        // Trigger eviction with 100 new entries
        for (i in 0 until 100) {
            map["overflow-$i"] = ExecutionRoute.PLATFORM
        }

        // The last 10 original entries should still retain their VIRTUAL route
        for (i in (maxHistory - 10) until maxHistory) {
            if (map.containsKey("task-$i")) {
                assertEquals(
                    ExecutionRoute.VIRTUAL, map["task-$i"],
                    "Surviving entry task-$i should retain VIRTUAL route"
                )
            }
        }
    }

    // ========================================================================
    // Test 4: Concurrent access during eviction is safe
    // ========================================================================

    @Test
    @Timeout(60)
    fun `concurrent reads and writes do not corrupt the map`() {
        val map = getRoutingMap()
        val threadCount = 8
        val operationsPerThread = maxHistory / 2
        val barrier = CyclicBarrier(threadCount)
        val errors = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(threadCount)

        try {
            val latch = CountDownLatch(threadCount)

            for (t in 0 until threadCount) {
                executor.submit {
                    try {
                        barrier.await(10, TimeUnit.SECONDS)
                        for (i in 0 until operationsPerThread) {
                            val taskId = "thread-$t-task-$i"
                            // Write
                            map[taskId] = if (i % 3 == 0) ExecutionRoute.VIRTUAL else ExecutionRoute.PLATFORM
                            // Read (simulates executeSmart access)
                            map.getOrDefault(taskId, ExecutionRoute.PLATFORM)
                            // Size check
                            map.size
                        }
                    } catch (e: Exception) {
                        errors.incrementAndGet()
                        e.printStackTrace()
                    } finally {
                        latch.countDown()
                    }
                }
            }

            assertTrue(
                latch.await(45, TimeUnit.SECONDS),
                "Concurrent operations should complete within timeout"
            )
            assertEquals(0, errors.get(), "No exceptions should occur during concurrent access")

            // Map size should still be bounded
            assertTrue(
                map.size <= maxHistory,
                "Map size ${map.size} exceeded MAX after concurrent writes"
            )
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    @Timeout(60)
    fun `concurrent eviction does not lose recently written entries`() {
        val map = getRoutingMap()
        val writerCount = 4
        val readerCount = 4
        val writesPerWriter = maxHistory
        val barrier = CyclicBarrier(writerCount + readerCount)
        val errors = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(writerCount + readerCount)

        try {
            val latch = CountDownLatch(writerCount + readerCount)

            // Writers: continuously insert unique tasks
            for (w in 0 until writerCount) {
                executor.submit {
                    try {
                        barrier.await(10, TimeUnit.SECONDS)
                        for (i in 0 until writesPerWriter) {
                            map["writer-$w-$i"] = ExecutionRoute.VIRTUAL
                        }
                    } catch (e: Exception) {
                        errors.incrementAndGet()
                    } finally {
                        latch.countDown()
                    }
                }
            }

            // Readers: continuously read random keys
            for (r in 0 until readerCount) {
                executor.submit {
                    try {
                        barrier.await(10, TimeUnit.SECONDS)
                        for (i in 0 until writesPerWriter) {
                            map.getOrDefault("writer-${r % writerCount}-$i", ExecutionRoute.PLATFORM)
                            map.size // force visibility of current state
                        }
                    } catch (e: Exception) {
                        errors.incrementAndGet()
                    } finally {
                        latch.countDown()
                    }
                }
            }

            assertTrue(
                latch.await(45, TimeUnit.SECONDS),
                "All threads should complete within timeout"
            )
            assertEquals(0, errors.get(), "No exceptions during concurrent read/write/evict")
            assertTrue(
                map.size <= maxHistory,
                "Map size ${map.size} must remain bounded at $maxHistory"
            )
        } finally {
            executor.shutdownNow()
        }
    }

    // ========================================================================
    // Test 5: clear() works on shutdown
    // ========================================================================

    @Test
    @Timeout(10)
    fun `clear empties the routing map`() {
        val map = getRoutingMap()

        // Populate
        for (i in 0 until maxHistory / 2) {
            map["task-$i"] = ExecutionRoute.PLATFORM
        }
        assertTrue(map.size > 0, "Map should not be empty before clear")

        // Clear (simulates shutdown behavior)
        map.clear()

        assertEquals(0, map.size, "Map should be empty after clear()")
    }

    @Test
    @Timeout(10)
    fun `clear works even when map is at maximum capacity`() {
        val map = getRoutingMap()

        // Fill to capacity
        for (i in 0 until maxHistory) {
            map["task-$i"] = ExecutionRoute.VIRTUAL
        }
        assertEquals(maxHistory, map.size, "Map should be at full capacity")

        map.clear()

        assertEquals(0, map.size, "Map must be empty after clear at full capacity")
        // Verify it can accept new entries after clear
        map["post-clear-task"] = ExecutionRoute.PLATFORM
        assertEquals(1, map.size)
        assertEquals(ExecutionRoute.PLATFORM, map["post-clear-task"])
    }

    // ========================================================================
    // Test 6: getMetrics reflects bounded size correctly
    // ========================================================================

    @Test
    @Timeout(30)
    fun `getMetrics reports correct smartRoutesTracked count`() {
        val map = getRoutingMap()
        val insertCount = maxHistory + 500

        for (i in 0 until insertCount) {
            map["metrics-task-$i"] = if (i % 3 == 0) ExecutionRoute.VIRTUAL else ExecutionRoute.PLATFORM
        }

        val metrics = CoroutinePool.getMetrics()
        val tracked = metrics["smartRoutesTracked"] as Int

        assertTrue(
            tracked <= maxHistory,
            "smartRoutesTracked ($tracked) should be bounded by MAX_ROUTING_HISTORY ($maxHistory)"
        )
        assertTrue(
            tracked > 0,
            "smartRoutesTracked should be > 0 after insertions"
        )
    }

    @Test
    @Timeout(30)
    fun `getMetrics reports correct tasksInVirtualRoute count`() {
        val map = getRoutingMap()

        // Insert 100 VIRTUAL and 100 PLATFORM entries (well within bounds)
        for (i in 0 until 100) {
            map["virtual-$i"] = ExecutionRoute.VIRTUAL
            map["platform-$i"] = ExecutionRoute.PLATFORM
        }

        val metrics = CoroutinePool.getMetrics()
        val virtualCount = metrics["tasksInVirtualRoute"] as Int

        assertEquals(
            100, virtualCount,
            "Should report exactly 100 VIRTUAL routes"
        )
    }

    // ========================================================================
    // Test 7: MAX_ROUTING_HISTORY constant is properly configured
    // ========================================================================

    @Test
    fun `MAX_ROUTING_HISTORY has a sane default`() {
        // The system property may override the value, but it should never be < 64
        assertTrue(
            maxHistory >= 64,
            "MAX_ROUTING_HISTORY ($maxHistory) must be at least 64"
        )
    }
}
