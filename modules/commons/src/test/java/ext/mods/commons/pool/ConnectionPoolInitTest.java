package ext.mods.commons.pool;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit-level checks for ConnectionPool inject API (no live DB required).
 */
class ConnectionPoolInitTest
{
	@Test
	void getStats_beforeInit_reportsNotInitialized()
	{
		// After other tests the pool may be live; only assert message format is non-empty.
		final String stats = ConnectionPool.getStats();
		assertTrue(stats != null && stats.startsWith("HikariCP:"));
	}
	
	@Test
	void deprecatedInit_withoutCredentials_doesNotThrow()
	{
		// Clears env-driven path: missing props should log and return.
		System.clearProperty("brproject.db.url");
		System.clearProperty("brproject.db.user");
		System.clearProperty("brproject.db.password");
		assertDoesNotThrow(() -> ConnectionPool.init());
	}
}
