package ext.mods.gameserver.data.repository;

/** Persistent registration data exchanged between LoginServer and GameServer. */
public record GameServerRegistration(int serverId, String hexId, String host)
{
}
