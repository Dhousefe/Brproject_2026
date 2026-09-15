package ext.mods.extensions.hooks;

import ext.mods.gameserver.model.actor.Player;

public final class SummonMobHooks
{
	public interface Api
	{
		void ensureLoaded();

		void resetDailyCounters();

		void reload();

		int getLoadedCount();
	}

	private static final Api NOOP = new Api()
	{
		@Override public void ensureLoaded() {}
		@Override public void resetDailyCounters() {}
		@Override public void reload() {}
		@Override public int getLoadedCount() { return 0; }
	};

	private static volatile Api api = NOOP;

	private SummonMobHooks() {}

	public static void register(Api implementation) { api = implementation != null ? implementation : NOOP; }
	public static void clear() { api = NOOP; }
	public static Api get() { return api; }
}
