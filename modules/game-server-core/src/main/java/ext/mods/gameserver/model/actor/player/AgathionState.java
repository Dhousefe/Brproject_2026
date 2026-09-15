package ext.mods.gameserver.model.actor.player;

import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.instance.Agathion;

/** Current agathion companion + summon cooldown. */
public final class AgathionState
{
	public static final PlayerAttachments.Key<Agathion> CURRENT = PlayerAttachments.Key.of("agathion.current");
	public static final PlayerAttachments.Key<Long> LAST_SUMMON_TIME = PlayerAttachments.Key.of("agathion.lastSummonTime");
	
	private AgathionState()
	{
	}
	
	public static Agathion get(Player player)
	{
		return player == null ? null : PlayerAttachments.get(player.getObjectId(), CURRENT);
	}
	
	public static void set(Player player, Agathion agathion)
	{
		if (player == null)
			return;
		if (agathion == null)
			PlayerAttachments.remove(player.getObjectId(), CURRENT);
		else
			PlayerAttachments.put(player.getObjectId(), CURRENT, agathion);
	}
	
	public static long lastSummonTime(Player player)
	{
		if (player == null)
			return 0L;
		final Long v = PlayerAttachments.get(player.getObjectId(), LAST_SUMMON_TIME);
		return v == null ? 0L : v;
	}
	
	public static void setLastSummonTime(Player player, long time)
	{
		if (player == null)
			return;
		if (time <= 0L)
			PlayerAttachments.remove(player.getObjectId(), LAST_SUMMON_TIME);
		else
			PlayerAttachments.put(player.getObjectId(), LAST_SUMMON_TIME, time);
	}
	
	/** Delete entity and clear attachment if it matches current. */
	public static void delete(Player player, Agathion agathion)
	{
		if (agathion != null)
		{
			agathion.deleteMe();
			if (player != null && agathion == get(player))
				set(player, null);
		}
	}
}
