package ext.mods.extensions.hooks;

import ext.mods.gameserver.model.actor.Attackable;
import ext.mods.gameserver.model.actor.Player;

public final class BattleBossHooks
{
	public interface Api
	{
		void onPlayerDeath(Player player);

		void onPlayerDeathMonster(Attackable monster, Player killer);
	}

	private static final Api NOOP = new Api()
	{
		@Override public void onPlayerDeath(Player player) {}
		@Override public void onPlayerDeathMonster(Attackable monster, Player killer) {}
	};

	private static volatile Api api = NOOP;

	private BattleBossHooks() {}

	public static void register(Api implementation) { api = implementation != null ? implementation : NOOP; }
	public static void clear() { api = NOOP; }
	public static Api get() { return api; }
}
