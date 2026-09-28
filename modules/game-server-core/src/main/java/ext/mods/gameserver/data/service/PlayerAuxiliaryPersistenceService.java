package ext.mods.gameserver.data.service;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

import ext.mods.gameserver.data.adapter.JdbcPlayerAuxiliaryStore;
import ext.mods.gameserver.data.repository.HennaRecord;
import ext.mods.gameserver.data.repository.MacroRecord;
import ext.mods.gameserver.data.repository.MemoRecord;
import ext.mods.gameserver.data.repository.MissionRecord;
import ext.mods.gameserver.data.repository.PlayerAuxiliaryStore;
import ext.mods.gameserver.data.repository.ShortcutRecord;

/** Application boundary for character-owned auxiliary state. */
public final class PlayerAuxiliaryPersistenceService
{
	private static final PlayerAuxiliaryStore STORE = new JdbcPlayerAuxiliaryStore();

	private PlayerAuxiliaryPersistenceService()
	{
	}

	public static List<MacroRecord> loadMacros(int characterObjectId) throws SQLException { return STORE.loadMacros(characterObjectId); }
	public static void saveMacro(int characterObjectId, MacroRecord macro) throws SQLException { STORE.saveMacro(characterObjectId, macro); }
	public static void deleteMacro(int characterObjectId, int macroId) throws SQLException { STORE.deleteMacro(characterObjectId, macroId); }
	public static List<ShortcutRecord> loadShortcuts(int characterObjectId, int classIndex) throws SQLException { return STORE.loadShortcuts(characterObjectId, classIndex); }
	public static void saveShortcut(int characterObjectId, ShortcutRecord shortcut) throws SQLException { STORE.saveShortcut(characterObjectId, shortcut); }
	public static void saveShortcuts(int characterObjectId, Collection<ShortcutRecord> shortcuts) throws SQLException { STORE.saveShortcuts(characterObjectId, shortcuts); }
	public static void deleteShortcut(int characterObjectId, ShortcutRecord shortcut) throws SQLException { STORE.deleteShortcut(characterObjectId, shortcut); }
	public static List<Integer> loadRecipes(int characterObjectId) throws SQLException { return STORE.loadRecipes(characterObjectId); }
	public static void addRecipe(int characterObjectId, int recipeId) throws SQLException { STORE.addRecipe(characterObjectId, recipeId); }
	public static void deleteRecipe(int characterObjectId, int recipeId) throws SQLException { STORE.deleteRecipe(characterObjectId, recipeId); }
	public static List<HennaRecord> loadHennas(int characterObjectId, int classIndex) throws SQLException { return STORE.loadHennas(characterObjectId, classIndex); }
	public static void addHenna(int characterObjectId, HennaRecord henna, int classIndex) throws SQLException { STORE.addHenna(characterObjectId, henna, classIndex); }
	public static void deleteHenna(int characterObjectId, int slot, int classIndex) throws SQLException { STORE.deleteHenna(characterObjectId, slot, classIndex); }
	public static List<MissionRecord> loadMissions(int characterObjectId) throws SQLException { return STORE.loadMissions(characterObjectId); }
	public static void saveMissions(int characterObjectId, Collection<MissionRecord> missions) throws SQLException { STORE.saveMissions(characterObjectId, missions); }
	public static List<MemoRecord> loadMemos(int characterObjectId) throws SQLException { return STORE.loadMemos(characterObjectId); }
	public static void saveMemo(int characterObjectId, MemoRecord memo) throws SQLException { STORE.saveMemo(characterObjectId, memo); }
	public static void deleteMemo(int characterObjectId, String key) throws SQLException { STORE.deleteMemo(characterObjectId, key); }
}
