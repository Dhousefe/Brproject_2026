package ext.mods.gameserver.model.actor.player;

import ext.mods.gameserver.model.actor.Player;

/** Temporary hero-aura flag from player-god mod. */
public final class PlayerGodState
{
	public static final PlayerAttachments.Key<Boolean> HERO_AURA = PlayerAttachments.Key.of("playergod.heroAura");
	
	private PlayerGodState()
	{
	}
	
	public static boolean hasHeroAura(Player player)
	{
		if (player == null)
			return false;
		final Boolean v = PlayerAttachments.get(player.getObjectId(), HERO_AURA);
		return v != null && v;
	}
	
	public static void setHeroAura(Player player, boolean hero)
	{
		if (player == null)
			return;
		if (hero)
			PlayerAttachments.put(player.getObjectId(), HERO_AURA, Boolean.TRUE);
		else
			PlayerAttachments.remove(player.getObjectId(), HERO_AURA);
	}
}
