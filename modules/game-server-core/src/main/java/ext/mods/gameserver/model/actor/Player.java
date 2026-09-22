/*
* Copyleft © 2024-2026 L2Brproject
* * This file is part of L2Brproject derived from aCis409/RusaCis3.8
* * L2Brproject is free software: you can redistribute it and/or modify it
* under the terms of the GNU General Public License as published by the
* Free Software Foundation, either version 3 of the License.
* * L2Brproject is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* General Public License for more details.
* * You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
* Our main Developers, Dhousefe-L2JBR, Agazes33, Ban-L2jDev, Warman, SrEli.
* Our special thanks, Nattan Felipe, Diego Fonseca, Junin, ColdPlay, Denky, MecBew, Localhost, MundvayneHELLBOY, 
* SonecaL2, Eduardo.SilvaL2J, biLL, xpower, xTech, kakuzo, Tiagorosendo, Schuster, LucasStark, damedd
* as a contribution for the forum L2JBrasil.com
 */
package ext.mods.gameserver.model.actor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import ext.mods.Config;

import ext.mods.commons.cached.CachedData;
import ext.mods.commons.cached.CachedDataValueBoolean;
import ext.mods.commons.cached.CachedDataValueInt;
import ext.mods.commons.cached.CachedDataValueObject;
import ext.mods.commons.cached.CachedDataValueObject.Converter;
import ext.mods.commons.cached.CachedDataValueString;
import ext.mods.commons.pool.ThreadPool;
import ext.mods.extensions.api.IFarmZoneInfo;
import ext.mods.extensions.hooks.BattleBossHooks;
import ext.mods.extensions.hooks.FarmEventHooks;
import ext.mods.extensions.hooks.PlayerGodHooks;
import ext.mods.extensions.hooks.SafeDisconnectHooks;
import ext.mods.extensions.hooks.TournamentHooks;
import ext.mods.extensions.hooks.TranslatorHooks;
import ext.mods.commons.random.Rnd;
import ext.mods.commons.util.ArraysUtil;
import ext.mods.extensions.listener.manager.CreatureListenerManager;
import ext.mods.extensions.listener.manager.InventoryListenerManager;
import ext.mods.extensions.listener.manager.PlayerListenerManager;
import ext.mods.gameserver.model.actor.player.PlayerAttachments;
import ext.mods.gameserver.model.actor.player.PlayerBoat;
import ext.mods.gameserver.model.actor.player.PlayerBypass;
import ext.mods.gameserver.model.actor.player.PlayerCursedWeapon;
import ext.mods.gameserver.model.actor.player.PlayerDeath;
import ext.mods.gameserver.model.actor.player.PlayerOperate;
import ext.mods.gameserver.model.actor.player.PlayerPenalty;
import ext.mods.gameserver.model.actor.player.PlayerProtect;
import ext.mods.gameserver.model.actor.player.PlayerPvP;
import ext.mods.gameserver.model.actor.player.PlayerSession;
import ext.mods.gameserver.model.actor.player.PlayerSocial;
import ext.mods.gameserver.model.actor.player.PlayerSoulShot;
import ext.mods.gameserver.model.actor.player.PlayerProfile;
import ext.mods.gameserver.model.actor.player.PlayerCombat;
import ext.mods.gameserver.model.actor.player.PlayerAppearanceBroadcast;
import ext.mods.gameserver.model.actor.player.PlayerInventoryAccess;
import ext.mods.gameserver.model.actor.player.PlayerSkillManager;
import ext.mods.gameserver.model.actor.player.PlayerTeam;
import ext.mods.gameserver.model.actor.player.PlayerUiPrefs;

import ext.mods.gameserver.model.actor.player.PlayerCharge;
import ext.mods.gameserver.model.actor.player.PlayerClan;
import ext.mods.gameserver.model.actor.player.PlayerDeathLifecycle;
import ext.mods.gameserver.model.actor.player.PlayerDuel;
import ext.mods.gameserver.model.actor.player.PlayerHero;
import ext.mods.gameserver.model.actor.player.PlayerHwid;
import ext.mods.gameserver.model.actor.player.PlayerMountAndSummon;
import ext.mods.gameserver.model.actor.player.PlayerSessionPersistence;
import ext.mods.gameserver.model.actor.player.PlayerSocialManager;
import ext.mods.gameserver.model.actor.player.PlayerMount;
import ext.mods.gameserver.model.actor.player.PlayerOffline;
import ext.mods.gameserver.model.actor.player.PlayerOlympiad;
import ext.mods.gameserver.model.actor.player.PlayerParty;
import ext.mods.gameserver.model.actor.player.PlayerPcCafe;
import ext.mods.gameserver.model.actor.player.PlayerPersistence;
import ext.mods.gameserver.model.actor.player.PlayerPremium;
import ext.mods.gameserver.model.actor.player.PlayerPrivateStore;
import ext.mods.gameserver.model.actor.player.PlayerRecom;
import ext.mods.gameserver.model.actor.player.PlayerRequest;
import ext.mods.gameserver.model.actor.player.PlayerSellBuff;
import ext.mods.gameserver.model.actor.player.PlayerSkillsDb;
import ext.mods.gameserver.model.actor.player.PlayerStorage;
import ext.mods.gameserver.model.actor.player.PlayerSubClass;
import ext.mods.gameserver.model.actor.player.PlayerTrade;
import ext.mods.gameserver.model.actor.player.TournamentState;
import ext.mods.gameserver.model.actor.player.TranslatorState;
import ext.mods.gameserver.model.actor.player.QuestKillState;
import ext.mods.gameserver.model.actor.player.BattleBossState;
import ext.mods.gameserver.LoginServerThread;
import ext.mods.gameserver.communitybbs.CommunityBoard;
import ext.mods.gameserver.communitybbs.model.Forum;
import ext.mods.gameserver.custom.data.PvPData;
import ext.mods.gameserver.custom.data.PvPData.ColorSystem;
import ext.mods.gameserver.custom.data.PvPData.RewardSystem;
import ext.mods.gameserver.data.SkillTable;
import ext.mods.gameserver.data.SkillTable.FrequentSkill;
import ext.mods.gameserver.data.manager.AntiFeedManager;
import ext.mods.gameserver.data.manager.CastleManager;
import ext.mods.gameserver.data.manager.CoupleManager;
import ext.mods.gameserver.data.manager.CursedWeaponManager;
import ext.mods.gameserver.data.manager.FestivalOfDarknessManager;
import ext.mods.gameserver.data.manager.HeroManager;
import ext.mods.gameserver.data.manager.PartyMatchRoomManager;
import ext.mods.gameserver.data.manager.PcCafeManager;
import ext.mods.gameserver.data.manager.RelationManager;
import ext.mods.gameserver.data.manager.SevenSignsManager;
import ext.mods.gameserver.data.manager.ZoneManager;
import ext.mods.gameserver.data.sql.ClanTable;
import ext.mods.gameserver.model.zone.type.RandomZone;
import ext.mods.gameserver.data.sql.PlayerInfoTable;
import ext.mods.gameserver.data.xml.AdminData;
import ext.mods.gameserver.data.xml.ItemData;
import ext.mods.gameserver.data.xml.NpcData;
import ext.mods.gameserver.data.xml.PlayerData;
import ext.mods.gameserver.data.xml.PlayerLevelData;
import ext.mods.gameserver.data.xml.RestartPointData;
import ext.mods.gameserver.data.xml.ScriptData;
import ext.mods.gameserver.data.xml.SysString;
import ext.mods.gameserver.enums.AiEventType;
import ext.mods.gameserver.enums.CabalType;
import ext.mods.gameserver.enums.GaugeColor;
import ext.mods.gameserver.enums.LootRule;
import ext.mods.gameserver.enums.MessageType;
import ext.mods.gameserver.enums.Paperdoll;
import ext.mods.gameserver.enums.PcCafeConsumeType;
import ext.mods.gameserver.enums.PrivateStoreType;
import ext.mods.gameserver.enums.PrivilegeType;
import ext.mods.gameserver.enums.PunishmentType;
import ext.mods.gameserver.enums.RestartType;
import ext.mods.gameserver.enums.ShortcutType;
import ext.mods.gameserver.enums.SpawnType;
import ext.mods.gameserver.enums.StatusType;
import ext.mods.gameserver.enums.TeamType;
import ext.mods.gameserver.enums.TeleportMode;
import ext.mods.gameserver.enums.ZoneId;
import ext.mods.gameserver.enums.actors.ClassId;
import ext.mods.gameserver.enums.actors.ClassRace;
import ext.mods.gameserver.enums.actors.ClassType;
import ext.mods.gameserver.enums.actors.MissionType;
import ext.mods.gameserver.enums.actors.MoveType;
import ext.mods.gameserver.enums.actors.OperateType;
import ext.mods.gameserver.enums.actors.Sex;
import ext.mods.gameserver.enums.actors.WeightPenalty;
import ext.mods.gameserver.enums.bbs.ForumAccess;
import ext.mods.gameserver.enums.bbs.ForumType;
import ext.mods.gameserver.enums.duels.DuelState;
import ext.mods.gameserver.enums.items.ActionType;
import ext.mods.gameserver.enums.items.EtcItemType;
import ext.mods.gameserver.enums.items.ItemLocation;
import ext.mods.gameserver.enums.items.ShotType;
import ext.mods.gameserver.enums.items.WeaponType;
import ext.mods.gameserver.enums.skills.EffectFlag;
import ext.mods.gameserver.enums.skills.EffectType;
import ext.mods.gameserver.enums.skills.SkillType;
import ext.mods.gameserver.enums.skills.Stats;
import ext.mods.gameserver.geoengine.GeoEngine;
import ext.mods.gameserver.handler.IItemHandler;
import ext.mods.gameserver.handler.ItemHandler;
import ext.mods.gameserver.handler.admincommandhandlers.AdminEditChar;
import ext.mods.gameserver.handler.skillhandlers.SummonFriend;
import ext.mods.gameserver.handler.voicedcommandhandlers.PlayerInfo;

import ext.mods.gameserver.model.AccessLevel;
import ext.mods.gameserver.model.SellBuffHolder;
import ext.mods.gameserver.model.World;
import ext.mods.gameserver.model.WorldObject;
import ext.mods.gameserver.model.actor.ai.type.PlayerAI;
import ext.mods.gameserver.model.actor.attack.PlayerAttack;
import ext.mods.gameserver.model.actor.cast.PlayerCast;
import ext.mods.gameserver.model.actor.container.npc.RewardInfo;
import ext.mods.gameserver.model.actor.container.player.Appearance;
import ext.mods.gameserver.model.actor.container.player.BoatInfo;
import ext.mods.gameserver.model.actor.container.player.CubicList;
import ext.mods.gameserver.model.actor.container.player.FishingStance;
import ext.mods.gameserver.model.actor.container.player.HennaList;
import ext.mods.gameserver.model.actor.container.player.MacroList;
import ext.mods.gameserver.model.actor.container.player.MissionList;
import ext.mods.gameserver.model.actor.container.player.Punishment;
import ext.mods.gameserver.model.actor.container.player.QuestList;
import ext.mods.gameserver.model.actor.container.player.RadarList;
import ext.mods.gameserver.model.actor.container.player.RecipeBook;
import ext.mods.gameserver.model.actor.container.player.Request;
import ext.mods.gameserver.model.actor.container.player.ShortcutList;
import ext.mods.gameserver.model.actor.container.player.SubClass;

import ext.mods.gameserver.model.actor.instance.Door;
import ext.mods.gameserver.model.actor.instance.FestivalMonster;
import ext.mods.gameserver.model.actor.instance.Folk;
import ext.mods.gameserver.model.actor.instance.Pet;
import ext.mods.gameserver.model.actor.instance.Servitor;
import ext.mods.gameserver.model.actor.instance.StaticObject;
import ext.mods.gameserver.model.actor.instance.TamedBeast;
import ext.mods.gameserver.model.actor.instance.WeddingManagerNpc;
import ext.mods.gameserver.model.actor.move.PlayerMove;
import ext.mods.gameserver.model.actor.status.PlayerStatus;
import ext.mods.gameserver.model.actor.template.NpcTemplate;
import ext.mods.gameserver.model.actor.template.PetTemplate;
import ext.mods.gameserver.model.actor.template.PlayerTemplate;
import ext.mods.gameserver.model.craft.ManufactureList;
import ext.mods.gameserver.model.entity.autofarm.AutoFarmManager;
import ext.mods.gameserver.model.entity.events.capturetheflag.CTFEvent;
import ext.mods.gameserver.model.entity.events.deathmatch.DMEvent;
import ext.mods.gameserver.model.entity.events.lastman.LMEvent;
import ext.mods.gameserver.model.entity.events.teamvsteam.TvTEvent;
import ext.mods.gameserver.model.group.CommandChannel;
import ext.mods.gameserver.model.group.Party;
import ext.mods.gameserver.model.group.PartyMatchRoom;
import ext.mods.gameserver.model.holder.IntIntHolder;
import ext.mods.gameserver.model.holder.skillnode.GeneralSkillNode;
import ext.mods.gameserver.model.item.instance.ItemInstance;
import ext.mods.gameserver.model.item.kind.Item;
import ext.mods.gameserver.model.item.kind.Weapon;
import ext.mods.gameserver.model.itemcontainer.Inventory;
import ext.mods.gameserver.model.itemcontainer.ItemContainer;
import ext.mods.gameserver.model.itemcontainer.PcFreight;
import ext.mods.gameserver.model.itemcontainer.PcInventory;
import ext.mods.gameserver.model.itemcontainer.PcWarehouse;
import ext.mods.gameserver.model.itemcontainer.listeners.ItemPassiveSkillsListener;
import ext.mods.gameserver.model.location.Location;
import ext.mods.gameserver.model.location.ObserverLocation;
import ext.mods.gameserver.model.memo.PlayerMemo;
import ext.mods.gameserver.model.multisell.PreparedListContainer;
import ext.mods.gameserver.model.olympiad.OlympiadGameManager;
import ext.mods.gameserver.model.olympiad.OlympiadGameTask;
import ext.mods.gameserver.model.olympiad.OlympiadManager;
import ext.mods.gameserver.model.pledge.Clan;
import ext.mods.gameserver.model.pledge.ClanMember;
import ext.mods.gameserver.model.records.ManufactureItem;
import ext.mods.gameserver.model.records.PetDataEntry;
import ext.mods.gameserver.model.records.Timestamp;
import ext.mods.gameserver.model.residence.castle.Castle;
import ext.mods.gameserver.model.trade.TradeItem;
import ext.mods.gameserver.model.trade.TradeList;
import ext.mods.gameserver.model.zone.type.BossZone;
import ext.mods.InstanceMap.InstanceManager;
import ext.mods.gameserver.network.GameClient;
import ext.mods.gameserver.network.SystemMessageId;
import ext.mods.gameserver.network.serverpackets.AbstractNpcInfo;
import ext.mods.gameserver.network.serverpackets.ActionFailed;
import ext.mods.gameserver.network.serverpackets.ChairSit;
import ext.mods.gameserver.network.serverpackets.ChangeWaitType;
import ext.mods.gameserver.network.serverpackets.CharInfo;
import ext.mods.gameserver.network.serverpackets.ConfirmDlg;
import ext.mods.gameserver.network.serverpackets.DeleteObject;
import ext.mods.gameserver.network.serverpackets.EnchantResult;
import ext.mods.gameserver.network.serverpackets.EtcStatusUpdate;
import ext.mods.gameserver.network.serverpackets.ExAutoSoulShot;
import ext.mods.gameserver.network.serverpackets.ExOlympiadMode;
import ext.mods.gameserver.network.serverpackets.ExPCCafePointInfo;
import ext.mods.gameserver.network.serverpackets.ExServerPrimitive;
import ext.mods.gameserver.network.serverpackets.ExSetCompassZoneCode;
import ext.mods.gameserver.network.serverpackets.ExStorageMaxCount;
import ext.mods.gameserver.network.serverpackets.HennaInfo;
import ext.mods.gameserver.network.serverpackets.InventoryUpdate;
import ext.mods.gameserver.network.serverpackets.L2GameServerPacket;
import ext.mods.gameserver.network.serverpackets.LeaveWorld;
import ext.mods.gameserver.network.serverpackets.MagicSkillUse;
import ext.mods.gameserver.network.serverpackets.MyTargetSelected;
import ext.mods.gameserver.network.serverpackets.NpcHtmlMessage;
import ext.mods.gameserver.network.serverpackets.ObserverEnd;
import ext.mods.gameserver.network.serverpackets.ObserverStart;
import ext.mods.gameserver.network.serverpackets.PartySmallWindowUpdate;
import ext.mods.gameserver.network.serverpackets.PledgeShowMemberListDelete;
import ext.mods.gameserver.network.serverpackets.PledgeShowMemberListUpdate;
import ext.mods.gameserver.network.serverpackets.PrivateStoreListBuy;
import ext.mods.gameserver.network.serverpackets.PrivateStoreListSell;
import ext.mods.gameserver.network.serverpackets.PrivateStoreManageListBuy;
import ext.mods.gameserver.network.serverpackets.PrivateStoreManageListSell;
import ext.mods.gameserver.network.serverpackets.PrivateStoreMsgBuy;
import ext.mods.gameserver.network.serverpackets.PrivateStoreMsgSell;
import ext.mods.gameserver.network.serverpackets.RecipeShopManageList;
import ext.mods.gameserver.network.serverpackets.RecipeShopMsg;
import ext.mods.gameserver.network.serverpackets.RecipeShopSellList;
import ext.mods.gameserver.network.serverpackets.RelationChanged;
import ext.mods.gameserver.network.serverpackets.Revive;
import ext.mods.gameserver.network.serverpackets.Ride;
import ext.mods.gameserver.network.serverpackets.SendTradeDone;
import ext.mods.gameserver.network.serverpackets.ServerClose;
import ext.mods.gameserver.network.serverpackets.SetupGauge;
import ext.mods.gameserver.network.serverpackets.ShortBuffStatusUpdate;
import ext.mods.gameserver.network.serverpackets.ShortCutInit;
import ext.mods.gameserver.network.serverpackets.SkillCoolTime;
import ext.mods.gameserver.network.serverpackets.SkillList;
import ext.mods.gameserver.network.serverpackets.SocialAction;
import ext.mods.gameserver.network.serverpackets.StaticObjectInfo;
import ext.mods.gameserver.network.serverpackets.StatusUpdate;
import ext.mods.gameserver.network.serverpackets.SystemMessage;
import ext.mods.gameserver.network.serverpackets.TargetSelected;
import ext.mods.gameserver.network.serverpackets.TargetUnselected;
import ext.mods.gameserver.network.serverpackets.TitleUpdate;
import ext.mods.gameserver.network.serverpackets.TradePressOtherOk;
import ext.mods.gameserver.network.serverpackets.TradePressOwnOk;
import ext.mods.gameserver.network.serverpackets.TradeStart;
import ext.mods.gameserver.network.serverpackets.UserInfo;
import ext.mods.gameserver.network.serverpackets.ValidateLocation;
import ext.mods.gameserver.scripting.Quest;
import ext.mods.gameserver.scripting.QuestState;
import ext.mods.gameserver.skills.AbstractEffect;
import ext.mods.gameserver.skills.Formulas;
import ext.mods.gameserver.skills.L2Skill;

import ext.mods.gameserver.skills.funcs.FuncHenna;
import ext.mods.gameserver.skills.funcs.FuncMaxCpMul;
import ext.mods.gameserver.skills.funcs.FuncRegenCpMul;
import ext.mods.gameserver.taskmanager.AttackStanceTaskManager;
import ext.mods.gameserver.taskmanager.PvpFlagTaskManager;
import ext.mods.gameserver.taskmanager.ShadowItemTaskManager;
import ext.mods.gameserver.taskmanager.WaterTaskManager;
import ext.mods.quests.QuestData;
import ext.mods.quests.QuestManager;
import ext.mods.quests.holder.QuestHolder;
import ext.mods.quests.holder.QuestObjective;
import ext.mods.config.ConfigLanguage;
import ext.mods.config.ConfigOfflineShop;
import ext.mods.config.ConfigPlayers;
import ext.mods.config.ConfigProject;
import ext.mods.config.ConfigRates;
import ext.mods.config.ConfigServer;
import ext.mods.config.ConfigSiege;

/**
 * This class represents a player in the world.<br>
 * There is always a client-thread connected to this (except if a player-store is activated upon logout).
 */
