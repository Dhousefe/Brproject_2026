package ext.mods.gameserver.data.adapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.CharacterSelection;
import ext.mods.gameserver.data.repository.CharacterSelectionStore;
import ext.mods.gameserver.enums.Paperdoll;

/** JDBC adapter for the character-selection read model. */
public final class JdbcCharacterSelectionStore implements CharacterSelectionStore
{
	private static final String SELECT_CHARACTERS = "SELECT obj_Id, char_name, level, maxHp, curHp, maxMp, curMp, face, hairStyle, hairColor, sex, x, y, z, exp, sp, karma, pvpkills, pkkills, clanid, race, classid, deletetime, accesslevel, lastAccess, base_class FROM characters WHERE account_name=?";
	private static final String SELECT_CURRENT_SUBCLASS = "SELECT exp, sp, level FROM character_subclasses WHERE char_obj_id=? AND class_id=? ORDER BY char_obj_id";
	private static final String SELECT_PAPERDOLLS = "SELECT object_id,item_id,loc_data,enchant_level FROM items WHERE owner_id=? AND loc='PAPERDOLL'";
	private static final String SELECT_AUGMENTATION = "SELECT attributes FROM augmentations WHERE item_oid=?";

	@Override
	public List<CharacterSelection> findByAccount(String accountName) throws SQLException
	{
		final List<CharacterSelection> result = new ArrayList<>();
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(SELECT_CHARACTERS))
		{
			ps.setString(1, accountName);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					result.add(readSelection(con, rs));
			}
		}
		return result;
	}

	private static CharacterSelection readSelection(Connection con, ResultSet rs) throws SQLException
	{
		final int objectId = rs.getInt("obj_id");
		final int activeClassId = rs.getInt("classid");
		final int storedBaseClassId = rs.getInt("base_class");
		long exp = rs.getLong("exp");
		int sp = rs.getInt("sp");
		int level = rs.getInt("level");

		if (storedBaseClassId != activeClassId)
		{
			try (PreparedStatement ps = con.prepareStatement(SELECT_CURRENT_SUBCLASS))
			{
				ps.setInt(1, objectId);
				ps.setInt(2, activeClassId);
				try (ResultSet subclass = ps.executeQuery())
				{
					if (subclass.next())
					{
						exp = subclass.getLong("exp");
						sp = subclass.getInt("sp");
						level = subclass.getInt("level");
					}
				}
			}
		}

		final int[][] paperdoll = loadPaperdoll(con, objectId);
		final int weaponObjectId = paperdoll[Paperdoll.RHAND.getId()][0];
		final int augmentationId = loadAugmentation(con, weaponObjectId);
		final int baseClassId = (storedBaseClassId == 0 && activeClassId > 0) ? activeClassId : storedBaseClassId;

		return new CharacterSelection(objectId, rs.getString("char_name"), rs.getLong("deletetime"), rs.getInt("accesslevel"), level, rs.getInt("maxhp"), rs.getDouble("curhp"), rs.getInt("maxmp"), rs.getDouble("curmp"), rs.getInt("karma"), rs.getInt("pkkills"), rs.getInt("pvpkills"), rs.getInt("face"), rs.getInt("hairstyle"), rs.getInt("haircolor"), rs.getInt("sex"), exp, sp, rs.getInt("clanid"), rs.getInt("race"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"), activeClassId, baseClassId, rs.getLong("lastaccess"), paperdoll, augmentationId);
	}

	private static int[][] loadPaperdoll(Connection con, int objectId) throws SQLException
	{
		final int[][] paperdoll = new int[0x12][3];
		try (PreparedStatement ps = con.prepareStatement(SELECT_PAPERDOLLS))
		{
			ps.setInt(1, objectId);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					final int slot = rs.getInt("loc_data");
					if (slot < 0 || slot >= paperdoll.length)
						continue;

					paperdoll[slot][0] = rs.getInt("object_id");
					paperdoll[slot][1] = rs.getInt("item_id");
					paperdoll[slot][2] = rs.getInt("enchant_level");
				}
			}
		}
		return paperdoll;
	}

	private static int loadAugmentation(Connection con, int weaponObjectId) throws SQLException
	{
		if (weaponObjectId <= 0)
			return 0;

		try (PreparedStatement ps = con.prepareStatement(SELECT_AUGMENTATION))
		{
			ps.setInt(1, weaponObjectId);
			try (ResultSet rs = ps.executeQuery())
			{
				if (rs.next())
				{
					final int attributes = rs.getInt("attributes");
					return attributes == -1 ? 0 : attributes;
				}
			}
		}
		return 0;
	}
}
