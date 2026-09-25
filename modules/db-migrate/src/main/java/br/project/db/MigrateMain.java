package br.project.db;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

import ext.mods.commons.jdbc.DatabaseDialect;
import ext.mods.commons.jdbc.SupportedDatabase;

/**
 * Phase 5 CLI entry for applying brproject-data Flyway migrations.
 *
 * <pre>
 * java -jar ... br.project.db.MigrateMain \
 *   --url=jdbc:postgresql://localhost:5432/l2jdb \
 *   --user=brproject --password=brproject
 * </pre>
 */
public final class MigrateMain
{
	private MigrateMain()
	{
	}
	
	public static void main(String[] args)
	{
		final Map<String, String> opts = parseArgs(args);
		final String url = opts.getOrDefault("url", System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/l2jdb"));
		final String user = opts.getOrDefault("user", System.getenv().getOrDefault("DB_USER", "brproject"));
		final String password = opts.getOrDefault("password", System.getenv().getOrDefault("DB_PASSWORD", "brproject"));

		// Select migration folder by JDBC URL — vendor-specific DDL lives in its own subtree.
		final String defaultLocations = selectMigrationsLocation(url);
		final Path migrations = Path.of(opts.getOrDefault("locations", defaultLocations)).toAbsolutePath().normalize();
		
		System.out.println("BrProject Flyway migrate");
		System.out.println("  url        = " + url);
		System.out.println("  user       = " + user);
		System.out.println("  migrations = " + migrations);
		
		final Flyway flyway = Flyway.configure()
			.dataSource(url, user, password)
			.locations("filesystem:" + migrations)
			.baselineOnMigrate(true)
			.baselineVersion("0")
			.validateMigrationNaming(true)
			.cleanDisabled(true)
			.load();
		System.out.println("  discovered  = " + flyway.info().all().length + " migration(s)");
		
		final MigrateResult result = flyway.migrate();
		System.out.println("Migrations executed: " + result.migrationsExecuted);
		System.out.println("Target schema version: " + result.targetSchemaVersion);

		final String gameServerHexid = System.getenv("GAME_SERVER_HEXID");
		final String gameServerHost = System.getenv("GAME_SERVER_HOST");
		if (gameServerHexid != null && !gameServerHexid.isBlank() && gameServerHost != null && !gameServerHost.isBlank())
		{
			seedGameServer(url, user, password, gameServerHexid, gameServerHost);
		}
		System.out.println("Success.");
	}
	
	/**
	 * Pick the vendor-specific migrations subtree based on the JDBC URL prefix.
	 * Override with {@code --locations=...} to bypass detection.
	 */
	static String selectMigrationsLocation(String url)
	{
		if (url == null)
		{
			return "brproject-data/migrations/mariadb";
		}
		final String normalized = url.toLowerCase();
		if (normalized.startsWith("jdbc:sqlite:"))
		{
			return "brproject-data/migrations/sqlite";
		}
		if (normalized.startsWith("jdbc:postgresql:"))
		{
			return "brproject-data/migrations/postgresql";
		}
		// MariaDB / MySQL / SQLServer / H2 use the legacy MariaDB-compatible subtree.
		return "brproject-data/migrations/mariadb";
	}

	private static void seedGameServer(String url, String user, String password, String hexid, String host)
	{
		final String sql = DatabaseDialect.upsert(SupportedDatabase.fromUrl(url), "gameservers", "server_id, hexid, host", "?, ?, ?", "server_id", "hexid, host");

		try (Connection connection = DriverManager.getConnection(url, user, password);
			PreparedStatement statement = connection.prepareStatement(sql))
		{
			statement.setInt(1, 1);
			statement.setString(2, hexid);
			statement.setString(3, host);
			statement.executeUpdate();
			System.out.println("GameServer seed applied: server_id=1, host=" + host);
		}
		catch (SQLException e)
		{
			throw new IllegalStateException("Unable to seed gameservers", e);
		}
	}

	private static Map<String, String> parseArgs(String[] args)
	{
		final Map<String, String> map = new HashMap<>();
		for (String a : args)
		{
			if (a.startsWith("--") && a.contains("="))
			{
				final int eq = a.indexOf('=');
				map.put(a.substring(2, eq), a.substring(eq + 1));
			}
		}
		return map;
	}
}
