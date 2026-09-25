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

	@Test
	void buildsPostgresqlCompositeKeyUpsert()
	{
		assertEquals(
			"INSERT INTO character_skills (char_obj_id,skill_id,skill_level,class_index) VALUES (?,?,?,?) ON CONFLICT (char_obj_id,skill_id,class_index) DO UPDATE SET skill_level=EXCLUDED.skill_level",
			DatabaseDialect.upsert(SupportedDatabase.POSTGRESQL, "character_skills", "char_obj_id,skill_id,skill_level,class_index", "?,?,?,?", "char_obj_id,skill_id,class_index", "skill_level"));
	}
}
