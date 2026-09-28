package ext.mods.gameserver.data.repository;

/**
 * Database representation of the mutable state stored in the characters row.
 * The record contains values only; JDBC types and SQL remain in the adapter.
 */
public record CharacterState(
	String accountName,
	int objectId,
	String charName,
	int level,
	int maxHp,
	double curHp,
	int maxCp,
	double curCp,
	int maxMp,
	double curMp,
	int face,
	int hairStyle,
	int hairColor,
	int sex,
	long exp,
	int sp,
	int race,
	int classId,
	int baseClass,
	String title,
	int accessLevel,
	long lastAccess,
	int heading,
	int x,
	int y,
	int z,
	long expBeforeDeath,
	int karma,
	int pvpKills,
	int pkKills,
	int clanId,
	long deleteTime,
	int online,
	int inSevenSignsDungeon,
	int wantsPeace,
	long onlineTime,
	int punishmentLevel,
	long punishmentTimer,
	int nobless,
	int powerGrade,
	int subpledge,
	int levelJoinedAcademy,
	int apprentice,
	int sponsor,
	int varkaKetraAlly,
	long clanJoinExpiryTime,
	long clanCreateExpiryTime,
	int deathPenaltyLevel,
	long heroUntil,
	int recHave,
	int recLeft)
{
	/** Creates the minimal row used when a new character is created. */
	public static CharacterState initial(String accountName, int objectId, String charName, int level, int maxHp, double curHp, int maxCp, double curCp, int maxMp, double curMp, int face, int hairStyle, int hairColor, int sex, long exp, int sp, int race, int classId, int baseClass, String title, int accessLevel)
	{
		return new CharacterState(accountName, objectId, charName, level, maxHp, curHp, maxCp, curCp, maxMp, curMp, face, hairStyle, hairColor, sex, exp, sp, race, classId, baseClass, title, accessLevel, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
	}
}
