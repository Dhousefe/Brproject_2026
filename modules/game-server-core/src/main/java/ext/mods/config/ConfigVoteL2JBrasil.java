package ext.mods.config;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import ext.mods.Config;
import ext.mods.commons.config.ExProperties;
import ext.mods.gameserver.model.holder.IntIntHolder;

/**
 * Configuration domain for Top L2JBrasil Vote Webhook System.
 * Reads game/config/votel2jbrasil.properties.
 */
public final class ConfigVoteL2JBrasil
{
	private ConfigVoteL2JBrasil()
	{
	}

	public static boolean ENABLE_VOTE_SYSTEM;
	public static String TOP_L2JBRASIL_VOTE_URL;
	public static String TOP_L2JBRASIL_SERVER_NAME;
	public static int TOP_L2JBRASIL_SERVER_ID;
	public static String TOP_L2JBRASIL_API_SECRET;
	public static String TOP_L2JBRASIL_ENDPOINT_KEY;
	public static String VOTE_REWARD_ITEMS_RAW;
	public static IntIntHolder[] VOTE_REWARD_ITEMS;
	public static int VOTE_COOLDOWN_HOURS;
	public static boolean ALLOW_OFFLINE_DELIVERY;
	public static boolean VOTE_REWARD_ANNOUNCE;
	public static String VOTE_REWARD_ANNOUNCE_MSG;

	public static String RATING_REWARD_ITEMS_RAW;
	public static IntIntHolder[] RATING_REWARD_ITEMS;
	public static boolean RATING_REWARD_ANNOUNCE;
	public static String RATING_REWARD_ANNOUNCE_MSG;

	public static String BOOST_REWARD_ITEMS_RAW;
	public static IntIntHolder[] BOOST_REWARD_ITEMS;
	public static boolean BOOST_REWARD_ANNOUNCE;
	public static String BOOST_REWARD_ANNOUNCE_MSG;

	public static int VOTE_MIN_CHARACTER_LEVEL;
	public static int VOTE_MIN_PLAYTIME_MINUTES;
	public static boolean VOTE_REQUIRE_HMAC_AUTH;
	public static int RATING_COOLDOWN_DAYS;
	public static boolean VOTE_CHECK_HWID;

	public static void load()
	{
		final String filePath = Config.CONFIG_PATH.resolve("votel2jbrasil.properties").toString();
		final ExProperties props = Config.initProperties(filePath);

		ENABLE_VOTE_SYSTEM = props.getProperty("EnableVoteSystem", true);
		TOP_L2JBRASIL_VOTE_URL = props.getProperty("TopL2jBrasilVoteUrl", "https://top.l2jbrasil.com/index.php?a=in&u=dhousefe");
		TOP_L2JBRASIL_SERVER_NAME = props.getProperty("TopL2jBrasilServerName", "dhousefe").trim();
		TOP_L2JBRASIL_SERVER_ID = props.getProperty("TopL2jBrasilServerId", 0);
		TOP_L2JBRASIL_API_SECRET = props.getProperty("TopL2jBrasilApiSecret", "").trim();
		TOP_L2JBRASIL_ENDPOINT_KEY = props.getProperty("TopL2jBrasilEndpointKey", "").trim();
		VOTE_REWARD_ITEMS_RAW = props.getProperty("VoteRewardItems", "4037,5; 57,5000000");
		VOTE_COOLDOWN_HOURS = props.getProperty("VoteCooldownHours", 24);
		ALLOW_OFFLINE_DELIVERY = props.getProperty("AllowOfflineDelivery", true);
		VOTE_REWARD_ANNOUNCE = props.getProperty("VoteRewardAnnounce", true);
		VOTE_REWARD_ANNOUNCE_MSG = props.getProperty("VoteRewardAnnounceMsg", "O jogador %s votou no Top L2JBrasil e recebeu sua recompensa!");

		RATING_REWARD_ITEMS_RAW = props.getProperty("RatingRewardItems", "4037,20; 57,1000000");
		RATING_REWARD_ANNOUNCE = props.getProperty("RatingRewardAnnounce", true);
		RATING_REWARD_ANNOUNCE_MSG = props.getProperty("RatingRewardAnnounceMsg", "O jogador %s avaliou o servidor no Top L2JBrasil com nota %d estrelas!");

		BOOST_REWARD_ITEMS_RAW = props.getProperty("BoostRewardItems", "4037,50; 57,10000000");
		BOOST_REWARD_ANNOUNCE = props.getProperty("BoostRewardAnnounce", true);
		BOOST_REWARD_ANNOUNCE_MSG = props.getProperty("BoostRewardAnnounceMsg", "O jogador %s ativou um BOOST (%dx) no Top L2JBrasil e turbinou o servidor!");

		VOTE_MIN_CHARACTER_LEVEL = props.getProperty("VoteMinCharacterLevel", 40);
		VOTE_MIN_PLAYTIME_MINUTES = props.getProperty("VoteMinPlaytimeMinutes", 60);
		VOTE_REQUIRE_HMAC_AUTH = props.getProperty("VoteRequireHmacAuth", false);
		RATING_COOLDOWN_DAYS = props.getProperty("RatingCooldownDays", 30);
		VOTE_CHECK_HWID = props.getProperty("VoteCheckHwid", true);

		VOTE_REWARD_ITEMS = parseItemHolders(VOTE_REWARD_ITEMS_RAW);
		RATING_REWARD_ITEMS = parseItemHolders(RATING_REWARD_ITEMS_RAW);
		BOOST_REWARD_ITEMS = parseItemHolders(BOOST_REWARD_ITEMS_RAW);
	}

	public static IntIntHolder[] parseItemHolders(String raw)
	{
		if (raw == null || raw.trim().isEmpty())
		{
			return new IntIntHolder[0];
		}

		final List<IntIntHolder> list = new ArrayList<>();
		final StringTokenizer st = new StringTokenizer(raw.trim(), ";");
		while (st.hasMoreTokens())
		{
			final String token = st.nextToken().trim();
			if (token.isEmpty())
			{
				continue;
			}

			final String[] parts = token.split(",");
			if (parts.length == 2)
			{
				try
				{
					final int itemId = Integer.parseInt(parts[0].trim());
					final int count = Integer.parseInt(parts[1].trim());
					if (itemId > 0 && count > 0)
					{
						list.add(new IntIntHolder(itemId, count));
					}
				}
				catch (NumberFormatException e)
				{
					Config.LOGGER.warn("ConfigVoteL2JBrasil: formato inválido de item na recompensa: " + token);
				}
			}
		}

		return list.toArray(new IntIntHolder[0]);
	}
}
