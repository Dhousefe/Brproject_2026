package ext.mods.gameserver.model.actor.player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ext.mods.commons.pool.ConnectionPool;
import ext.mods.commons.pool.ThreadPool;
import ext.mods.config.ConfigOfflineShop;
import ext.mods.config.ConfigPlayers;
import ext.mods.gameserver.data.manager.CursedWeaponManager;
import ext.mods.gameserver.data.manager.HeroManager;
import ext.mods.gameserver.data.sql.ClanTable;
import ext.mods.gameserver.data.xml.PlayerData;
import ext.mods.gameserver.enums.actors.Sex;
import ext.mods.gameserver.model.World;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.container.player.Appearance;
import ext.mods.gameserver.model.actor.container.player.SubClass;
import ext.mods.gameserver.model.actor.instance.Pet;
import ext.mods.gameserver.model.actor.template.PlayerTemplate;
import ext.mods.gameserver.model.location.Location;
import ext.mods.gameserver.model.pledge.Clan;
import ext.mods.gameserver.model.pledge.ClanMember;

/**
 * Character-row JDBC + load/restore orchestration for a {@link Player}
 * (att-ver-3.0 onda 1 + onda 4).
 * Subclass/skills/recom/premium live in dedicated components.
 */
public final class PlayerPersistence
{
	private static final Logger LOGGER = LoggerFactory.getLogger(PlayerPersistence.class);
	
	public static final String INSERT_CHARACTER = "INSERT INTO characters (account_name,obj_Id,char_name,level,maxHp,curHp,maxCp,curCp,maxMp,curMp,face,hairStyle,hairColor,sex,exp,sp,race,classid,base_class,title,accesslevel) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
	public static final String UPDATE_CHARACTER = "UPDATE characters SET level=?,maxHp=?,curHp=?,maxCp=?,curCp=?,maxMp=?,curMp=?,face=?,hairStyle=?,hairColor=?,sex=?,heading=?,x=?,y=?,z=?,exp=?,expBeforeDeath=?,sp=?,karma=?,pvpkills=?,pkkills=?,clanid=?,race=?,classid=?,deletetime=?,title=?,accesslevel=?,online=?,isin7sdungeon=?,wantspeace=?,base_class=?,onlinetime=?,punish_level=?,punish_timer=?,nobless=?,power_grade=?,subpledge=?,lvl_joined_academy=?,apprentice=?,sponsor=?,varka_ketra_ally=?,clan_join_expiry_time=?,clan_create_expiry_time=?,char_name=?,death_penalty_level=?,herountil=? WHERE obj_id=?";
	public static final String RESTORE_CHARACTER = "SELECT * FROM characters WHERE obj_id=?";
	public static final String UPDATE_ONLINE_STATUS = "UPDATE characters SET online=?, lastAccess=? WHERE obj_id=?";
	public static final String RESTORE_ACCOUNT_CHARS = "SELECT obj_Id, char_name FROM characters WHERE account_name=? AND obj_Id<>?";
	public static final String UPDATE_NOBLESS = "UPDATE characters SET nobless=? WHERE obj_Id=?";
	
	private final Player _owner;
	
	public PlayerPersistence(Player owner)
	{
		_owner = owner;
	}
	
