package ext.mods.gameserver.listener

import ext.mods.commons.logging.CLogger
import ext.mods.gameapi.clan.ClanBroadcaster
import ext.mods.gameserver.model.actor.Player

/**
 * Notifies the Site API broadcaster when clan members come online/offline.
 * This keeps the per-clan member cache in ClanBroadcaster up-to-date for fast delivery.
 *
 * Attach this listener to game-server player login/logout events.
 */
object ClanMembershipListener {
    private val LOGGER = CLogger(ClanMembershipListener::class.java.name)

    fun onPlayerLogin(player: Player?) {
        if (player == null) return
        try {
            val clanId = player.clanId
            if (clanId > 0) {
                ClanBroadcaster.onPlayerLogin(clanId)
            }
        } catch (e: Exception) {
            LOGGER.warn("[ClanBroadcaster] Failed to notify login", e)
        }
    }

    fun onPlayerLogout(player: Player?) {
        if (player == null) return
        try {
            val clanId = player.clanId
            if (clanId > 0) {
                ClanBroadcaster.onPlayerLogout(clanId)
            }
        } catch (e: Exception) {
            LOGGER.warn("[ClanBroadcaster] Failed to notify logout", e)
        }
    }
}
