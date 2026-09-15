package ext.mods.commons.mmocore;

/**
 * Minimal effect snapshot for packet writes (avoids gameserver {@code EffectHolder} coupling).
 */
public interface EffectView
{
	int id();
	
	int level();
	
	int duration();
}
