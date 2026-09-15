package ext.mods.extensions.hooks;

import ext.mods.gameserver.model.actor.Player;

public final class LevelUpMakerHooks
{
	public interface Api
	{
		boolean isEnabled();

		int getQuestionMarkId();

		int getScrollSkillId();

		int getScrollSkillLevel();

		int getCastTimeMs();

		String getTutorialAlertHtml(Player player);

		void sendQuestionMark(Player player);
	}

	private static final Api NOOP = new Api()
	{
		@Override public boolean isEnabled() { return false; }
		@Override public int getQuestionMarkId() { return -1; }
		@Override public int getScrollSkillId() { return 2040; }
		@Override public int getScrollSkillLevel() { return 1; }
		@Override public int getCastTimeMs() { return 10000; }
		@Override public String getTutorialAlertHtml(Player player) { return null; }
		@Override public void sendQuestionMark(Player player) {}
	};

	private static volatile Api api = NOOP;

	private LevelUpMakerHooks() {}

	public static void register(Api implementation) { api = implementation != null ? implementation : NOOP; }
	public static void clear() { api = NOOP; }
	public static Api get() { return api; }
}
