package ext.mods.extensions.hooks;

import ext.mods.gameserver.model.actor.Player;

public final class DonationHooks
{
	public interface Api
	{
		void offlinePlayer(Player player);

		boolean handleBypass(Player player, String command);
	}

	private static final Api NOOP = new Api()
	{
		@Override public void offlinePlayer(Player player) {}
		@Override public boolean handleBypass(Player player, String command) { return false; }
	};

	private static volatile Api api = NOOP;

	private DonationHooks() {}

	public static void register(Api implementation) { api = implementation != null ? implementation : NOOP; }
	public static void clear() { api = NOOP; }
	public static Api get() { return api; }
}
