package ext.mods.gameserver.data.repository;

/**
 * Read model used by the character-selection screen.
 *
 * <p>The selection packet must not know how characters, subclasses, equipped
 * items or augmentations are stored. The JDBC adapter composes this snapshot
 * before it crosses the repository boundary.</p>
 */
public record CharacterSelection(int objectId, String name, long deleteTime, int accessLevel, int level, int maxHp, double currentHp, int maxMp, double currentMp, int karma, int pkKills, int pvpKills, int face, int hairStyle, int hairColor, int sex, long exp, int sp, int clanId, int race, int x, int y, int z, int classId, int baseClassId, long lastAccess, int[][] paperdoll, int augmentationId)
{
}
