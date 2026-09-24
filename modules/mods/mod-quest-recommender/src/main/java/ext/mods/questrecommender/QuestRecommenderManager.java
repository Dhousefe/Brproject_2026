package ext.mods.questrecommender;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import ext.mods.Config;
import ext.mods.commons.config.ExProperties;
import ext.mods.commons.logging.CLogger;
import ext.mods.commons.pool.ThreadPool;
import ext.mods.extensions.listener.actor.player.OnLevelUpListener;
import ext.mods.extensions.listener.command.OnBypassCommandListener;
import ext.mods.extensions.listener.manager.BypassCommandManager;
import ext.mods.extensions.listener.manager.PlayerListenerManager;
import ext.mods.extensions.hooks.LevelUpMakerHooks;
import ext.mods.gameserver.data.HTMLData;
import ext.mods.gameserver.data.xml.ItemData;
import ext.mods.gameserver.data.xml.NpcData;
import ext.mods.gameserver.data.xml.ScriptData;
import ext.mods.gameserver.enums.EventHandler;
import ext.mods.gameserver.enums.actors.ClassId;
import ext.mods.gameserver.enums.actors.ClassRace;
import ext.mods.gameserver.model.World;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.template.NpcTemplate;
import ext.mods.gameserver.model.item.kind.Item;
import ext.mods.gameserver.model.location.Location;
import ext.mods.gameserver.network.serverpackets.ActionFailed;
import ext.mods.gameserver.network.serverpackets.NpcHtmlMessage;
import ext.mods.gameserver.network.serverpackets.TutorialShowQuestionMark;
import ext.mods.gameserver.scripting.Quest;
import ext.mods.gameserver.scripting.QuestState;
import ext.mods.questrecommender.navigation.QuestNavigationService;
import ext.mods.questrecommender.navigation.QuestPathVisualizerService;
import ext.mods.questrecommender.vector.QuestMetadata;
import ext.mods.questrecommender.vector.QuestRewardItem;
import ext.mods.questrecommender.vector.QuestVectorEngine;

/**
 * Orquestrador central do Quest Recommender.
 *
 * Mecânica alinhada com LevelUpMaker:
 * - Janela com Quest Mark tutorial.
 * - Recomendações vetoriais sob medida com zero GC overhead.
 * - Suporte a navegação por radar (<5000 range ou mesma cidade) e teleporte para a cidade mais próxima (>5000 range).
 */
public final class QuestRecommenderManager implements OnBypassCommandListener, OnLevelUpListener
{
	private static final CLogger LOGGER = new CLogger(QuestRecommenderManager.class.getName());

	public static final String BYPASS_PREFIX = "questrec_";
	public static final String BYPASS_SHOW = "questrec_show";
	public static final String BYPASS_NAVIGATE = "questrec_nav ";
	public static final String BYPASS_CLOSE = "questrec_close";

	private static final QuestRecommenderManager INSTANCE = new QuestRecommenderManager();

	private boolean _enabled;
	private int _questionMarkId;
	private int _castTimeMs;
	private int _scrollSkillId;
	private int _scrollSkillLevel;
	private int _rangeLimit;
	private int _refreshIntervalSec;
	private int _levelUpDelaySec;
	private int _topK;
	private String _cannotTeleportMessage;

	private volatile ScheduledFuture<?> _refreshTask;
	private QuestVectorEngine _vectorEngine;
	private final List<QuestMetadata> _questCatalog = new ArrayList<>();

	public static QuestRecommenderManager getInstance()
	{
		return INSTANCE;
	}

	private QuestRecommenderManager()
	{
	}

	public void init()
	{
		loadConfig();
		if (!_enabled)
		{
			LOGGER.info("QuestRecommender is disabled in config.");
			return;
		}

		buildQuestIndex();
		BypassCommandManager.getInstance().registerBypassListener(this);
		PlayerListenerManager.getInstance().registerLevelUpListener(this);
		startRefreshTask();

		LOGGER.info("QuestRecommender initialized. QuestionMarkId={}, TotalIndexed={}, RangeLimit={}",
			_questionMarkId, _questCatalog.size(), _rangeLimit);
	}

	public void shutdown()
	{
		if (_refreshTask != null)
		{
			_refreshTask.cancel(false);
			_refreshTask = null;
		}
		BypassCommandManager.getInstance().unregisterBypassListener(this);
		PlayerListenerManager.getInstance().unregisterLevelUpListener(this);
	}

	private void loadConfig()
	{
		final Path path = Config.CONFIG_PATH.resolve("questrecommender.properties");
		final ExProperties props = new ExProperties();
		if (Files.exists(path))
		{
			try (Reader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8))
			{
				props.load(reader);
			}
			catch (IOException e)
			{
				LOGGER.error("Failed to load questrecommender.properties. Using defaults.", e);
			}
		}

		_enabled = props.getProperty("QuestRecommenderEnabled", true);
		_questionMarkId = props.getProperty("QuestRecommenderQuestionMarkId", 2020);
		_castTimeMs = Math.max(1000, props.getProperty("QuestRecommenderCastTimeMs", 10000));
		_scrollSkillId = props.getProperty("QuestRecommenderScrollSkillId", 2040);
		_scrollSkillLevel = props.getProperty("QuestRecommenderScrollSkillLevel", 1);
		_rangeLimit = Math.max(1000, props.getProperty("QuestRecommenderRangeLimit", 5000));
		_refreshIntervalSec = Math.max(30, props.getProperty("QuestRecommenderRefreshIntervalSec", 90));
		_levelUpDelaySec = Math.max(0, props.getProperty("QuestRecommenderLevelUpDelaySec", 20));
		_topK = Math.max(1, props.getProperty("QuestRecommenderTopK", 4));
		_cannotTeleportMessage = props.getProperty("QuestRecommenderCannotTeleportMessage",
			"Você não pode teleportar no momento. Verifique se está em combate, karma ou evento.");

