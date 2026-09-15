package ext.mods.extensions.api;

import ext.mods.gameserver.model.actor.Attackable;
import ext.mods.gameserver.model.actor.Player;

/**
 * Active dungeon run attached to Player/Monster (implemented by mod-dungeon).
 */
public interface IDungeonSession
{
	void onMobKill(Attackable attackable);

	void pauseForDisconnect(Player player);

	void resumeForReconnect(Player player);
}
