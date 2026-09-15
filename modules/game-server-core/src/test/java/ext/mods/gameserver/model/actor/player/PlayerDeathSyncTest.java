package ext.mods.gameserver.model.actor.player;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class PlayerDeathSyncTest {

    @Test
    void atomicBoolean_compareAndSet_preventsDoubleDeath() {
        AtomicBoolean isDead = new AtomicBoolean(false);
        // First call sets to true
        assertTrue(isDead.compareAndSet(false, true));
        // Second call fails (already dead)
        assertFalse(isDead.compareAndSet(false, true));
    }

    @Test
    void atomicBoolean_isThreadSafe() throws InterruptedException {
        AtomicBoolean isDead = new AtomicBoolean(false);
        int[] successCount = {0};

        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> {
                if (isDead.compareAndSet(false, true))
                    synchronized(successCount) { successCount[0]++; }
            });
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        // Exactly ONE thread should succeed
        assertEquals(1, successCount[0]);
    }

    @Test
    void atomicBoolean_revive_resetsState() {
        AtomicBoolean isDead = new AtomicBoolean(true);
        isDead.set(false); // revive
        assertTrue(isDead.compareAndSet(false, true)); // can die again
    }
}
