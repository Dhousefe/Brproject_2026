package ext.mods.extensions.api;

/**
 * Core visual skin boundary for DressMe (implemented by mod-dressme).
 */
public interface IDressMeSkin
{
	int getSkillId();

	String getName();

	boolean isVip();

	int getChestId();

	int getLegsId();

	int getGlovesId();

	int getFeetId();

	int getHelmetId();

	String getWeaponTypeVisual();

	int getRightHandId();

	int getLeftHandId();

	int getTwoHandId();

	/** ARMOR or WEAPON (string form keeps core free of enum dependency). */
	String getVisualTypeName();
}
