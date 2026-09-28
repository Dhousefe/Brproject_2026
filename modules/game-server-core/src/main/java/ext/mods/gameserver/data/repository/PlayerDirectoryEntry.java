package ext.mods.gameserver.data.repository;

/** Read model used to initialize the in-memory character directory. */
public record PlayerDirectoryEntry(int objectId, String accountName, String playerName, int accessLevel)
{
}