	/**
	 * Insert a newly created character row. Returns false on failure.
	 */
	public static boolean insertCharacter(Player player, String accountName)
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(INSERT_CHARACTER))
		{
			ps.setString(1, accountName);
			ps.setInt(2, player.getObjectId());
			ps.setString(3, player.getName());
			ps.setInt(4, player.getStatus().getLevel());
			ps.setInt(5, player.getStatus().getMaxHp());
			ps.setDouble(6, player.getStatus().getHp());
			ps.setInt(7, player.getStatus().getMaxCp());
			ps.setDouble(8, player.getStatus().getCp());
			ps.setInt(9, player.getStatus().getMaxMp());
			ps.setDouble(10, player.getStatus().getMp());
			ps.setInt(11, player.getAppearance().getFace());
			ps.setInt(12, player.getAppearance().getHairStyle());
			ps.setInt(13, player.getAppearance().getHairColor());
			ps.setInt(14, player.getAppearance().getSex().ordinal());
			ps.setLong(15, player.getStatus().getExp());
			ps.setInt(16, player.getStatus().getSp());
			ps.setInt(17, player.getRace().ordinal());
			ps.setInt(18, player.getClassId().getId());
			ps.setInt(19, player.getBaseClass());
			ps.setString(20, player.getTitle());
			ps.setInt(21, player.getAccessLevel().getLevel());
			ps.executeUpdate();
			return true;
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't create player {} for {} account.", player.getName(), accountName, e);
			return false;
		}
	}
	
	/**
	 * Update online status and lastAccess for the owner.
	 */
	public void updateOnlineStatus()
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(UPDATE_ONLINE_STATUS))
		{
			ps.setInt(1, _owner.isOnlineInt());
			ps.setLong(2, System.currentTimeMillis());
			ps.setInt(3, _owner.getObjectId());
			ps.execute();
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't set player online status.", e);
		}
	}
	
	/**
	 * Persist noblesse flag only.
	 */
	public void storeNobless(boolean isNoble)
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(UPDATE_NOBLESS))
		{
			ps.setBoolean(1, isNoble);
			ps.setInt(2, _owner.getObjectId());
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't update nobless status for {}.", _owner.getName(), e);
		}
	}
	
	/**
	 * Restores secondary data for the owner, based on the current class index.
	 */
	public void restoreCharData()
	{
		// Register offline store + sellbuff CachedData keys before bulk load.
		_owner.getPrivateStore().ensureStoreCaches();
		_owner.getSellBuffComponent().ensureCache();
		
		_owner.getCachedData().load();
		_owner.getProfile().getCachedData().load();

		_owner.getSkillsDb().restoreSkills();
		
		_owner.getMacroList().restore();
		
		if (!_owner.isSubClassActive())
			_owner.getRecipeBook().restore();
		
		_owner.getShortcutList().restore();
		
		_owner.getHennaList().restore();
		
		_owner.getRecom().restore();
		
		_owner.getQuestList().restore();
		
		_owner.getMissions().restore();
	}
	
	/**
	 * Retrieve a Player from the characters table of the database.
	 * @param objectId Identifier of the object to initialized
	 * @param offline offline-shop restore path
	 * @return The Player loaded from the database, or null
	 */
	public static Player restore(int objectId, boolean offline)
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(RESTORE_CHARACTER))
		{
			ps.setInt(1, objectId);
			
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					final int activeClassId = rs.getInt("classid");
					final PlayerTemplate template = PlayerData.getInstance().getTemplate(activeClassId);
					final Appearance app = new Appearance(rs.getByte("face"), rs.getByte("hairColor"), rs.getByte("hairStyle"), Sex.VALUES[rs.getInt("sex")]);
					
					final Player player = new Player(objectId, template, rs.getString("account_name"), app);
					player.restorePremServiceData(player, rs.getString("account_name"));
					player.setName(rs.getString("char_name"));
					player.setLastAccess(rs.getLong("lastAccess"));
					
					player.getStatus().setExp(rs.getLong("exp"));
					player.getStatus().setLevel(rs.getByte("level"));
					player.getStatus().setSp(rs.getInt("sp"));
					
					player.setExpBeforeDeath(rs.getLong("expBeforeDeath"));
					player.setWantsPeace(rs.getInt("wantspeace") == 1);
					player.setKarma(rs.getInt("karma"));
					player.setPvpKills(rs.getInt("pvpkills"));
					player.setPkKills(rs.getInt("pkkills"));
					player.setOnlineTime(rs.getLong("onlinetime"));
					player.setNoble(rs.getInt("nobless") == 1, false);
					
					player.setClanJoinExpiryTime(rs.getLong("clan_join_expiry_time"));
					if (player.getClanJoinExpiryTime() < System.currentTimeMillis())
						player.setClanJoinExpiryTime(0);
					
					player.setClanCreateExpiryTime(rs.getLong("clan_create_expiry_time"));
					if (player.getClanCreateExpiryTime() < System.currentTimeMillis())
						player.setClanCreateExpiryTime(0);
					
					player.setSponsor(rs.getInt("sponsor"));
					player.setLvlJoinedAcademy(rs.getInt("lvl_joined_academy"));
					
					final Clan clan = ClanTable.getInstance().getClan(rs.getInt("clanid"));
					if (clan != null)
					{
						player.setClan(clan);
						player.setPowerGrade(rs.getInt("power_grade"));
						player.setPledgeType(rs.getInt("subpledge"));
						player.setPledgeClass(ClanMember.calculatePledgeClass(player));
					}
					
					player.setDeleteTimer(rs.getLong("deletetime"));
					player.setTitle(rs.getString("title"));
					int characterAccessLevel = rs.getInt("accesslevel");
					final var account = ext.mods.loginserver.data.sql.AccountTable.getInstance().getAccount(player.getAccountName());
					final int accountAccessLevel = account != null ? account.getAccessLevel() : 0;
					player.setAccessLevel(Math.max(characterAccessLevel, accountAccessLevel));
					player.setUptime(System.currentTimeMillis());
					player.setRecomHave(rs.getInt("rec_have"));
					player.setRecomLeft(rs.getInt("rec_left"));
					
					player.getSubClassComponent().setClassIndex(0);
					try
					{
						player.setBaseClass(rs.getInt("base_class"));
					}
					catch (Exception e)
					{
						player.setBaseClass(activeClassId);
					}
					
					if (PlayerSubClass.restoreSubClassData(player) && activeClassId != player.getBaseClass())
					{
						for (final SubClass subClass : player.getSubClasses().values())
							if (subClass.getClassId() == activeClassId)
								player.getSubClassComponent().setClassIndex(subClass.getClassIndex());
					}
					
					if (player.getClassIndex() == 0 && activeClassId != player.getBaseClass())
						player.setClassId(player.getBaseClass());
					else
						player.setClassTemplate(activeClassId);
					
					player.setApprentice(rs.getInt("apprentice"));
					player.setIsIn7sDungeon(rs.getInt("isin7sdungeon") == 1);
					
					player.getPunishment().load(rs.getInt("punish_level"), rs.getLong("punish_timer"));
					
					CursedWeaponManager.getInstance().checkPlayer(player);
					
					player.setAllianceWithVarkaKetra(rs.getInt("varka_ketra_ally"));
					
					player.setDeathPenaltyBuffLevel(rs.getInt("death_penalty_level"));
					
					player.setHeroUntil(rs.getLong("herountil"));
					
					player.getPosition().set(rs.getInt("x"), rs.getInt("y"), rs.getInt("z"), rs.getInt("heading"));
					
					if (HeroManager.getInstance().isActiveHero(objectId))
						player.setHero(true);
					
					if (player.getHeroUntil() > System.currentTimeMillis())
					{
						player.setHero(true);
						player.broadcastUserInfo();
						ThreadPool.schedule(new Runnable()
						{
							@Override
							public void run()
							{
								if (player.isOnline() && player.isHero())
								{
									player.setHero(false);
									player.setHeroUntil(0);
									player.store();
									player.broadcastUserInfo();
									player.sendMessage(player.getSysString(10_025));
								}
							}
						}, player.getHeroUntil() - System.currentTimeMillis());
					}
					else
						player.setHeroUntil(0);
					
					player.getPersistence().restoreCharData();
					player.giveSkills();
					
					if (clan != null)
						clan.getClanMember(objectId).setPlayerInstance(player);
					
					if (ConfigPlayers.STORE_SKILL_COOLTIME)
						player.restoreEffects();
					
					final Pet pet = World.getInstance().getPet(player.getObjectId());
					if (pet != null)
					{
						player.setSummon(pet);
						pet.setOwner(player);
					}
					
					player.refreshWeightPenalty();
					player.refreshExpertisePenalty();
					player.refreshHennaList();
					
					player.setOnlineStatus(true, false);
					player.setRunning(true);
					player.setStanding(true);
					
					final double currentHp = rs.getDouble("curHp");
					
					player.getStatus().setCpHpMp(rs.getDouble("curCp"), currentHp, rs.getDouble("curMp"));
					
					if (currentHp < 0.5)
					{
						player.setIsDead(true);
						player.getStatus().stopHpMpRegeneration();
					}
					
					if (ConfigOfflineShop.RESTORE_STORE_ITEMS && !offline)
						player.restoreStoreList();
					
					World.getInstance().addPlayer(player);
					
					try (PreparedStatement ps2 = con.prepareStatement(RESTORE_ACCOUNT_CHARS))
					{
						ps2.setString(1, player.getAccountNamePlayer());
						ps2.setInt(2, objectId);
						
						try (ResultSet rs2 = ps2.executeQuery())
						{
							while (rs2.next())
								player.getAccountChars().put(rs2.getInt("obj_Id"), rs2.getString("char_name"));
						}
					}
					
					return player;
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't restore player data.", e);
		}
		return null;
	}
	
	/**
	 * Update characters table with base-class stats.
	 * <p>
	 * Base exp/level/sp are read via {@link ext.mods.gameserver.model.actor.status.PlayerStatus}
	 * base-class accessors (PlayableStatus fields) — no classIndex flip, so concurrent subclass
	 * logic never observes a wrong index during store.
	 */
	public void storeCharBase()
	{
		// Base-class stats live on PlayableStatus regardless of active subclass (no classIndex mutation).
		final long exp = _owner.getStatus().getBaseClassExp();
		final int level = _owner.getStatus().getBaseClassLevel();
		final int sp = _owner.getStatus().getBaseClassSp();
		
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(UPDATE_CHARACTER))
		{
			ps.setInt(1, level);
			ps.setInt(2, _owner.getStatus().getMaxHp());
			ps.setDouble(3, _owner.getStatus().getHp());
			ps.setInt(4, _owner.getStatus().getMaxCp());
			ps.setDouble(5, _owner.getStatus().getCp());
			ps.setInt(6, _owner.getStatus().getMaxMp());
			ps.setDouble(7, _owner.getStatus().getMp());
			ps.setInt(8, _owner.getAppearance().getFace());
			ps.setInt(9, _owner.getAppearance().getHairStyle());
			ps.setInt(10, _owner.getAppearance().getHairColor());
			ps.setInt(11, _owner.getAppearance().getSex().ordinal());
			ps.setInt(12, _owner.getHeading());
			
			Location location = _owner.getBoatInfo().getDockLocation();
			if (location.equals(Location.DUMMY_LOC))
				location = (!_owner.isInObserverMode() ? _owner.getPosition() : _owner.getSavedLocation());
			
			ps.setInt(13, location.getX());
			ps.setInt(14, location.getY());
			ps.setInt(15, location.getZ());
			
			ps.setLong(16, exp);
			ps.setLong(17, _owner.getExpBeforeDeath());
			ps.setInt(18, sp);
			ps.setInt(19, _owner.getKarma());
			ps.setInt(20, _owner.getPvpKills());
			ps.setInt(21, _owner.getPkKills());
			ps.setInt(22, _owner.getClanId());
			ps.setInt(23, _owner.getRace().ordinal());
			ps.setInt(24, _owner.getClassId().getId());
			ps.setLong(25, _owner.getDeleteTimer());
			ps.setString(26, _owner.getTitle());
			ps.setInt(27, _owner.getAccessLevel().getLevel());
			ps.setInt(28, _owner.isOnlineInt());
			ps.setInt(29, _owner.isIn7sDungeon() ? 1 : 0);
			ps.setInt(30, _owner.wantsPeace() ? 1 : 0);
			ps.setInt(31, _owner.getBaseClass());
			
			long totalOnlineTime = _owner.getOnlineTime();
			if (_owner.getOnlineBeginTime() > 0)
				totalOnlineTime += (System.currentTimeMillis() - _owner.getOnlineBeginTime()) / 1000;
			
			ps.setLong(32, totalOnlineTime);
			ps.setInt(33, _owner.getPunishment().getType().ordinal());
			ps.setLong(34, _owner.getPunishment().getTimer());
			ps.setInt(35, _owner.isNoble() ? 1 : 0);
			ps.setLong(36, _owner.getPowerGrade());
			ps.setInt(37, _owner.getPledgeType());
			ps.setInt(38, _owner.getLvlJoinedAcademy());
			ps.setLong(39, _owner.getApprentice());
			ps.setLong(40, _owner.getSponsor());
			ps.setInt(41, _owner.getAllianceWithVarkaKetra());
			ps.setLong(42, _owner.getClanJoinExpiryTime());
			ps.setLong(43, _owner.getClanCreateExpiryTime());
			ps.setString(44, _owner.getName());
			ps.setLong(45, _owner.getDeathPenaltyBuffLevel());
			ps.setLong(46, _owner.getHeroUntil());
			ps.setInt(47, _owner.getObjectId());
			
			ps.execute();
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't store player base data.", e);
		}
	}
	
	/**
	 * High-level store orchestration: cached data, missions, base, subclasses, effects.
	 */
	public void store(boolean storeActiveEffects)
	{
		_owner.getCachedData().store();
		_owner.getProfile().getCachedData().store();
		_owner.getMissions().store();

		storeCharBase();
		_owner.getSubClassComponent().storeCharSub();
		_owner.getSkillsDb().storeEffect(storeActiveEffects);
	}
}