public class Player extends Playable
{
	public static final int REQUEST_TIMEOUT = PlayerRequest.REQUEST_TIMEOUT;

	// character / skills / subclass / recom / premium SQL → components (att-ver-3.0 onda 1–5)
	
	
	private static final Comparator<GeneralSkillNode> COMPARE_SKILLS_BY_MIN_LVL = Comparator.comparing(GeneralSkillNode::getMinLvl);
	private static final Comparator<GeneralSkillNode> COMPARE_SKILLS_BY_LVL = Comparator.comparing(GeneralSkillNode::getValue);
	
	
	private final Map<Integer, String> _chars = new HashMap<>();
	
	private final String _accountName;
	
	
	protected int _baseClass;
	protected int _activeClass;
	
	private final Appearance _appearance;
	
	private long _expBeforeDeath;
	
	private int _lastCompassZone;
	
	
	private final Punishment _punishment = new Punishment(this);
	private final RecipeBook _recipeBook = new RecipeBook(this);
	
	private TeleportMode _teleportMode = TeleportMode.NONE;
	
	private boolean _isSitting;
	private boolean _isStanding;
	private boolean _isSittingNow;
	private boolean _isStandingNow;
	
	private final Location _savedLocation = new Location(0, 0, 0);
	
	private final PcInventory _inventory = new PcInventory(this);
	
	/** Core trade/store + persistence components (att-ver-3.0). */
	private final PlayerStorage _storage = new PlayerStorage(this);
	private final PlayerTrade _trade = new PlayerTrade(this);
	private final PlayerPrivateStore _privateStore = new PlayerPrivateStore(this);
	private final PlayerPersistence _persistence = new PlayerPersistence(this);
	private final PlayerSubClass _subClass = new PlayerSubClass(this);
	private final PlayerSkillsDb _skillsDb = new PlayerSkillsDb(this);
	private final PlayerRecom _recom = new PlayerRecom(this);
	private final PlayerPremium _premium = new PlayerPremium(this);
	private final PlayerOlympiad _olympiad = new PlayerOlympiad(this);
	private final PlayerParty _partyComponent = new PlayerParty(this);
	private final PlayerClan _clanComponent = new PlayerClan(this);
	private final PlayerMount _mount = new PlayerMount(this);
	private final PlayerDuel _duel = new PlayerDuel(this);
	private final PlayerRequest _requestComponent = new PlayerRequest(this);
	private final PlayerCharge _charge = new PlayerCharge(this);
	private final PlayerOffline _offline = new PlayerOffline(this);
	private final PlayerSellBuff _sellBuff = new PlayerSellBuff(this);
	private final PlayerHero _hero = new PlayerHero(this);
	private final PlayerPcCafe _pcCafe = new PlayerPcCafe(this);
	private final PlayerHwid _hwidComponent = new PlayerHwid(this);
	private final PlayerBoat _boat = new PlayerBoat(this);
	private final PlayerPvP _pvp = new PlayerPvP(this);
	private final PlayerSession _session = new PlayerSession(this);
	private final PlayerDeath _death = new PlayerDeath(this);
	private final PlayerPenalty _penalty = new PlayerPenalty(this);
	private final PlayerSoulShot _soulShot = new PlayerSoulShot(this);
	private final PlayerCursedWeapon _cursedWeapon = new PlayerCursedWeapon(this);
	private final PlayerProtect _protect = new PlayerProtect(this);
	private final PlayerOperate _operate = new PlayerOperate(this);
	private final PlayerSocial _social = new PlayerSocial(this);
	private final PlayerBypass _bypass = new PlayerBypass(this);
	private final PlayerUiPrefs _uiPrefs = new PlayerUiPrefs(this);
	private final PlayerTeam _teamState = new PlayerTeam(this);

	public PlayerUiPrefs getUiPrefs()
	{
		return _uiPrefs;
	}

	// === Delegated state components (att-ver-3.0 decomposition) ===
	private final PlayerProfile _profile = new PlayerProfile(this);
	private final PlayerCombat _combat = new PlayerCombat(this);
	private final PlayerInventoryAccess _inventoryAccess = new PlayerInventoryAccess(this);
	private final PlayerSkillManager _skillManager = new PlayerSkillManager(this);
	private final PlayerAppearanceBroadcast _appearanceBroadcast = new PlayerAppearanceBroadcast(this);
	private final PlayerMountAndSummon _mountAndSummon = new PlayerMountAndSummon(this);
	private final PlayerDeathLifecycle _deathLifecycle = new PlayerDeathLifecycle(this);
	private final PlayerSocialManager _socialManager = new PlayerSocialManager(this);
	private final PlayerSessionPersistence _sessionPersistence = new PlayerSessionPersistence(this);

	// operate type moved to PlayerOperate
	
	
	
	
	private Folk _currentFolk;
	
	private final PlayerMemo _memos = new PlayerMemo(getObjectId());
	
	private final FishingStance _fishingStance = new FishingStance(this);
	private final ShortcutList _shortcutList = new ShortcutList(this);
	private final MacroList _macroList = new MacroList(this);
	private final HennaList _hennaList = new HennaList(this);
	private final RadarList _radarList = new RadarList(this);
	private final CubicList _cubicList = new CubicList(this);
	private final QuestList _questList = new QuestList(this);

	private boolean _wantsPeace;
	
	
	
	private AccessLevel _accessLevel;
	
	
	
	
	/** Task que reaplica invisibilidade após revelação temporária (evita travamento ao atacar mob em modo invisível). */
	
	
	
	private ItemInstance _activeEnchantItem;
	
	protected boolean _inventoryDisable;
	
	
	
	
	
	
	private Forum _forumMemo;

	// Skill storage lives in {@link PlayerSkillManager}. Do not reintroduce a parallel map here:
	// Player.getSkills() must read from the manager so addSkill/removeSkill and the level-up
	// auto-learn path share the same source of truth. (regression introduced in 8876e293.)
	private static final int FALLING_VALIDATION_DELAY = 5000;
	private volatile long _fallingTimestamp;

	private final CachedData _cachedData = new CachedData(getObjectId());
	private final CachedDataValueInt _setNameColor = _cachedData.newInt("nameColor");
	private final CachedDataValueInt _setTitleColor = _cachedData.newInt("titleColor");
	private final CachedDataValueBoolean _stopExp = _cachedData.newBoolean("stopexp", ConfigProject.PROP_STOP_EXP);
	private final CachedDataValueBoolean _tradeRefusal = _cachedData.newBoolean("traderefusal", ConfigProject.PROP_TRADE_REFUSAL);
	private final CachedDataValueBoolean _autoLoot = _cachedData.newBoolean("autoloot", ConfigProject.PROP_AUTO_LOOT);
	
	
	private final CachedDataValueBoolean _buffProtected = _cachedData.newBoolean("buffProtected", ConfigProject.PROP_BUFF_PROTECTED);
	
	private final CachedDataValueObject<Locale> _locale = _cachedData.newObject("locale", ConfigLanguage.DEFAULT_LOCALE.toLanguageTag(), new Converter<>()
	{
		@Override
		public Locale fromString(String value)
		{
			return Locale.forLanguageTag(value);
		}
		
		@Override
		public String toString(Locale locale)
		{
			return locale.toString().replace("_", "-");
		}
	});
	
	private final CachedDataValueInt _acpCp = _cachedData.newInt("acpCp", 95);
	private final CachedDataValueInt _acpHp = _cachedData.newInt("acpHp", 95);
	private final CachedDataValueInt _acpMp = _cachedData.newInt("acpMp", 75);
	
	
	
	public String _originalTitle;

	private final MissionList _missionList = new MissionList(this);
	
	/**
	 * Constructor of Player (use Creature constructor).
	 * <ul>
	 * <li>Call the Creature constructor to create an empty _skills slot and copy basic Calculator set to this Player</li>
	 * <li>Set the name of the Player</li>
	 * </ul>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method SET the level of the Player to 1</B></FONT>
	 * @param objectId Identifier of the object to initialized
	 * @param template The L2PcTemplate to apply to the Player
	 * @param accountName The name of the account including this Player
	 * @param app The PcAppearance of the Player
	 */
	public Player(int objectId, PlayerTemplate template, String accountName, Appearance app)
	{
		super(objectId, template);

		// Break circular delegation: give _inventoryAccess direct ref to _inventory
		_inventoryAccess.setDirectInventory(_inventory);

		getStatus().initializeValues();

		_accountName = accountName;
		_appearance = app;

		_ai = new PlayerAI(this);

		getInventory().restore();
		getWarehouse();
		getFreight();
	}
	
	/**
	 * Create a new Player and add it in the characters table of the database.
	 * <ul>
	 * <li>Create a new Player with an account name</li>
	 * <li>Set the name, the Hair Style, the Hair Color and the Face type of the Player</li>
	 * <li>Add the player in the characters table of the database</li>
	 * </ul>
	 * @param objectId Identifier of the object to initialized
	 * @param template The L2PcTemplate to apply to the Player
	 * @param accountName The name of the Player
	 * @param name The name of the Player
	 * @param hairStyle The hair style Identifier of the Player
	 * @param hairColor The hair color Identifier of the Player
	 * @param face The face type Identifier of the Player
	 * @param sex The sex type Identifier of the Player
	 * @return The Player added to the database or null
	 */
	public static Player create(int objectId, PlayerTemplate template, String accountName, String name, byte hairStyle, byte hairColor, byte face, Sex sex)
	{
		final Appearance app = new Appearance(face, hairColor, hairStyle, sex);
		final Player player = new Player(objectId, template, accountName, app);
		
		player.setName(name);
		
		player.setAccessLevel(ConfigPlayers.DEFAULT_ACCESS_LEVEL);
		
		PlayerInfoTable.getInstance().addPlayer(objectId, accountName, name, player.getAccessLevel().getLevel());
		
		player.setBaseClass(player.getClassId());
		
		if (!PlayerPersistence.insertCharacter(player, accountName))
			return null;
		
		return player;
	}
	
	@Override
	public void addFuncsToNewCharacter()
	{
		super.addFuncsToNewCharacter();
		
		addStatFunc(FuncMaxCpMul.getInstance());
		addStatFunc(FuncRegenCpMul.getInstance());
		
		addStatFunc(FuncHenna.getSTR());
		addStatFunc(FuncHenna.getCON());
		addStatFunc(FuncHenna.getDEX());
		addStatFunc(FuncHenna.getINT());
		addStatFunc(FuncHenna.getMEN());
		addStatFunc(FuncHenna.getWIT());
	}
	
	@Override
	public PlayerStatus getStatus()
	{
		return (PlayerStatus) _status;
	}
	
	@Override
	public void setStatus()
	{
		_status = new PlayerStatus(this);
	}
	
	public final Appearance getAppearance()
	{
		return _appearance;
	}

	/**
	 * Revela temporariamente um GM invisível (ex.: ao atacar um NPC), evitando que a IA entre em loop
	 * e trave o servidor. Após o tempo indicado, a invisibilidade é reaplicada.
	 * @param seconds Tempo em segundos até voltar a ficar invisível (ex.: 10).
	 */
	public void setTemporarilyVisible(int seconds)
	{
		_appearanceBroadcast.setTemporarilyVisible(seconds);
	}

	/**
	 * @return the {@link PlayerTemplate} linked to this {@link Player} base class.
	 */
	public final PlayerTemplate getBaseTemplate()
	{
		return PlayerData.getInstance().getTemplate(_baseClass);
	}
	
	@Override
	public final PlayerTemplate getTemplate()
	{
		return (PlayerTemplate) super.getTemplate();
	}
	
	@Override
	public void setWalkOrRun(boolean value)
	{
		super.setWalkOrRun(value);
		
		broadcastUserInfo();
	}
	
	public void setTemplate(ClassId newclass)
	{
		super.setTemplate(PlayerData.getInstance().getTemplate(newclass));
	}
	
	@Override
	public boolean denyAiAction()
	{
		return super.denyAiAction() || isInStoreMode() || getOperateType() == OperateType.OBSERVE;
	}
	
	/**
	 * Return the AI of the Player (create it if necessary).
	 */
	@Override
	public PlayerAI getAI()
	{
		return (PlayerAI) _ai;
	}
	
	@Override
	public void setAI()
	{
		_ai = new PlayerAI(this);
	}
	
	@Override
	public PlayerMove getMove()
	{
		return (PlayerMove) _move;
	}
	
	@Override
	public void setMove()
	{
		_move = new PlayerMove(this);
	}
	
	@Override
	public PlayerAttack getAttack()
	{
		return (PlayerAttack) _attack;
	}
	
	@Override
	public void setAttack()
	{
		_attack = new PlayerAttack(this);
	}
	
	@Override
	public PlayerCast getCast()
	{
		return (PlayerCast) _cast;
	}
	
	@Override
	public void setCast()
	{
		_cast = new PlayerCast(this);
	}
	
	@Override
	public boolean canBeHealed()
	{
		return super.canBeHealed() && !isCursedWeaponEquipped();
	}
	
	/**
	 * A newbie is a {@link Player} between level 6 and 25 which didn't yet acquired first occupation change.<br>
	 * <br>
	 * <b>Since IL, Newbie statut isn't anymore the first character of an account reaching that state, but any.</b>
	 * @param checkLowLevel : If true, check also low level requirement.
	 * @return True if this {@link Player} can be considered a Newbie.
	 */
	public boolean isNewbie(boolean checkLowLevel)
	{
		return (checkLowLevel) ? (getClassId().getLevel() <= 1 && getStatus().getLevel() >= 6 && getStatus().getLevel() <= 25) : (getClassId().getLevel() <= 1 && getStatus().getLevel() <= 25);
	}
	
	public void setBaseClass(int baseClass)
	{
		_baseClass = baseClass;
	}
	
	public void setBaseClass(ClassId classId)
	{
		_baseClass = classId.ordinal();
	}
	
	/**
	 * @return True if the state of {@link OperateType} of this {@link Player} is different than NONE.
	 */
	@Override
	public boolean isOperating()
	{
		return _operate.isOperating();
	}
	
	public boolean isCrafting()
	{
		return _operate.isCrafting();
	}
	
	public void setCrafting(boolean state)
	{
		_operate.setCrafting(state);
	}
	
	public void setStanding(boolean value)
	{
		_isStanding = value;
	}
	
	/**
	 * @return The {@link PlayerMemo} of the current {@link Player}.
	 */
	public PlayerMemo getMemos()
	{
		return _memos;
	}
	
	/**
	 * @return The {@link ShortcutList} of the current {@link Player}.
	 */
	public ShortcutList getShortcutList()
	{
		return _shortcutList;
	}
	
	/**
	 * @return The {@link MacroList} of the current {@link Player}.
	 */
	public MacroList getMacroList()
	{
		return _macroList;
	}
	
	/**
	 * 0 = not involved, 1 = attacker, 2 = defender
	 * @return The siege state of the {@link Player}.
	 */
	public int getSiegeState()
	{
		return _pvp.getSiegeState();
	}
	
	/**
	 * Set the siege state of the {@link Player}.
	 * @param siegeState : The new value to set.
	 */
	public void setSiegeState(int siegeState)
	{
		_pvp.setSiegeState(siegeState);
	}
	
	@Override
	public byte getPvpFlag()
	{
		return _pvp.getPvpFlag();
	}
	
	/**
	 * Set the PvP flag of the {@link Player}.
	 * @param pvpFlag : 0 or 1.
	 */
	public void setPvpFlag(int pvpFlag)
	{
		_pvp.setPvpFlag(pvpFlag);
	}
	
	public void updatePvPFlag(int value)
	{
		_pvp.updatePvPFlag(value);
	}
	
	public int getRelation(Player target)
	{
		return _combat.getRelation(target);
	}
	
	@Override
	public void revalidateZone(boolean force)
	{
		super.revalidateZone(force);
		
		if (ConfigServer.ALLOW_WATER)
		{
			if (isInWater())
				WaterTaskManager.getInstance().add(this);
			else
				WaterTaskManager.getInstance().remove(this);
		}
		
		if (isInsideZone(ZoneId.SIEGE))
		{
			if (_lastCompassZone == ExSetCompassZoneCode.SIEGEWARZONE2)
				return;
			
			_lastCompassZone = ExSetCompassZoneCode.SIEGEWARZONE2;
			sendPacket(new ExSetCompassZoneCode(ExSetCompassZoneCode.SIEGEWARZONE2));
		}
		else if (isInsideZone(ZoneId.PVP))
		{
			if (_lastCompassZone == ExSetCompassZoneCode.PVPZONE)
				return;
			
			_lastCompassZone = ExSetCompassZoneCode.PVPZONE;
			sendPacket(new ExSetCompassZoneCode(ExSetCompassZoneCode.PVPZONE));
		}
		else if (isIn7sDungeon())
		{
			if (_lastCompassZone == ExSetCompassZoneCode.SEVENSIGNSZONE)
				return;
			
			_lastCompassZone = ExSetCompassZoneCode.SEVENSIGNSZONE;
			sendPacket(new ExSetCompassZoneCode(ExSetCompassZoneCode.SEVENSIGNSZONE));
		}
		else if (isInsideZone(ZoneId.PEACE))
		{
			if (_lastCompassZone == ExSetCompassZoneCode.PEACEZONE)
				return;
			
			_lastCompassZone = ExSetCompassZoneCode.PEACEZONE;
			sendPacket(new ExSetCompassZoneCode(ExSetCompassZoneCode.PEACEZONE));
		}
		else
		{
			if (_lastCompassZone == ExSetCompassZoneCode.GENERALZONE)
				return;
			
			if (_lastCompassZone == ExSetCompassZoneCode.SIEGEWARZONE2)
				updatePvPStatus();
			
			_lastCompassZone = ExSetCompassZoneCode.GENERALZONE;
			sendPacket(new ExSetCompassZoneCode(ExSetCompassZoneCode.GENERALZONE));
		}
	}
	
	/**
	 * @return the PK counter of the Player.
	 */
	public int getPkKills()
	{
		return _pvp.getPkKills();
	}
	
	/**
	 * Set the PK counter of the Player.
	 * @param pkKills A number.
	 */
	public void setPkKills(int pkKills)
	{
		_pvp.setPkKills(pkKills);
	}
	
	/**
	 * @return The _deleteTimer of the Player.
	 */
	public long getDeleteTimer()
	{
		return _session.getDeleteTimer();
	}
	
	/**
	 * Set the _deleteTimer of the Player.
	 * @param deleteTimer Time in ms.
	 */
	public void setDeleteTimer(long deleteTimer)
	{
		_session.setDeleteTimer(deleteTimer);
	}
	
	/**
	 * @return The current weight of the Player.
	 */
	public int getCurrentWeight()
	{
		return _inventoryAccess.getCurrentWeight();
	}
	
	/**
	 * @return The number of recommendation obtained by the Player.
	 */
	public int getRecomHave()
	{
		return _recom.getRecomHave();
	}
	
	/**
	 * Set the number of recommendation obtained by the Player (Max : 255).
	 * @param value Number of recommendation obtained.
	 */
	public void setRecomHave(int value)
	{
		_recom.setRecomHave(value);
	}
	
	/**
	 * Edit the number of recommendation obtained by the Player (Max : 255).
	 * @param value : The value to add or remove.
	 */
	public void editRecomHave(int value)
	{
		_recom.editRecomHave(value);
	}
	
	/**
	 * @return The number of recommendation that the Player can give.
	 */
	public int getRecomLeft()
	{
		return _recom.getRecomLeft();
	}
	
	/**
	 * Set the number of givable recommendation by the {@link Player} (Max : 9).
	 * @param value : The number of recommendations a player can give.
	 */
	public void setRecomLeft(int value)
	{
		_recom.setRecomLeft(value);
	}
	
	/**
	 * Decrement the number of recommendation that the Player can give.
	 */
	protected void decRecomLeft()
	{
		_recom.decRecomLeft();
	}
	
	public List<Integer> getRecomChars()
	{
		return _recom.getRecomChars();
	}
	
	public void giveRecom(Player target)
	{
		_recom.giveRecom(target);
	}
	
	public boolean canRecom(Player target)
	{
		return _recom.canRecom(target);
	}
	
	/**
	 * @return the exp of this {@link Player} before a death.
	 */
	public long getExpBeforeDeath()
	{
		return _expBeforeDeath;
	}
	
	/**
	 * Set the exp of this {@link Player} before a death.
	 * @param expBeforeDeath : The value to set.
	 */
	public void setExpBeforeDeath(long expBeforeDeath)
	{
		_expBeforeDeath = expBeforeDeath;
	}
	
