/*
* Copyleft © 2024-2026 L2Brproject
* * This file is part of L2Brproject derived from aCis409/RusaCis3.8
* * L2Brproject is free software: you can redistribute it and/or modify it
* under the terms of the GNU General Public License as published by the
* Free Software Foundation, either version 3 of the License.
* * L2Brproject is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* General Public License for more details.
* * You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*/
package ext.mods.gameserver.model.records.custom;

import ext.mods.gameserver.data.manager.SpawnManager;
import ext.mods.gameserver.model.World;
import ext.mods.gameserver.model.actor.Npc;
import ext.mods.gameserver.model.location.Location;
import ext.mods.gameserver.model.location.SpawnLocation;
import ext.mods.gameserver.model.spawn.ASpawn;
import ext.mods.gameserver.model.spawn.MultiSpawn;
import ext.mods.gameserver.model.spawn.SpawnData;

/**
 * Holds drop/spoil information for a recipe from a specific monster.
 */
public record RecipeDropHolder(
	int npcId,
	String npcName,
	int npcLevel,
	boolean isSpoil,
	double chancePercent,
	int minDrop,
	int maxDrop,
	int locX,
	int locY,
	int locZ
)
{
	public boolean hasValidLocation()
	{
		if (locX != 0 || locY != 0 || locZ != 0)
			return true;

		return resolveLocation() != null;
	}

	public Location toLocation()
	{
		if (locX != 0 || locY != 0 || locZ != 0)
			return new Location(locX, locY, locZ);

		return resolveLocation();
	}

	private Location resolveLocation()
	{
		try
		{
			// 1. Check active monster in World
			Npc worldNpc = World.getInstance().getNpc(npcId);
			if (worldNpc != null && !worldNpc.isDecayed())
				return new Location(worldNpc.getX(), worldNpc.getY(), worldNpc.getZ());

			// 2. Check SpawnManager spawns
			ASpawn spawn = SpawnManager.getInstance().getSpawn(npcId);
			if (spawn != null)
			{
				if (spawn.getNpc() != null && !spawn.getNpc().isDecayed())
					return new Location(spawn.getNpc().getX(), spawn.getNpc().getY(), spawn.getNpc().getZ());

				if (spawn instanceof MultiSpawn multi)
				{
					int[][] coords = multi.getCoords();
					if (coords != null && coords.length > 0)
						return new Location(coords[0][0], coords[0][1], coords[0].length > 2 ? coords[0][2] : 0);
				}

				SpawnData sData = spawn.getSpawnData();
				if (sData != null && (sData.getX() != 0 || sData.getY() != 0 || sData.getZ() != 0))
					return new Location(sData.getX(), sData.getY(), sData.getZ());

				SpawnLocation sLoc = spawn.getSpawnLocation();
				if (sLoc != null)
					return new Location(sLoc.getX(), sLoc.getY(), sLoc.getZ());
			}
		}
		catch (Exception ignored)
		{
		}
		return null;
	}
}
