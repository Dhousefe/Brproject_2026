package ext.mods.extensions.hooks;

import ext.mods.gameserver.model.actor.Player;

public final class PlayerGodHooks
{
	public interface Api
	{
		void onKill(Player killer);

		void onEnterWorld(Player player);
	}

	private static final Api NOOP = new Api()
	{
		@Override public void onKill(Player killer) {}
		@Override public void onEnterWorld(Player player) {}
	};

	private static volatile Api api = NOOP;

	private PlayerGodHooks() {}

	public static void register(Api implementation) { api = implementation != null ? implementation : NOOP; }
	public static void clear() { api = NOOP; }
	public static Api get() { return api; }
}
