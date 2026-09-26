package ext.mods.gameserver.data.adapter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import ext.mods.commons.pool.ConnectionPool;

/** Optional PostgreSQL contract test for custom event state persistence. */
class JdbcCustomEventStateStorePersistenceTest
{
	private static final String EVENT_NAME = "__contract_event_state__";

	@AfterEach
	void tearDown()
	{
		if (databaseUrl() != null)
		{
			try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement("DELETE FROM events_custom_data WHERE event_name = ?"))
			{
				ps.setString(1, EVENT_NAME);
				ps.executeUpdate();
			}
			catch (Exception ignored)
			{
				// The contract test must not hide its primary assertion failure.
			}
		}
		ConnectionPool.shutdown();
	}

	@Test
	void savesAndRestoresEventStateThroughTheAdapter()
	{
		Assumptions.assumeTrue(databaseUrl() != null, "Set DB_TEST_URL to run the custom event state persistence contract test");
		ConnectionPool.init(databaseUrl(), databaseUser(), databasePassword(), "CustomEventStateStoreContractPool");

		final JdbcCustomEventStateStore store = new JdbcCustomEventStateStore();
		store.setEnabled(EVENT_NAME, true);
		assertTrue(store.isEnabled(EVENT_NAME));
		store.setEnabled(EVENT_NAME, false);
		assertFalse(store.isEnabled(EVENT_NAME));
	}

	private static String databaseUrl()
	{
		return firstNonBlank(System.getProperty("dbTestUrl"), System.getenv("DB_TEST_URL"));
	}

	private static String databaseUser()
	{
		return firstNonBlank(System.getProperty("dbTestUser"), System.getenv("DB_TEST_USER"), "brproject");
	}

	private static String databasePassword()
	{
		return firstNonBlank(System.getProperty("dbTestPassword"), System.getenv("DB_TEST_PASSWORD"), "brproject");
	}

	private static String firstNonBlank(String... values)
	{
		for (String value : values)
			if (value != null && !value.isBlank())
				return value;
		return null;
	}
}
