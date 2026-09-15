package ext.mods.extensions.api;

import ext.mods.gameserver.model.actor.Player;

/**
 * Active tournament battle session (implemented by mod-tour).
 */
public interface ITournamentBattle
{
	int getId();

	void pauseForDisconnect(Player player);

	void resumeForReconnect(Player player);
}
