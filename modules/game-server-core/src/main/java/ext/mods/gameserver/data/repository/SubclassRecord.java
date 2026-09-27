package ext.mods.gameserver.data.repository;

/** Persisted state for one player subclass slot. */
public record SubclassRecord(int classId, int classIndex, long exp, int sp, int level)
{
}