	@Override
	public int getKarma()
	{
		return _pvp.getKarma();
	}
	
	/**
	 * Set the Karma of this {@link Player}.
	 * @param karma : The value to set.
	 */
	public void setKarma(int karma)
	{
		_pvp.setKarma(karma);
	}
	
	@Override
	public int getWeightLimit()
	{
		return _inventoryAccess.getWeightLimit();
	}

	public int getArmorGradePenalty()
	{
		return _penalty.getArmorGradePenalty();
	}

	public boolean getWeaponGradePenalty()
	{
		return _penalty.getWeaponGradePenalty();
	}

	public WeightPenalty getWeightPenalty()
	{
		return _penalty.getWeightPenalty();
	}

	/**
	 * Update the overloaded status of this {@link Player}.
	 * Delegates to {@link PlayerPenalty}; PlayerInventoryAccess.refreshWeightPenalty()
	 * routes through this method to break circular delegation.
	 */
	public void refreshWeightPenalty()
	{
		_penalty.refreshWeightPenalty();
	}

	/**
	 * Refresh expertise level ; weapon got one rank, when armor got 4 ranks.
	 * Delegates to {@link PlayerPenalty}; PlayerInventoryAccess.refreshExpertisePenalty()
	 * routes through this method to break circular delegation.
	 */
	public void refreshExpertisePenalty()
	{
		_penalty.refreshExpertisePenalty();
	}

	/**
	 * @return True if the Weight Penalty is 3 or more or if the inventory size is superior or equal to 80% - false otherwise.
	 */
	public boolean isOverweight()
	{
		return _penalty.isOverweight();
	}
	
	/**
	 * Equip or unequip the {@link ItemInstance} set as parameter.
	 * <UL>
	 * <LI>If the item is equipped, shots are applied if automation is on.</LI>
	 * <LI>If the item is unequipped, shots are discharged.</LI>
	 * </UL>
	 * @param item : The {@link ItemInstance} to equip/unequip.
	 * @param abortAttack : If true, the current attack will be aborted.
	 */
	public void useEquippableItem(ItemInstance item, boolean abortAttack)
	{
		_inventoryAccess.useEquippableItem(item, abortAttack);
	}
	
	private boolean _isEquipBatching = false;
	private boolean _needSkillList = false;
	private boolean _needEtcStatusUpdate = false;

	public boolean isEquipBatching()
	{
		return _isEquipBatching;
	}

	public void setEquipBatching(boolean isEquipBatching)
	{
		_isEquipBatching = isEquipBatching;
	}

	public boolean isNeedSkillList()
	{
		return _needSkillList;
	}

	public void setNeedSkillList(boolean needSkillList)
	{
		_needSkillList = needSkillList;
	}

	public boolean isNeedEtcStatusUpdate()
	{
		return _needEtcStatusUpdate;
	}

	public void setNeedEtcStatusUpdate(boolean needEtcStatusUpdate)
	{
		_needEtcStatusUpdate = needEtcStatusUpdate;
	}
	
	/**
	 * @return The total PvP kills amount of this {@link Player} (number of killed players during PvP).
	 */
	public int getPvpKills()
	{
		return _pvp.getPvpKills();
	}
	
	/**
	 * Set the total PvP kills amount of this {@link Player} (number of killed players during PvP).
	 * @param pvpKills : The value to set.
	 */
	public void setPvpKills(int pvpKills)
	{
		_pvp.setPvpKills(pvpKills);
	}
	
	/**
	 * @return The {@link ClassId} of this {@link Player}.
	 */
	public ClassId getClassId()
	{
		return getTemplate().getClassId();
	}
	
	/**
	 * Set the {@link ClassId} of this {@link Player}.
	 * @param id : The id of the {@link ClassId} to set.
	 */
	public void setClassId(int id)
	{
		if (!_subClass.tryLock())
			return;
		
		try
		{
			if (getLvlJoinedAcademy() != 0 && getClan() != null && ClassId.VALUES[id].getLevel() == 2)
			{
				int points;
				if (getLvlJoinedAcademy() <= 16)
					points = 650;
				else if (getLvlJoinedAcademy() >= 39)
					points = 190;
				else
					points = (41 - getLvlJoinedAcademy()) * 20 + 150;
				
				getClan().addReputationScore(points);
				
				getClan().broadcastToMembers(SystemMessage.getSystemMessage(SystemMessageId.CLAN_ACADEMY_MEMBER_S1_HAS_SUCCESSFULLY_COMPLETED_THE_2ND_CLASS_TRANSFER_AND_OBTAINED_S2_CLAN_REPUTATION_POINTS).addString(getName()).addNumber(points));
				
				sendPacket(SystemMessageId.ACADEMY_MEMBERSHIP_TERMINATED);
				sendPacket(SystemMessageId.YOU_HAVE_WITHDRAWN_FROM_CLAN);
				
				setLvlJoinedAcademy(0);
				
				getClan().broadcastToMembersExcept(this, new PledgeShowMemberListDelete(getName()), SystemMessage.getSystemMessage(SystemMessageId.S1_HAS_WITHDRAWN_FROM_THE_CLAN).addString(getName()));
				
				getClan().addGraduate(getObjectId());
				
				getClan().removeClanMember(getObjectId(), 0);
				
				addItem(8181, 1, true);
				_missionList.update(MissionType.ACADEMY);
			}
			
			if (isSubClassActive())
			{
				final SubClass activeSub = _subClass.get(_subClass.getClassIndex());
				if (activeSub != null)
					activeSub.setClassId(id);
			}
			
			broadcastPacket(new MagicSkillUse(this, this, 5103, 1, 1000, 0));
			setClassTemplate(id);
			
			if (getClassId().getLevel() == 3)
				sendPacket(SystemMessageId.THIRD_CLASS_TRANSFER);
			else
				sendPacket(SystemMessageId.CLASS_TRANSFER);
			
			if (getParty() != null)
				getParty().broadcastPacket(new PartySmallWindowUpdate(this));
			
			if (getClan() != null)
				getClan().broadcastToMembers(new PledgeShowMemberListUpdate(this));
			
			if (ConfigPlayers.AUTO_LEARN_SKILLS && getStatus().getLevel() <= ConfigPlayers.LVL_AUTO_LEARN_SKILLS)
				rewardSkills();
		}
		finally
		{
			_subClass.unlock();
		}
	}
	
	/**
	 * @return The {@link ItemInstance} used as active enchant item. Mostly used to see if the enchant window is opened.
	 */
	public ItemInstance getActiveEnchantItem()
	{
		return _activeEnchantItem;
	}

	/**
	 * Set the active enchant item.
	 * @param scroll : The {@link ItemInstance} to set.
	 */
	public void setActiveEnchantItem(ItemInstance scroll)
	{
		_activeEnchantItem = scroll;
	}
	
	/**
	 * @return The {@link ClassRace} of this {@link Player}. If subclass is active, we use the base template one.
	 */
	public ClassRace getRace()
	{
		return (isSubClassActive()) ? getBaseTemplate().getRace() : getTemplate().getRace();
	}
	
	public RadarList getRadarList()
	{
		return _radarList;
	}
	
	public CubicList getCubicList()
	{
		return _cubicList;
	}
	
	@Override
	public int getClanId()
	{
		return _clanComponent.getClanId();
	}
	
	@Override
	public Clan getClan()
	{
		return _clanComponent.getClan();
	}
	
	/**
	 * Set the {@link Clan} informations of this {@link Player}.
	 * @param clan : The {@link Clan} object used to feed {@link Player} values.
	 */
	public void setClan(Clan clan)
	{
		_clanComponent.setClan(clan);
	}
	
	/**
	 * @param castleId : The {@link Castle} to check.
	 * @return True if this {@link Player} is a {@link Clan} leader in ownership of the passed {@link Castle}, or false otherwise.
	 */
	public boolean isCastleLord(int castleId)
	{
		return _clanComponent.isCastleLord(castleId);
	}
	
	/**
	 * @return True if the {@link Player} is the leader of its {@link Clan}, or false otherwise.
	 */
	public boolean isClanLeader()
	{
		return _clanComponent.isClanLeader();
	}
	
	/**
	 * @return The {@link Clan} crest id of the {@link Player}, or 0 if not set.
	 */
	public int getClanCrestId()
	{
		return _clanComponent.getClanCrestId();
	}
	
	/**
	 * @return The large {@link Clan} crest id of the {@link Player}, or 0 if not set.
	 */
	public int getClanCrestLargeId()
	{
		return _clanComponent.getClanCrestLargeId();
	}
	
	/**
	 * @return The expiration timer to join a {@link Clan}, used by defective {@link Clan} member leaving their {@link Clan}.
	 */
	public long getClanJoinExpiryTime()
	{
		return _clanComponent.getClanJoinExpiryTime();
	}
	
	/**
	 * Set the expiration timer to join a {@link Clan}.
	 * @param time : The value to set.
	 */
	public void setClanJoinExpiryTime(long time)
	{
		_clanComponent.setClanJoinExpiryTime(time);
	}
	
	/**
	 * @return The expiration timer to create a {@link Clan}, used by defective {@link Clan} leader leaving their {@link Clan}.
	 */
	public long getClanCreateExpiryTime()
	{
		return _clanComponent.getClanCreateExpiryTime();
	}
	
	/**
	 * Set the expiration timer to create a {@link Clan}.
	 * @param time : The value to set.
	 */
	public void setClanCreateExpiryTime(long time)
	{
		_clanComponent.setClanCreateExpiryTime(time);
	}
	
	public void setOnlineTime(long time)
	{
		_session.setOnlineTime(time);
	}
	
	@Override
	public PcInventory getInventory()
	{
		return _inventory;
	}

	@Override
	public boolean isSitting()
	{
		return _isSitting;
	}
	
	@Override
	public boolean isStanding()
	{
		return _isStanding;
	}
	
	@Override
	public boolean isSittingNow()
	{
		return _isSittingNow;
	}
	
	@Override
	public boolean isStandingNow()
	{
		return _isStandingNow;
	}
	
	/**
	 * Sit down this {@link Player}. The {@link Player} retrieves control after a 2.5s delay.
	 * <ul>
	 * <li>Set the AI Intention to IDLE.</li>
	 * <li>Broadcast {@link ChangeWaitType} packet.</li>
	 * </ul>
	 * @return True if this {@link Player} could successfully sit down, false otherwise.
	 */
	public boolean sitDown()
	{
		_isSittingNow = true;
		_isStanding = false;
		
		ThreadPool.schedule(() ->
		{
			_isSittingNow = false;
			_isSitting = true;
			
			getAI().notifyEvent(AiEventType.SAT_DOWN, null, null);
			
		}, 2500);
		
		broadcastPacket(new ChangeWaitType(this, ChangeWaitType.WT_SITTING));
		
		final QuestState qs = _questList.getQuestState("Tutorial");
		if (qs != null)
			qs.getQuest().notifyEvent("CE8388608", null, this);
		
		return true;
	}
	
	/**
	 * Stand this {@link Player} up. The {@link Player} retrieves control after a 2.5s delay.
	 * <ul>
	 * <li>Schedule the STOOD_UP event.</li>
	 * <li>Broadcast {@link ChangeWaitType} packet.</li>
	 * </ul>
	 */
	public void standUp()
	{
		_isStandingNow = true;
		_isSitting = false;
		
		ThreadPool.schedule(() ->
		{
			_isStandingNow = false;
			_isStanding = true;
			
			getAI().notifyEvent(AiEventType.STOOD_UP, null, null);
			
		}, 2500);
		
		broadcastPacket(new ChangeWaitType(this, ChangeWaitType.WT_STANDING));
	}
	
	public PlayerStorage getStorage()
	{
		return _storage;
	}
	
	public PlayerTrade getTrade()
	{
		return _trade;
	}
	
	public PlayerPrivateStore getPrivateStore()
	{
		return _privateStore;
	}
	
	public PlayerPersistence getPersistence()
	{
		return _persistence;
	}
	
	/** Package component accessor used by persistence helpers. */
	public PlayerSubClass getSubClassComponent()
	{
		return _subClass;
	}
	
	public PlayerSkillsDb getSkillsDb()
	{
		return _skillsDb;
	}
	
	public PlayerRecom getRecom()
	{
		return _recom;
	}
	
	public PlayerPremium getPremium()
	{
		return _premium;
	}
	
	public PlayerOlympiad getOlympiad()
	{
		return _olympiad;
	}
	
	public PlayerParty getPartyComponent()
	{
		return _partyComponent;
	}
	
	public PlayerClan getClanComponent()
	{
		return _clanComponent;
	}
	
	public PlayerMount getMountComponent()
	{
		return _mount;
	}
	
	public PlayerDuel getDuelComponent()
	{
		return _duel;
	}
	
	public PlayerRequest getRequestComponent()
	{
		return _requestComponent;
	}
	
	public PlayerCharge getChargeComponent()
	{
		return _charge;
	}
	
	public PlayerOffline getOfflineComponent()
	{
		return _offline;
	}
	
	public PlayerSellBuff getSellBuffComponent()
	{
		return _sellBuff;
	}
	
	public PlayerHero getHeroComponent()
	{
		return _hero;
	}
	
	public PlayerPcCafe getPcCafeComponent()
	{
		return _pcCafe;
	}
	
	public PlayerHwid getHwidComponent()
	{
		return _hwidComponent;
	}
	
	public PlayerBoat getBoatComponent()
	{
		return _boat;
	}

	public PlayerProfile getProfile()
	{
		return _profile;
	}

	public PlayerCombat getCombat()
	{
		return _combat;
	}

	public PlayerInventoryAccess getInventoryAccess()
	{
		return _inventoryAccess;
	}

	public PlayerSkillManager getSkillManager()
	{
		return _skillManager;
	}

	public PlayerAppearanceBroadcast getAppearanceBroadcast()
	{
		return _appearanceBroadcast;
	}

	public PlayerProtect getProtect()
	{
		return _protect;
	}

	public PlayerPvP getPvP()
	{
		return _pvp;
	}

	public PlayerMountAndSummon getMountAndSummon()
	{
		return _mountAndSummon;
	}

	public PlayerDeathLifecycle getDeathLifecycle()
	{
		return _deathLifecycle;
	}

	public PlayerSocialManager getSocialManager()
	{
		return _socialManager;
	}

	public PlayerSessionPersistence getSessionPersistence()
	{
		return _sessionPersistence;
	}

	public long getOnlineTime()
	{
		return _session.getOnlineTime();
	}
	
	public void setLastAccess(long lastAccess)
	{
		_session.setLastAccess(lastAccess);
	}
	
	public long getOnlineBeginTime()
	{
		return _session.getOnlineBeginTime();
	}
	
	/** Clear charges when switching subclass (used by {@link PlayerSubClass#setActiveClass}). */
	public void clearChargesForClassChange()
	{
		_charge.clearForClassChange();
	}
	
	/** @return The {@link PcWarehouse} of this {@link Player}. */
	public PcWarehouse getWarehouse()
	{
		return _storage.getWarehouse();
	}

	/** Free memory used by this {@link Player}'s {@link PcWarehouse}. */
	public void clearWarehouse()
	{
		_storage.clearWarehouse();
	}

	/** @return The {@link PcFreight} of this {@link Player}. */
	public PcFreight getFreight()
	{
		return _storage.getFreight();
	}

	/** Free memory used by this {@link Player}'s {@link PcFreight}. */
	public void clearFreight()
	{
		_storage.clearFreight();
	}

	/**
	 * @param objectId : The id of the owner.
	 * @return The deposited {@link PcFreight} for the objectId set as parameter, or create a new one if not existing.
	 */
	public PcFreight getDepositedFreight(int objectId)
	{
		return _storage.getDepositedFreight(objectId);
	}

	/** Free memory used by this {@link Player}'s {@link PcFreight}s used as deposit. */
	public void clearDepositedFreight()
	{
		_storage.clearDepositedFreight();
	}
	
	/**
	 * @return The Adena amount of this {@link Player}.
	 */
	public int getAdena()
	{
		return _inventoryAccess.getAdena();
	}

	/**
	 * @return The Ancient Adena amount of this {@link Player}.
	 */
	public int getAncientAdena()
	{
		return _inventoryAccess.getAncientAdena();
	}

	/**
	 * Add Adena to this {@link Player}.
	 * @param count : The quantity of Adena to add.
	 * @param sendMessage : Send {@link SystemMessage} client notification if set to true.
	 */
	public void addAdena(int count, boolean sendMessage)
	{
		_inventoryAccess.addAdena(count, sendMessage);
	}

	/**
	 * Reduce Adena from this {@link Player}.
	 * @param count : The quantity of Adena to remove.
	 * @param sendMessage : Send {@link SystemMessage} client notification if set to true.
	 * @return True if the action was successful, or false otherwise.
	 */
	public boolean reduceAdena(int count, boolean sendMessage)
	{
		return _inventoryAccess.reduceAdena(count, sendMessage);
	}

	/**
	 * Add Ancient Adena to this {@link Player}.
	 * @param count : The quantity of Ancient Adena to add.
	 * @param sendMessage : Send {@link SystemMessage} client notification if set to true.
	 */
	public void addAncientAdena(int count, boolean sendMessage)
	{
		_inventoryAccess.addAncientAdena(count, sendMessage);
	}

	/**
	 * Reduce Ancient Adena from this {@link Player}.
	 * @param count : The quantity of Ancient Adena to remove.
	 * @param sendMessage : Send {@link SystemMessage} client notification if set to true.
	 * @return True if the action was successful, or false otherwise.
	 */
	public boolean reduceAncientAdena(int count, boolean sendMessage)
	{
		return _inventoryAccess.reduceAncientAdena(count, sendMessage);
	}
	
	@Override
	public void addItem(ItemInstance item, boolean sendMessage)
	{
		_inventoryAccess.addItem(item, sendMessage);
	}

	@Override
	public ItemInstance addItem(int itemId, int count, boolean sendMessage)
	{
		return _inventoryAccess.addItem(itemId, count, sendMessage);
	}

	/**
	 * Add an item to this {@link Player}. Harvest, Sweep and Quest behaviors use it. Only {@link SystemMessage} differ.
	 * @param itemId : The itemId of item to add.
	 * @param count : The quantity of items to add.
	 * @param sendMessage : Send {@link SystemMessage} client notification if set to true.
	 * @return an {@link ItemInstance} of a newly generated item for this {@link Player}, using itemId and count.
	 */
	public ItemInstance addEarnedItem(int itemId, int count, boolean sendMessage)
	{
		return _inventoryAccess.addEarnedItem(itemId, count, sendMessage);
	}
	
	/**
	 * Destroy entirely (whole amount) an {@link ItemInstance} from this {@link Player}.
	 * @param item : The {@link ItemInstance} to destroy.
	 * @param sendMessage : Send {@link SystemMessage} client notification if set to true.
	 * @return True if the action was successful, or false otherwise.
	 */
	public boolean destroyItem(ItemInstance item, boolean sendMessage)
	{
		return _inventoryAccess.destroyItem(item, sendMessage);
	}

	/**
	 * Destroy a part of an {@link ItemInstance} from this {@link Player}.
	 * @param item : The {@link ItemInstance} to destroy.
	 * @param count : The amount to remove. If amount is superior or equal to {@link ItemInstance} quantity, the item is entirely destroyed.
	 * @param sendMessage : Send {@link SystemMessage} client notification if set to true.
	 * @return True if the action was successful, or false otherwise.
	 */
	public boolean destroyItem(ItemInstance item, int count, boolean sendMessage)
	{
		return _inventoryAccess.destroyItem(item, count, sendMessage);
	}

	@Override
	public boolean destroyItem(int objectId, int count, boolean sendMessage)
	{
		return _inventoryAccess.destroyItem(objectId, count, sendMessage);
	}

	@Override
	public boolean destroyItemByItemId(int itemId, int count, boolean sendMessage)
	{
		return _inventoryAccess.destroyItemByItemId(itemId, count, sendMessage);
	}
	
	@Override
	public ItemInstance transferItem(int objectId, int amount, Playable target)
	{
		final ItemInstance oldItem = checkItemManipulation(objectId, amount);
		if (oldItem == null)
			return null;
		
		return super.transferItem(objectId, amount, target);
	}
	
	/**
	 * Drop an {@link ItemInstance} from this {@link Player}.
	 * @param item : The {@link ItemInstance} to drop.
	 * @param sendMessage : Send {@link SystemMessage} client notification if set to true.
	 * @return True if the action was successful, or false otherwise.
	 */
	public boolean dropItem(ItemInstance item, boolean sendMessage)
	{
		return _inventoryAccess.dropItem(item, sendMessage);
	}

