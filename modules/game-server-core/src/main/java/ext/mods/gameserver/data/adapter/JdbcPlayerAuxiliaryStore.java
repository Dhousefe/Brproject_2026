package ext.mods.gameserver.data.adapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import ext.mods.commons.jdbc.DatabaseDialect;
import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.HennaRecord;
import ext.mods.gameserver.data.repository.MacroRecord;
import ext.mods.gameserver.data.repository.MemoRecord;
import ext.mods.gameserver.data.repository.MissionRecord;
import ext.mods.gameserver.data.repository.PlayerAuxiliaryStore;
import ext.mods.gameserver.data.repository.ShortcutRecord;

/** JDBC adapter for character-owned macros, shortcuts and auxiliary state. */
public final class JdbcPlayerAuxiliaryStore implements PlayerAuxiliaryStore
{
	private static final String LOAD_MACROS = "SELECT id,icon,name,descr,acronym,commands FROM character_macroses WHERE char_obj_id=?";
	private static final String DELETE_MACRO = "DELETE FROM character_macroses WHERE char_obj_id=? AND id=?";
	private static final String LOAD_SHORTCUTS = "SELECT slot,page,type,id,level,class_index FROM character_shortcuts WHERE char_obj_id=? AND class_index=?";
	private static final String DELETE_RECIPE = "DELETE FROM character_recipebook WHERE charId=? AND recipeId=?";
	private static final String LOAD_RECIPES = "SELECT recipeId FROM character_recipebook WHERE charId=?";
	private static final String INSERT_RECIPE = "INSERT INTO character_recipebook (charId,recipeId) VALUES (?,?)";
	private static final String LOAD_HENNAS = "SELECT slot,symbol_id FROM character_hennas WHERE char_obj_id=? AND class_index=?";
	private static final String INSERT_HENNA = "INSERT INTO character_hennas (char_obj_id,symbol_id,slot,class_index) VALUES (?,?,?,?)";
	private static final String DELETE_HENNA = "DELETE FROM character_hennas WHERE char_obj_id=? AND slot=? AND class_index=?";
	private static final String LOAD_MISSIONS = "SELECT type,level,value FROM character_mission WHERE object_id=?";
	private static final String LOAD_MEMOS = "SELECT var,val FROM character_memo WHERE charId=?";
	private static final String DELETE_MEMO = "DELETE FROM character_memo WHERE charId=? AND var=?";

