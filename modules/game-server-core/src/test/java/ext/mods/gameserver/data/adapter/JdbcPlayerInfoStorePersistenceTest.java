package ext.mods.gameserver.data.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.PreparedStatement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.PlayerDirectoryEntry;

/** Optional PostgreSQL/MariaDB contract test for the character directory read model. */
class JdbcPlayerInfoStorePersistenceTest
{
	private static final int OBJECT_ID = 2147482993;
	private static final String ACCOUNT = "player-info-contract";
	private static final String NAME = "PlayerInfoContract";

	@AfterEach
	void tearDown()
	{
		if (databaseUrl() != null)
		{
			try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement("DELETE FROM characters WHERE obj_Id=?"))
			{
				ps.setInt(1, OBJECT_ID);
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
	void loadsCharacterDirectoryEntry() throws Exception
	{
		Assumptions.assumeTrue(databaseUrl() != null, "Set DB_TEST_URL to run the player info contract test");
		ConnectionPool.init(databaseUrl(), databaseUser(), databasePassword(), "PlayerInfoContractPool");
		seed();

		final PlayerDirectoryEntry entry = new JdbcPlayerInfoStore().load().stream().filter(value -> value.objectId() == OBJECT_ID).findFirst().orElseThrow();
		assertEquals(ACCOUNT, entry.accountName());
		assertEquals(NAME, entry.playerName());
		assertEquals(7, entry.accessLevel());
	}

	private static void seed() throws Exception
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			try (PreparedStatement delete = con.prepareStatement("DELETE FROM characters WHERE obj_Id=?"))
			{
				delete.setInt(1, OBJECT_ID);
				delete.executeUpdate();
			}
			try (PreparedStatement insert = con.prepareStatement("INSERT INTO characters (account_name,obj_Id,char_name,accesslevel) VALUES (?,?,?,?)"))
			{
				insert.setString(1, ACCOUNT);
				insert.setInt(2, OBJECT_ID);
				insert.setString(3, NAME);
				insert.setInt(4, 7);
				insert.executeUpdate();
			}
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