	/**
	 * @param objectId : The objectId of the {@link ItemInstance} to drop.
	 * @param count : The amount to drop. If amount is superior or equal to {@link ItemInstance} quantity, the item is entirely removed.
	 * @param x : The X position.
	 * @param y : The Y position.
	 * @param z : The Z position.
	 * @param sendMessage : Send {@link SystemMessage} client notification if set to true.
	 * @return The {@link ItemInstance} from this {@link Player}.
	 */
	public ItemInstance dropItem(int objectId, int count, int x, int y, int z, boolean sendMessage)
	{
		return _inventoryAccess.dropItem(objectId, count, x, y, z, sendMessage);
	}
	
	@Override
	public ItemInstance checkItemManipulation(int objectId, int count)
	{
		return _inventoryAccess.checkItemManipulation(objectId, count);
	}
	
	/**
	 * @return True if this {@link Player} is under a spawn protection task, or false otherwise.
	 */
	public boolean isSpawnProtected()
	{
		return _protect.isSpawnProtected();
	}
	
	/**
	 * Launch a task corresponding to {@link Config#PLAYER_SPAWN_PROTECTION} setting.
	 * @param isActive : If true, we try to launch the task. If false, we cancel it.
	 */
	public void setSpawnProtection(boolean isActive)
	{
		_protect.setSpawnProtection(isActive);
	}
	
	/**
	 * Set protection from agro mobs when getting up from fake death, according settings.
	 */
	public void setRecentFakeDeath()
	{
		_deathLifecycle.setRecentFakeDeath();
	}

	public void clearRecentFakeDeath()
	{
		_death.setRecentFakeDeathEndTime(0);
	}

	public boolean isRecentFakeDeath()
	{
		return _deathLifecycle.isRecentFakeDeath();
	}
	
	@Override
	public final boolean isFakeDeath()
	{
		return _death.isFakeDeath();
	}
	
	@Override
	public final boolean isAlikeDead()
	{
		if (super.isAlikeDead())
			return true;
		
		return isFakeDeath();
	}
	
	/**
	 * @return The client owner of this char.
	 */
	public GameClient getClient()
	{
		return _session.getClient();
	}
	
	public void setClient(GameClient client)
	{
		_session.setClient(client);
	}
	
	public String getAccountName()
	{
		if (getClient() == null)
			return getAccountNamePlayer();

		return _accountName;
	}

	public String getAccountNamePlayer()
	{
		return _accountName;
	}

	public Map<Integer, String> getAccountChars()
	{
		return _chars;
	}
	
	/**
	 * Close the active connection with the {@link GameClient} linked to this {@link Player}.
	 * @param closeClient : If true, the client is entirely closed. Otherwise, the client is sent back to login.
	 */
	public void logout(boolean closeClient)
	{
		SafeDisconnectHooks.get().markExpectedLogout(this);
		final GameClient client = getClient();
		if (client == null)
			return;

		if (client.isDetached())
			client.cleanMe(true);
		else if (!client.getConnection().isClosed())
			client.close((closeClient) ? LeaveWorld.STATIC_PACKET : ServerClose.STATIC_PACKET);
	}
	
	@Override
	public void enableSkill(L2Skill skill)
	{
		super.enableSkill(skill);
		removeTimeStamp(skill);
	}
	
	@Override
	public void onAction(Player player, boolean isCtrlPressed, boolean isShiftPressed)
	{
		if (this == player && player.getTarget() == this)
			return;
		
		if (!CTFEvent.getInstance().onAction(player, getObjectId()) || !DMEvent.getInstance().onAction(player, getObjectId()) || !LMEvent.getInstance().onAction(player, getObjectId()) || !TvTEvent.getInstance().onAction(player, getObjectId()))
			return;
		
		if (isShiftPressed && player.isGM())
		{
			var html = new NpcHtmlMessage(0);
			AdminEditChar.gatherPlayerInfo(player, this, html);
			player.sendPacket(html);
			return;
		}
		
		if (player.getTarget() != this)
			player.setTarget(this);
		else
		{
			if (player.isInBoat() && !isInBoat() || !player.isInBoat() && isInBoat())
				player.sendPacket(ActionFailed.STATIC_PACKET);
			else if (isAttackableWithoutForceBy(player) || (isCtrlPressed && isAttackableBy(player)))
				player.getAI().tryToAttack(this, isCtrlPressed, isShiftPressed);
			else if (isOperating())
				player.getAI().tryToInteract(this, isCtrlPressed, isShiftPressed);
			else
				player.getAI().tryToFollow(this, isShiftPressed);
		}
		
		if (isShiftPressed && !player.isGM())
		{
			PlayerInfo voiced = new PlayerInfo();
			voiced.useVoicedCommand("info", player);
		}
	}
	
	@Override
	public boolean isVisibleTo(WorldObject wo)
	{
		return _appearanceBroadcast.isVisibleTo(wo);
	}
	
	@Override
	public void broadcastPacket(L2GameServerPacket packet, boolean selfToo)
	{
		if (selfToo)
			sendPacket(packet);
		
		super.broadcastPacket(packet, selfToo);
	}
	
	@Override
	public void broadcastPacketInRadius(L2GameServerPacket packet, int radius)
	{
		sendPacket(packet);
		
		super.broadcastPacketInRadius(packet, radius);
	}
	
	/**
	 * Broadcast informations from a user to himself and his knownlist.<BR>
	 * If player is morphed, it sends informations from the template the player is using.
	 * <ul>
	 * <li>Send a UserInfo packet (public and private data) to this Player.</li>
	 * <li>Send a CharInfo packet (public data only) to Player's knownlist.</li>
	 * </ul>
	 */
	public final void broadcastUserInfo()
	{
		_appearanceBroadcast.broadcastUserInfo();
	}

	public final void broadcastCharInfo()
	{
		_appearanceBroadcast.broadcastCharInfo();
	}

	/**
	 * Broadcast player title information.
	 */
	public final void broadcastTitleInfo()
	{
		_appearanceBroadcast.broadcastTitleInfo();
	}
	
	/**
	 * @return the Alliance Identifier of the Player.
	 */
	public int getAllyId()
	{
		return _clanComponent.getAllyId();
	}
	
	public int getAllyCrestId()
	{
		return _clanComponent.getAllyCrestId();
	}
	
