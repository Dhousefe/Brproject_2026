package ext.mods.extensions.hooks;

import ext.mods.gameserver.model.actor.Player;

/**
 * Tournament host hooks. Save/restore arena state lives in {@code mod-tour}.
 */
public final class TournamentHooks
{
	public interface Api
	{
		void onPlayerDeath(Player player);
		
		boolean isRunning();
		
		boolean isRunningRegister();
		
		String lastEvent();
		
		int tutorialQuestionMarkId();
		
		String getTutorialAlertHtml(Player player);
		
		int getDurationMinutes();
		
		/** Show tournament ranking UI after battle restore (optional). */
		void showRanking(Player player);
		
		/** Snapshot position/instance before arena teleport. */
		void savePlayerState(Player player);
		
		/** Restore position and clear tournament flags after battle. */
		void restorePlayerState(Player player);
	}
	
	private static final Api NOOP = new Api()
	{
		@Override
		public void onPlayerDeath(Player player)
		{
		}
		
		@Override
		public boolean isRunning()
		{
			return false;
		}
		
		@Override
		public boolean isRunningRegister()
		{
			return false;
		}
		
		@Override
		public String lastEvent()
		{
			return "";
		}
		
		@Override
		public int tutorialQuestionMarkId()
		{
			return -1;
		}
		
		@Override
		public String getTutorialAlertHtml(Player player)
		{
			return null;
		}
		
		@Override
		public int getDurationMinutes()
		{
			return 0;
		}
		
		@Override
		public void showRanking(Player player)
		{
		}
		
		@Override
		public void savePlayerState(Player player)
		{
		}
		
		@Override
		public void restorePlayerState(Player player)
		{
		}
	};
	
	private static volatile Api api = NOOP;
	
	private TournamentHooks()
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
