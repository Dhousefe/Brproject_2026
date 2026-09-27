package ext.mods.gameserver.data.service;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

import ext.mods.gameserver.data.adapter.JdbcSkillStore;
import ext.mods.gameserver.data.repository.SkillRecord;
import ext.mods.gameserver.data.repository.SkillSaveRecord;
import ext.mods.gameserver.data.repository.SkillStore;

/** Application boundary for character skill persistence. */
public final class SkillPersistenceService
{
	private static final SkillStore STORE = new JdbcSkillStore();

	private SkillPersistenceService()
	{
	}

	public static List<SkillRecord> loadSkills(int characterObjectId, int classIndex, boolean allClassIndexes) throws SQLException
	{
		return STORE.loadSkills(characterObjectId, classIndex, allClassIndexes);
	}

	public static void upsertSkill(int characterObjectId, int skillId, int skillLevel, int classIndex) throws SQLException
	{
		STORE.upsertSkill(characterObjectId, skillId, skillLevel, classIndex);
	}

	public static void deleteSkill(int characterObjectId, int skillId, int classIndex) throws SQLException
	{
		STORE.deleteSkill(characterObjectId, skillId, classIndex);
	}

	public static void deleteSkills(int characterObjectId, int classIndex) throws SQLException
	{
		STORE.deleteSkills(characterObjectId, classIndex);
	}

	public static List<SkillSaveRecord> loadSkillSaves(int characterObjectId, int classIndex) throws SQLException
	{
		return STORE.loadSkillSaves(characterObjectId, classIndex);
	}

	public static void deleteSkillSaves(int characterObjectId, int classIndex) throws SQLException
	{
		STORE.deleteSkillSaves(characterObjectId, classIndex);
	}

	public static void replaceSkillSaves(int characterObjectId, int classIndex, Collection<SkillSaveRecord> records) throws SQLException
	{
		STORE.replaceSkillSaves(characterObjectId, classIndex, records);
	}
}
