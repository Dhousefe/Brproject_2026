package ext.mods.extensions.hooks;

import java.util.Collection;
import java.util.Collections;

import ext.mods.gameserver.model.actor.Player;

public final class BuffShopHooks
{
	public interface Api
	{
		boolean handleBypass(Player player, String command);

		Collection<? extends Player> getSellers();

		boolean isSeller(int objectId);

		Integer getSellerOwnerId(int objectId);

		void showIndexWindow(Player player);

		void showBuffRemovalWindow(Player player);
	}

	private static final Api NOOP = new Api()
	{
		@Override public boolean handleBypass(Player player, String command) { return false; }
		@Override public Collection<? extends Player> getSellers() { return Collections.emptyList(); }
		@Override public boolean isSeller(int objectId) { return false; }
		@Override public Integer getSellerOwnerId(int objectId) { return null; }
		@Override public void showIndexWindow(Player player) {}
		@Override public void showBuffRemovalWindow(Player player) {}
	};

	private static volatile Api api = NOOP;

	private BuffShopHooks() {}

	public static void register(Api implementation) { api = implementation != null ? implementation : NOOP; }
	public static void clear() { api = NOOP; }
	public static Api get() { return api; }
}
