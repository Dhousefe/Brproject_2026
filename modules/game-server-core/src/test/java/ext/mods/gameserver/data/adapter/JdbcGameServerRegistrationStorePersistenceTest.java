package ext.mods.gameserver.data.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.PreparedStatement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.GameServerRegistration;

/** Optional PostgreSQL contract test for gameserver registration persistence. */
class JdbcGameServerRegistrationStorePersistenceTest
{
	private static final int SERVER_ID = 2147482992;

	@AfterEach
	void tearDown()
	{
		if (databaseUrl() != null)
		{
			try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement("DELETE FROM gameservers WHERE server_id=?"))
			{
				ps.setInt(1, SERVER_ID);
				ps.executeUpdate();
			}
			catch (Exception ignored)
			{
				// Cleanup must not hide the primary assertion failure.
			}
		}
		ConnectionPool.shutdown();
	}

	@Test
	void savesLoadsUpdatesAndDeletesRegistrationThroughTheRepository()
	{
		Assumptions.assumeTrue(databaseUrl() != null, "Set DB_TEST_URL to run the gameserver registration contract test");
		try
		{
			ConnectionPool.init(databaseUrl(), databaseUser(), databasePassword(), "GameServerRegistrationContractPool");
			final JdbcGameServerRegistrationStore store = new JdbcGameServerRegistrationStore();
			store.save(new GameServerRegistration(SERVER_ID, "a1b2c3", "127.0.0.1"));
			assertEquals("a1b2c3", store.load().stream().filter(registration -> registration.serverId() == SERVER_ID).findFirst().orElseThrow().hexId());

			store.save(new GameServerRegistration(SERVER_ID, "d4e5f6", "gameserver"));
			final GameServerRegistration updated = store.load().stream().filter(registration -> registration.serverId() == SERVER_ID).findFirst().orElseThrow();
			assertEquals("d4e5f6", updated.hexId());
			assertEquals("gameserver", updated.host());

			store.delete(SERVER_ID);
			assertEquals(0, store.load().stream().filter(registration -> registration.serverId() == SERVER_ID).count());
		}
		catch (Exception e)
		{
			throw new AssertionError("Gameserver registration persistence contract failed", e);
		}
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