	@Override
	public void sendPacket(L2GameServerPacket packet)
	{
		if (getClient() == null)
			return;
		
		if (packet instanceof NpcHtmlMessage html) {
			
			if (html.isFromTranslator()) {
				getClient().sendPacket(packet);
				return;
			}
			
			if (TranslatorState.isHtmlTranslationEnabled(this) && TranslatorHooks.get().isTranslatorAvailable()) {
				if (html.isValidForTranslation()) {
					try {
						if (!TranslatorHooks.get().handleHtmlPacket(this, html)) {
							return;
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		
		getClient().sendPacket(packet);
	}
	
	public void sendPackets(L2GameServerPacket... packets)
	{
		if (getClient() != null && packets != null && packets.length > 0)
			getClient().sendPackets(packets);
	}
	
	public void sendPackets(java.util.List<L2GameServerPacket> packets)
	{
		if (getClient() != null && packets != null && !packets.isEmpty())
			getClient().sendPackets(packets);
	}
	
	private static final ext.mods.gameserver.network.GameClient.PacketBatchScope NOOP_BATCH_SCOPE = () -> {};
	
	public void startPacketBatch()
	{
		final GameClient client = getClient();
		if (client != null)
			client.startPacketBatch();
	}
	
	public void endPacketBatch()
	{
		final GameClient client = getClient();
		if (client != null)
			client.endPacketBatch();
	}
	
	public ext.mods.gameserver.network.GameClient.PacketBatchScope openPacketBatch()
	{
		final GameClient client = getClient();
		return client != null ? client.openPacketBatch() : NOOP_BATCH_SCOPE;
	}
	
	@Override
	public void sendPacket(SystemMessageId id)
	{
		sendPacket(SystemMessage.getSystemMessage(id));
	}
	
	/** Check if this {@link Player} can open any type of private shop (buy, sell, manufacture). */
	public boolean canOpenPrivateStore(boolean cancelActiveTrade)
	{
		return _privateStore.canOpenPrivateStore(cancelActiveTrade);
	}
	
	public void tryOpenPrivateBuyStore()
	{
		_privateStore.tryOpenPrivateBuyStore();
	}
	
	public void tryOpenPrivateSellStore(boolean isPackageSale)
	{
		_privateStore.tryOpenPrivateSellStore(isPackageSale);
	}
	
	public void tryOpenWorkshop(boolean isDwarven)
	{
		_privateStore.tryOpenWorkshop(isDwarven);
	}
	
	public final PreparedListContainer getMultiSell()
	{
		return _operate.getCurrentMultiSell();
	}
	
	public final void setMultiSell(PreparedListContainer list)
	{
		_operate.setCurrentMultiSell(list);
	}
	
	@Override
	public void setTarget(WorldObject newTarget)
	{
		if (newTarget != null)
		{
			if (!newTarget.isVisible() && !(newTarget instanceof Player && isInParty() && getParty().containsPlayer(newTarget)))
				newTarget = null;
			
			if (newTarget instanceof FestivalMonster && !isFestivalParticipant())
				newTarget = null;
		}
		
		final WorldObject oldTarget = getTarget();
		
		if (oldTarget != null)
		{
			if (oldTarget == newTarget)
				return;
			
			if (oldTarget instanceof Creature oldTargetCreature)
				oldTargetCreature.getStatus().removeStatusListener(this);
		}
		
		if (newTarget instanceof StaticObject newTargetStaticObject)
		{
			sendPacket(new MyTargetSelected(newTarget.getObjectId(), 0));
			sendPacket(new StaticObjectInfo(newTargetStaticObject));
		}
		else if (newTarget instanceof Creature newTargetCreature)
		{
			sendPacket(new MyTargetSelected(newTargetCreature.getObjectId(), (newTargetCreature.isAttackableBy(this) || newTargetCreature instanceof Summon) ? getStatus().getLevel() - newTargetCreature.getStatus().getLevel() : 0));
			
			newTargetCreature.getStatus().addStatusListener(this);
			
			final StatusUpdate su = new StatusUpdate(newTargetCreature);
			su.addAttribute(StatusType.MAX_HP, newTargetCreature.getStatus().getMaxHp());
			su.addAttribute(StatusType.CUR_HP, (int) newTargetCreature.getStatus().getHp());
			sendPacket(su);
			
			broadcastPacket(new TargetSelected(getObjectId(), newTarget.getObjectId(), getX(), getY(), getZ()), false);
		}
		
		if (newTarget instanceof Folk newTargetFolk)
			setCurrentFolk(newTargetFolk);
		else if (newTarget == null)
		{
			sendPacket(ActionFailed.STATIC_PACKET);
			
			if (getTarget() != null)
			{
				broadcastPacket(new TargetUnselected(this));
				setCurrentFolk(null);
			}
		}
		
		super.setTarget(newTarget);
	}
	
	@Override
	public ItemInstance getActiveWeaponInstance()
	{
		return _inventoryAccess.getActiveWeaponInstance();
	}

	@Override
	public Weapon getActiveWeaponItem()
	{
		return _inventoryAccess.getActiveWeaponItem();
	}

	@Override
	public WeaponType getAttackType()
	{
		return _inventoryAccess.getAttackType();
	}

	@Override
	public ItemInstance getSecondaryWeaponInstance()
	{
		return _inventoryAccess.getSecondaryWeaponInstance();
	}

	@Override
	public Item getSecondaryWeaponItem()
	{
		return _inventoryAccess.getSecondaryWeaponItem();
	}
	
	public boolean isUnderMarryRequest()
	{
		return _socialManager.isUnderMarryRequest();
	}

	public void setUnderMarryRequest(boolean state)
	{
		_socialManager.setUnderMarryRequest(state);
	}

	public int getCoupleId()
	{
		return _socialManager.getCoupleId();
	}

	public void setCoupleId(int coupleId)
	{
		_socialManager.setCoupleId(coupleId);
	}
	
	public void setRequesterId(int requesterId)
	{
		_social.setRequesterId(requesterId);
	}
	
	public void engageAnswer(int answer)
	{
		_socialManager.engageAnswer(answer);
	}
	
	@Override
	public boolean doDie(Creature killer)
	{
		return _combat.doDie(killer);
	}
	
	private void onDieDropItem(Creature killer)
	{
		if (killer == null)
			return;
		
		final Player pk = killer.getActingPlayer();
		if (getKarma() <= 0 && pk != null && pk.getClan() != null && getClan() != null && pk.getClan().isAtWarWith(getClanId()))
			return;
		
		if ((!isInsideZone(ZoneId.PVP) || pk == null) && (!isGM() || ConfigPlayers.KARMA_DROP_GM))
		{
			final boolean isKillerNpc = (killer instanceof Npc);
			final int pkLimit = ConfigPlayers.KARMA_PK_LIMIT;
			
			int dropEquip = 0;
			int dropEquipWeapon = 0;
			int dropItem = 0;
			int dropLimit = 0;
			int dropPercent = 0;
			
			if (getKarma() > 0 && getPkKills() >= pkLimit)
			{
				dropPercent = ConfigRates.KARMA_RATE_DROP;
				dropEquip = ConfigRates.KARMA_RATE_DROP_EQUIP;
				dropEquipWeapon = ConfigRates.KARMA_RATE_DROP_EQUIP_WEAPON;
				dropItem = ConfigRates.KARMA_RATE_DROP_ITEM;
				dropLimit = ConfigRates.KARMA_DROP_LIMIT;
			}
			else if (isKillerNpc && getStatus().getLevel() > 4 && !isFestivalParticipant())
			{
				dropPercent = ConfigRates.PLAYER_RATE_DROP;
				dropEquip = ConfigRates.PLAYER_RATE_DROP_EQUIP;
				dropEquipWeapon = ConfigRates.PLAYER_RATE_DROP_EQUIP_WEAPON;
				dropItem = ConfigRates.PLAYER_RATE_DROP_ITEM;
				dropLimit = ConfigRates.PLAYER_DROP_LIMIT;
			}
			
			if (dropPercent > 0 && Rnd.get(100) < dropPercent)
			{
				int dropCount = 0;
				int itemDropPercent = 0;
				
				for (final ItemInstance itemDrop : getInventory().getItems())
				{
					if (!itemDrop.isDropable() || itemDrop.isShadowItem() || itemDrop.getItemId() == 57 || itemDrop.getItem().getType2() == Item.TYPE2_QUEST || (getSummon() != null && getSummon().getControlItemId() == itemDrop.getItemId()) || ArraysUtil.contains(ConfigPlayers.KARMA_NONDROPPABLE_ITEMS, itemDrop.getItemId()) || ArraysUtil.contains(ConfigPlayers.KARMA_NONDROPPABLE_PET_ITEMS, itemDrop.getItemId()))
						continue;
					
					if (itemDrop.isEquipped())
					{
						itemDropPercent = itemDrop.getItem().getType2() == Item.TYPE2_WEAPON ? dropEquipWeapon : dropEquip;
						getInventory().unequipItemInSlot(itemDrop.getLocationSlot());
					}
					else
						itemDropPercent = dropItem;
						
					if (Rnd.get(100) < itemDropPercent)
					{
						dropItem(itemDrop, true);
						
						if (++dropCount >= dropLimit)
							break;
					}
				}
			}
		}
	}
	
	public void updateKarmaLoss(long exp)
	{
		if (!isCursedWeaponEquipped() && getKarma() > 0)
		{
			final int karmaLost = Formulas.calculateKarmaLost(getStatus().getLevel(), exp);
			if (karmaLost > 0)
				setKarma(getKarma() - karmaLost);
		}
	}
	
	/**
	 * This method is used to update PvP counter, or PK counter / add Karma if necessary.<br>
	 * It also updates clan kills/deaths counters on siege.
	 * @param target The L2Playable victim.
	 */
	public void onKillUpdatePvPKarma(Playable target)
	{
		if (target == null)
			return;
		
		final Player targetPlayer = target.getActingPlayer();
		if (targetPlayer == null || targetPlayer == this)
			return;
		
		if (CTFEvent.getInstance().isStarted() && CTFEvent.getInstance().isPlayerParticipant(getObjectId()) || DMEvent.getInstance().isStarted() && DMEvent.getInstance().isPlayerParticipant(getObjectId()) || LMEvent.getInstance().isStarted() && LMEvent.getInstance().isPlayerParticipant(getObjectId()) || TvTEvent.getInstance().isStarted() && TvTEvent.getInstance().isPlayerParticipant(getObjectId()))
			return;
		if (TournamentState.isIn(this))
		{
			return;
		}	
		if (BattleBossState.isInEvent(this))
		{
			return;
		}
		if (isCursedWeaponEquipped() && target instanceof Player)
		{
			CursedWeaponManager.getInstance().increaseKills(getCursedWeaponEquippedId());
			return;
		}
		
		if (isInDuel() && targetPlayer.isInDuel())
			return;
		
		if (isInsideZone(ZoneId.PVP) && targetPlayer.isInsideZone(ZoneId.PVP))
		{
			if (target instanceof Player && getSiegeState() > 0 && targetPlayer.getSiegeState() > 0 && getSiegeState() != targetPlayer.getSiegeState())
			{
				final Clan killerClan = getClan();
				if (killerClan != null)
					killerClan.setSiegeKills(killerClan.getSiegeKills() + 1);
				
				final Clan targetClan = targetPlayer.getClan();
				if (targetClan != null)
					targetClan.setSiegeDeaths(targetClan.getSiegeDeaths() + 1);
			}
			return;
		}
		
		if (checkIfPvP(target) || (targetPlayer.getClan() != null && getClan() != null && getClan().isAtWarWith(targetPlayer.getClanId()) && targetPlayer.getClan().isAtWarWith(getClanId()) && targetPlayer.getPledgeType() != Clan.SUBUNIT_ACADEMY && getPledgeType() != Clan.SUBUNIT_ACADEMY) || (targetPlayer.getKarma() > 0 && ConfigPlayers.KARMA_AWARD_PK_KILL))
		{
			if (target instanceof Player && AntiFeedManager.getInstance().check(this, target))
			{
				FarmEventHooks.get().onPvPKill(this, (Player) target);
				
				setPvpKills(getPvpKills() + 1);
				
				for (RewardSystem kills : PvPData.getInstance().getReward())
				{
					for (IntIntHolder reward : kills.reward())
					{
						if (reward.getId() > 0)
							addItem(reward.getId(), reward.getValue(), true);
					}
				}
				
				for (ColorSystem pvpColor : PvPData.getInstance().getColor())
				{
					if (getPvpKills() >= pvpColor.pvpAmount())
					{
						getAppearance().setNameColor(pvpColor.nameColor());
						getAppearance().setTitleColor(pvpColor.titleColor());
						broadcastUserInfo();
					}
				}
				
				PcCafeManager.getInstance().onPlayerPvPKill(this);
				
				_missionList.update(MissionType.PVP);
				
				sendPacket(new UserInfo(this));
			}
		}
		else if (targetPlayer.getKarma() == 0 && targetPlayer.getPvpFlag() == 0)
		{
			if (target instanceof Player || AntiFeedManager.getInstance().check(this, target))
				setPkKills(getPkKills() + 1);
			
			setKarma(getKarma() + Formulas.calculateKarmaGain(getPkKills(), target instanceof Summon));
			
			checkItemRestriction();
			
			_missionList.update(MissionType.PK);
			
			PvpFlagTaskManager.getInstance().remove(this, true);
		}
	}
	
	public void updatePvPStatus()
	{
		_combat.updatePvPStatus();
	}

	public void updatePvPStatus(Creature target)
	{
		_combat.updatePvPStatus(target);
	}

	/**
	 * Verifica se o player está dentro de uma EnchanterZone ativa.
	 * @return true se está em EnchanterZone ativa, false caso contrário
	 */
	public boolean isInEnchanterZone()
	{
		return _combat.isInEnchanterZone();
	}
	
	/**
	 * Restore the experience this Player has lost and sends StatusUpdate packet.
	 * @param restorePercent The specified % of restored experience.
	 */
	public void restoreExp(double restorePercent)
	{
		_deathLifecycle.restoreExp(restorePercent);
	}

	/**
	 * Calculate the xp loss and the karma decrease.
	 * @param atWar : If true, use clan war penalty system instead of regular system.
	 * @param killedByPlayable : If true, we use specific rules if killed by playable only.
	 */
	public void applyDeathPenalty(boolean atWar, boolean killedByPlayable)
	{
		_deathLifecycle.applyDeathPenalty(atWar, killedByPlayable);
	}
	
	public int getPartyRoom()
	{
		return _partyComponent.getPartyRoom();
	}
	
	public boolean isInPartyMatchRoom()
	{
		return _partyComponent.isInPartyMatchRoom();
	}
	
	public void setPartyRoom(int id)
	{
		_partyComponent.setPartyRoom(id);
	}
	
	/**
	 * Remove the {@link Player} from both waiting list and any potential {@link PartyMatchRoom}.
	 */
	public void removeMeFromPartyMatch()
	{
		_partyComponent.removeMeFromPartyMatch();
	}
	
	@Override
	public Summon getSummon()
	{
		return _mountAndSummon.getSummon();
	}

	/**
	 * Set the {@link Summon} of this {@link Player}.
	 * @param summon : The Summon to set.
	 */
	public void setSummon(Summon summon)
	{
		_mountAndSummon.setSummon(summon);
	}

	/**
	 * @return true if this {@link Player} has a {@link Pet}, false otherwise.
	 */
	public boolean hasPet()
	{
		return _mountAndSummon.hasPet();
	}

	/**
	 * @return true if this {@link Player} has a {@link Servitor}, false otherwise.
	 */
	public boolean hasServitor()
	{
		return _mountAndSummon.hasServitor();
	}

	/**
	 * @return the {@link TamedBeast} of this {@link Player}, null otherwise.
	 */
	public TamedBeast getTamedBeast()
	{
		return _mountAndSummon.getTamedBeast();
	}

	/**
	 * Set the {@link TamedBeast} of this {@link Player}.
	 * @param tamedBeast : The TamedBeast to set.
	 */
	public void setTamedBeast(TamedBeast tamedBeast)
	{
		_mountAndSummon.setTamedBeast(tamedBeast);
	}
	
	/**
	 * @return the current {@link Request}.
	 */
	public Request getRequest()
	{
		return _requestComponent.getRequest();
	}
	
	/**
	 * Set the Player requester of a transaction (ex : FriendInvite, JoinAlly, JoinParty...).
	 * @param requester
	 */
	public void setActiveRequester(Player requester)
	{
		_requestComponent.setActiveRequester(requester);
	}
	
	/**
	 * @return the Player requester of a transaction (ex : FriendInvite, JoinAlly, JoinParty...).
	 */
	public Player getActiveRequester()
	{
		return _requestComponent.getActiveRequester();
	}
	
	/**
	 * @return True if a request is in progress, or false otherwise.
	 */
	public boolean isProcessingRequest()
	{
		return _requestComponent.isProcessingRequest();
	}
	
	/**
	 * @return True if a transaction <B>(trade OR request)</B> is in progress.
	 */
	public boolean isProcessingTransaction()
	{
		return _requestComponent.isProcessingTransaction();
	}
	
	/**
	 * Set the request expire time of that {@link Player}, and set his {@link Player} partner as the active requester.
	 * @param partner : The {@link Player} partner to test.
	 */
	public void onTransactionRequest(Player partner)
	{
		_requestComponent.onTransactionRequest(partner);
	}
	
	/**
	 * @return True if last request is expired, or false otherwise.
	 */
	public boolean isRequestExpired()
	{
		return _requestComponent.isRequestExpired();
	}
	
	public void onTransactionResponse()
	{
		_requestComponent.onTransactionResponse();
	}
	
	public ItemContainer getActiveWarehouse()
	{
		return _storage.getActiveWarehouse();
	}
	
	public void setActiveWarehouse(ItemContainer warehouse)
	{
		_storage.setActiveWarehouse(warehouse);
	}
	
	public TradeList getActiveTradeList()
	{
		return _trade.getActiveTradeList();
	}
	
	public void setActiveTradeList(TradeList tradeList)
	{
		_trade.setActiveTradeList(tradeList);
	}
	
	public void onTradeStart(Player partner)
	{
		_trade.onTradeStart(partner);
	}
	
	public void onTradeConfirm(Player partner)
	{
		_trade.onTradeConfirm(partner);
	}
	
	public void onTradeCancel(Player partner)
	{
		_trade.onTradeCancel(partner);
	}
	
	public void onTradeFinish(boolean isSuccessful)
	{
		_trade.onTradeFinish(isSuccessful);
	}
	
	public void startTrade(Player partner)
	{
		_trade.startTrade(partner);
	}
	
	public void cancelActiveTrade()
	{
		_trade.cancelActiveTrade();
	}
	
	public void cancelActiveEnchant()
	{
		_inventoryAccess.cancelActiveEnchant();
	}
	
	/** @return The Buy {@link TradeList} of this {@link Player}. */
	public TradeList getBuyList()
	{
		return _privateStore.getBuyList();
	}
	
	/** @return The Sell {@link TradeList} of this {@link Player}. */
	public TradeList getSellList()
	{
		return _privateStore.getSellList();
	}
	
	/** @return The {@link ManufactureList} of this {@link Player}. */
	public ManufactureList getManufactureList()
	{
		return _privateStore.getManufactureList();
	}
	
	/** @return The {@link OperateType} of this {@link Player}. */
	public OperateType getOperateType()
	{
		return _operate.getOperateType();
	}
	
	/**
	 * Set the {@link OperateType} of this {@link Player}.
	 * @param type : The new {@link OperateType} state to set.
	 */
	public void setOperateType(OperateType type)
	{
		_operate.setOperateType(type);
	}
	
	/** @return True if this {@link Player} is set on any store mode, or false otherwise. */
	public boolean isInStoreMode()
	{
		return _privateStore.isInStoreMode();
	}
	
	/** @return True if this {@link Player} is set on any store manage mode, or false otherwise. */
	public boolean isInManageStoreMode()
	{
		return _privateStore.isInManageStoreMode();
	}
	
	/**
	 * @return True if this {@link Player} can crystallize.
	 */
	public boolean hasCrystallize()
	{
		return hasSkill(L2Skill.SKILL_CRYSTALLIZE);
	}
	
	/**
	 * @return True if this {@link Player} can use dwarven recipes.
	 */
	public boolean hasDwarvenCraft()
	{
		return hasSkill(L2Skill.SKILL_CREATE_DWARVEN);
	}
	
	/**
	 * @return True if this {@link Player} can use common recipes.
	 */
	public boolean hasCommonCraft()
	{
		return hasSkill(L2Skill.SKILL_CREATE_COMMON);
	}
	
	/**
	 * Method used by regular leveling system.<br>
	 * Reward the {@link Player} with autoGet skills only, or if ConfigPlayers.AUTO_LEARN_SKILLS is activated, with all available skills.
	 */
	public void giveSkills()
	{
		_skillManager.giveSkills();
	}
	
	/**
	 * Method used by admin commands, ConfigPlayers.AUTO_LEARN_SKILLS or class master.<br>
	 * Reward the {@link Player} with all available skills, being autoGet or general skills.
	 */
	public void rewardSkills()
	{
		_skillManager.rewardSkills();
	}
	
	public void rewardSkills(boolean storeAllSkills)
	{
		_skillManager.rewardSkills(storeAllSkills);
	}

	/**
	 * Delete all invalid {@link L2Skill}s for this {@link Player}.<br>
	 * <br>
	 * A skill is considered invalid when the level of obtention of the skill is superior to 9 compared to player level (expertise skill obtention level is compared to player level without any penalty).<br>
	 * <br>
	 * It is then either deleted, or level is refreshed.
	 */
	private void removeInvalidSkills()
	{
		if (getSkills().isEmpty())
			return;
		
		final Set<Integer> templateSkillIds = new HashSet<>();
		
		final Map<Integer, GeneralSkillNode> availableSkills = new HashMap<>();
		
		final int playerLevel = getStatus().getLevel();
		
		for (GeneralSkillNode skillNode : getTemplate().getSkills())
		{
			templateSkillIds.add(skillNode.getId());
			
			if (skillNode.getMinLvl() <= playerLevel + (skillNode.getId() == L2Skill.SKILL_EXPERTISE ? 0 : 9))
			{
				final GeneralSkillNode existingNode = availableSkills.get(skillNode.getId());
				if (existingNode == null || skillNode.getValue() > existingNode.getValue())
					availableSkills.put(skillNode.getId(), skillNode);
			}
		}
		
		for (final L2Skill skill : getSkills().values())
		{
			if (!templateSkillIds.contains(skill.getId()))
				continue;
			
			final GeneralSkillNode availableSkill = availableSkills.get(skill.getId());
			if (availableSkill == null)
			{
				removeSkill(skill.getId(), true);
				continue;
			}
			
			final int maxLevel = SkillTable.getInstance().getMaxLevel(skill.getId());
			
			if (skill.getLevel() > maxLevel)
			{
				if ((playerLevel < 76 || availableSkill.getValue() < maxLevel) && skill.getLevel() > availableSkill.getValue())
					addSkill(availableSkill.getSkill(), true);
			}
			else if (skill.getLevel() > availableSkill.getValue())
				addSkill(availableSkill.getSkill(), true);
		}
	}
	
	/**
	 * Regive all skills which aren't saved to database, like Noble, Hero, Clan Skills.<br>
	 * <b>Do not call this on enterworld or char load.</b>.
	 */
	/** Regive temporary skills; public for {@link PlayerSubClass#setActiveClass}. */
	public void regiveTemporarySkills()
	{
		if (isNoble())
			setNoble(true, false);
		
		if (isHero())
			setHero(true);
		
		if (getClan() != null)
		{
			getClan().checkAndAddClanSkills(this);
			
			if (getClan().getLevel() >= ConfigSiege.MINIMUM_CLAN_LEVEL && isClanLeader())
				addSiegeSkills();
		}
		
		getInventory().reloadEquippedItems();
		
		if (getDeathPenaltyBuffLevel() > 0)
			addSkill(SkillTable.getInstance().getInfo(5076, getDeathPenaltyBuffLevel()), false);
	}
	
	public void addSiegeSkills()
	{
		for (final L2Skill sk : SkillTable.getInstance().getSiegeSkills(isNoble()))
			addSkill(sk, false);
	}
	
	public void removeSiegeSkills()
	{
		for (final L2Skill sk : SkillTable.getInstance().getSiegeSkills(isNoble()))
			removeSkill(sk.getId(), false);
	}
	
	/**
	 * @return a {@link List} of all available autoGet {@link GeneralSkillNode}s <b>of maximal level</b> for this {@link Player}.
	 */
	public List<GeneralSkillNode> getAvailableAutoGetSkills()
	{
		final List<GeneralSkillNode> result = new ArrayList<>();
		
		getTemplate().getSkills().stream().filter(s -> s.getMinLvl() <= getStatus().getLevel() && s.getCost() == 0).collect(Collectors.groupingBy(s -> s.getId(), Collectors.maxBy(COMPARE_SKILLS_BY_LVL))).forEach((i, s) ->
		{
			if (getSkillLevel(i) < s.get().getValue())
				result.add(s.get());
		});
		return result;
	}
	
	/**
	 * @return a {@link List} of available {@link GeneralSkillNode}s (only general) for this {@link Player}.
	 */
	public List<GeneralSkillNode> getAvailableSkills()
	{
		final List<GeneralSkillNode> result = new ArrayList<>();
		
		getTemplate().getSkills().stream().filter(s -> s.getMinLvl() <= getStatus().getLevel() && s.getCost() != 0).forEach(s ->
		{
			if (getSkillLevel(s.getId()) == s.getValue() - 1)
				result.add(s);
		});
		return result;
	}
	
	/**
	 * @return a {@link List} of all available {@link GeneralSkillNode}s (being general or autoGet) <b>of maximal level</b> for this {@link Player}.
	 */
	public List<GeneralSkillNode> getAllAvailableSkills()
	{
		final List<GeneralSkillNode> result = new ArrayList<>();
		
		getTemplate().getSkills().stream().filter(s -> s.getMinLvl() <= getStatus().getLevel()).collect(Collectors.groupingBy(s -> s.getId(), Collectors.maxBy(COMPARE_SKILLS_BY_LVL))).forEach((i, s) ->
		{
			if (getSkillLevel(i) < s.get().getValue())
				result.add(s.get());
		});
		return result;
	}
	
	/**
	 * Retrieve next lowest level skill to learn, based on current player level and skill sp cost.
	 * @return the required level for next {@link GeneralSkillNode} to learn for this {@link Player}.
	 */
	public int getRequiredLevelForNextSkill()
	{
		return getTemplate().getSkills().stream().filter(s -> s.getMinLvl() > getStatus().getLevel() && s.getCost() != 0).min(COMPARE_SKILLS_BY_MIN_LVL).map(s -> s.getMinLvl()).orElse(0);
	}
	
	@Override
	public void reduceArrowCount()
	{
		_inventoryAccess.reduceArrowCount();
	}

	/**
	 * Check if the arrow item exists on inventory and is already slotted ; if not, equip it.
	 */
	@Override
	public boolean checkAndEquipArrows()
	{
		return _inventoryAccess.checkAndEquipArrows();
	}

	/**
	 * Disarm the {@link Player}'s weapon.
	 * @param leftHandIncluded : If set to True, the left hand item is also disarmed.
	 * @return True if successful, false otherwise.
	 */
	public boolean disarmWeapon(boolean leftHandIncluded)
	{
		return _inventoryAccess.disarmWeapon(leftHandIncluded);
	}
	
	public boolean mount(Summon pet)
	{
		return _mount.mount(pet);
	}
	
	public boolean mount(int npcId, int controlItemId)
	{
		return _mount.mount(npcId, controlItemId);
	}
	
	/**
	 * Test if this {@link Player} can mount the selected {@link Summon} or dismount if already mounted, and act accordingly.<br>
	 * <br>
	 * This method is used by both "Actions" panel "Mount/Dismount" button, and /mount /dismount usercommands.
	 * @param summon : The Summon to check.
	 */
	public void mountPlayer(Summon summon)
	{
		_mount.mountPlayer(summon);
	}
	
	public void dismount()
	{
		_mount.dismount();
	}
	
	public void storePetFood(int petId)
	{
		_mount.storePetFood(petId);
	}
	
	
	public synchronized void startFeed(int npcId)
	{
		_mount.startFeed(npcId);
	}

	public synchronized void stopFeed()
	{
		_mount.stopFeed();
	}

	/**
	 * Public wrapper allowing {@link ext.mods.gameserver.model.actor.player.PlayerCombat}
	 * to invoke the {@link Creature#doDie(Creature)} implementation from {@link Playable}.
	 * @param killer the killing creature.
	 * @return the result of {@code super.doDie(killer)}.
	 */
	public boolean callSuperDoDie(Creature killer)
	{
		return super.doDie(killer);
	}

	/**
	 * Public wrapper allowing {@link ext.mods.gameserver.model.actor.player.PlayerCombat}
	 * to invoke the {@link Creature#doRevive()} implementation from {@link Playable}.
	 */
	public void callSuperDoRevive()
	{
		super.doRevive();
	}

	/**
	 * Public wrapper allowing {@link ext.mods.gameserver.model.actor.player.PlayerAppearanceBroadcast}
	 * to invoke the {@link Creature#broadcastPacket(L2GameServerPacket, boolean)} implementation.
	 */
	public void superBroadcastPacket(L2GameServerPacket packet, boolean selfToo)
	{
		super.broadcastPacket(packet, selfToo);
	}

	/**
	 * Public wrapper allowing {@link ext.mods.gameserver.model.actor.player.PlayerAppearanceBroadcast}
	 * to invoke the {@link Creature#broadcastPacketInRadius(L2GameServerPacket, int)} implementation.
	 */
	public void superBroadcastPacketInRadius(L2GameServerPacket packet, int radius)
	{
		super.broadcastPacketInRadius(packet, radius);
	}

	/**
	 * Public wrapper allowing {@link ext.mods.gameserver.model.actor.player.PlayerAppearanceBroadcast}
	 * to invoke the {@link Creature#polymorph(int)} implementation.
	 */
	public boolean superPolymorph(int npcId)
	{
		return super.polymorph(npcId);
	}

	/**
	 * Public wrapper allowing {@link ext.mods.gameserver.model.actor.player.PlayerAppearanceBroadcast}
	 * to invoke the {@link Creature#unpolymorph()} implementation.
	 */
	public void superUnpolymorph()
	{
		super.unpolymorph();
	}
	
	public PetTemplate getPetTemplate()
	{
		return _mount.getPetTemplate();
	}
	
	public PetDataEntry getPetDataEntry()
	{
		return _mount.getPetDataEntry();
	}
	
	public int getCurrentFeed()
	{
		return _mount.getCurrentFeed();
	}
		
	public void setCurrentFeed(int num)
	{
		_mount.setCurrentFeed(num);
	}
	
	/**
	 * @param state : The state to check (can be autofeed, hungry or unsummon).
	 * @return true if the limit is reached, false otherwise or if there is no need to feed.
	 */
	public boolean checkFoodState(double state)
	{
		return _mount.checkFoodState(state);
	}
	
	public void setUptime(long time)
	{
		_session.setUptime(time);
	}
	
	public long getUptime()
	{
		return _session.getUptime();
	}
	
	/**
	 * Return True if the Player is invulnerable.
	 */
	@Override
	public boolean isInvul()
	{
		return super.isInvul() || isSpawnProtected();
	}
	
	/**
	 * Return True if the Player has a Party in progress.
	 */
	@Override
	public boolean isInParty()
	{
		return _partyComponent.isInParty();
	}
	
	/**
	 * Set the party object of the Player (without joining it).
	 * @param party The object.
	 */
	public void setParty(Party party)
	{
		_partyComponent.setParty(party);
	}
	
	/**
	 * Return the party object of the Player.
	 */
	@Override
	public Party getParty()
	{
		return _partyComponent.getParty();
	}
	
	public LootRule getLootRule()
	{
		return _partyComponent.getLootRule();
	}
	
	public void setLootRule(LootRule lootRule)
	{
		_partyComponent.setLootRule(lootRule);
	}
	
	/**
	 * Return True if the Player is a GM.
	 */
	@Override
	public boolean isGM()
	{
		
		return getAccessLevel() != null && getAccessLevel().isGm();
	}
	
	/**
	 * Set the {@link AccessLevel} of this {@link Player}.
	 * <ul>
	 * <li>If invalid, set the default user access level 0.</li>
	 * <li>If superior to 0, it means it's a special access.</li>
	 * </ul>
	 * @param level : The level to set.
	 */
	public void setAccessLevel(int level)
	{
		AccessLevel accessLevel = AdminData.getInstance().getAccessLevel(level);
		if (accessLevel == null)
		{
			LOGGER.warn("An invalid access level {} has been granted for {}, therefore it has been reset.", level, toString());
			accessLevel = AdminData.getInstance().getAccessLevel(0);
		}
		
		_accessLevel = accessLevel;
		
		if (level > 0)
			setTitle(accessLevel.getName());
			
		if (accessLevel.isGm())
		{
			if (!AdminData.getInstance().isRegisteredAsGM(this))
				AdminData.getInstance().addGm(this, false);
		}
		else
			AdminData.getInstance().deleteGm(this);
		
		getAppearance().setNameColor(accessLevel.getNameColor());
		getAppearance().setTitleColor(accessLevel.getTitleColor());
		broadcastUserInfo();
		
		PlayerInfoTable.getInstance().updatePlayerData(this, true);
	}
	
	public void setAccountAccesslevel(int level)
	{
		LoginServerThread.getInstance().sendAccessLevel(getAccountName(), level);
	}
	
	/**
	 * @return the _accessLevel of the Player.
	 */
	public AccessLevel getAccessLevel()
	{
		return _accessLevel;
	}
	
	public final void setEnterWorldLoc(int x, int y, int z)
	{
		_uiPrefs.setEnterWorld(new Location(x, y, z));
	}
	
	public final ExServerPrimitive getDebugPacket(String name)
	{
		return _uiPrefs.getDebugPacket(name);
	}
	
	public final void clearDebugPackets()
	{
		_uiPrefs.clearDebugPackets();
	}
	
	/**
	 * Update Stats of the Player client side by sending UserInfo/StatusUpdate to this Player and CharInfo/StatusUpdate to all Player in its _KnownPlayers (broadcast).
	 * @param broadcastType
	 */
	public void updateAndBroadcastStatus(int broadcastType)
	{
		refreshWeightPenalty();
		refreshExpertisePenalty();
		
		if (broadcastType == 1)
			sendPacket(new UserInfo(this));
		else if (broadcastType == 2)
			broadcastUserInfo();
	}
	
	/**
	 * Set the online Flag to True or False and update the characters table of the database with online status and lastAccess (called when login and logout).
	 * @param isOnline
	 * @param updateInDb
	 */
	public void setOnlineStatus(boolean isOnline, boolean updateInDb)
	{
		if (_session.isOnline() != isOnline)
			_session.setOnline(isOnline);
		
		if (updateInDb)
			updateOnlineStatus();
	}
	
	public void setIsIn7sDungeon(boolean isIn7sDungeon)
	{
		_pvp.setIsIn7sDungeon(isIn7sDungeon);
	}
	
	/**
	 * Update the characters table of the database with online status and lastAccess of this Player (called when login and logout).
	 */
	public void updateOnlineStatus()
	{
		_persistence.updateOnlineStatus();
	}
	
	public static Player restore(int objectId)
	{
		return restore(objectId, false);
	}
	
	/**
	 * Retrieve a Player from the characters table of the database.
	 * <ul>
	 * <li>Retrieve the Player from the characters table of the database</li>
	 * <li>Set the x,y,z position of the Player and make it invisible</li>
	 * <li>Update the overloaded status of the Player</li>
	 * </ul>
	 * @param objectId Identifier of the object to initialized
	 * @param offline
	 * @return The Player loaded from the database
	 */
	public static Player restore(int objectId, boolean offline)
	{
		return PlayerPersistence.restore(objectId, offline);
	}
	
	public Forum getMemo()
	{
		if (_forumMemo == null)
			_forumMemo = CommunityBoard.getInstance().getOrCreateForum(ForumType.MEMO, ForumAccess.ALL, getObjectId());

		return _forumMemo;
	}
	
	/**
	 * Restores secondary data for the Player, based on the current class index.
	 */
	private void restoreCharData()
	{
		_persistence.restoreCharData();
	}
	
	/**
	 * Update Player stats in the characters table of the database.
	 * @param storeActiveEffects
	 */
	public synchronized void store(boolean storeActiveEffects)
	{
		_persistence.store(storeActiveEffects);
	}

	public synchronized void store()
	{
		store(true);
	}

	public void storeCharBase()
	{
		_persistence.storeCharBase();
	}
	
	public void storeEffect(boolean storeEffects)
	{
		_skillsDb.storeEffect(storeEffects);
	}
	
	/**
	 * @return True if the Player is online.
	 */
	public boolean isOnline()
	{
		return _session.isOnline();
	}
	
	/**
	 * @return an int interpretation of online status.
	 */
	public int isOnlineInt()
	{
		if (_session.isOnline() && getClient() != null)
			return getClient().isDetached() ? 2 : 1;
		
		return 0;
	}
	
	public boolean isIn7sDungeon()
	{
		return _pvp.isIn7sDungeon();
	}
	
	/**
	 * Add a {@link L2Skill} and its Func objects to the calculator set of the {@link Player}. Don't refresh shortcuts.
	 * @see Player#addSkill(L2Skill, boolean, boolean)
	 * @param newSkill : The skill to add.
	 * @param store : If true, we save the skill on database.
	 * @return true if the skill has been successfully added.
	 */
	public boolean addSkill(L2Skill newSkill, boolean store)
	{
		return _skillManager.addSkill(newSkill, store);
	}

	/**
	 * Add a {@link L2Skill} and its Func objects to the calculator set of the {@link Player}.<BR>
	 * <ul>
	 * <li>Replace or add oldSkill by newSkill (only if oldSkill is different than newSkill)</li>
	 * <li>If an old skill has been replaced, remove all its Func objects of Creature calculator set</li>
	 * <li>Add Func objects of newSkill to the calculator set of the Creature</li>
	 * </ul>
	 * @param newSkill : The skill to add.
	 * @param store : If true, we save the skill on database.
	 * @param updateShortcuts : If true, we refresh all shortcuts associated to that skill (should be only called when skill upgrades, either manually or by trainer).
	 * @return true if the skill has been successfully added.
	 */
	public boolean addSkill(L2Skill newSkill, boolean store, boolean updateShortcuts)
	{
		return _skillManager.addSkill(newSkill, store, updateShortcuts);
	}

	/**
	 * Remove a {@link L2Skill} from this {@link Player}. If parameter store is true, we also remove it from database and update shortcuts.
	 * @param skillId : The skill identifier to remove.
	 * @param store : If true, we delete the skill from database.
	 * @return the L2Skill removed or null if it couldn't be removed.
	 */
	public L2Skill removeSkill(int skillId, boolean store)
	{
		return _skillManager.removeSkill(skillId, store);
	}

	/**
	 * Remove a {@link L2Skill} from this {@link Player}. If parameter store is true, we also remove it from database and update shortcuts.
	 * @param skillId : The skill identifier to remove.
	 * @param store : If true, we delete the skill from database.
	 * @param removeEffect : If true, we remove the associated effect if existing.
	 * @return the L2Skill removed or null if it couldn't be removed.
	 */
	public L2Skill removeSkill(int skillId, boolean store, boolean removeEffect)
	{
		return _skillManager.removeSkill(skillId, store, removeEffect);
	}
	
	/**
	 * Insert or update a {@link Player} skill in the database.<br>
	 * If newClassIndex > -1, the skill will be stored with that class index, not the current one.
	 * @param skill : The skill to add or update (if updated, only the level is refreshed).
	 * @param classIndex : The current class index to set, or current if none is found.
	 */
	private void storeSkill(L2Skill skill, int classIndex)
	{
		_skillsDb.storeSkill(skill, classIndex);
	}
	
	/**
	 * Restore all skills from database for this {@link Player} and feed getSkills().
	 */
	private void restoreSkills()
	{
		_skillsDb.restoreSkills();
	}
	
	/**
	 * Restore {@link L2Skill} effects of this {@link Player} from the database.
	 */
	public void restoreEffects()
	{
		_skillsDb.restoreEffects();
	}
	
	/**
	 * Restore Recommendation data of this {@link Player} from the database.
	 */
	private void restoreRecom()
	{
		_recom.restore();
	}
	
	/**
	 * @return the {@link HennaList} of this {@link Player}.
	 */
	public HennaList getHennaList()
	{
		return _hennaList;
	}
	
	/**
	 * Refresh the {@link HennaList} of this {@link Player}.
	 */
	public void refreshHennaList()
	{
		_hennaList.recalculateStats();
		
		sendPacket(new HennaInfo(this));
	}
	
	/**
	 * @param objectId : The looter object to make checks on.
	 * @return true if the active player is the looter or in the same party or command channel than looter objectId.
	 */
	public boolean isLooterOrInLooterParty(int objectId)
	{
		if (objectId == getObjectId())
			return true;
		
		final Player looter = World.getInstance().getPlayer(objectId);
		if (looter == null)
			return false;
		
		if (getParty() == null)
			return false;
		
		final CommandChannel channel = getParty().getCommandChannel();
		return (channel != null) ? channel.containsPlayer(looter) : getParty().containsPlayer(looter);
	}
	
	public boolean canCastBeneficialSkillOnPlayable(Playable target, L2Skill skill, boolean isCtrlPressed)
	{
		if (this == target)
			return true;
		
		final Player targetPlayer = target.getActingPlayer();
		
		if (targetPlayer.isInsideZone(ZoneId.PVP))
		{
			if (isInsideZone(ZoneId.PVP))
				return true;
			
			if (isInsideZone(ZoneId.PEACE))
				return isCtrlPressed;
		}
		
		if (isInOlympiadMode() && targetPlayer.isInOlympiadMode() && getOlympiadGameId() == targetPlayer.getOlympiadGameId())
			return true;
		
		if (isInDuel() && targetPlayer.isInDuel() && getDuelId() == targetPlayer.getDuelId())
			return true;
		
		final boolean sameParty = (isInParty() && targetPlayer.isInParty() && getParty().getLeader() == targetPlayer.getParty().getLeader());
		final boolean sameCommandChannel = (isInParty() && targetPlayer.isInParty() && getParty().getCommandChannel() != null && getParty().getCommandChannel().containsPlayer(targetPlayer));
		final boolean sameClan = (getClanId() > 0 && getClanId() == targetPlayer.getClanId());
		final boolean sameAlliance = (getAllyId() > 0 && getAllyId() == targetPlayer.getAllyId());
		if (sameParty || sameCommandChannel || sameClan || sameAlliance)
			return true;
		
		if (targetPlayer.getPvpFlag() > 0 || targetPlayer.getKarma() > 0)
			return isCtrlPressed;
		
		return true;
	}
	
	/**
	 * @return True if the Player is a Mage (based on class templates).
	 */
	public boolean isMageClass()
	{
		return getClassId().getType() != ClassType.FIGHTER;
	}
	
	public boolean isMounted()
	{
		return _mount.isMounted();
	}
	
	/**
	 * This method allows to :
	 * <ul>
	 * <li>change isRiding/isFlying flags</li>
	 * <li>gift player with Wyvern Breath skill if mount is a wyvern</li>
	 * <li>send the skillList (faded icons update)</li>
	 * </ul>
	 * @param npcId the npcId of the mount
	 * @param npcLevel The level of the mount
	 * @param mountType 0, 1 or 2 (dismount, strider or wyvern).
	 */
	public void setMount(int npcId, int npcLevel, int mountType)
	{
		_mount.setMount(npcId, npcLevel, mountType);
	}
	
	@Override
	public boolean isSeated()
	{
		return _mountAndSummon.isSeated();
	}

	public int getThroneId()
	{
		return _mountAndSummon.getThroneId();
	}

	public void setThroneId(int id)
	{
		_mountAndSummon.setThroneId(id);
	}

	@Override
	public boolean isRiding()
	{
		return _mount.isRiding();
	}

	@Override
	public boolean isFlying()
	{
		return _mount.isFlying();
	}

	/**
	 * @return the type of Pet mounted (0 : none, 1 : Strider, 2 : Wyvern).
	 */
	public int getMountType()
	{
		return _mount.getMountType();
	}
	
	@Override
	public final void stopAllEffects()
	{
		super.stopAllEffects();
		updateAndBroadcastStatus(2);
	}
	
	@Override
	public final void stopAllEffectsDebuff()
	{
		super.stopAllEffectsDebuff();
		updateAndBroadcastStatus(2);
	}
	
	@Override
	public final void stopAllEffectsExceptThoseThatLastThroughDeath()
	{
		super.stopAllEffectsExceptThoseThatLastThroughDeath();
		updateAndBroadcastStatus(2);
	}
	
	/**
	 * Stop all toggle-type effects
	 */
	public final void stopAllToggles()
	{
		_effects.stopAllToggles();
	}
	
	/**
	 * Send UserInfo to this Player and CharInfo to all Player in its _KnownPlayers.<BR>
	 * <ul>
	 * <li>Send UserInfo to this Player (Public and Private Data)</li>
	 * <li>Send CharInfo to all Player in _KnownPlayers of the Player (Public data only)</li>
	 * </ul>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : DON'T SEND UserInfo packet to other players instead of CharInfo packet. Indeed, UserInfo packet contains PRIVATE DATA as MaxHP, STR, DEX...</B></FONT><BR>
	 * <BR>
	 */
	@Override
	public void updateAbnormalEffect()
	{
		broadcastUserInfo();
	}
	
	/**
	 * Disable the Inventory and create a new task to enable it after 1.5s.
	 * <p>
	 * Source of truth lives here: {@link #_inventoryDisable} is read by
	 * {@link #isInventoryDisabled()} and reset by the scheduled task below.
	 * {@link PlayerInventoryAccess} forwards to these methods without re-delegating.
	 */
	public void tempInventoryDisable()
	{
		_inventoryDisable = true;
		ThreadPool.schedule(() -> _inventoryDisable = false, 1500);
	}

	/**
	 * @return True if the Inventory is disabled.
	 */
	public boolean isInventoryDisabled()
	{
		return _inventoryDisable;
	}

	/**
	 * @return the modifier corresponding to the Enchant Effect of the Active Weapon (Min : 127).
	 */
	public int getEnchantEffect()
	{
		return _inventoryAccess.getEnchantEffect();
	}
	
	/**
	 * Remember the current {@link Folk} of the {@link Player}, used notably for integrity check.
	 * @param folk : The Folk to remember.
	 */
	public void setCurrentFolk(Folk folk)
	{
		_currentFolk = folk;
	}
	
	/**
	 * @return the current {@link Folk} of the {@link Player}.
	 */
	public Folk getCurrentFolk()
	{
		return _currentFolk;
	}
	
	/**
	 * @return True if Player is a participant in the Festival of Darkness.
	 */
	public boolean isFestivalParticipant()
	{
		return FestivalOfDarknessManager.getInstance().isParticipant(this);
	}
	
	public void addAutoSoulShot(int itemId)
	{
		_soulShot.raw().add(itemId);
	}
	
	public boolean removeAutoSoulShot(int itemId)
	{
		return _soulShot.raw().remove(itemId);
	}
	
	public Set<Integer> getAutoSoulShot()
	{
		return _soulShot.raw();
	}
	
	@Override
	public boolean isChargedShot(ShotType type)
	{
		final ItemInstance weapon = getActiveWeaponInstance();
		return weapon != null && weapon.isChargedShot(type);
	}
	
	@Override
	public void setChargedShot(ShotType type, boolean charged)
	{
		final ItemInstance weapon = getActiveWeaponInstance();
		if (weapon != null)
			weapon.setChargedShot(type, charged);
	}
	
	@Override
	public void rechargeShots(boolean physical, boolean magic)
	{
		if (_soulShot.raw().isEmpty())
			return;
		
		for (final int itemId : _soulShot.raw())
		{
			final ItemInstance item = getInventory().getItemByItemId(itemId);
			if (item != null)
			{
				if (magic && item.getItem().getDefaultAction() == ActionType.spiritshot)
				{
					final IItemHandler handler = ItemHandler.getInstance().getHandler(item.getEtcItem());
					if (handler != null)
						handler.useItem(this, item, false);
				}
				
				if (physical && item.getItem().getDefaultAction() == ActionType.soulshot)
				{
					final IItemHandler handler = ItemHandler.getInstance().getHandler(item.getEtcItem());
					if (handler != null)
						handler.useItem(this, item, false);
				}
			}
			else
				removeAutoSoulShot(itemId);
		}
	}
	
	/**
	 * Cancel autoshot use for shot itemId
	 * @param itemId int id to disable
	 * @return true if canceled.
	 */
	public boolean disableAutoShot(int itemId)
	{
		if (_soulShot.raw().contains(itemId))
		{
			removeAutoSoulShot(itemId);
			sendPacket(new ExAutoSoulShot(itemId, 0));
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.AUTO_USE_OF_S1_CANCELLED).addItemName(itemId));
			return true;
		}
		
		return false;
	}
	
	public void disableBeastShots()
	{
		for (int itemId : _soulShot.raw())
		{
			switch (ItemData.getInstance().getTemplate(itemId).getDefaultAction())
			{
				case summon_soulshot, summon_spiritshot:
					disableAutoShot(itemId);
					break;
			}
		}
	}
	
	/**
	 * Cancel all autoshots for player
	 */
	public void disableAutoShotsAll()
	{
		for (final int itemId : _soulShot.raw())
		{
			sendPacket(new ExAutoSoulShot(itemId, 0));
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.AUTO_USE_OF_S1_CANCELLED).addItemName(itemId));
		}
		_soulShot.raw().clear();
	}
	
	public int getClanPrivileges()
	{
		return _clanComponent.getClanPrivileges();
	}
	
	public boolean hasClanPrivileges(PrivilegeType priv)
	{
		return _clanComponent.hasClanPrivileges(priv);
	}
	
	public int getPledgeClass()
	{
		return _clanComponent.getPledgeClass();
	}
	
	public void setPledgeClass(int classId)
	{
		_clanComponent.setPledgeClass(classId);
	}
	
	public int getPledgeType()
	{
		return _clanComponent.getPledgeType();
	}
	
	public void setPledgeType(int typeId)
	{
		_clanComponent.setPledgeType(typeId);
	}
	
	public int getApprentice()
	{
		return _clanComponent.getApprentice();
	}
	
	public void setApprentice(int id)
	{
		_clanComponent.setApprentice(id);
	}
	
	public int getSponsor()
	{
		return _clanComponent.getSponsor();
	}
	
	public void setSponsor(int id)
	{
		_clanComponent.setSponsor(id);
	}
	
	@Override
	public void sendMessage(String message)
	{
		sendPacket(SystemMessage.sendString(message));
	}
	
	@Override
	public boolean teleportTo(int x, int y, int z, int randomOffset)
	{
		if (!super.teleportTo(x, y, z, randomOffset))
			return false;
		
		cancelActiveEnchant();
		cancelActiveTrade();
		
		final Boat boat = getBoatInfo().getBoat();
		if (boat != null)
			boat.removePassenger(this);
		PlayerListenerManager.getInstance().notifyTeleport(this, x, y, z);
		return true;
	}
	
	@Override
	public void teleportTo(RestartType type)
	{
		teleportTo(RestartPointData.getInstance().getLocationToTeleport(this, type), 20);
	}
	
	private int _incorrectValidateCount = 0;
	
	public int getIncorrectValidateCount()
	{
		return _incorrectValidateCount;
	}
	
	public void incIncorrectValidateCount()
	{
		_incorrectValidateCount++;
	}
	
	public void resetIncorrectValidateCount()
	{
		_incorrectValidateCount = 0;
	}
	
	/**
	 * Unsummon all types of summons : pets, cubics, normal summons and trained beasts.
	 */
	public void dropAllSummons()
	{
		if (getSummon() != null)
			getSummon().unSummon(this);

		if (getTamedBeast() != null)
			getTamedBeast().deleteMe();

		_cubicList.stopCubics(true);
	}
	
	public void enterObserverMode(ObserverLocation loc)
	{
		if (loc.getCost() > 0 && !reduceAdena(loc.getCost(), true))
			return;
		
		dropAllSummons();
		
		if (getParty() != null)
			getParty().removePartyMember(this, MessageType.EXPELLED);
		
		standUp();
		
		_savedLocation.set(getPosition());
		
		setInvul(true);
		getAppearance().setVisible(false);
		setIsParalyzed(true);
		
		abortAll(true);
		
		teleportTo(loc, 0);
		sendPacket(new ObserverStart(loc));
	}
	
	public void enterOlympiadObserverMode(int id)
	{
		_olympiad.enterOlympiadObserverMode(id);
	}
	
	public void leaveObserverMode()
	{
		getAI().tryToIdle();
		
		setTarget(null);
		getAppearance().setVisible(true);
		setInvul(false);
		setIsParalyzed(false);
		
		sendPacket(new ObserverEnd(_savedLocation));
		teleportTo(_savedLocation, 0);
		
		_savedLocation.clean();
	}
	
	public void leaveOlympiadObserverMode()
	{
		_olympiad.leaveOlympiadObserverMode();
	}
	
	public int getOlympiadSide()
	{
		return _olympiad.getOlympiadSide();
	}
	
	public void setOlympiadSide(int i)
	{
		_olympiad.setOlympiadSide(i);
	}
	
	public int getOlympiadGameId()
	{
		return _olympiad.getOlympiadGameId();
	}
	
	public void setOlympiadGameId(int id)
	{
		_olympiad.setOlympiadGameId(id);
	}
	
	public Location getSavedLocation()
	{
		return _savedLocation;
	}
	
	public boolean isInObserverMode()
	{
		return !_olympiad.isInOlympiadMode() && !_savedLocation.equals(Location.DUMMY_LOC);
	}
	
	public TeleportMode getTeleportMode()
	{
		return _teleportMode;
	}
	
	public void setTeleportMode(TeleportMode mode)
	{
		_teleportMode = mode;
	}
	
	public int getLoto(int i)
	{
		return _uiPrefs.getLoto(i);
	}
	
	public void setLoto(int i, int val)
	{
		_uiPrefs.setLoto(i, val);
	}
	
	public int getRace(int i)
	{
		return _uiPrefs.getRace(i);
	}
	
	public void setRace(int i, int val)
	{
		_uiPrefs.setRace(i, val);
	}
	
	public QuestList getQuestList()
	{
		return _questList;
	}
	
	public boolean isHero()
	{
		return _hero.isHero();
	}
	
	public void setHero(boolean hero)
	{
		_hero.setHero(hero);
	}
	
	public boolean isOfflineFarm()
	{
		return _offline.isOfflineFarm();
	}
	
	public void setOfflineFarm(boolean offlineFarm)
	{
		_offline.setOfflineFarm(offlineFarm);
	}
	
	public void startOfflineFarm()
	{
		_offline.startOfflineFarm();
	}
	
	public void stopOfflineFarm()
	{
		_offline.stopOfflineFarm();
	}
	
	public void setHpPotionPercentage(int percent)
	{
		_uiPrefs.setHpPotionPercentage(percent);
	}
	
	public int getHpPotionPercentage()
	{
		return _uiPrefs.getHpPotionPercentage();
	}
	
	public void setMpPotionPercentage(int percent)
	{
		_uiPrefs.setMpPotionPercentage(percent);
	}
	
	public int getMpPotionPercentage()
	{
		return _uiPrefs.getMpPotionPercentage();
	}
	
	public String getLastCommand()
	{
		return _uiPrefs.getLastCommand();
	}
	
	public void setLastCommand(String lastCommand)
	{
		_uiPrefs.setLastCommand(lastCommand);
	}
	
	public boolean isInVehicle()
	{
		return isInBoat();
	}
	
	public boolean isOlympiadProtection()
	{
		return _olympiad.isOlympiadProtection();
	}
	
	public boolean isOlympiadStart()
	{
		return _olympiad.isOlympiadStart();
	}
	
	public void setOlympiadStart(boolean b)
	{
		_olympiad.setOlympiadStart(b);
	}
	
	public boolean isInOlympiadMode()
	{
		return _olympiad.isInOlympiadMode();
	}
	
	public void setOlympiadMode(boolean b)
	{
		_olympiad.setOlympiadMode(b);
	}
	
	public boolean isInDuel()
	{
		return _duel.isInDuel();
	}
	
	public int getDuelId()
	{
		return _duel.getDuelId();
	}
	
	public void setDuelState(DuelState state)
	{
		_duel.setDuelState(state);
	}
	
	public DuelState getDuelState()
	{
		return _duel.getDuelState();
	}
	
	/**
	 * Sets up the duel state using a non 0 duelId.
	 * @param duelId 0=not in a duel
	 */
	public void setInDuel(int duelId)
	{
		_duel.setInDuel(duelId);
	}
	
	/**
	 * This returns a SystemMessage stating why the player is not available for duelling.
	 * @return S1_CANNOT_DUEL... message
	 */
	public SystemMessage getNoDuelReason()
	{
		return _duel.getNoDuelReason();
	}
	
	/**
	 * Checks if this player might join / start a duel. To get the reason use getNoDuelReason() after calling this function.
	 * @return true if the player might join/start a duel.
	 */
	public boolean canDuel()
	{
		return _duel.canDuel();
	}
	
	public void resetDuelState()
	{
		_duel.resetDuelState();
	}
	
	public void prepareToDuel(TeamType type)
	{
		_duel.prepareToDuel(type);
	}
	
	public boolean isNoble()
	{
		return _hero.isNoble();
	}
	
	/**
	 * Set Noblesse status, and reward with nobles' {@link L2Skill}s.
	 * @param isNoble : If true, add {@link L2Skill}s ; otherwise remove them.
	 * @param storeInDb : If true, store directly the data in the db.
	 */
	public void setNoble(boolean isNoble, boolean storeInDb)
	{
		_hero.setNoble(isNoble, storeInDb);
	}
	
	public void setLvlJoinedAcademy(int lvl)
	{
		_clanComponent.setLvlJoinedAcademy(lvl);
	}
	
	public int getLvlJoinedAcademy()
	{
		return _clanComponent.getLvlJoinedAcademy();
	}
	
	public boolean isAcademyMember()
	{
		return _clanComponent.isAcademyMember();
	}
	
	public void setTeam(TeamType team)
	{
		_teamState.setTeam(team);
	}
	
	public TeamType getTeam()
	{
		return _teamState.getTeam();
	}
	
	public void setWantsPeace(boolean wantsPeace)
	{
		_wantsPeace = wantsPeace;
	}
	
	public boolean wantsPeace()
	{
		return _wantsPeace;
	}
	
	public boolean isFishing()
	{
		return _fishingStance.isUnderFishCombat() || _fishingStance.isLookingForFish();
	}
	
	public void setAllianceWithVarkaKetra(int sideAndLvlOfAlliance)
	{
		_appearanceBroadcast.setAllianceWithVarkaKetra(sideAndLvlOfAlliance);
	}

	/**
	 * [-5,-1] varka, 0 neutral, [1,5] ketra
	 * @return the side faction.
	 */
	public int getAllianceWithVarkaKetra()
	{
		return _appearanceBroadcast.getAllianceWithVarkaKetra();
	}

	public boolean isAlliedWithVarka()
	{
		return _appearanceBroadcast.isAlliedWithVarka();
	}

	public boolean isAlliedWithKetra()
	{
		return _appearanceBroadcast.isAlliedWithKetra();
	}
	
	/**
	 * 1. Add the specified class ID as a subclass (up to the maximum number of <b>three</b>) for this character.<BR>
	 * 2. This method no longer changes the active class index of the player. This is only done by the calling of setActiveClass() method as that should be the only way to do so.
	 * @param classId
	 * @param classIndex
	 * @return boolean subclassAdded
	 */
	public boolean addSubClass(int classId, int classIndex)
	{
		return _subClass.addSubClass(classId, classIndex);
	}
	
	/**
	 * 1. Completely erase all existance of the subClass linked to the classIndex.<BR>
	 * 2. Send over the newClassId to addSubClass()to create a new instance on this classIndex.<BR>
	 * 3. Upon Exception, revert the player to their BaseClass to avoid further problems.<BR>
	 * @param classIndex
	 * @param newClassId
	 * @return boolean subclassAdded
	 */
	public boolean modifySubClass(int classIndex, int newClassId)
	{
		return _subClass.modifySubClass(classIndex, newClassId);
	}
	
	public boolean isSubClassActive()
	{
		return _subClass.isSubClassActive();
	}
	
	public Map<Integer, SubClass> getSubClasses()
	{
		return _subClass.getSubClasses();
	}
	
	public int getBaseClass()
	{
		return _baseClass;
	}
	
	public int getActiveClass()
	{
		return _activeClass;
	}
	
	public int getClassIndex()
	{
		return _subClass.getClassIndex();
	}
	
	/** Apply class template; public for {@link PlayerSubClass#setActiveClass}. */
	public void setClassTemplate(int classId)
	{
		_activeClass = classId;
		
		setTemplate(PlayerData.getInstance().getTemplate(classId));
	}
	
	/**
	 * Changes the character's class based on the given class index. <BR>
	 * <BR>
	 * An index of zero specifies the character's original (base) class, while indexes 1-3 specifies the character's sub-classes respectively.
	 * @param classIndex
	 * @return true if successful.
	 */
	public boolean setActiveClass(int classIndex)
	{
		return _subClass.setActiveClass(classIndex);
	}
	
	public boolean isLocked()
	{
		return _subClass.isLocked();
	}
	
	public void onPlayerEnter()
	{

		if (isCursedWeaponEquipped())
			CursedWeaponManager.getInstance().getCursedWeapon(getCursedWeaponEquippedId()).cursedOnLogin();

		if (!isGM() && !ConfigProject.CATACOMBS_IN_ANY_PERIOD && isIn7sDungeon())
		{
			if (SevenSignsManager.getInstance().isSealValidationPeriod() || SevenSignsManager.getInstance().isCompResultsPeriod())
			{
				if (SevenSignsManager.getInstance().getPlayerCabal(getObjectId()) != SevenSignsManager.getInstance().getWinningCabal())
				{
					teleportTo(RestartType.TOWN);
					setIsIn7sDungeon(false);
				}
			}
			else if (SevenSignsManager.getInstance().getPlayerCabal(getObjectId()) == CabalType.NORMAL)
			{
				teleportTo(RestartType.TOWN);
				setIsIn7sDungeon(false);
			}
		}

		_punishment.handle();

		if (isGM())
		{
			if (isInvul())
				sendMessage(getSysString(10_014));

			if (!getAppearance().isVisible())
				sendMessage(getSysString(10_015));

			if (isBlockingAll())
				sendMessage(getSysString(10_016));
		}

		revalidateZone(true);

		RelationManager.getInstance().notifyFriends(this, true);
		AutoFarmManager.getInstance().onPlayerLogin(this);
		PlayerListenerManager.getInstance().notifyPlayerEnter(this);
		QuestKillState.load(this);

		showLanguageMenuOnLogin();
	}
	
	public long getLastAccess()
	{
		return _session.getLastAccess();
	}
	
	@Override
	public void doRevive()
	{
		_combat.doRevive();
	}

	@Override
	public void doRevive(double revivePower)
	{
		_combat.doRevive(revivePower);
	}
	
	public void reviveRequest(Player reviver, L2Skill skill, boolean isPet)
	{
		_deathLifecycle.reviveRequest(reviver, skill, isPet);
	}

	public void reviveAnswer(int answer)
	{
		_deathLifecycle.reviveAnswer(answer);
	}
	
	public boolean isReviveRequested()
	{
		return (getReviveRequested() == 1);
	}
	
	public boolean isRevivingPet()
	{
		return isRevivePet();
	}
	
	public void removeReviving()
	{
		setReviveRequested(0);
		setRevivePower(0);
	}
	
	public void onActionRequest()
	{
		if (isSpawnProtected())
		{
			sendMessage(getSysString(10_017));
			setSpawnProtection(false);
		}
	}
	
	@Override
	public final void onTeleported()
	{
		super.onTeleported();
		
		if (ConfigPlayers.PLAYER_SPAWN_PROTECTION > 0)
			setSpawnProtection(true);
		
		if (getTamedBeast() != null)
			getTamedBeast().teleportTo(getPosition(), 0);
		
		if (getSummon() != null)
			getSummon().teleportTo(getPosition(), 0);
		
		if (isInStoreMode())
			setOperateType(OperateType.NONE);
		
		CTFEvent.getInstance().onTeleported(this);
		DMEvent.getInstance().onTeleported(this);
		LMEvent.getInstance().onTeleported(this);
		TvTEvent.getInstance().onTeleported(this);
	}
	
	@Override
	public void addExpAndSp(long addToExp, int addToSp)
	{
		if (getStopExp())
			getStatus().addExpAndSp(addToExp, addToSp);
		else
			getStatus().addExpAndSp(0, addToSp);
	}
	
	public void addExpAndSp(long addToExp, int addToSp, Map<Creature, RewardInfo> rewards)
	{
		if (getStopExp())
			getStatus().addExpAndSp(addToExp, addToSp, rewards);
		else
			getStatus().addExpAndSp(0, addToSp, rewards);
	}
	
	public void removeExpAndSp(long removeExp, int removeSp)
	{
		getStatus().removeExpAndSp(removeExp, removeSp);
	}
	
	@Override
	public void reduceCurrentHp(double value, Creature attacker, boolean awake, boolean isDOT, L2Skill skill)
	{
		_combat.reduceCurrentHp(value, attacker, awake, isDOT, skill);
	}
	
	public synchronized void addBypass(String bypass)
	{
		_bypass.addBypass(bypass);
	}
	
	public synchronized void addBypass2(String bypass)
	{
		_bypass.addBypass2(bypass);
	}
	
	public synchronized boolean validateBypass(String cmd)
	{
		return _bypass.validateBypass(cmd);
	}
	
	/**
	 * Test cases (player drop, trade item) where the item shouldn't be able to manipulate.
	 * @param objectId : The item objectId.
	 * @return true if it the item can be manipulated, false ovtherwise.
	 */
	public ItemInstance validateItemManipulation(int objectId)
	{
		return _inventoryAccess.validateItemManipulation(objectId);
	}
	
	public synchronized void clearBypass()
	{
		_bypass.clearBypass();
	}
	
	public void setCrystallizing(boolean mode)
	{
		_operate.setCrystallizing(mode);
	}
	
	public boolean isCrystallizing()
	{
		return _operate.isCrystallizing();
	}
	
	/**
	 * Manage the delete task of a Player (Leave Party, Unsummon pet, Save its inventory in the database, Remove it from the world...).
	 * <ul>
	 * <li>If the Player is in observer mode, set its position to its position before entering in observer mode</li>
	 * <li>Set the online Flag to True or False and update the characters table of the database with online status and lastAccess</li>
	 * <li>Stop the HP/MP/CP Regeneration task</li>
	 * <li>Cancel Crafting, Attak or Cast</li>
	 * <li>Remove the Player from the world</li>
	 * <li>Stop Party and Unsummon Pet</li>
	 * <li>Update database with items in its inventory and remove them from the world</li>
	 * <li>Remove the object from region</li>
	 * <li>Close the connection with the client</li>
	 * </ul>
	 */
	@Override
	public void deleteMe()
	{
		super.deleteMe();
		
		if (getMountType() == 2 && isInsideZone(ZoneId.NO_LANDING))
			teleportTo(RestartType.TOWN);
		PlayerListenerManager.getInstance().notifyPlayerExit(this);
		
		cleanup();
		store();
	}
	
	private synchronized void cleanup()
	{
		try
		{

			setOnlineStatus(false, true);

			abortAll(true);

			removeMeFromPartyMatch();

			if (isFlying())
				removeSkill(FrequentSkill.WYVERN_BREATH.getSkill().getId(), false);

			if (isMounted())
				dismount();
			else if (getSummon() != null)
				getSummon().unSummon(this);

			stopChargeTask();

			_punishment.stopTask(true);

			WaterTaskManager.getInstance().remove(this);
			AttackStanceTaskManager.getInstance().remove(this);
			PvpFlagTaskManager.getInstance().remove(this, false);
			ShadowItemTaskManager.getInstance().remove(this);

			for (Quest quest : ScriptData.getInstance().getQuests())
				quest.cancelQuestTimers(this);

			forEachKnownType(Creature.class, creature -> creature.getFusionSkill() != null && creature.getFusionSkill().getTarget() == this, creature -> creature.getCast().stop());

			for (final AbstractEffect effect : getAllEffects())
			{
				if (effect.getSkill().isToggle())
				{
					effect.exit();
					continue;
				}

				switch (effect.getEffectType())
				{
					case SIGNET_GROUND, SIGNET_EFFECT:
						effect.exit();
						break;
				}
			}

			decayMe();

			if (getParty() != null)
				getParty().removePartyMember(this, MessageType.DISCONNECTED);

			if (OlympiadManager.getInstance().isRegistered(this) || getOlympiadGameId() != -1)
				OlympiadManager.getInstance().removeDisconnectedCompetitor(this);

			if (getClan() != null)
			{
				final ClanMember clanMember = getClan().getClanMember(getObjectId());
				if (clanMember != null)
					clanMember.setPlayerInstance(null);
			}

			if (getActiveRequester() != null)
			{
				setActiveRequester(null);
				cancelActiveTrade();
			}

			if (isGM())
				AdminData.getInstance().deleteGm(this);

			if (isInObserverMode())
				setXYZInvisible(_savedLocation);

			CTFEvent.getInstance().onLogout(this);
			DMEvent.getInstance().onLogout(this);
			LMEvent.getInstance().onLogout(this);
			TvTEvent.getInstance().onLogout(this);

			getInventory().deleteMe();

			clearWarehouse();

			clearFreight();
			clearDepositedFreight();

			if (isCursedWeaponEquipped())
				CursedWeaponManager.getInstance().getCursedWeapon(getCursedWeaponEquippedId()).setPlayer(null);

			if (getClan() != null)
				getClan().broadcastToMembersExcept(this, new PledgeShowMemberListUpdate(this));

			if (isSeated())
			{
				final WorldObject object = World.getInstance().getObject(getThroneId());
				if (object instanceof StaticObject staticObject)
					staticObject.setBusy(false);
			}

			RelationManager.getInstance().notifyFriends(this, false);

			AutoFarmManager.getInstance().stopPlayer(this, null);

			World.getInstance().removePlayer(this);
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't disconnect correctly the player.", e);
		}
	}
	
	public FishingStance getFishingStance()
	{
		return _fishingStance;
	}
	
	public int getMountNpcId()
	{
		return _mount.getMountNpcId();
	}

	public int getMountLevel()
	{
		return _mount.getMountLevel();
	}

	public void setMountObjectId(int id)
	{
		_mount.setMountObjectId(id);
	}

	public int getMountObjectId()
	{
		return _mount.getMountObjectId();
	}
	
	@Override
	public Map<Integer, L2Skill> getSkills()
	{
		return _skillManager.getSkills();
	}
	
	public Punishment getPunishment()
	{
		return _punishment;
	}
	
	public RecipeBook getRecipeBook()
	{
		return _recipeBook;
	}
	
	/**
	 * @return true if the {@link Player} is jailed, false otherwise.
	 */
	public boolean isInJail()
	{
		return _punishment.getType() == PunishmentType.JAIL;
	}
	
	/**
	 * @return true if the {@link Player} is chat banned, false otherwise.
	 */
	public boolean isChatBanned()
	{
		return _punishment.getType() == PunishmentType.CHAT;
	}
	
	public int getPowerGrade()
	{
		return _clanComponent.getPowerGrade();
	}
	
	public void setPowerGrade(int power)
	{
		_clanComponent.setPowerGrade(power);
	}
	
	public boolean isCursedWeaponEquipped()
	{
		return _cursedWeapon.isEquipped();
	}
	
	public void setCursedWeaponEquippedId(int value)
	{
		_cursedWeapon.setCursedWeaponEquippedId(value);
	}
	
	public int getCursedWeaponEquippedId()
	{
		return _cursedWeapon.getCursedWeaponEquippedId();
	}
	
	public void shortBuffStatusUpdate(int magicId, int level, int time)
	{
		_skillManager.shortBuffStatusUpdate(magicId, level, time);
	}

	public int getShortBuffTaskSkillId()
	{
		return _skillManager.getShortBuffTaskSkillId();
	}

	public void setShortBuffTaskSkillId(int id)
	{
		_skillManager.setShortBuffTaskSkillId(id);
	}
	
	public int getDeathPenaltyBuffLevel()
	{
		return _death.getDeathPenaltyBuffLevel();
	}
	
	public void setDeathPenaltyBuffLevel(int level)
	{
		_death.setDeathPenaltyBuffLevel(level);
	}
	
	/**
	 * Check and calculate if a new Death Penalty buff level needs to be added. If Death Penalty already applies, raise its level by 1.
	 * @param killer : The {@link Creature} who killed this {@link Player}.
	 */
	public void calculateDeathPenaltyBuffLevel(Creature killer)
	{
		_combat.calculateDeathPenaltyBuffLevel(killer);
	}

	/**
	 * Reduce the Death Penalty buff effect from this {@link Player} of 1. If it reaches 0, remove it entirely.
	 */
	public void reduceDeathPenaltyBuffLevel()
	{
		_combat.reduceDeathPenaltyBuffLevel();
	}

	/**
	 * Remove the Death Penalty buff effect from this {@link Player}.
	 */
	public void removeDeathPenaltyBuffLevel()
	{
		_combat.removeDeathPenaltyBuffLevel();
	}
	
	public Collection<Timestamp> getReuseTimeStamps()
	{
		return _skillManager.getReuseTimeStamps();
	}

	public Map<Integer, Timestamp> getReuseTimeStamp()
	{
		return _skillManager.getReuseTimeStamp();
	}

	/**
	 * Index according to skill id the current timestamp of use.
	 * @param skill
	 * @param reuse delay
	 */
	@Override
	public void addTimeStamp(L2Skill skill, long reuse)
	{
		_skillManager.addTimeStamp(skill, reuse);
	}

	/**
	 * Index according to skill this TimeStamp instance for restoration purposes only.
	 * @param skill
	 * @param reuse
	 * @param systime
	 */
	public void addTimeStamp(L2Skill skill, long reuse, long systime)
	{
		_skillManager.addTimeStamp(skill, reuse, systime);
	}

	/**
	 * Remove the reuse timestamp entry for the given skill, if any.
	 * @param skill the skill.
	 */
	public void removeTimeStamp(L2Skill skill)
	{
		_skillManager.removeTimeStamp(skill);
	}
	
	@Override
	public Player getActingPlayer()
	{
		return this;
	}
	
	@Override
	public final void sendDamageMessage(Creature target, int damage, boolean mcrit, boolean pcrit, boolean miss)
	{
		if (miss)
		{
			sendPacket(SystemMessageId.MISSED_TARGET);
			return;
		}
		
		if (pcrit)
			sendPacket(SystemMessageId.CRITICAL_HIT);
		if (mcrit)
			sendPacket(SystemMessageId.CRITICAL_HIT_MAGIC);
		
		if (target.isInvul())
		{
			if (target.isParalyzed())
				sendPacket(SystemMessageId.OPPONENT_PETRIFIED);
			else
				sendPacket(SystemMessageId.ATTACK_WAS_BLOCKED);
		}
		else
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_DID_S1_DMG).addNumber(damage));
		
