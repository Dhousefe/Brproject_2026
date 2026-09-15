package ext.mods.extensions.api;

import ext.mods.gameserver.model.location.Location;

/**
 * Lightweight view of farm-event zone data for core call-sites.
 */
public interface IFarmZoneInfo
{
	boolean isActive();

	boolean isEnchanterZone();

	boolean isDwarvenOnly();

	Location getSpawnLocation();
}
