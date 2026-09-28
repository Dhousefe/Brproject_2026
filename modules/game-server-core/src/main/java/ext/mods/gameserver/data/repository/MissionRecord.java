package ext.mods.gameserver.data.repository;

/** Persisted mission progress. */
public record MissionRecord(String type, int level, int value)
{
}
