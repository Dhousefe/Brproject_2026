package br.project.db;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

/**
 * Phase 5 CLI entry for applying brproject-data Flyway migrations.
 *
 * <pre>
 * java -jar ... br.project.db.MigrateMain \
 *   --url=jdbc:mariadb://localhost:3306/l2jdb \
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
		final String url = opts.getOrDefault("url", System.getenv().getOrDefault("DB_URL", "jdbc:mariadb://localhost:3306/l2jdb?useUnicode=true&characterEncoding=UTF-8"));
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
		// MariaDB / MySQL / PostgreSQL / SQLServer / H2 — share the MariaDB-style DDL (compatible subset).
		return "brproject-data/migrations/mariadb";
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
