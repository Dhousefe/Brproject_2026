package ext.mods.extensions.hooks;

import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.instance.Monster;

public final class GlobalDropHooks
{
	public interface Api
	{
		boolean shouldCancelOriginalDrop(Monster monster);

		void onKill(Player killer, Monster monster);

		void reload();
	}

	private static final Api NOOP = new Api()
	{
		@Override public boolean shouldCancelOriginalDrop(Monster monster) { return false; }
		@Override public void onKill(Player killer, Monster monster) {}
		@Override public void reload() {}
	};

	private static volatile Api api = NOOP;

	private GlobalDropHooks() {}

	public static void register(Api implementation) { api = implementation != null ? implementation : NOOP; }
	public static void clear() { api = NOOP; }
	public static Api get() { return api; }
}
