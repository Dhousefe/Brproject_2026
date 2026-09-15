package ext.mods.gameserver.model.actor.player;

import java.util.List;

import ext.mods.extensions.api.ITournamentBattle;
import ext.mods.gameserver.model.actor.Player;

/**
 * Tournament runtime state keyed by player objectId (PlayerAttachments).
 * Prefer this over methods on {@link Player}.
 */
public final class TournamentState
{
	public static final PlayerAttachments.Key<Boolean> IN = PlayerAttachments.Key.of("tournament.in");
	public static final PlayerAttachments.Key<ITournamentBattle> BATTLE = PlayerAttachments.Key.of("tournament.battle");
	public static final PlayerAttachments.Key<List<Player>> OPPONENTS = PlayerAttachments.Key.of("tournament.opponents");
	
	private TournamentState()
	{
	}
	
	public static boolean isIn(Player player)
	{
		if (player == null)
			return false;
		final Boolean v = PlayerAttachments.get(player.getObjectId(), IN);
		return v != null && v;
	}
	
	public static void setIn(Player player, boolean value)
	{
		if (player == null)
			return;
		if (value)
			PlayerAttachments.put(player.getObjectId(), IN, Boolean.TRUE);
		else
			PlayerAttachments.remove(player.getObjectId(), IN);
	}
	
	public static ITournamentBattle battle(Player player)
	{
		return player == null ? null : PlayerAttachments.get(player.getObjectId(), BATTLE);
	}
	
	public static void setBattle(Player player, ITournamentBattle battle)
	{
		if (player == null)
			return;
		if (battle == null)
			PlayerAttachments.remove(player.getObjectId(), BATTLE);
		else
			PlayerAttachments.put(player.getObjectId(), BATTLE, battle);
	}
	
	public static List<Player> opponents(Player player)
	{
		return player == null ? null : PlayerAttachments.get(player.getObjectId(), OPPONENTS);
	}
	
	public static void setOpponents(Player player, List<Player> opponents)
	{
		if (player == null)
			return;
		if (opponents == null)
			PlayerAttachments.remove(player.getObjectId(), OPPONENTS);
		else
			PlayerAttachments.put(player.getObjectId(), OPPONENTS, opponents);
	}
}
