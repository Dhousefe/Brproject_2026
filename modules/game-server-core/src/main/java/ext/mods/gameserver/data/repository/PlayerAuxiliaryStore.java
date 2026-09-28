package ext.mods.gameserver.data.repository;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

/** Persistence port for character-owned auxiliary state. */
public interface PlayerAuxiliaryStore
{
	List<MacroRecord> loadMacros(int characterObjectId) throws SQLException;

	void saveMacro(int characterObjectId, MacroRecord macro) throws SQLException;

	void deleteMacro(int characterObjectId, int macroId) throws SQLException;

	List<ShortcutRecord> loadShortcuts(int characterObjectId, int classIndex) throws SQLException;

	void saveShortcut(int characterObjectId, ShortcutRecord shortcut) throws SQLException;

	void saveShortcuts(int characterObjectId, Collection<ShortcutRecord> shortcuts) throws SQLException;

	void deleteShortcut(int characterObjectId, ShortcutRecord shortcut) throws SQLException;

	List<Integer> loadRecipes(int characterObjectId) throws SQLException;

	void addRecipe(int characterObjectId, int recipeId) throws SQLException;

	void deleteRecipe(int characterObjectId, int recipeId) throws SQLException;

	List<HennaRecord> loadHennas(int characterObjectId, int classIndex) throws SQLException;

	void addHenna(int characterObjectId, HennaRecord henna, int classIndex) throws SQLException;

	void deleteHenna(int characterObjectId, int slot, int classIndex) throws SQLException;

	List<MissionRecord> loadMissions(int characterObjectId) throws SQLException;

	void saveMissions(int characterObjectId, Collection<MissionRecord> missions) throws SQLException;

	List<MemoRecord> loadMemos(int characterObjectId) throws SQLException;

	void saveMemo(int characterObjectId, MemoRecord memo) throws SQLException;

	void deleteMemo(int characterObjectId, String key) throws SQLException;
}
