package ext.mods.gameserver.model.actor.player;

import ext.mods.extensions.api.IDungeonSession;
import ext.mods.gameserver.model.actor.Player;

/** Dungeon session pointer for a player (attachments). */
public final class DungeonState
{
	public static final PlayerAttachments.Key<IDungeonSession> SESSION = PlayerAttachments.Key.of("dungeon.session");
	
	private DungeonState()
	{
	}
	
	public static IDungeonSession get(Player player)
	{
		return player == null ? null : PlayerAttachments.get(player.getObjectId(), SESSION);
	}
	
	public static void set(Player player, IDungeonSession session)
	{
		if (player == null)
			return;
		if (session == null)
			PlayerAttachments.remove(player.getObjectId(), SESSION);
		else
			PlayerAttachments.put(player.getObjectId(), SESSION, session);
	}
}
