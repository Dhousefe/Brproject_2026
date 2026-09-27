package ext.mods.gameserver.data.repository;

/** Persisted effect or reuse state for one skill. */
public record SkillSaveRecord(int skillId, int skillLevel, int effectCount, int effectCurrentTime, long reuseDelay, long systemTime, int restoreType, int buffIndex, boolean npc)
{
}
