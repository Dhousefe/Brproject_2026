package ext.mods.gameserver.data.adapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import ext.mods.commons.logging.CLogger;
import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.CharacterStore;
import ext.mods.gameserver.data.repository.CharacterState;
import ext.mods.gameserver.data.repository.CharacterSummary;

/** JDBC adapter for character lifecycle persistence. */
public final class JdbcCharacterStore implements CharacterStore
{
	private static final CLogger LOGGER = new CLogger(JdbcCharacterStore.class.getName());

	private static final String INSERT_CHARACTER = "INSERT INTO characters (account_name,obj_Id,char_name,level,maxHp,curHp,maxCp,curCp,maxMp,curMp,face,hairStyle,hairColor,sex,exp,sp,race,classid,base_class,title,accesslevel) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
	private static final String UPDATE_CHARACTER = "UPDATE characters SET level=?,maxHp=?,curHp=?,maxCp=?,curCp=?,maxMp=?,curMp=?,face=?,hairStyle=?,hairColor=?,sex=?,heading=?,x=?,y=?,z=?,exp=?,expBeforeDeath=?,sp=?,karma=?,pvpkills=?,pkkills=?,clanid=?,race=?,classid=?,deletetime=?,title=?,accesslevel=?,online=?,isin7sdungeon=?,wantspeace=?,base_class=?,onlinetime=?,punish_level=?,punish_timer=?,nobless=?,power_grade=?,subpledge=?,lvl_joined_academy=?,apprentice=?,sponsor=?,varka_ketra_ally=?,clan_join_expiry_time=?,clan_create_expiry_time=?,char_name=?,death_penalty_level=?,herountil=? WHERE obj_id=?";
	private static final String RESTORE_CHARACTER = "SELECT * FROM characters WHERE obj_id=?";
	private static final String UPDATE_ONLINE_STATUS = "UPDATE characters SET online=?, lastAccess=? WHERE obj_id=?";
	private static final String RESTORE_ACCOUNT_CHARS = "SELECT obj_Id, char_name FROM characters WHERE account_name=? AND obj_Id<>?";
	private static final String UPDATE_NOBLESS = "UPDATE characters SET nobless=? WHERE obj_Id=?";
	private static final String SELECT_CLAN = "SELECT clanId FROM characters WHERE obj_id=?";
	private static final String UPDATE_DELETE_TIME = "UPDATE characters SET deletetime=? WHERE obj_id=?";
	private static final String DELETE_CHAR_HENNAS = "DELETE FROM character_hennas WHERE char_obj_id=?";
	private static final String DELETE_CHAR_MACROS = "DELETE FROM character_macroses WHERE char_obj_id=?";
	private static final String DELETE_CHAR_MEMOS = "DELETE FROM character_memo WHERE charId=?";
	private static final String DELETE_CHAR_QUESTS = "DELETE FROM character_quests WHERE charId=?";
	private static final String DELETE_CHAR_RECIPES = "DELETE FROM character_recipebook WHERE charId=?";
	private static final String DELETE_CHAR_RELATIONS = "DELETE FROM character_relations WHERE char_id=? OR friend_id=?";
	private static final String DELETE_CHAR_SHORTCUTS = "DELETE FROM character_shortcuts WHERE char_obj_id=?";
	private static final String DELETE_CHAR_SKILLS = "DELETE FROM character_skills WHERE char_obj_id=?";
	private static final String DELETE_CHAR_SKILLS_SAVE = "DELETE FROM character_skills_save WHERE char_obj_id=?";
	private static final String DELETE_CHAR_SUBCLASSES = "DELETE FROM character_subclasses WHERE char_obj_id=?";
	private static final String DELETE_CHAR_HERO = "DELETE FROM heroes WHERE char_id=?";
	private static final String DELETE_CHAR_NOBLE = "DELETE FROM olympiad_nobles WHERE char_id=?";
	private static final String DELETE_CHAR_SEVEN_SIGNS = "DELETE FROM seven_signs WHERE char_obj_id=?";
	private static final String DELETE_CHAR_PETS = "DELETE FROM pets WHERE item_obj_id IN (SELECT object_id FROM items WHERE items.owner_id=?)";
	private static final String DELETE_CHAR_AUGMENTS = "DELETE FROM augmentations WHERE item_oid IN (SELECT object_id FROM items WHERE items.owner_id=?)";
	private static final String DELETE_CHAR_ITEMS = "DELETE FROM items WHERE owner_id=?";
	private static final String DELETE_CHAR_RBP = "DELETE FROM character_raid_points WHERE char_id=?";
	private static final String DELETE_CHAR = "DELETE FROM characters WHERE obj_Id=?";
	private static final String DELETE_CHAR_CACHE = "DELETE FROM character_data WHERE charId=?";

