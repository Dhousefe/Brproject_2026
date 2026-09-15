package ext.mods.gameserver.model.actor.player;

import ext.mods.gameserver.model.actor.Player;

/** Battle-boss rumble / event membership. */
public final class BattleBossState
{
	public static final PlayerAttachments.Key<Integer> RUMBLE_ID = PlayerAttachments.Key.of("battleboss.rumbleId");
	public static final PlayerAttachments.Key<Integer> EVENT_ID = PlayerAttachments.Key.of("battleboss.eventId");
	
	private BattleBossState()
	{
	}
	
	public static int rumbleId(Player player)
	{
		if (player == null)
			return 0;
		final Integer v = PlayerAttachments.get(player.getObjectId(), RUMBLE_ID);
		return v == null ? 0 : v;
	}
	
	public static void setRumbleId(Player player, int rumbleId)
	{
		if (player == null)
			return;
		if (rumbleId <= 0)
			PlayerAttachments.remove(player.getObjectId(), RUMBLE_ID);
		else
			PlayerAttachments.put(player.getObjectId(), RUMBLE_ID, rumbleId);
	}
	
	public static boolean isInRumble(Player player)
	{
		return rumbleId(player) > 0;
	}
	
	public static int eventId(Player player)
	{
		if (player == null)
			return -1;
		final Integer v = PlayerAttachments.get(player.getObjectId(), EVENT_ID);
		return v == null ? -1 : v;
	}
	
	public static void setEventId(Player player, int eventId)
	{
		if (player == null)
			return;
		if (eventId < 0)
			PlayerAttachments.remove(player.getObjectId(), EVENT_ID);
		else
			PlayerAttachments.put(player.getObjectId(), EVENT_ID, eventId);
	}
	
	public static boolean isInEvent(Player player)
	{
		return eventId(player) > 0;
	}
	
	public static void leave(Player player)
	{
		setEventId(player, -1);
	}
}
