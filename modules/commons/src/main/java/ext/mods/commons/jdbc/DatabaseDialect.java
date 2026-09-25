package ext.mods.commons.jdbc;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Database compatibility boundary for SQL features that are not portable.
 *
 * <p>Game code should use this class (preferably through a repository) instead
 * of embedding vendor-specific upsert or replacement syntax. The legacy
 * {@link SqlDialect} class remains as a source-compatible facade while callers
 * are migrated.</p>
 */
public final class DatabaseDialect
{
	private DatabaseDialect()
	{
	}

	private static volatile SupportedDatabase _activeDb = SupportedDatabase.MARIADB;

	public static void setActiveDatabase(SupportedDatabase database)
	{
		_activeDb = database != null ? database : SupportedDatabase.SQLITE;
	}

	public static SupportedDatabase getActiveDatabase()
	{
		return _activeDb;
	}

	public static boolean isSqlite()
	{
		return _activeDb == SupportedDatabase.SQLITE;
	}

	public static boolean isPostgresql()
	{
		return _activeDb == SupportedDatabase.POSTGRESQL;
	}

	/**
	 * Builds an INSERT that updates an existing row identified by a unique key.
	 */
	public static String upsert(String table, String columns, String values, String conflictColumns, String updateColumns)
	{
		final String insert = "INSERT INTO " + table + " (" + columns + ") VALUES (" + values + ")";
		final String[] updates = updateColumns.split(",");

		if (_activeDb == SupportedDatabase.POSTGRESQL)
		{
			final String assignments = Arrays.stream(updates)
				.map(String::trim)
				.filter(column -> !column.isEmpty())
				.map(column -> column + "=EXCLUDED." + column)
				.collect(Collectors.joining(","));
			return insert + " ON CONFLICT (" + conflictColumns + ") DO UPDATE SET " + assignments;
		}

		if (_activeDb == SupportedDatabase.SQLITE)
			return insert.replaceFirst("^INSERT INTO", "INSERT OR REPLACE INTO");

		final String assignments = Arrays.stream(updates)
			.map(String::trim)
			.filter(column -> !column.isEmpty())
			.map(column -> column + "=VALUES(" + column + ")")
			.collect(Collectors.joining(","));
		return insert + " ON DUPLICATE KEY UPDATE " + assignments;
	}

	/**
	 * Adapts legacy statements that have a safe SQLite equivalent.
	 */
	public static String adapt(String sql)
	{
		if (!isSqlite() || sql == null)
			return sql;

		if (sql.contains("ON DUPLICATE KEY UPDATE"))
			return rewriteOnDuplicateKey(sql);

		final String trimmed = sql.stripLeading();
		if (trimmed.startsWith("TRUNCATE ") || trimmed.startsWith("TRUNCATE\t"))
			return "DELETE FROM " + truncateTarget(trimmed);

		return sql;
	}

	private static String truncateTarget(String sql)
	{
		String target = sql.substring("TRUNCATE".length()).trim();
		if (target.toUpperCase().startsWith("TABLE "))
			target = target.substring("TABLE ".length()).trim();
		return target;
	}

	private static String rewriteOnDuplicateKey(String sql)
	{
		final int duplicateIndex = sql.toUpperCase().indexOf("ON DUPLICATE KEY UPDATE");
		if (duplicateIndex < 0)
			return sql;

		final String insertPart = sql.substring(0, duplicateIndex).trim();
		final int insertIntoIndex = insertPart.toUpperCase().indexOf("INSERT INTO");
		if (insertIntoIndex < 0)
			return insertPart;

		return insertPart.substring(0, insertIntoIndex) + "INSERT OR REPLACE INTO" + insertPart.substring(insertIntoIndex + "INSERT INTO".length());
	}
}
