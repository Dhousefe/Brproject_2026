package ext.mods.gameserver.model.actor.player;

import ext.mods.gameserver.model.actor.Player;

/** Offline buff-shop / tool dummy flag. */
public final class DummyState
{
	public static final PlayerAttachments.Key<Boolean> DUMMY = PlayerAttachments.Key.of("player.dummy");
	
	private DummyState()
	{
	}
	
	public static boolean isDummy(Player player)
	{
		if (player == null)
			return false;
		final Boolean v = PlayerAttachments.get(player.getObjectId(), DUMMY);
		return v != null && v;
	}
	
	public static void setDummy(Player player, boolean dummy)
	{
		if (player == null)
			return;
		if (dummy)
			PlayerAttachments.put(player.getObjectId(), DUMMY, Boolean.TRUE);
		else
			PlayerAttachments.remove(player.getObjectId(), DUMMY);
	}
}