	@Override
	public List<MacroRecord> loadMacros(int characterObjectId) throws SQLException
	{
		final List<MacroRecord> result = new ArrayList<>();
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(LOAD_MACROS))
		{
			ps.setInt(1, characterObjectId);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					result.add(new MacroRecord(rs.getInt("id"), rs.getInt("icon"), rs.getString("name"), rs.getString("descr"), rs.getString("acronym"), rs.getString("commands")));
			}
		}
		return result;
	}

	@Override
	public void saveMacro(int characterObjectId, MacroRecord macro) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(getMacroUpsertSql()))
		{
			ps.setInt(1, characterObjectId);
			ps.setInt(2, macro.id());
			ps.setInt(3, macro.icon());
			ps.setString(4, macro.name());
			ps.setString(5, macro.description());
			ps.setString(6, macro.acronym());
			ps.setString(7, macro.commands());
			ps.executeUpdate();
		}
	}

	@Override
	public void deleteMacro(int characterObjectId, int macroId) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(DELETE_MACRO))
		{
			ps.setInt(1, characterObjectId);
			ps.setInt(2, macroId);
			ps.executeUpdate();
		}
	}

	@Override
	public List<ShortcutRecord> loadShortcuts(int characterObjectId, int classIndex) throws SQLException
	{
		final List<ShortcutRecord> result = new ArrayList<>();
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(LOAD_SHORTCUTS))
		{
			ps.setInt(1, characterObjectId);
			ps.setInt(2, classIndex);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					result.add(new ShortcutRecord(rs.getInt("slot"), rs.getInt("page"), rs.getString("type"), rs.getInt("id"), rs.getInt("level"), rs.getInt("class_index")));
			}
		}
		return result;
	}

	@Override
	public void saveShortcut(int characterObjectId, ShortcutRecord shortcut) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(getShortcutUpsertSql()))
		{
			bindShortcut(ps, characterObjectId, shortcut);
			ps.executeUpdate();
		}
	}

	@Override
	public void saveShortcuts(int characterObjectId, Collection<ShortcutRecord> shortcuts) throws SQLException
	{
		if (shortcuts.isEmpty())
			return;
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(getShortcutUpsertSql()))
		{
			for (ShortcutRecord shortcut : shortcuts)
			{
				bindShortcut(ps, characterObjectId, shortcut);
				ps.addBatch();
			}
			ps.executeBatch();
		}
	}

	private static void bindShortcut(PreparedStatement ps, int characterObjectId, ShortcutRecord shortcut) throws SQLException
	{
		ps.setInt(1, characterObjectId);
		ps.setInt(2, shortcut.slot());
		ps.setInt(3, shortcut.page());
		ps.setString(4, shortcut.type());
		ps.setInt(5, shortcut.id());
		ps.setInt(6, shortcut.level());
		ps.setInt(7, shortcut.classIndex());
	}

	@Override
	public void deleteShortcut(int characterObjectId, ShortcutRecord shortcut) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement("DELETE FROM character_shortcuts WHERE char_obj_id=? AND slot=? AND page=? AND class_index=?"))
		{
			ps.setInt(1, characterObjectId);
			ps.setInt(2, shortcut.slot());
			ps.setInt(3, shortcut.page());
			ps.setInt(4, shortcut.classIndex());
			ps.executeUpdate();
		}
	}

	@Override
	public List<Integer> loadRecipes(int characterObjectId) throws SQLException
	{
		final List<Integer> result = new ArrayList<>();
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(LOAD_RECIPES))
		{
			ps.setInt(1, characterObjectId);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					result.add(rs.getInt("recipeId"));
			}
		}
		return result;
	}

	@Override
	public void addRecipe(int characterObjectId, int recipeId) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(INSERT_RECIPE))
		{
			ps.setInt(1, characterObjectId);
			ps.setInt(2, recipeId);
			ps.executeUpdate();
		}
	}

	@Override
	public void deleteRecipe(int characterObjectId, int recipeId) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(DELETE_RECIPE))
		{
			ps.setInt(1, characterObjectId);
			ps.setInt(2, recipeId);
			ps.executeUpdate();
		}
	}

	@Override
	public List<HennaRecord> loadHennas(int characterObjectId, int classIndex) throws SQLException
	{
		final List<HennaRecord> result = new ArrayList<>();
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(LOAD_HENNAS))
		{
			ps.setInt(1, characterObjectId);
			ps.setInt(2, classIndex);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					result.add(new HennaRecord(rs.getInt("slot"), rs.getInt("symbol_id")));
			}
		}
		return result;
	}

	@Override
	public void addHenna(int characterObjectId, HennaRecord henna, int classIndex) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(INSERT_HENNA))
		{
			ps.setInt(1, characterObjectId);
			ps.setInt(2, henna.symbolId());
			ps.setInt(3, henna.slot());
			ps.setInt(4, classIndex);
			ps.executeUpdate();
		}
	}

	@Override
	public void deleteHenna(int characterObjectId, int slot, int classIndex) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(DELETE_HENNA))
		{
			ps.setInt(1, characterObjectId);
			ps.setInt(2, slot);
			ps.setInt(3, classIndex);
			ps.executeUpdate();
		}
	}

	@Override
	public List<MissionRecord> loadMissions(int characterObjectId) throws SQLException
	{
		final List<MissionRecord> result = new ArrayList<>();
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(LOAD_MISSIONS))
		{
			ps.setInt(1, characterObjectId);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					result.add(new MissionRecord(rs.getString("type"), rs.getInt("level"), rs.getInt("value")));
			}
		}
		return result;
	}

	@Override
	public void saveMissions(int characterObjectId, Collection<MissionRecord> missions) throws SQLException
	{
		if (missions.isEmpty())
			return;
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(getMissionUpsertSql()))
		{
			for (MissionRecord mission : missions)
			{
				ps.setInt(1, characterObjectId);
				ps.setString(2, mission.type());
				ps.setInt(3, mission.level());
				ps.setInt(4, mission.value());
				ps.addBatch();
			}
			ps.executeBatch();
		}
	}

	@Override
	public List<MemoRecord> loadMemos(int characterObjectId) throws SQLException
	{
		final List<MemoRecord> result = new ArrayList<>();
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(LOAD_MEMOS))
		{
			ps.setInt(1, characterObjectId);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					result.add(new MemoRecord(rs.getString("var"), rs.getString("val")));
			}
		}
		return result;
	}

	@Override
	public void saveMemo(int characterObjectId, MemoRecord memo) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(getMemoUpsertSql()))
		{
			ps.setInt(1, characterObjectId);
			ps.setString(2, memo.key());
			ps.setString(3, memo.value());
			ps.executeUpdate();
		}
	}

	@Override
	public void deleteMemo(int characterObjectId, String key) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(DELETE_MEMO))
		{
			ps.setInt(1, characterObjectId);
			ps.setString(2, key);
			ps.executeUpdate();
		}
	}

	private static String getMacroUpsertSql()
	{
		return DatabaseDialect.upsert("character_macroses", "char_obj_id,id,icon,name,descr,acronym,commands", "?,?,?,?,?,?,?", "char_obj_id,id", "icon,name,descr,acronym,commands");
	}

	private static String getShortcutUpsertSql()
	{
		return DatabaseDialect.upsert("character_shortcuts", "char_obj_id,slot,page,type,id,level,class_index", "?,?,?,?,?,?,?", "char_obj_id,slot,page,class_index", "type,id,level");
	}

	private static String getMissionUpsertSql()
	{
		return DatabaseDialect.upsert("character_mission", "object_id,type,level,value", "?,?,?,?", "object_id,type", "level,value");
	}

	private static String getMemoUpsertSql()
	{
		return DatabaseDialect.upsert("character_memo", "charId,var,val", "?,?,?", "charId,var", "val");
	}
}