		final boolean visualizerEnabled = props.getProperty("QuestRecommenderVisualizerEnabled", true);
		final int pathDurationSec = props.getProperty("QuestRecommenderPathDurationSec", 60);
		final int arrowSpacing = props.getProperty("QuestRecommenderArrowSpacing", 450);
		QuestPathVisualizerService.getInstance().configure(visualizerEnabled, pathDurationSec, arrowSpacing);
	}

	/**
	 * Constrói o catálogo inicial e a matriz contígua de vetores para Mechanical Sympathy.
	 */
	/**
	 * Constrói o catálogo completo e a matriz contígua de vetores para Mechanical Sympathy.
	 * Combina quests curadas prioritárias com auto-descoberta dinâmica de 100% das quests do servidor.
	 */
	private void buildQuestIndex()
	{
		_questCatalog.clear();

		// 1. Quests clássicas e do Core com metadados curados prioritários
		addIndexedQuest(1, "Letters of Love", 2, 8, 30048, new Location(-84033, 243232, -3730), false, "Necklace + Adena / Exp",
			new QuestRewardItem[] { new QuestRewardItem(906, 1), new QuestRewardItem(57, 450) },
			new ClassRace[] { ClassRace.HUMAN }, null);
		addIndexedQuest(2, "What Women Want", 2, 7, 30223, new Location(11200, 16000, -4500), false, "Adena + Travel Goods",
			new QuestRewardItem[] { new QuestRewardItem(57, 450), new QuestRewardItem(1060, 5) },
			new ClassRace[] { ClassRace.ELF, ClassRace.HUMAN }, null);
		addIndexedQuest(3, "Will the Seal be Broken?", 16, 26, 30141, new Location(12100, 17200, -4400), false, "Adena + Enchant Scroll",
			new QuestRewardItem[] { new QuestRewardItem(57, 3200), new QuestRewardItem(956, 1) },
			new ClassRace[] { ClassRace.DARK_ELF }, null);
		addIndexedQuest(4, "Longlive the Pa'agrio Lord", 2, 6, 30578, new Location(-45200, -112500, -240), false, "Pa'agrio Amulet",
			new QuestRewardItem[] { new QuestRewardItem(1542, 1), new QuestRewardItem(57, 350) },
			new ClassRace[] { ClassRace.ORC }, null);
		addIndexedQuest(5, "Miner's Favor", 2, 6, 30554, new Location(115700, -178000, -900), false, "Adena + Pickaxe",
			new QuestRewardItem[] { new QuestRewardItem(57, 450), new QuestRewardItem(1060, 10) },
			new ClassRace[] { ClassRace.DWARF }, null);
		addIndexedQuest(15, "Sweet Whispers", 60, 75, 31302, new Location(82698, 148638, -3468), false, "Adena + High Exp",
			new QuestRewardItem[] { new QuestRewardItem(57, 15000) }, null, null);
		addIndexedQuest(16, "The Coming Darkness", 61, 75, 31517, new Location(147450, 27120, -2200), false, "Exp + SP Boost",
			new QuestRewardItem[] { new QuestRewardItem(57, 18000) }, null, null);
		addIndexedQuest(17, "Light and Darkness", 61, 75, 31517, new Location(147450, 27120, -2200), false, "Exp + SP Boost",
			new QuestRewardItem[] { new QuestRewardItem(57, 18000) }, null, null);
		addIndexedQuest(21, "Hidden Truth", 63, 75, 31522, new Location(33450, -53800, 2040), false, "Adena + Cross of Miracle",
			new QuestRewardItem[] { new QuestRewardItem(7140, 1), new QuestRewardItem(57, 25000) }, null, null);
		addIndexedQuest(38, "Dragon Fangs", 19, 29, 30386, new Location(18500, 145200, -3100), true, "Bone Helmet + Adena",
			new QuestRewardItem[] { new QuestRewardItem(43, 1), new QuestRewardItem(57, 5200) }, null, null);
		addIndexedQuest(39, "Red-Eyed Invaders", 20, 28, 30332, new Location(82500, 148500, -3460), true, "Materials + Adena",
			new QuestRewardItem[] { new QuestRewardItem(57, 6500) }, null, null);
		addIndexedQuest(101, "Sword of Solidarity", 9, 15, 30013, new Location(-84100, 243100, -3730), false, "Sword of Solidarity + Adena",
			new QuestRewardItem[] { new QuestRewardItem(738, 1), new QuestRewardItem(57, 10981) },
			new ClassRace[] { ClassRace.HUMAN }, null);
		addIndexedQuest(102, "Sea of Spores Fever", 12, 24, 30220, new Location(12150, 16800, -4500), false, "Adena + Sword of Sentinel",
			new QuestRewardItem[] { new QuestRewardItem(743, 1), new QuestRewardItem(57, 8500) },
			new ClassRace[] { ClassRace.ELF }, null);
		addIndexedQuest(107, "Merciless Punishment", 12, 20, 30010, new Location(-84200, 243300, -3730), false, "Wooden Helmet + Adena",
			new QuestRewardItem[] { new QuestRewardItem(43, 1), new QuestRewardItem(57, 3450) },
			new ClassRace[] { ClassRace.ORC }, null);
		addIndexedQuest(334, "The Wishing Potion", 30, 45, 30738, new Location(83100, 147900, -3400), true, "Random Rare Items + Adena",
			new QuestRewardItem[] { new QuestRewardItem(57, 10000) }, null, null);
		addIndexedQuest(348, "An Arrogant Search", 60, 75, 30864, new Location(82900, 148200, -3460), true, "Bloody Fabrics (Baium Access)",
			new QuestRewardItem[] { new QuestRewardItem(4295, 1) }, null, null);
		addIndexedQuest(351, "Black Swan", 32, 45, 30916, new Location(111300, 219000, -3500), true, "Adena + Coin of Fair",
			new QuestRewardItem[] { new QuestRewardItem(57, 8000) }, null, null);
		addIndexedQuest(336, "Coin of Magic", 40, 55, 30738, new Location(83100, 147900, -3400), true, "Coins of Magic + Adena",
			new QuestRewardItem[] { new QuestRewardItem(57, 12000) }, null, null);
		addIndexedQuest(344, "1000 Years, the End of Lamentation", 48, 57, 30754, new Location(83100, 148100, -3460), true, "Adena + High Grade Materials",
			new QuestRewardItem[] { new QuestRewardItem(57, 16000) }, null, null);
		addIndexedQuest(357, "Carnimal Feast", 45, 55, 30088, new Location(82800, 148300, -3460), true, "Adena + Rare Recipes",
			new QuestRewardItem[] { new QuestRewardItem(57, 15000) }, null, null);
		addIndexedQuest(376, "Exploration of Giants Cave 1", 65, 75, 31147, new Location(178500, 54200, -6100), true, "A-Grade Armor Recipes",
			new QuestRewardItem[] { new QuestRewardItem(57, 30000) }, null, null);
		addIndexedQuest(377, "Exploration of Giants Cave 2", 70, 78, 31147, new Location(178500, 54200, -6100), true, "A-Grade Weapon Recipes",
			new QuestRewardItem[] { new QuestRewardItem(57, 45000) }, null, null);
		addIndexedQuest(501, "Survival of the Fittest", 50, 62, 30588, new Location(115700, -178000, -900), true, "B-Grade Weapon Materials",
			new QuestRewardItem[] { new QuestRewardItem(57, 22000) }, null, null);
		addIndexedQuest(605, "Alliance with Ketra Orcs", 74, 80, 31371, new Location(139400, -56200, -2000), true, "S-Grade Armor Recipes",
			new QuestRewardItem[] { new QuestRewardItem(57, 50000) }, null, null);
		addIndexedQuest(611, "Alliance with Varka Silenos", 74, 80, 31378, new Location(105400, -41800, -1800), true, "S-Grade Armor Recipes",
			new QuestRewardItem[] { new QuestRewardItem(57, 50000) }, null, null);

		// Mapeamento rápido dos IDs já cadastrados no catálogo curado
		final IntSet registeredIds = new IntOpenHashSet(_questCatalog.size());
		for (QuestMetadata q : _questCatalog)
			registeredIds.add(q.getQuestId());

		// 2. Mapeamento reverso dos NPCs de início para todas as quests via NpcData
		final Int2IntMap questToStartNpc = new Int2IntOpenHashMap();
		for (NpcTemplate tmpl : NpcData.getInstance().getTemplates())
		{
			if (tmpl == null)
				continue;

			final List<Quest> startQuests = tmpl.getEventQuests(EventHandler.QUEST_START);
			if (startQuests != null && !startQuests.isEmpty())
			{
				for (Quest q : startQuests)
				{
					if (q != null && q.isRealQuest() && !questToStartNpc.containsKey(q.getQuestId()))
					{
						questToStartNpc.put(q.getQuestId(), tmpl.getNpcId());
					}
				}
			}
		}

		// 3. Varredura dinâmica de todas as quests reais registradas em ScriptData
		final List<Quest> serverQuests = ScriptData.getInstance().getQuests();
		if (serverQuests != null)
		{
			for (Quest q : serverQuests)
			{
				if (q == null || !q.isRealQuest())
					continue;

				final int qId = q.getQuestId();
				if (registeredIds.contains(qId))
					continue;

				final int startNpcId = questToStartNpc.getOrDefault(qId, 0);
				Location startLoc = null;

				// Tenta obter localização ativa do NPC no mundo
				if (startNpcId > 0)
				{
					final ext.mods.gameserver.model.actor.Npc activeNpc = World.getInstance().getNpc(startNpcId);
					if (activeNpc != null && activeNpc.getPosition() != null)
						startLoc = new Location(activeNpc.getX(), activeNpc.getY(), activeNpc.getZ());
				}

				// Fallback de localização segura caso não esteja spawnado ou seja dinâmico
				if (startLoc == null)
					startLoc = resolveFallbackLocation(qId);

				final int[] levelRange = estimateQuestLevelRange(qId, q.getDescr());
				final boolean repeatable = isQuestRepeatable(qId, q.getDescr());
				final String reward = estimateQuestReward(qId, q.getDescr());
				String qName = q.getDescr();
				if (qName == null || qName.isBlank())
					qName = q.getName();

				final ClassRace[] allowedRaces = estimateAllowedRaces(qId, qName);
				final ClassId[] allowedClasses = estimateAllowedClasses(qId, qName);
				final QuestRewardItem[] rewards = estimateQuestRewardItems(qId, q.getDescr());

				addIndexedQuest(qId, qName, levelRange[0], levelRange[1], startNpcId, startLoc, repeatable, reward, rewards, allowedRaces, allowedClasses);
				registeredIds.add(qId);
			}
		}

		// 4. Montagem da matriz vetorial contígua 8D (Mechanical Sympathy: array contíguo primitivo)
		final int total = _questCatalog.size();
		final int[] ids = new int[total];
		final float[] vectors = new float[total * QuestVectorEngine.DIMENSIONS];

		for (int i = 0; i < total; i++)
		{
			final QuestMetadata q = _questCatalog.get(i);
			ids[i] = q.getQuestId();

			final int offset = i * QuestVectorEngine.DIMENSIONS;
			// 0: Level Médio Normalizado (1 a 80)
			final float avgLvl = (q.getMinLevel() + q.getMaxLevel()) / 2.0f;
			vectors[offset] = Math.min(1.0f, Math.max(0.0f, avgLvl / 80.0f));
			// 1: Fighter Affinity
			vectors[offset + 1] = isMageSpecificQuest(q.getQuestId(), q.getName()) ? 0.2f : 0.8f;
			// 2: Mage Affinity
			vectors[offset + 2] = isFighterSpecificQuest(q.getQuestId(), q.getName()) ? 0.2f : 0.8f;
			// 3: Adena Weight
			vectors[offset + 3] = q.getRewardDescription().contains("Adena") ? 1.0f : 0.4f;
			// 4: Exp Weight
			vectors[offset + 4] = q.getRewardDescription().contains("Exp") || q.getRewardDescription().contains("SP") ? 1.0f : 0.5f;
			// 5: Item/Equipment Weight
			vectors[offset + 5] = q.getRewardDescription().contains("Recipes") || q.getRewardDescription().contains("Sword")
				|| q.getRewardDescription().contains("Materials") || q.getRewardDescription().contains("Equip") ? 1.0f : 0.5f;
			// 6: Repeatable
			vectors[offset + 6] = q.isRepeatable() ? 1.0f : 0.0f;
			// 7: Party/Raid
			vectors[offset + 7] = q.getMinLevel() >= 60 ? 0.7f : 0.1f;
		}

		_vectorEngine = new QuestVectorEngine(ids, vectors);
	}

	private static Location resolveFallbackLocation(int questId)
	{
		// Cidades de origem por faixa e perfil clássico
		if (questId <= 20)
			return new Location(-84033, 243232, -3730); // Talking Island Village
		if (questId <= 60)
			return new Location(46912, 51467, -2977); // Elven Village
		if (questId >= 70 && questId <= 100)
			return new Location(82900, 148200, -3460); // Giran Castle Town (Sagas / 3rd class)
		if (questId >= 211 && questId <= 240)
			return new Location(82900, 148200, -3460); // Giran Castle Town (2nd class change)
		if (questId >= 401 && questId <= 423)
			return new Location(-84033, 243232, -3730); // 1st class change
		return new Location(82900, 148200, -3460); // Giran default
	}

	private static int getExactMinLevel(int questId)
	{
		switch (questId)
		{
			case 1: case 2: case 4: case 5: case 153: case 154: case 166: return 2;
			case 6: case 7: case 8: case 9: case 10: case 45: case 46: case 47: case 48: case 49: case 155: case 160: case 161: case 165: case 168: case 258: case 264: case 266: return 3;
			case 267: case 271: case 291: return 4;
			case 157: case 272: case 292: return 5;
			case 257: case 260: case 265: case 273: case 293: return 6;
			case 262: case 263: case 313: return 8;
			case 101: case 274: return 9;
			case 104: case 105: case 106: case 108: case 152: case 294: case 303: case 320: return 10;
			case 103: case 163: case 275: case 295: case 319: return 11;
			case 102: case 107: case 159: case 162: case 347: return 12;
			case 151: case 156: case 167: case 169: case 259: case 261: case 276: case 277: case 296: case 297: case 325: case 362: case 363: case 364: case 419: return 15;
			case 3: return 16;
			case 306: case 340: return 17;
			case 316: case 317: case 324: return 18;
			case 38: case 118: case 123: case 401: case 402: case 403: case 404: case 405: case 406: case 407: case 408: case 409: case 410: case 411: case 412: case 413: case 414: case 415: case 416: case 417: case 418: return 19;
			case 39: case 112: case 122: case 341: case 378: case 379: case 385: case 634: case 644: return 20;
			case 158: case 164: case 170: case 326: case 328: case 661: return 21;
			case 645: return 23;
			case 44: case 330: case 380: return 24;
			case 42: case 298: case 327: case 333: case 369: return 25;
			case 43: case 651: case 659: return 26;
			case 27: case 50: case 171: return 27;
			case 370: return 28;
			case 116: case 334: case 649: case 660: return 30;
			case 432: return 31;
			case 331: case 351: return 32;
			case 329: return 33;
			case 299: case 300: return 34;
			case 211: case 212: case 213: case 215: case 216: case 345: case 420: return 35;
			case 28: case 51: case 214: case 355: case 368: case 653: return 36;
			case 217: case 220: case 367: return 37;
			case 218: case 219: case 221: case 354: case 431: return 38;
			case 117: case 222: case 223: case 224: case 225: case 226: case 227: case 228: case 229: case 230: case 231: case 232: case 233: case 352: case 365: case 650: return 39;
			case 113: case 336: case 338: case 343: case 350: case 384: case 646: return 40;
			case 422: return 41;
			case 383: return 42;
			case 356: return 43;
			case 32: case 335: case 421: return 45;
			case 121: case 647: case 652: return 46;
			case 357: return 47;
			case 29: case 52: case 344: case 366: return 48;
			case 114: return 49;
			case 120: case 241: case 337: return 50;
			case 376: return 51;
			case 360: return 52;
			case 115: case 648: return 53;
			case 353: case 381: case 382: return 55;
			case 374: return 56;
			case 373: case 377: return 57;
			case 386: return 58;
			case 371: case 372: return 59;
			case 15: case 30: case 33: case 34: case 35: case 36: case 37: case 53: case 242: case 348: case 359: case 375: case 618: case 626: case 627: return 60;
			case 17: case 662: return 61;
			case 16: return 62;
			case 19: case 21: case 22: case 358: case 632: return 63;
			case 23: return 64;
			case 20: case 24: case 246: case 631: case 633: return 65;
			case 18: case 25: case 31: case 109: case 628: case 629: case 640: return 66;
			case 602: case 621: case 622: return 68;
			case 601: case 623: return 71;
			case 603: case 604: case 624: case 625: case 636: case 637: case 638: case 639: return 73;
			case 11: case 12: case 13: case 14: case 119: case 605: case 606: case 609: case 611: case 612: case 615: case 617: case 619: case 620: case 654: return 74;
			case 110: case 111: case 124: case 234: case 235: case 247: case 607: case 608: case 610: case 613: case 614: case 616: case 642: case 643: case 688: return 75;
			case 125: return 76;
			case 126: case 641: return 77;
			default:
				if (questId >= 70 && questId <= 100)
					return 76;
				return 1;
		}
	}

	private static int[] estimateQuestLevelRange(int questId, String descr)
	{
		final int minLvl = getExactMinLevel(questId);

		// 3rd Class Change: Saga of ...
		if (questId >= 70 && questId <= 100)
			return new int[] { 76, 80 };

		// 2nd Class Change
		if (questId >= 211 && questId <= 240)
			return new int[] { minLvl, 48 };

		// 1st Class Change
		if (questId >= 401 && questId <= 418)
			return new int[] { minLvl, 25 };

		// Quests de endgame / farm contínuo
		if (minLvl >= 70)
			return new int[] { minLvl, 80 };
		if (minLvl >= 50)
			return new int[] { minLvl, Math.min(80, minLvl + 15) };
		if (minLvl >= 30)
			return new int[] { minLvl, minLvl + 12 };

		// Quests iniciais: janela relevante de evolução
		return new int[] { minLvl, Math.min(25, minLvl + 8) };
	}

	private static boolean isQuestRepeatable(int questId, String descr)
	{
		if (questId >= 70 && questId <= 100) // Sagas são uma única vez
			return false;
		if (questId >= 211 && questId <= 240) // Mudança de classe
			return false;
		if (questId >= 401 && questId <= 423)
			return false;
		return questId >= 300; // Maioria das quests acima do 300 são repetíveis de farm/craft
	}

	private static String estimateQuestReward(int questId, String descr)
	{
		if (questId >= 70 && questId <= 100)
			return "Third Class Transfer + Book of Giants";
		if (questId >= 211 && questId <= 240)
			return "Second Class Proof / Mark";
		if (questId >= 401 && questId <= 423)
			return "First Class Token + Exp";
		if (questId >= 600)
			return "S-Grade Materials & Recipes + Adena";
		if (questId >= 300)
			return "Exp + SP + Adena & Craft Materials";
		return "Adena + Exp / Basic Equipment";
	}

	private static QuestRewardItem[] estimateQuestRewardItems(int questId, String descr)
	{
		// 3rd Class: Book of Giants
		if (questId >= 70 && questId <= 100)
			return new QuestRewardItem[] { new QuestRewardItem(6622, 1), new QuestRewardItem(57, 5000000) };
		// Quests de endgame
		if (questId >= 600)
			return new QuestRewardItem[] { new QuestRewardItem(57, 45000) };
		// Quests de farm intermediárias
		if (questId >= 300)
			return new QuestRewardItem[] { new QuestRewardItem(57, 15000) };
		// Quests de 1st class transfer: Shadow Item Exchange Coupon (D-Grade) ou Adena
		if (questId >= 401 && questId <= 423)
			return new QuestRewardItem[] { new QuestRewardItem(8869, 15), new QuestRewardItem(57, 3200) };
		// Padrão: Adena básica
		return new QuestRewardItem[] { new QuestRewardItem(57, 2500) };
	}

	private static boolean isMageSpecificQuest(int questId, String name)
	{
		if (name == null)
			return false;
		final String lower = name.toLowerCase();
		return lower.contains("mage") || lower.contains("wizard") || lower.contains("sorcerer")
			|| lower.contains("necromancer") || lower.contains("cleric") || lower.contains("oracle")
			|| lower.contains("mystic") || lower.contains("shillien saint") || lower.contains("archmage");
	}

	private static boolean isFighterSpecificQuest(int questId, String name)
	{
		if (name == null)
			return false;
		final String lower = name.toLowerCase();
		return lower.contains("fighter") || lower.contains("knight") || lower.contains("warrior")
			|| lower.contains("rogue") || lower.contains("gladiator") || lower.contains("duelist")
			|| lower.contains("warlord") || lower.contains("paladin") || lower.contains("assassin");
	}

	private void addIndexedQuest(int questId, String name, int minLvl, int maxLvl, int npcId, Location npcLoc, boolean repeatable, String reward)
	{
		addIndexedQuest(questId, name, minLvl, maxLvl, npcId, npcLoc, repeatable, reward, null, null, null);
	}

	private void addIndexedQuest(int questId, String name, int minLvl, int maxLvl, int npcId, Location npcLoc, boolean repeatable, String reward, ClassRace[] allowedRaces, ClassId[] allowedClasses)
	{
		addIndexedQuest(questId, name, minLvl, maxLvl, npcId, npcLoc, repeatable, reward, null, allowedRaces, allowedClasses);
	}

	private void addIndexedQuest(int questId, String name, int minLvl, int maxLvl, int npcId, Location npcLoc, boolean repeatable, String reward, QuestRewardItem[] rewards, ClassRace[] allowedRaces, ClassId[] allowedClasses)
	{
		_questCatalog.add(new QuestMetadata(questId, name, minLvl, maxLvl, npcId, npcLoc, repeatable, reward, rewards, allowedRaces, allowedClasses));
	}

	private static ClassRace[] estimateAllowedRaces(int questId, String name)
	{
		// Quests clássicas de vilas de partida restritas por raça
		switch (questId)
		{
			case 1:
			case 6:
			case 45:
			case 101:
			case 104:
			case 217:
				return new ClassRace[] { ClassRace.HUMAN };

			case 2:
				return new ClassRace[] { ClassRace.ELF, ClassRace.HUMAN };

			case 7:
			case 46:
			case 102:
			case 105:
			case 159:
			case 160:
			case 161:
			case 218:
			case 260:
			case 266:
			case 267:
			case 316:
				return new ClassRace[] { ClassRace.ELF };

			case 3:
			case 8:
			case 47:
			case 103:
			case 106:
			case 162:
			case 163:
			case 164:
			case 165:
			case 166:
			case 168:
			case 169:
			case 170:
			case 219:
			case 263:
			case 265:
			case 320:
				return new ClassRace[] { ClassRace.DARK_ELF };

			case 4:
			case 9:
			case 48:
			case 107:
			case 220:
			case 222:
			case 232:
			case 233:
			case 271:
			case 272:
			case 273:
			case 274:
			case 275:
			case 276:
			case 343:
				return new ClassRace[] { ClassRace.ORC };

			case 5:
			case 10:
			case 49:
			case 108:
			case 116:
			case 221:
			case 292:
			case 293:
			case 294:
				return new ClassRace[] { ClassRace.DWARF };

			default:
				// Sagas da 3ª classe (Q070 a Q100)
				if (questId >= 70 && questId <= 100)
				{
					if (name != null)
					{
						final String lower = name.toLowerCase();
						if (lower.contains("phoenix knight") || lower.contains("hell knight") || lower.contains("adventurer")
							|| lower.contains("sagittarius") || lower.contains("archmage") || lower.contains("soultaker")
							|| lower.contains("arcana lord") || lower.contains("cardinal") || lower.contains("hierophant")
							|| lower.contains("duelist") || lower.contains("dreadnought"))
							return new ClassRace[] { ClassRace.HUMAN };
						if (lower.contains("evas templar") || lower.contains("sword muse") || lower.contains("wind rider")
							|| lower.contains("moonlight sentinel") || lower.contains("mystic muse") || lower.contains("elemental master")
							|| lower.contains("evas saint"))
							return new ClassRace[] { ClassRace.ELF };
						if (lower.contains("shillien templar") || lower.contains("spectral dancer") || lower.contains("ghost hunter")
							|| lower.contains("ghost sentinel") || lower.contains("storm screamer") || lower.contains("spectral master")
							|| lower.contains("shillien saint"))
							return new ClassRace[] { ClassRace.DARK_ELF };
						if (lower.contains("titan") || lower.contains("grand khavatari") || lower.contains("dominator") || lower.contains("doomcryer"))
							return new ClassRace[] { ClassRace.ORC };
						if (lower.contains("fortune seeker") || lower.contains("maestro"))
							return new ClassRace[] { ClassRace.DWARF };
					}
				}
				return null;
		}
	}

	private static ClassId[] estimateAllowedClasses(int questId, String name)
	{
		// 1st Class Change (Q401 a Q418): Mapeamento exato e à prova de falhas por questId
		switch (questId)
		{
			case 401: // Path to a Warrior
			case 402: // Path to a Human Knight
			case 403: // Path to a Rogue
				return new ClassId[] { ClassId.HUMAN_FIGHTER };

			case 404: // Path to a Human Wizard
			case 405: // Path to a Cleric
				return new ClassId[] { ClassId.HUMAN_MYSTIC };

			case 406: // Path to an Elven Knight
			case 407: // Path to an Elven Scout
				return new ClassId[] { ClassId.ELVEN_FIGHTER };

			case 408: // Path to an Elven Wizard
			case 409: // Path to an Elven Oracle
				return new ClassId[] { ClassId.ELVEN_MYSTIC };

			case 410: // Path to a Palus Knight
			case 411: // Path to an Assassin
				return new ClassId[] { ClassId.DARK_FIGHTER };

			case 412: // Path to a Dark Wizard
			case 413: // Path to a Shillien Oracle
				return new ClassId[] { ClassId.DARK_MYSTIC };

			case 414: // Path to an Orc Raider
			case 415: // Path to a Monk
				return new ClassId[] { ClassId.ORC_FIGHTER };

			case 416: // Path to an Orc Shaman
				return new ClassId[] { ClassId.ORC_MYSTIC };

			case 417: // Path to a Scavenger
			case 418: // Path to an Artisan
				return new ClassId[] { ClassId.DWARVEN_FIGHTER };
		}

		if (name != null)
		{
			final String lower = name.toLowerCase();
			if (lower.contains("path to a human knight") || lower.contains("path of the human knight"))
				return new ClassId[] { ClassId.HUMAN_FIGHTER };
			if (lower.contains("path to a warrior") || lower.contains("path of the warrior"))
				return new ClassId[] { ClassId.HUMAN_FIGHTER };
			if (lower.contains("path to a rogue") || lower.contains("path of the rogue"))
				return new ClassId[] { ClassId.HUMAN_FIGHTER };
			if (lower.contains("path to a wizard") || lower.contains("path of the human wizard"))
				return new ClassId[] { ClassId.HUMAN_MYSTIC };
			if (lower.contains("path to a cleric") || lower.contains("path of the cleric"))
				return new ClassId[] { ClassId.HUMAN_MYSTIC };
			if (lower.contains("elven knight"))
				return new ClassId[] { ClassId.ELVEN_FIGHTER };
			if (lower.contains("elven scout"))
				return new ClassId[] { ClassId.ELVEN_FIGHTER };
			if (lower.contains("elven wizard"))
				return new ClassId[] { ClassId.ELVEN_MYSTIC };
			if (lower.contains("elven oracle"))
				return new ClassId[] { ClassId.ELVEN_MYSTIC };
			if (lower.contains("palus knight"))
				return new ClassId[] { ClassId.DARK_FIGHTER };
			if (lower.contains("assassin"))
				return new ClassId[] { ClassId.DARK_FIGHTER };
			if (lower.contains("dark wizard"))
				return new ClassId[] { ClassId.DARK_MYSTIC };
			if (lower.contains("shillien oracle"))
				return new ClassId[] { ClassId.DARK_MYSTIC };
			if (lower.contains("orc raider") || lower.contains("orc monk"))
				return new ClassId[] { ClassId.ORC_FIGHTER };
			if (lower.contains("orc shaman"))
				return new ClassId[] { ClassId.ORC_MYSTIC };
			if (lower.contains("scavenger") || lower.contains("artisan"))
				return new ClassId[] { ClassId.DWARVEN_FIGHTER };
		}
		return null;
	}

	public boolean isEnabled()
	{
		return _enabled;
	}

	public int getQuestionMarkId()
	{
		return _questionMarkId;
	}

	public String getTutorialAlertHtml(Player player)
	{
		if (player == null || !_enabled)
			return null;

		final boolean lumEnabled = LevelUpMakerHooks.get().isEnabled();
		final String html = HTMLData.getInstance().getHtm(player.getLocale(), "html/mods/questrecommender/tutorial_alert.htm");
		if (html == null || html.isEmpty())
		{
			// Fallback limpo e inline caso htm ainda não exista no locale
			final StringBuilder fallback = new StringBuilder();
			fallback.append("<html><body><center><br><font color=LEVEL>Quest Recommender</font><br><br>");
			fallback.append("Descubra as melhores quests para o seu nível e classe!<br><br>");
			fallback.append("<table cellpadding=2 cellspacing=2><tr>");
			fallback.append("<td><button value=\"Ver Quests\" action=\"bypass -h questrec_show\" width=110 height=25 back=\"L2UI_ch3.BigButton3_down\" fore=\"L2UI_ch3.BigButton3\"></td>");
			if (lumEnabled)
			{
				fallback.append("<td><button value=\"Level Up Maker\" action=\"bypass -h levelupmaker_teleport\" width=110 height=25 back=\"L2UI_ch3.BigButton3_down\" fore=\"L2UI_ch3.BigButton3\"></td>");
			}
			fallback.append("</tr></table><br>");
			fallback.append("</center></body></html>");
			return fallback.toString();
		}

		final String lumButton = lumEnabled
			? "<td><button value=\"Level Up Maker\" action=\"bypass -h levelupmaker_teleport\" width=110 height=25 back=\"L2UI_ch3.BigButton3_down\" fore=\"L2UI_ch3.BigButton3\"></td>"
			: "";

		return html
			.replace("%button_bypass%", BYPASS_SHOW)
			.replace("%levelupmaker_button%", lumButton);
	}

	public void sendQuestionMark(Player player)
	{
		if (!_enabled || player == null || !QuestNavigationService.getInstance().canTeleport(player))
			return;

		player.sendPacket(new TutorialShowQuestionMark(_questionMarkId));
	}

	@Override
	public void onLevelUp(Player player)
	{
		if (!_enabled || _levelUpDelaySec <= 0 || player == null)
			return;

		ThreadPool.schedule(() ->
		{
			if (player == null || !player.isOnline() || player.isDead())
				return;

			sendQuestionMark(player);
		}, _levelUpDelaySec * 1000L);
	}

	@Override
	public boolean onBypass(Player player, String command)
	{
		if (command == null || !command.startsWith(BYPASS_PREFIX))
			return false;

		if (command.equals(BYPASS_SHOW))
		{
			showRecommendationsWindow(player, 1);
			return true;
		}

		if (command.equals(BYPASS_CLOSE))
		{
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return true;
		}

		if (command.startsWith(BYPASS_NAVIGATE))
		{
			try
			{
				final int questId = Integer.parseInt(command.substring(BYPASS_NAVIGATE.length()).trim());
				handleQuestSelection(player, questId);
			}
			catch (Exception e)
			{
				LOGGER.warn("Invalid quest navigation bypass: {}", command);
			}
			return true;
		}

		return false;
	}

	/**
	 * Processa o clique na quest: direciona pelo radar (<5000 range/mesma cidade) ou teleporta para a cidade mais próxima.
	 */
	private void handleQuestSelection(Player player, int questId)
	{
		final QuestMetadata meta = getMetadata(questId);
		if (meta == null)
		{
			player.sendMessage("Quest não encontrada no catálogo de recomendações.");
			return;
		}

		QuestNavigationService.getInstance().navigateOrTeleportToNpc(
			player,
			meta.getStartNpcId(),
			meta.getStartNpcLoc(),
			_scrollSkillId,
			_scrollSkillLevel,
			_castTimeMs,
			_rangeLimit,
			_cannotTeleportMessage
		);
	}

	/**
	 * Gera e envia a janela de recomendações vetoriais sob medida para o jogador.
	 */
	public void showRecommendationsWindow(Player player, int page)
	{
		if (player == null || _vectorEngine == null)
			return;

		final int playerLevel = player.getStatus().getLevel();
		final boolean isMage = player.isMageClass();

		// Vetor de entrada do jogador (8 dimensões normalizadas)
		final float[] playerVector = new float[QuestVectorEngine.DIMENSIONS];
		playerVector[0] = Math.min(1.0f, Math.max(0.0f, playerLevel / 80.0f));
		playerVector[1] = isMage ? 0.2f : 0.9f; // Fighter
		playerVector[2] = isMage ? 0.9f : 0.2f; // Mage
		playerVector[3] = 0.8f; // Adena preference
		playerVector[4] = 0.9f; // Exp preference
		playerVector[5] = 0.7f; // Item preference
		playerVector[6] = 0.5f; // Repeatable preference
		playerVector[7] = 0.3f; // Party preference

		final int total = _vectorEngine.getTotalQuests();
		final float[] scores = new float[total];
		final boolean[] validMask = new boolean[total];

		// Máscara de validação estrita de nível, raça/classe e estado de conclusão da quest
		for (int i = 0; i < total; i++)
		{
			final QuestMetadata m = _questCatalog.get(i);
			// Filtro: nível estrito (dentro do range da quest) e elegibilidade de raça/classe
			if (playerLevel < m.getMinLevel() || playerLevel > m.getMaxLevel() || !m.isEligible(player))
			{
				validMask[i] = false;
				continue;
			}

			// Se completada e não-repetível -> descarta
			final QuestState qs = player.getQuestList().getQuestState(m.getQuestId());
			if (qs != null && qs.isCompleted() && !m.isRepeatable())
			{
				validMask[i] = false;
				continue;
			}

			validMask[i] = true;
		}

		_vectorEngine.computeScores(playerVector, scores);

		final IntList topQuestIds = new IntArrayList(_topK);
		_vectorEngine.getTopK(scores, validMask, _topK, topQuestIds);

		final int limit = _topK;
		final String recHtml = renderRecommendationsHtml(player, limit);

		final StringBuilder sb = new StringBuilder();
		sb.append("<html><body><center>");
		sb.append("<br><font color=\"LEVEL\">=== Motor de Recomendação de Quests ===</font><br1>");
		sb.append("<font color=\"B09878\">Quests selecionadas para o seu nível (").append(playerLevel).append(") e classe</font><br>");
		sb.append("<img src=\"L2UI.SquareGray\" width=280 height=1><br>");

		sb.append(recHtml);

		sb.append("<br><font color=808080><small>Se estiver longe, será levado à cidade mais próxima.</small></font><br>");

		final boolean lumEnabled = LevelUpMakerHooks.get().isEnabled();
		if (lumEnabled)
		{
			sb.append("<table cellspacing=4><tr>");
			sb.append("<td><button value=\"Level Up Maker\" action=\"bypass -h levelupmaker_teleport\" width=120 height=22 back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\"></td>");
			sb.append("<td><button value=\"Fechar\" action=\"bypass -h questrec_close\" width=70 height=22 back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\"></td>");
			sb.append("</tr></table>");
		}
		else
		{
			sb.append("<button value=\"Fechar\" action=\"bypass -h questrec_close\" width=70 height=22 back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\">");
		}
		sb.append("</center></body></html>");

		final NpcHtmlMessage msg = new NpcHtmlMessage(0);
		msg.setHtml(sb.toString());
		msg.disableValidation();
		player.sendPacket(msg);
	}

	/**
	 * Gera o bloco HTML com as melhores quests recomendadas, formatado estritamente para o cliente Interlude.
	 */
	public String renderRecommendationsHtml(Player player, int limit)
	{
		if (player == null || _vectorEngine == null)
			return "";

		final int playerLevel = player.getStatus().getLevel();
		final boolean isMage = player.isMageClass();

		final float[] playerVector = new float[QuestVectorEngine.DIMENSIONS];
		playerVector[0] = Math.min(1.0f, Math.max(0.0f, playerLevel / 80.0f));
		playerVector[1] = isMage ? 0.2f : 0.9f;
		playerVector[2] = isMage ? 0.9f : 0.2f;
		playerVector[3] = 0.8f;
		playerVector[4] = 0.9f;
		playerVector[5] = 0.7f;
		playerVector[6] = 0.5f;
		playerVector[7] = 0.3f;

		final int total = _vectorEngine.getTotalQuests();
		final float[] scores = new float[total];
		final boolean[] validMask = new boolean[total];

		for (int i = 0; i < total; i++)
		{
			final QuestMetadata m = _questCatalog.get(i);
			if (playerLevel < m.getMinLevel() || playerLevel > m.getMaxLevel() || !m.isEligible(player))
			{
				validMask[i] = false;
				continue;
			}

			final QuestState qs = player.getQuestList().getQuestState(m.getQuestId());
			if (qs != null && qs.isCompleted() && !m.isRepeatable())
			{
				validMask[i] = false;
				continue;
			}

			validMask[i] = true;
		}

		_vectorEngine.computeScores(playerVector, scores);

		final int k = Math.max(1, limit);
		final IntList topQuestIds = new IntArrayList(k);
		_vectorEngine.getTopK(scores, validMask, k, topQuestIds);

		final StringBuilder sb = new StringBuilder();
		if (topQuestIds.isEmpty())
		{
			sb.append("<table width=270 bgcolor=000000 cellpadding=6><tr>");
			sb.append("<td width=270 align=center>");
			sb.append("<font color=B09878>Nenhuma quest sugerida para o nível </font><font color=LEVEL>").append(playerLevel).append("</font><font color=B09878> no momento.</font><br1>");
			sb.append("<font color=808080><small>Continue evoluindo seu personagem para desbloquear novas aventuras!</small></font>");
			sb.append("</td></tr></table>");
		}
		else
		{
			for (int qid : topQuestIds)
			{
				final QuestMetadata m = getMetadata(qid);
				if (m == null)
					continue;

				final QuestState qs = player.getQuestList().getQuestState(qid);
				final String statusText = (qs != null && qs.isStarted()) ? "<font color=00FF00>[EM PROGRESSO]</font>" : "<font color=LEVEL>[DISPONÍVEL]</font>";

				sb.append("<table width=270 height=76 bgcolor=000000 cellpadding=4><tr>");
				sb.append("<td width=195 height=76 align=left valign=top>");
				sb.append("<font color=FFFFFF><b>").append(m.getName()).append("</b></font> ").append(statusText).append("<br1>");
				sb.append("<font color=B09878>Nível: ").append(m.getMinLevel()).append("-").append(m.getMaxLevel()).append("</font><br1>");

				
				final boolean hasExp = m.hasExpReward();
				final QuestRewardItem[] rewards = m.getRewards();
				final boolean hasRewards = (rewards != null && rewards.length > 0);

				if (hasExp || hasRewards)
				{
					sb.append("<table cellpadding=0 cellspacing=2 height=36><tr>");
					if (hasExp)
					{
						sb.append("<td width=34 height=34 align=center valign=middle><img src=\"icon.etc_blessed_kalie_i00\" width=32 height=32></td>");
					}
					if (hasRewards)
					{
						final int maxIcons = hasExp ? 4 : 5;
						for (int rIdx = 0; rIdx < Math.min(maxIcons, rewards.length); rIdx++)
						{
							final QuestRewardItem r = rewards[rIdx];
							final Item item = ItemData.getInstance().getTemplate(r.getItemId());
							final String icon = (item != null && item.getIcon() != null) ? item.getIcon() : "icon.etc_adena_i00";
							sb.append("<td width=34 height=34 align=center valign=middle><img src=\"").append(icon).append("\" width=32 height=32></td>");
						}
					}
					sb.append("</tr></table>");
				}
				else
				{
					sb.append("<font color=FFFF99>Recompensa: ").append(m.getRewardDescription()).append("</font>");
				}

				sb.append("</td>");
				sb.append("<td width=75 height=76 align=center valign=center>");
				sb.append("<button value=\"Ir até NPC\" action=\"bypass -h questrec_nav ").append(m.getQuestId()).append("\" width=70 height=21 back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\">");
				sb.append("</td>");
				sb.append("</tr></table>");
				sb.append("<br1><img src=\"L2UI.SquareGray\" width=270 height=1><br1>");
			}
		}

		return sb.toString();
	}

	private QuestMetadata getMetadata(int questId)
	{
		for (QuestMetadata m : _questCatalog)
		{
			if (m.getQuestId() == questId)
				return m;
		}
		return null;
	}

	private void startRefreshTask()
	{
		if (_refreshTask != null)
			_refreshTask.cancel(false);

		_refreshTask = ThreadPool.scheduleAtFixedRate(() ->
		{
			if (!_enabled)
				return;

			for (Player player : World.getInstance().getPlayers())
			{
				if (player != null && player.isOnline() && !player.isDead())
					sendQuestionMark(player);
			}
		}, _refreshIntervalSec * 1000L, _refreshIntervalSec * 1000L);
	}
}
