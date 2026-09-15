package ext.mods.commons.mmocore;

import org.junit.jupiter.api.Test;
import java.util.concurrent.locks.LockSupport;
import static org.junit.jupiter.api.Assertions.*;

class SelectorThreadTimingTest {
    @Test
    void parkNanos_shouldNotExceedExpectedDelay() {
        long start = System.nanoTime();
        LockSupport.parkNanos(1_000_000L); // 1ms
        long elapsed = System.nanoTime() - start;
        // Should be close to 1ms (allow up to 5ms for OS scheduling)
        assertTrue(elapsed < 5_000_000L, "parkNanos took too long: " + elapsed + "ns");
    }

    @Test
    void parkNanos_shouldBeInterruptible() {
        Thread testThread = Thread.currentThread();
        Thread interrupter = new Thread(() -> {
            try { Thread.sleep(50); } catch (InterruptedException e) { }
            LockSupport.unpark(testThread);
        });
        interrupter.start();
        long start = System.nanoTime();
        LockSupport.parkNanos(1_000_000_000L); // 1 second
        long elapsed = System.nanoTime() - start;
        // Should be woken up in ~50ms, not 1 second
        assertTrue(elapsed < 200_000_000L, "Thread was not unparked in time: " + elapsed + "ns");
    }
}
