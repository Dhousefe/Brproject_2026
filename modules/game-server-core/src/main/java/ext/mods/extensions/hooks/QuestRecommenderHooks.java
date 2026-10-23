package ext.mods.extensions.hooks;

import java.util.List;

import ext.mods.gameserver.model.actor.Player;

/**
 * SPI Hook Interface para desacoplamento do motor Quest Recommender.
 * Dhousefe - L2JBR
 */
public final class QuestRecommenderHooks
{
	public interface Api
	{
		record RewardQuest(int questId, String questName, int minLevel, int maxLevel, String startNpcName, long rewardCount) {}

		boolean isEnabled();

		int getQuestionMarkId();

		String getTutorialAlertHtml(Player player);

		void sendQuestionMark(Player player);

		void showRecommendationsWindow(Player player, int page);

		String renderRecommendationsHtml(Player player, int limit);

		List<RewardQuest> findAvailableRewardQuests(Player player, int itemId);

		boolean markAvailableRewardQuestStart(Player player, int itemId, int questId);
	}

	private static final Api NOOP = new Api()
	{
		@Override public boolean isEnabled() { return false; }
		@Override public int getQuestionMarkId() { return -1; }
		@Override public String getTutorialAlertHtml(Player player) { return null; }
		@Override public void sendQuestionMark(Player player) {}
		@Override public void showRecommendationsWindow(Player player, int page) {}
		@Override public String renderRecommendationsHtml(Player player, int limit) { return ""; }
		@Override public List<RewardQuest> findAvailableRewardQuests(Player player, int itemId) { return List.of(); }
		@Override public boolean markAvailableRewardQuestStart(Player player, int itemId, int questId) { return false; }
	};

	private static volatile Api api = NOOP;

	private QuestRecommenderHooks() {}

	public static void register(Api implementation) { api = implementation != null ? implementation : NOOP; }
	public static void clear() { api = NOOP; }
	public static Api get() { return api; }
}
