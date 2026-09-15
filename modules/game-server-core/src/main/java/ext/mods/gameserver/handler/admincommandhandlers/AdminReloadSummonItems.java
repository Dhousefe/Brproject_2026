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
package ext.mods.gameserver.handler.admincommandhandlers;

import ext.mods.commons.logging.CLogger;
import ext.mods.extensions.hooks.SummonMobHooks;
import ext.mods.gameserver.handler.IAdminCommandHandler;
import ext.mods.gameserver.model.actor.Player;

/**
 * Admin command handler para recarregar configurações de summon items
 *
 * @author Dhousefe
 */
public class AdminReloadSummonItems implements IAdminCommandHandler
{
	private static final CLogger LOGGER = new CLogger(AdminReloadSummonItems.class.getName());

	private static final String[] ADMIN_COMMANDS =
	{
		"admin_reload_summon_items",
		"admin_reload_summon_mobs"
	};
	
	@Override
	public void useAdminCommand(String command, Player activeChar)
	{
		if (command.startsWith("admin_reload_summon_items") || command.startsWith("admin_reload_summon_mobs"))
		{
			try
			{
				SummonMobHooks.get().reload();
				
				final int loadedCount = SummonMobHooks.get().getLoadedCount();
				
				activeChar.sendMessage("Summon items configuration reloaded successfully!");
				activeChar.sendMessage("Loaded " + loadedCount + " summon item configurations.");
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Error reloading summon items configuration: " + e.getMessage());
				LOGGER.error("Error reloading summon items", e);
			}
		}
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
