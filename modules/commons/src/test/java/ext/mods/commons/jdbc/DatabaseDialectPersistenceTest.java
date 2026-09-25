package ext.mods.commons.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.DriverManager;
import java.sql.ResultSet;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Optional real-database contract test.
 *
 * <p>Set DB_TEST_URL, DB_TEST_USER and DB_TEST_PASSWORD to run it against a
 * PostgreSQL or MariaDB container. Without DB_TEST_URL the test is skipped so
 * normal unit builds remain independent from a developer's local database.</p>
 */
class DatabaseDialectPersistenceTest
{
	@Test
	void upsertPersistsAndUpdatesAcrossSupportedDatabases() throws Exception
	{
		final String url = firstNonBlank(System.getProperty("dbTestUrl"), System.getenv("DB_TEST_URL"));
		Assumptions.assumeTrue(url != null, "Set DB_TEST_URL to run the real database contract test");

		final String user = firstNonBlank(System.getProperty("dbTestUser"), System.getenv("DB_TEST_USER"), "brproject");
		final String password = firstNonBlank(System.getProperty("dbTestPassword"), System.getenv("DB_TEST_PASSWORD"), "brproject");
		DatabaseDialect.setActiveDatabase(SupportedDatabase.fromUrl(url));

		try (var connection = DriverManager.getConnection(url, user, password))
		{
			connection.createStatement().execute("CREATE TABLE IF NOT EXISTS database_dialect_contract (id INTEGER PRIMARY KEY, value VARCHAR(64) NOT NULL)");
			final String sql = DatabaseDialect.upsert("database_dialect_contract", "id, value", "?, ?", "id", "value");

			try (var statement = connection.prepareStatement(sql))
			{
				statement.setInt(1, 1);
				statement.setString(2, "first");
				statement.executeUpdate();
				statement.setInt(1, 1);
				statement.setString(2, "second");
				statement.executeUpdate();
			}

			try (ResultSet result = connection.createStatement().executeQuery("SELECT value FROM database_dialect_contract WHERE id=1"))
			{
				result.next();
				assertEquals("second", result.getString(1));
			}
			finally
			{
				connection.createStatement().execute("DROP TABLE IF EXISTS database_dialect_contract");
			}
		}
	}

	private static String firstNonBlank(String... values)
	{
		for (String value : values)
			if (value != null && !value.isBlank())
				return value;
		return null;
	}
}
