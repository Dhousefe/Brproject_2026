package ext.mods.gameserver.model.actor.player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ext.mods.commons.pool.ThreadPool;
import ext.mods.config.ConfigOfflineShop;
import ext.mods.config.ConfigPlayers;
import ext.mods.gameserver.data.manager.CursedWeaponManager;
import ext.mods.gameserver.data.manager.HeroManager;
import ext.mods.gameserver.data.repository.CharacterState;
import ext.mods.gameserver.data.sql.ClanTable;
import ext.mods.gameserver.data.service.CharacterLifecycleService;
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
		try
		{
			CharacterLifecycleService.insert(CharacterState.initial(accountName, player.getObjectId(), player.getName(), player.getStatus().getLevel(), player.getStatus().getMaxHp(), player.getStatus().getHp(), player.getStatus().getMaxCp(), player.getStatus().getCp(), player.getStatus().getMaxMp(), player.getStatus().getMp(), player.getAppearance().getFace(), player.getAppearance().getHairStyle(), player.getAppearance().getHairColor(), player.getAppearance().getSex().ordinal(), player.getStatus().getExp(), player.getStatus().getSp(), player.getRace().ordinal(), player.getClassId().getId(), player.getBaseClass(), player.getTitle(), player.getAccessLevel().getLevel()));
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
		try
		{
			CharacterLifecycleService.updateOnlineStatus(_owner.getObjectId(), _owner.isOnlineInt(), System.currentTimeMillis());
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
		try
		{
			CharacterLifecycleService.updateNobless(_owner.getObjectId(), isNoble);
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
		try
		{
			final CharacterState state = CharacterLifecycleService.restore(objectId);
			if (state == null)
				return null;

			final int activeClassId = state.classId();
					final PlayerTemplate template = PlayerData.getInstance().getTemplate(activeClassId);
					final Appearance app = new Appearance((byte) state.face(), (byte) state.hairColor(), (byte) state.hairStyle(), Sex.VALUES[state.sex()]);

					final Player player = new Player(objectId, template, state.accountName(), app);
					player.restorePremServiceData(player, state.accountName());
					player.setName(state.charName());
					player.setLastAccess(state.lastAccess());

					player.getStatus().setExp(state.exp());
					player.getStatus().setLevel((byte) state.level());
					player.getStatus().setSp(state.sp());

					player.setExpBeforeDeath(state.expBeforeDeath());
					player.setWantsPeace(state.wantsPeace() == 1);
					player.setKarma(state.karma());
					player.setPvpKills(state.pvpKills());
					player.setPkKills(state.pkKills());
					player.setOnlineTime(state.onlineTime());
					player.setNoble(state.nobless() == 1, false);

					player.setClanJoinExpiryTime(state.clanJoinExpiryTime());
					if (player.getClanJoinExpiryTime() < System.currentTimeMillis())
						player.setClanJoinExpiryTime(0);
					
					player.setClanCreateExpiryTime(state.clanCreateExpiryTime());
					if (player.getClanCreateExpiryTime() < System.currentTimeMillis())
						player.setClanCreateExpiryTime(0);
					
					player.setSponsor(state.sponsor());
					player.setLvlJoinedAcademy(state.levelJoinedAcademy());
					
					final Clan clan = ClanTable.getInstance().getClan(state.clanId());
					if (clan != null)
					{
						player.setClan(clan);
						player.setPowerGrade(state.powerGrade());
						player.setPledgeType(state.subpledge());
						player.setPledgeClass(ClanMember.calculatePledgeClass(player));
					}
					
					player.setDeleteTimer(state.deleteTime());
					player.setTitle(state.title());
					int characterAccessLevel = state.accessLevel();
					final var account = ext.mods.loginserver.data.sql.AccountTable.getInstance().getAccount(player.getAccountName());
					final int accountAccessLevel = account != null ? account.getAccessLevel() : 0;
					player.setAccessLevel(Math.max(characterAccessLevel, accountAccessLevel));
					player.setUptime(System.currentTimeMillis());
					player.setRecomHave(state.recHave());
					player.setRecomLeft(state.recLeft());
					
					player.getSubClassComponent().setClassIndex(0);
					try
					{
						player.setBaseClass(state.baseClass());
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
					
					player.setApprentice(state.apprentice());
					player.setIsIn7sDungeon(state.inSevenSignsDungeon() == 1);
					
					player.getPunishment().load(state.punishmentLevel(), state.punishmentTimer());
					
					CursedWeaponManager.getInstance().checkPlayer(player);
					
					player.setAllianceWithVarkaKetra(state.varkaKetraAlly());
					
					player.setDeathPenaltyBuffLevel(state.deathPenaltyLevel());
					
					player.setHeroUntil(state.heroUntil());
					
					player.getPosition().set(state.x(), state.y(), state.z(), state.heading());
					
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
					
					final double currentHp = state.curHp();
					
					player.getStatus().setCpHpMp(state.curCp(), currentHp, state.curMp());
					
					if (currentHp < 0.5)
					{
						player.setIsDead(true);
						player.getStatus().stopHpMpRegeneration();
					}
					
					if (ConfigOfflineShop.RESTORE_STORE_ITEMS && !offline)
						player.restoreStoreList();
					
					World.getInstance().addPlayer(player);
					
			for (final var character : CharacterLifecycleService.findAccountCharacters(player.getAccountNamePlayer(), objectId))
				player.getAccountChars().put(character.objectId(), character.name());

			return player;
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
		
		try
		{
			final Location location = resolveStoreLocation();
			long totalOnlineTime = _owner.getOnlineTime();
			if (_owner.getOnlineBeginTime() > 0)
				totalOnlineTime += (System.currentTimeMillis() - _owner.getOnlineBeginTime()) / 1000;
			CharacterLifecycleService.update(new CharacterState(_owner.getAccountName(), _owner.getObjectId(), _owner.getName(), level, _owner.getStatus().getMaxHp(), _owner.getStatus().getHp(), _owner.getStatus().getMaxCp(), _owner.getStatus().getCp(), _owner.getStatus().getMaxMp(), _owner.getStatus().getMp(), _owner.getAppearance().getFace(), _owner.getAppearance().getHairStyle(), _owner.getAppearance().getHairColor(), _owner.getAppearance().getSex().ordinal(), exp, sp, _owner.getRace().ordinal(), _owner.getClassId().getId(), _owner.getBaseClass(), _owner.getTitle(), _owner.getAccessLevel().getLevel(), 0, _owner.getHeading(), location.getX(), location.getY(), location.getZ(), _owner.getExpBeforeDeath(), _owner.getKarma(), _owner.getPvpKills(), _owner.getPkKills(), _owner.getClanId(), _owner.getDeleteTimer(), _owner.isOnlineInt(), _owner.isIn7sDungeon() ? 1 : 0, _owner.wantsPeace() ? 1 : 0, totalOnlineTime, _owner.getPunishment().getType().ordinal(), _owner.getPunishment().getTimer(), _owner.isNoble() ? 1 : 0, _owner.getPowerGrade(), _owner.getPledgeType(), _owner.getLvlJoinedAcademy(), _owner.getApprentice(), _owner.getSponsor(), _owner.getAllianceWithVarkaKetra(), _owner.getClanJoinExpiryTime(), _owner.getClanCreateExpiryTime(), _owner.getDeathPenaltyBuffLevel(), _owner.getHeroUntil(), 0, 0));
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't store player base data.", e);
		}
	}

	private Location resolveStoreLocation()
	{
		Location location = _owner.getBoatInfo().getDockLocation();
		if (location.equals(Location.DUMMY_LOC))
			location = (!_owner.isInObserverMode() ? _owner.getPosition() : _owner.getSavedLocation());
		return location;
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