		if (isInOlympiadMode() && target instanceof Player targetPlayer && targetPlayer.isInOlympiadMode() && targetPlayer.getOlympiadGameId() == getOlympiadGameId())
			OlympiadGameManager.getInstance().notifyCompetitorDamage(this, damage);
		
		CreatureListenerManager.getInstance().notifyAttackHit(this, target);
		
	}
	
	public void checkItemRestriction()
	{
		for (final ItemInstance item : getInventory().getPaperdollItems())
		{
			if (item.getItem().checkCondition(this, this, false))
				continue;
			
			useEquippableItem(item, item.isWeapon());
		}
	}
	
	/**
	 * A method used to test player entrance on no landing zone.<br>
	 * <br>
	 * If a player is mounted on a Wyvern, it launches a dismount task after 5 seconds, and a warning message.
	 */
	public void enterOnNoLandingZone()
	{
		_mount.enterOnNoLandingZone();
	}
	
	/**
	 * A method used to test player leave on no landing zone.<br>
	 * <br>
	 * If a player is mounted on a Wyvern, it cancels the dismount task, if existing.
	 */
	public void exitOnNoLandingZone()
	{
		_mount.exitOnNoLandingZone();
	}
	
	/**
	 * Remove player from BossZones (used on char logout/exit)
	 */
	public void removeFromBossZone()
	{
		for (final BossZone zone : ZoneManager.getInstance().getAllZones(BossZone.class))
			zone.removePlayer(this);
	}
	
	/**
	 * @return the number of charges this Player got.
	 */
	public int getCharges()
	{
		return _charge.getCharges();
	}
	
	public void increaseCharges(int count, int max)
	{
		_charge.increaseCharges(count, max);
	}
	
	public boolean decreaseCharges(int count)
	{
		return _charge.decreaseCharges(count);
	}
	
	public void clearCharges()
	{
		_charge.clearCharges();
	}
	
	/**
	 * Starts/Restarts the ChargeTask to Clear Charges after 10 Mins.
	 */

	
	/**
	 * Stops the Charges Clearing Task.
	 */
	public void stopChargeTask()
	{
		_charge.stopChargeTask();
	}
	
	public int getMailPosition()
	{
		return _uiPrefs.getMailPosition();
	}
	
	public void setMailPosition(int mailPosition)
	{
		_uiPrefs.setMailPosition(mailPosition);
	}
	
	public boolean temporaryFixPagan()
	{
		if (isInsideZone(ZoneId.PAGAN))
			return true;
		
		return false;
	}
	
	/**
	 * @param z
	 * @return true if character falling now On the start of fall return false for correct coord sync !
	 */
	public final boolean isFalling(int z)
	{
		if (isDead() || getMove().getMoveType() != MoveType.GROUND)
			return false;
		
		if (temporaryFixPagan())
			return false;
		
		if (System.currentTimeMillis() < _fallingTimestamp)
			return true;
		
		final int deltaZ = getZ() - z;
		if (deltaZ <= getBaseTemplate().getSafeFallHeight(getAppearance().getSex()))
			return false;
		
		final int damage = (int) Formulas.calcFallDam(this, deltaZ, getBaseTemplate().getSafeFallHeight(getAppearance().getSex()));
		if (damage > 0)
		{
			reduceCurrentHp(Math.min(damage, getStatus().getHp() - 1), null, false, true, null);
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.FALL_DAMAGE_S1).addNumber(damage));
		}
		
		setFalling();
		return false;
	}
	
	/**
	 * Set falling timestamp
	 */
	public final void setFalling()
	{
		_fallingTimestamp = System.currentTimeMillis() + FALLING_VALIDATION_DELAY;
	}
	
	public boolean isAllowedToEnchantSkills()
	{
		if (isLocked())
			return false;
		
		if (AttackStanceTaskManager.getInstance().isInAttackStance(this))
			return false;
		
		if (getCast().isCastingNow())
			return false;
		
		return !getBoatInfo().isInBoat();
	}
	
	@Override
	public void broadcastRelationsChanges()
	{
		forEachKnownType(Player.class, player ->
		{
			final int relation = getRelation(player);
			final boolean isAutoAttackable = isAttackableWithoutForceBy(player);
			
			player.sendPacket(new RelationChanged(this, relation, isAutoAttackable));
			if (getSummon() != null)
				player.sendPacket(new RelationChanged(getSummon(), relation, isAutoAttackable));
		});
	}
	
	@Override
	public void sendInfo(Player player)
	{
		if (!isVisibleTo(player))
			return;
		
		if (getPolymorphTemplate() != null)
			player.sendPacket(new AbstractNpcInfo.PcMorphInfo(this, getPolymorphTemplate()));
		else
		{
			player.sendPacket(new CharInfo(this));
			
			if (isSeated())
			{
				final WorldObject object = World.getInstance().getObject(getThroneId());
				if (object instanceof StaticObject staticObject)
					player.sendPacket(new ChairSit(getObjectId(), staticObject.getStaticObjectId()));
			}
		}
		
		int relation = getRelation(player);
		boolean isAutoAttackable = isAttackableWithoutForceBy(player);
		
		player.sendPacket(new RelationChanged(this, relation, isAutoAttackable));
		if (getSummon() != null)
			player.sendPacket(new RelationChanged(getSummon(), relation, isAutoAttackable));
		
		if (player.isVisibleTo(this))
		{
			relation = player.getRelation(this);
			isAutoAttackable = player.isAttackableWithoutForceBy(this);
			
			sendPacket(new RelationChanged(player, relation, isAutoAttackable));
			if (player.getSummon() != null)
				sendPacket(new RelationChanged(player.getSummon(), relation, isAutoAttackable));
		}
		
		switch (getOperateType())
		{
			case SELL, PACKAGE_SELL:
				player.sendPacket(new PrivateStoreMsgSell(this));
				break;
			
			case BUY:
				player.sendPacket(new PrivateStoreMsgBuy(this));
				break;
			
			case MANUFACTURE:
				player.sendPacket(new RecipeShopMsg(this));
				break;
		}
		
		getBoatInfo().sendInfo(player);
	}
	
	@Override
	public double getCollisionRadius()
	{
		return _appearanceBroadcast.getCollisionRadius();
	}

	@Override
	public double getCollisionHeight()
	{
		return _appearanceBroadcast.getCollisionHeight();
	}
	
	public boolean teleportRequest(Player requester, L2Skill skill)
	{
		if (getSummonTargetRequest() != null && requester != null)
			return false;
		
		setSummonTargetRequest(requester);
		setSummonSkillRequest(skill);
		return true;
	}
	
	public void teleportAnswer(int answer, int requesterId)
	{
		if (getSummonTargetRequest() == null)
			return;
		
		if (answer == 1 && getSummonTargetRequest().getObjectId() == requesterId)
			SummonFriend.teleportTo(this, getSummonTargetRequest(), getSummonSkillRequest());
		
		setSummonTargetRequest(null);
		setSummonSkillRequest(null);
	}
	
	public void activateGate(int answer, int type)
	{
		if (getRequestedGate() == null)
			return;
		
		if (answer == 1 && getTarget() == getRequestedGate() && getRequestedGate().canBeManuallyOpenedBy(this))
		{
			if (type == 1)
				getRequestedGate().openMe();
			else if (type == 0)
				getRequestedGate().closeMe();
		}
		
		setRequestedGate(null);
	}
	
	@Override
	public boolean polymorph(int npcId)
	{
		return _appearanceBroadcast.polymorph(npcId);
	}

	@Override
	public void unpolymorph()
	{
		_appearanceBroadcast.unpolymorph();
	}
	
	@Override
	public void addKnownObject(WorldObject object)
	{
		sendInfoFrom(object);
	}
	
	@Override
	public void removeKnownObject(WorldObject object)
	{
		super.removeKnownObject(object);
		
		if (isTeleporting())
			return;
		
		if (object.isVisibleTo(this))
			sendPacket(new DeleteObject(object, object instanceof Player player && player.isSeated()));
	}
	
	public final void refreshInfos()
	{
		forEachKnownType(WorldObject.class, object ->
		{
			if (object instanceof Player player && player.isInObserverMode())
				return;
			
			if (!object.isVisibleTo(this))
				return;
			
			sendInfoFrom(object);
		});
	}
	
	/**
	 * teleToLocation method without Dimensional Rift check.
	 * @param loc : The Location to teleport.
	 */
	public final void teleToLocation(Location loc)
	{
		super.teleportTo(loc, 0);
	}
	
	private final void sendInfoFrom(WorldObject object)
	{
		object.sendInfo(this);
		
		if (object instanceof Creature creature && creature.isVisibleTo(this))
			creature.getAI().describeStateToPlayer(this);
	}
	
	/**
	 * @return true if this {@link Player} is currently wearing a Formal Wear.
	 */
	public boolean isWearingFormalWear()
	{
		return _appearanceBroadcast.isWearingFormalWear();
	}
	
	public final void startFakeDeath()
	{
		setIsFakeDeath(true);
		getAI().notifyEvent(AiEventType.SAT_DOWN, null, null);
		broadcastPacket(new ChangeWaitType(this, ChangeWaitType.WT_START_FAKEDEATH));
	}
	
	public final void stopFakeDeath(boolean removeEffects)
	{
		if (removeEffects)
			stopEffects(EffectType.FAKE_DEATH);
		
		setIsFakeDeath(false);
		setRecentFakeDeath();
		
		getAI().notifyEvent(AiEventType.STOOD_UP, null, null);
		broadcastPacket(new ChangeWaitType(this, ChangeWaitType.WT_STOP_FAKEDEATH));
		broadcastPacket(new Revive(this));
	}
	
	public final void setIsFakeDeath(boolean value)
	{
		_death.setFakeDeath(value);
	}
	
	@Override
	public void onInteract(Player player)
	{
		switch (getOperateType())
		{
			case SELL, PACKAGE_SELL:
				player.sendPacket(new PrivateStoreListSell(player, this));
				break;
			
			case BUY:
				player.sendPacket(new PrivateStoreListBuy(player, this));
				break;
			
			case MANUFACTURE:
				player.sendPacket(new RecipeShopSellList(player, this));
				break;
		}
	}
	
	@Override
	public void checkCondition(double curHp, double newHp)
	{
		byte[] _hp =
		{
			30,
			30,
		};
		
		short[] _skills =
		{
			290,
			291,
		};
		
		short[] _effectsSkillsId =
		{
			292,
			292
		};
		
		byte[] _effectsHp =
		{
			30,
			60
		};
		
		final double percent = getStatus().getMaxHp() / 100;
		final double _curHpPercent = curHp / percent;
		final double _newHpPercent = newHp / percent;
		boolean needsUpdate = false;
		
		for (int i = 0; i < _skills.length; i++)
		{
			if (getSkillLevel(_skills[i]) > 0)
			{
				if (_curHpPercent > _hp[i] && _newHpPercent <= _hp[i])
				{
					sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_HP_DECREASED_EFFECT_APPLIES).addSkillName(_skills[i]));
					needsUpdate = true;
				}
				else if (_curHpPercent <= _hp[i] && _newHpPercent > _hp[i])
				{
					sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_HP_INCREASED_EFFECT_DISAPPEARS).addSkillName(_skills[i]));
					needsUpdate = true;
				}
			}
		}
		
		for (int i = 0; i < _effectsSkillsId.length; i++)
		{
			if (getFirstEffect(_effectsSkillsId[i]) != null)
			{
				if (_curHpPercent > _effectsHp[i] && _newHpPercent <= _effectsHp[i])
				{
					sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_HP_DECREASED_EFFECT_APPLIES).addSkillName(_effectsSkillsId[i]));
					needsUpdate = true;
				}
				else if (_curHpPercent <= _effectsHp[i] && _newHpPercent > _effectsHp[i])
				{
					sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_HP_INCREASED_EFFECT_DISAPPEARS).addSkillName(_effectsSkillsId[i]));
					needsUpdate = true;
				}
			}
		}
		
		if (needsUpdate)
			broadcastUserInfo();
	}
	
	public CachedData getCachedData()
	{
		return _cachedData;
	}
	
	public int getNameColor()
	{
		return _profile.getNameColor();
	}

	public int setNameColor(int value)
	{
		return _profile.setNameColor(value);
	}

	public int getTitleColor()
	{
		return _profile.getTitleColor();
	}

	public int setTitleColor(int value)
	{
		return _profile.setTitleColor(value);
	}

	public void setStopExp(boolean value)
	{
		_profile.setStopExp(value);
	}

	public boolean getStopExp()
	{
		return _profile.getStopExp();
	}

	public void setTradeRefusal(boolean value)
	{
		_profile.setTradeRefusal(value);
	}

	public boolean getTradeRefusal()
	{
		return _profile.getTradeRefusal();
	}

	public void setAutoLoot(boolean value)
	{
		_profile.setAutoLoot(value);
	}

	public boolean getAutoLoot()
	{
		return _profile.getAutoLoot();
	}

	public void setBuffProtected(boolean value)
	{
		_profile.setBuffProtected(value);
	}

	public boolean isBuffProtected()
	{
		return _profile.isBuffProtected();
	}

	public Locale getLocale()
	{
		return _profile.getLocale();
	}

	public String getSysString(int id, Object... args)
	{
		return _profile.getSysString(id, args);
	}

	public void setLocale(Locale locale)
	{
		_profile.setLocale(locale);
	}
	
	public long getOfflineStartTime()
	{
		return _offline.getOfflineStartTime();
	}
	
	public void setOfflineStartTime(long time)
	{
		_offline.setOfflineStartTime(time);
	}
	
	public void createPSdb()
	{
		_premium.createPSdb();
	}
	
	public static void psTimeOver(String account)
	{
		PlayerPremium.psTimeOver(account);
	}
	
	public long getPremServiceData()
	{
		return _premium.getPremServiceData();
	}
	
	public void restorePremServiceData(Player player, String account)
	{
		player.getPremium().restorePremServiceData(player, account);
	}
	
	public void setHeroUntil(long val)
	{
		_hero.setHeroUntil(val);
	}
	
	public long getHeroUntil()
	{
		return _hero.getHeroUntil();
	}
	
	public int getPointScore()
	{
		return _uiPrefs.getPointScore();
	}
	
	public void increasePointScore()
	{
		_uiPrefs.increasePointScore();
	}
	
	public void clearPoints()
	{
		_uiPrefs.clearPoints();
	}
	
	public void saveTradeList()
	{
		_privateStore.saveTradeList();
	}
	
	public void restoreStoreList()
	{
		_privateStore.restoreStoreList();
	}
	

	

	

	

	
	/**
	 * @return the cafe points of the player.
	 */
	public int getPcCafePoints()
	{
		return _pcCafe.getPcCafePoints();
	}
	
	public void increasePcCafePoints(int count)
	{
		_pcCafe.increasePcCafePoints(count);
	}
	
	public void increasePcCafePoints(int count, boolean doubleAmount)
	{
		_pcCafe.increasePcCafePoints(count, doubleAmount);
	}
	
	public void decreasePcCafePoints(int count)
	{
		_pcCafe.decreasePcCafePoints(count);
	}
	
	/**
	 * Added to other GMs, test also this {@link Player} instance. If GM, set it.
	 */
	@Override
	public void forEachKnownGM(Consumer<Player> action)
	{
		super.forEachKnownGM(action);
		
		action.accept(this);
	}
	
	@Override
	public void sendIU()
	{
		if (_inventory.getUpdateList().isEmpty())
			return;
		
		sendPacket(new InventoryUpdate(this));
	}
	
	public BoatInfo getBoatInfo()
	{
		return _boat.getBoatInfo();
	}
	
	@Override
	public boolean isInBoat()
	{
		return _boat.isInBoat();
	}
	
	public boolean isBlockingAll()
	{
		return _socialManager.isBlockingAll();
	}

	public void setInBlockingAll(boolean isBlockingAll)
	{
		_socialManager.setInBlockingAll(isBlockingAll);
	}

	public void selectFriend(int friendId)
	{
		_socialManager.selectFriend(friendId);
	}

	public void deselectFriend(int friendId)
	{
		_socialManager.deselectFriend(friendId);
	}

	public Set<Integer> getSelectedFriendList()
	{
		return _socialManager.getSelectedFriendList();
	}

	public void selectBlock(int friendId)
	{
		_socialManager.selectBlock(friendId);
	}

	public void deselectBlock(int friendId)
	{
		_socialManager.deselectBlock(friendId);
	}

	public Set<Integer> getSelectedBlocksList()
	{
		return _socialManager.getSelectedBlocksList();
	}
	
	public void stopToFight()
	{
		getCast().stop();
		getAI().tryToIdle();
		setTarget(null);
		sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	public int isAcpCp()
	{
		return _acpCp.get();
	}
	
	public void setAcpCp(int value)
	{
		_acpCp.set(value);
	}
	
	public int isAcpHp()
	{
		return _acpHp.get();
	}
	
	public void setAcpHp(int value)
	{
		_acpHp.set(value);
	}
	
	public int isAcpMp()
	{
		return _acpMp.get();
	}
	
	public void setAcpMp(int value)
	{
		_acpMp.set(value);
	}
	
	public boolean isSellingBuffs()
	{
		return _sellBuff.isSellingBuffs();
	}
	
	public String getSellBuffList()
	{
		return _sellBuff.getSellBuffList();
	}
	
	public String setSellBuffList(String value)
	{
		return _sellBuff.setSellBuffList(value);
	}
	
	public void setSellingBuffs(boolean value)
	{
		_sellBuff.setSellingBuffs(value);
	}
	
	public List<SellBuffHolder> getSellingBuffs()
	{
		return _sellBuff.getSellingBuffs();
	}
	

	
	public void saveSellingBuffs()
	{
		_sellBuff.saveSellingBuffs();
	}
	
	public MissionList getMissions()
	{
		return _missionList;
	}
	
	// att-ver-3.0 wave7: feature state via *State APIs (no Player accessors)

	
	public String getHWid()
	{
		return _hwidComponent.getHWid();
	}
	
	public void setPrivateStoreType(PrivateStoreType type)
	{
		_privateStore.setPrivateStoreType(type);
	}
	
	public PrivateStoreType getPrivateStoreType()
	{
		return _privateStore.getPrivateStoreType();
	}
	
	private void showLanguageMenuOnLogin()
	{
		ThreadPool.schedule(() ->
		{
			if (isOnline() && !isGM() && TranslatorState.isApiActive())
				TranslatorState.showLanguageMenu(this);
		}, 3000);
	}
	
	public boolean hasClan()
	{
		return _clanComponent.hasClan();
	}
	
	public int getLevel()
	{
		final SubClass subClass = _subClass.get(_subClass.getClassIndex());
		if (subClass == null)
			return 1;
		return subClass.getLevel();
	}

	public int getRequesterId()
	{
		return _social.getRequesterId();
	}
	
	public Player getSummonTargetRequest()
	{
		return _social.getSummonTargetRequest();
	}
	
	public void setSummonTargetRequest(Player target)
	{
		_social.setSummonTargetRequest(target);
	}
	
	public L2Skill getSummonSkillRequest()
	{
		return _social.getSummonSkillRequest();
	}
	
	public void setSummonSkillRequest(L2Skill skill)
	{
		_social.setSummonSkillRequest(skill);
	}
	
	public Door getRequestedGate()
	{
		return _social.getRequestedGate();
	}
	
	public void setRequestedGate(Door door)
	{
		_social.setRequestedGate(door);
	}
	
	public int getReviveRequested()
	{
		return _death.getReviveRequested();
	}
	
	public void setReviveRequested(int value)
	{
		_death.setReviveRequested(value);
	}
	
	public double getRevivePower()
	{
		return _death.getRevivePower();
	}
	
	public void setRevivePower(double value)
	{
		_death.setRevivePower(value);
	}
	
	public boolean isRevivePet()
	{
		return _death.isRevivePet();
	}
	
	public void setRevivePet(boolean value)
	{
		_death.setRevivePet(value);
	}
}
