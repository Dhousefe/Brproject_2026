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
* Our main Developers, Dhousefe-L2JBR, Agazes33, Ban-L2jDev, Warman, SrEli.
* Our special thanks, Nattan Felipe, Diego Fonseca, Junin, ColdPlay, Denky, MecBew, Localhost, MundvayneHELLBOY, 
* SonecaL2, Eduardo.SilvaL2J, biLL, xpower, xTech, kakuzo, Tiagorosendo, Schuster, LucasStark, damedd
* as a contribution for the forum L2JBrasil.com
 */
package ext.mods.gameserver.enums;

import ext.mods.Config;
import ext.mods.gameserver.model.actor.Npc;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.config.ConfigNpcs;
import ext.mods.config.ConfigRates;

public enum DropType
{
	SPOIL,
	CURRENCY,
	DROP,
	HERB,
	SEALSTONE;
	
	public double getDropRate(Player player, Npc npc, boolean isRaid, boolean isGrand)
	{
		switch (this)
		{
			case SPOIL:
				if (player.getPremiumService() == 1 && npc.isChampion())
					return ConfigNpcs.PREMIUM_CHAMPION_SPOIL_REWARDS;
				else if (npc.isChampion())
					return ConfigNpcs.CHAMPION_SPOIL_REWARDS;
				
				if (player.getPremiumService() == 1)
					return ConfigRates.PREMIUM_RATE_DROP_SPOIL;
				
				return ConfigRates.RATE_DROP_SPOIL;
			
			case CURRENCY:
				if (player.getPremiumService() == 1 && npc.isChampion())
					return ConfigNpcs.PREMIUM_CHAMPION_ADENAS_REWARDS;
				else if (npc.isChampion())
					return ConfigNpcs.CHAMPION_ADENAS_REWARDS;
				
				if (player.getPremiumService() == 1)
					return ConfigRates.PREMIUM_RATE_DROP_CURRENCY;
				
				return ConfigRates.RATE_DROP_CURRENCY;
			
			case DROP:
				if (player.getPremiumService() == 1 && npc.isChampion())
					return ConfigNpcs.PREMIUM_CHAMPION_REWARDS;
				else if (npc.isChampion())
					return ConfigNpcs.CHAMPION_REWARDS;
				
				if (player.getPremiumService() == 1)
				{
					if (isGrand)
						return ConfigRates.PREMIUM_RATE_DROP_ITEMS_BY_GRAND;
					
					if (isRaid)
						return ConfigRates.PREMIUM_RATE_DROP_ITEMS_BY_RAID;
					
					return ConfigRates.PREMIUM_RATE_DROP_ITEMS;
				}
				else
				{
					if (isGrand)
						return ConfigRates.RATE_DROP_ITEMS_BY_GRAND;
					
					if (isRaid)
						return ConfigRates.RATE_DROP_ITEMS_BY_RAID;
					
					return ConfigRates.RATE_DROP_ITEMS;
				}
				
			case HERB:
				return ConfigRates.RATE_DROP_HERBS;
			
			case SEALSTONE:
				if (player.getPremiumService() == 1 && npc.isChampion())
					return ConfigNpcs.PREMIUM_CHAMPION_SEALSTONE_REWARDS;
				else if (npc.isChampion())
					return ConfigNpcs.CHAMPION_SEALSTONE_REWARDS;
				
				if (player.getPremiumService() == 1)
					return ConfigRates.PREMIUM_RATE_DROP_SEAL_STONE;
				
				return ConfigRates.RATE_DROP_SEAL_STONE;
			
			default:
				return 0;
		}
	}
}