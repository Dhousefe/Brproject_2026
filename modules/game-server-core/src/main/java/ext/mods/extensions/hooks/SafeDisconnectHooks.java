package ext.mods.extensions.hooks;

import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.network.GameClient;

/**
 * Optional SafeDisconnect API so core call-sites do not hard-depend on the manager type forever.
 * Registered by first-party features SPI (Phase 3).
 */
public final class SafeDisconnectHooks
{
	public interface Api
	{
		void markExpectedLogout(Player player);
		
		boolean handleDisconnect(GameClient client);
		
		void onEnterWorld(Player player);
		
		boolean prepareReconnect(Player player, GameClient newClient);
		
		boolean isSafeDisconnectActive(Player player);
	}
	
	private static final Api NOOP = new Api()
	{
		@Override
		public void markExpectedLogout(Player player)
		{
		}
		
		@Override
		public boolean handleDisconnect(GameClient client)
		{
			return false;
		}
		
		@Override
		public void onEnterWorld(Player player)
		{
		}
		
		@Override
		public boolean prepareReconnect(Player player, GameClient newClient)
		{
			return false;
		}
		
		@Override
		public boolean isSafeDisconnectActive(Player player)
		{
			return false;
		}
	};
	
	private static volatile Api api = NOOP;
	
	private SafeDisconnectHooks()
	{
	}
	
	public static void register(Api implementation)
	{
		api = implementation != null ? implementation : NOOP;
	}
	
	public static void clear()
	{
		api = NOOP;
	}
	
	public static Api get()
	{
		return api;
	}
}
