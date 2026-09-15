package ext.mods.extensions.api;

/**
 * Agathion NPC binding data used by core item/skill handlers.
 */
public interface IAgathionSpec
{
	int getNpcId();

	boolean getRunSpeed();

	int getHealAmount();

	int getHealDelay();

	int getRandomAnimDelay();

	int getFollowCheckDelay();
}
