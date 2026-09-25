package ext.mods.commons.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SqlDialectTest
{
	@AfterEach
	void restoreDefaultDialect()
	{
		SqlDialect.setActiveDatabase(SupportedDatabase.MARIADB);
	}

	@Test
	void buildsPostgresqlUpsert()
	{
		SqlDialect.setActiveDatabase(SupportedDatabase.POSTGRESQL);

		assertEquals(
			"INSERT INTO character_data (charId, valueName, valueData) VALUES (?, ?, ?) ON CONFLICT (charId, valueName) DO UPDATE SET valueData=EXCLUDED.valueData",
			SqlDialect.upsert("character_data", "charId, valueName, valueData", "?, ?, ?", "charId, valueName", "valueData"));
	}

	@Test
	void buildsSqliteReplacement()
	{
		SqlDialect.setActiveDatabase(SupportedDatabase.SQLITE);

		assertEquals(
			"INSERT OR REPLACE INTO character_data (charId, valueName, valueData) VALUES (?, ?, ?)",
			SqlDialect.upsert("character_data", "charId, valueName, valueData", "?, ?, ?", "charId, valueName", "valueData"));
	}

	@Test
	void buildsMariaDbUpsert()
	{
		SqlDialect.setActiveDatabase(SupportedDatabase.MARIADB);

		assertEquals(
			"INSERT INTO character_data (charId, valueName, valueData) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE valueData=VALUES(valueData)",
			SqlDialect.upsert("character_data", "charId, valueName, valueData", "?, ?, ?", "charId, valueName", "valueData"));
	}
}
