package ext.mods.extensions.hooks;

import ext.mods.gameserver.model.actor.Player;

/**
 * SPI Hook Interface para desacoplamento do motor Quest Recommender.
 */
public final class QuestRecommenderHooks
{
	public interface Api
	{
		boolean isEnabled();

		int getQuestionMarkId();

		String getTutorialAlertHtml(Player player);

		void sendQuestionMark(Player player);

		void showRecommendationsWindow(Player player, int page);

		String renderRecommendationsHtml(Player player, int limit);
	}

	private static final Api NOOP = new Api()
	{
		@Override public boolean isEnabled() { return false; }
		@Override public int getQuestionMarkId() { return -1; }
		@Override public String getTutorialAlertHtml(Player player) { return null; }
		@Override public void sendQuestionMark(Player player) {}
		@Override public void showRecommendationsWindow(Player player, int page) {}
		@Override public String renderRecommendationsHtml(Player player, int limit) { return ""; }
	};

	private static volatile Api api = NOOP;

	private QuestRecommenderHooks() {}

	public static void register(Api implementation) { api = implementation != null ? implementation : NOOP; }
	public static void clear() { api = NOOP; }
	public static Api get() { return api; }
}
