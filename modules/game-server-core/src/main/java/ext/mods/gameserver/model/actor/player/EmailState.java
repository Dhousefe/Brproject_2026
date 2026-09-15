package ext.mods.gameserver.model.actor.player;

import ext.mods.gameserver.model.actor.Player;

/** Email item UI selection temps. */
public final class EmailState
{
	public static final PlayerAttachments.Key<String> TARGET = PlayerAttachments.Key.of("email.selectedTarget");
	public static final PlayerAttachments.Key<String> DURATION = PlayerAttachments.Key.of("email.selectedDuration");
	
	private EmailState()
	{
	}
	
	public static String target(Player player)
	{
		return player == null ? null : PlayerAttachments.get(player.getObjectId(), TARGET);
	}
	
	public static void setTarget(Player player, String name)
	{
		if (player == null)
			return;
		if (name == null)
			PlayerAttachments.remove(player.getObjectId(), TARGET);
		else
			PlayerAttachments.put(player.getObjectId(), TARGET, name);
	}
	
	public static String duration(Player player)
	{
		return player == null ? null : PlayerAttachments.get(player.getObjectId(), DURATION);
	}
	
	public static void setDuration(Player player, String duration)
	{
		if (player == null)
			return;
		if (duration == null)
			PlayerAttachments.remove(player.getObjectId(), DURATION);
		else
			PlayerAttachments.put(player.getObjectId(), DURATION, duration);
	}
}
