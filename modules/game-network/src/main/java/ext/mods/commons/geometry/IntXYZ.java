package ext.mods.commons.geometry;

/**
 * Minimal 3D point view for networking helpers (avoids gameserver {@code Location} coupling).
 */
public interface IntXYZ
{
	int getX();
	
	int getY();
	
	int getZ();
}