	@Override
	public void insert(CharacterState state) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(INSERT_CHARACTER))
		{
			ps.setString(1, state.accountName());
			ps.setInt(2, state.objectId());
			ps.setString(3, state.charName());
			ps.setInt(4, state.level());
			ps.setInt(5, state.maxHp());
			ps.setDouble(6, state.curHp());
			ps.setInt(7, state.maxCp());
			ps.setDouble(8, state.curCp());
			ps.setInt(9, state.maxMp());
			ps.setDouble(10, state.curMp());
			ps.setInt(11, state.face());
			ps.setInt(12, state.hairStyle());
			ps.setInt(13, state.hairColor());
			ps.setInt(14, state.sex());
			ps.setLong(15, state.exp());
			ps.setInt(16, state.sp());
			ps.setInt(17, state.race());
			ps.setInt(18, state.classId());
			ps.setInt(19, state.baseClass());
			ps.setString(20, state.title());
			ps.setInt(21, state.accessLevel());
			ps.executeUpdate();
		}
	}

	@Override
	public void update(CharacterState state) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(UPDATE_CHARACTER))
		{
			ps.setInt(1, state.level());
			ps.setInt(2, state.maxHp());
			ps.setDouble(3, state.curHp());
			ps.setInt(4, state.maxCp());
			ps.setDouble(5, state.curCp());
			ps.setInt(6, state.maxMp());
			ps.setDouble(7, state.curMp());
			ps.setInt(8, state.face());
			ps.setInt(9, state.hairStyle());
			ps.setInt(10, state.hairColor());
			ps.setInt(11, state.sex());
			ps.setInt(12, state.heading());
			ps.setInt(13, state.x());
			ps.setInt(14, state.y());
			ps.setInt(15, state.z());
			ps.setLong(16, state.exp());
			ps.setLong(17, state.expBeforeDeath());
			ps.setInt(18, state.sp());
			ps.setInt(19, state.karma());
			ps.setInt(20, state.pvpKills());
			ps.setInt(21, state.pkKills());
			ps.setInt(22, state.clanId());
			ps.setInt(23, state.race());
			ps.setInt(24, state.classId());
			ps.setLong(25, state.deleteTime());
			ps.setString(26, state.title());
			ps.setInt(27, state.accessLevel());
			ps.setInt(28, state.online());
			ps.setInt(29, state.inSevenSignsDungeon());
			ps.setInt(30, state.wantsPeace());
			ps.setInt(31, state.baseClass());
			ps.setLong(32, state.onlineTime());
			ps.setInt(33, state.punishmentLevel());
			ps.setLong(34, state.punishmentTimer());
			ps.setInt(35, state.nobless());
			ps.setInt(36, state.powerGrade());
			ps.setInt(37, state.subpledge());
			ps.setInt(38, state.levelJoinedAcademy());
			ps.setInt(39, state.apprentice());
			ps.setInt(40, state.sponsor());
			ps.setInt(41, state.varkaKetraAlly());
			ps.setLong(42, state.clanJoinExpiryTime());
			ps.setLong(43, state.clanCreateExpiryTime());
			ps.setString(44, state.charName());
			ps.setInt(45, state.deathPenaltyLevel());
			ps.setLong(46, state.heroUntil());
			ps.setInt(47, state.objectId());
			ps.executeUpdate();
		}
	}

	@Override
	public void updateOnlineStatus(int objectId, int online, long lastAccess) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(UPDATE_ONLINE_STATUS))
		{
			ps.setInt(1, online);
			ps.setLong(2, lastAccess);
			ps.setInt(3, objectId);
			ps.executeUpdate();
		}
	}

	@Override
	public void updateNobless(int objectId, boolean noble) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(UPDATE_NOBLESS))
		{
			ps.setInt(1, noble ? 1 : 0);
			ps.setInt(2, objectId);
			ps.executeUpdate();
		}
	}

	@Override
	public CharacterState restore(int objectId) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(RESTORE_CHARACTER))
		{
			ps.setInt(1, objectId);
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next() ? readState(rs) : null;
			}
		}
	}

	@Override
	public List<CharacterSummary> findAccountCharacters(String accountName, int excludedObjectId) throws SQLException
	{
		final List<CharacterSummary> characters = new ArrayList<>();
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(RESTORE_ACCOUNT_CHARS))
		{
			ps.setString(1, accountName);
			ps.setInt(2, excludedObjectId);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					characters.add(new CharacterSummary(rs.getInt("obj_Id"), rs.getString("char_name")));
			}
		}
		return characters;
	}

	private static CharacterState readState(ResultSet rs) throws SQLException
	{
		return new CharacterState(rs.getString("account_name"), rs.getInt("obj_Id"), rs.getString("char_name"), rs.getInt("level"), rs.getInt("maxHp"), rs.getDouble("curHp"), rs.getInt("maxCp"), rs.getDouble("curCp"), rs.getInt("maxMp"), rs.getDouble("curMp"), rs.getInt("face"), rs.getInt("hairStyle"), rs.getInt("hairColor"), rs.getInt("sex"), rs.getLong("exp"), rs.getInt("sp"), rs.getInt("race"), rs.getInt("classid"), rs.getInt("base_class"), rs.getString("title"), rs.getInt("accesslevel"), rs.getLong("lastAccess"), rs.getInt("heading"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"), rs.getLong("expBeforeDeath"), rs.getInt("karma"), rs.getInt("pvpkills"), rs.getInt("pkkills"), rs.getInt("clanid"), rs.getLong("deletetime"), rs.getInt("online"), rs.getInt("isin7sdungeon"), rs.getInt("wantspeace"), rs.getLong("onlinetime"), rs.getInt("punish_level"), rs.getLong("punish_timer"), rs.getInt("nobless"), rs.getInt("power_grade"), rs.getInt("subpledge"), rs.getInt("lvl_joined_academy"), rs.getInt("apprentice"), rs.getInt("sponsor"), rs.getInt("varka_ketra_ally"), rs.getLong("clan_join_expiry_time"), rs.getLong("clan_create_expiry_time"), rs.getInt("death_penalty_level"), rs.getLong("herountil"), rs.getInt("rec_have"), rs.getInt("rec_left"));
	}

	@Override
	public int findClanId(int objectId) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(SELECT_CLAN))
		{
			ps.setInt(1, objectId);
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next() ? rs.getInt(1) : 0;
			}
		}
	}

	@Override
	public void updateDeleteTime(int objectId, long deleteTime) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(UPDATE_DELETE_TIME))
		{
			ps.setLong(1, deleteTime);
			ps.setInt(2, objectId);
			ps.executeUpdate();
		}
	}

	@Override
	public void deleteCharacter(int objectId) throws SQLException
	{
		if (objectId < 0)
			return;

		try (Connection con = ConnectionPool.getConnection())
		{
			final boolean previousAutoCommit = con.getAutoCommit();
			con.setAutoCommit(false);
			try
			{
				executeDelete(con, DELETE_CHAR_HENNAS, objectId);
				executeDelete(con, DELETE_CHAR_MACROS, objectId);
				executeDelete(con, DELETE_CHAR_MEMOS, objectId);
				executeDelete(con, DELETE_CHAR_QUESTS, objectId);
				executeDelete(con, DELETE_CHAR_RECIPES, objectId);
				executeDeleteRelations(con, objectId);
				executeDelete(con, DELETE_CHAR_SHORTCUTS, objectId);
				executeDelete(con, DELETE_CHAR_SKILLS, objectId);
				executeDelete(con, DELETE_CHAR_SKILLS_SAVE, objectId);
				executeDelete(con, DELETE_CHAR_SUBCLASSES, objectId);
				executeDelete(con, DELETE_CHAR_HERO, objectId);
				executeDelete(con, DELETE_CHAR_NOBLE, objectId);
				executeDelete(con, DELETE_CHAR_SEVEN_SIGNS, objectId);
				executeDelete(con, DELETE_CHAR_PETS, objectId);
				executeDelete(con, DELETE_CHAR_AUGMENTS, objectId);
				executeDelete(con, DELETE_CHAR_ITEMS, objectId);
				executeDelete(con, DELETE_CHAR_RBP, objectId);
				executeDelete(con, DELETE_CHAR, objectId);
				executeDelete(con, DELETE_CHAR_CACHE, objectId);
				con.commit();
			}
			catch (SQLException e)
			{
				try
				{
					con.rollback();
				}
				catch (SQLException rollbackFailure)
				{
					e.addSuppressed(rollbackFailure);
				}
				throw e;
			}
			finally
			{
				con.setAutoCommit(previousAutoCommit);
			}
		}
	}

	private static void executeDelete(Connection con, String sql, int objectId) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement(sql))
		{
			ps.setInt(1, objectId);
			ps.executeUpdate();
		}
	}

	private static void executeDeleteRelations(Connection con, int objectId) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement(DELETE_CHAR_RELATIONS))
		{
			ps.setInt(1, objectId);
			ps.setInt(2, objectId);
			ps.executeUpdate();
		}
	}
}
