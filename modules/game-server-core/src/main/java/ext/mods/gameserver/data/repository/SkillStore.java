package ext.mods.gameserver.data.repository;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

/** Persistence contract for character skills and saved skill effects. */
public interface SkillStore
{
	List<SkillRecord> loadSkills(int characterObjectId, int classIndex, boolean allClassIndexes) throws SQLException;

	void upsertSkill(int characterObjectId, int skillId, int skillLevel, int classIndex) throws SQLException;

	void deleteSkill(int characterObjectId, int skillId, int classIndex) throws SQLException;

	void deleteSkills(int characterObjectId, int classIndex) throws SQLException;

	List<SkillSaveRecord> loadSkillSaves(int characterObjectId, int classIndex) throws SQLException;

	void deleteSkillSaves(int characterObjectId, int classIndex) throws SQLException;

	void replaceSkillSaves(int characterObjectId, int classIndex, Collection<SkillSaveRecord> records) throws SQLException;
}
