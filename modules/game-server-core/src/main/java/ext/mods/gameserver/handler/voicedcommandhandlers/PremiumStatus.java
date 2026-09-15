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
package ext.mods.gameserver.handler.voicedcommandhandlers;

import java.text.SimpleDateFormat;

import ext.mods.Config;
import ext.mods.gameserver.handler.IVoicedCommandHandler;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.network.serverpackets.NpcHtmlMessage;
import ext.mods.config.ConfigProject;
import ext.mods.config.ConfigRates;

public class PremiumStatus implements IVoicedCommandHandler
{
	private static final String[] VOICED_COMMANDS =
	{
		"premium"
	};
	
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm");
	
	@Override
	public boolean useVoicedCommand(String command, Player player, String target)
	{
		if (!ConfigProject.USE_PREMIUM_SERVICE)
		{
			player.sendMessage(player.getSysString(10_200));
			return false;
		}
		
		if ("premium".equals(command))
		{
			NpcHtmlMessage htm = new NpcHtmlMessage(0);
			htm.setFile(player.getLocale(), (player.getPremiumService() == 0 ? "html/mods/premium/normal.htm" : "html/mods/premium/premium.htm"));
			
			htm.replace("%rate_xp%", ConfigRates.RATE_XP);
			htm.replace("%rate_sp%", ConfigRates.RATE_SP);
			htm.replace("%rate_drop%", ConfigRates.RATE_DROP_ITEMS);
			htm.replace("%rate_spoil%", ConfigRates.RATE_DROP_SPOIL);
			htm.replace("%rate_currency%", ConfigRates.RATE_DROP_CURRENCY);
			htm.replace("%current%", String.valueOf(DATE_FORMAT.format(System.currentTimeMillis())));
			htm.replace("%prem_rate_xp%", ConfigRates.PREMIUM_RATE_XP);
			htm.replace("%prem_rate_sp%", ConfigRates.PREMIUM_RATE_SP);
			htm.replace("%prem_rate_drop%", ConfigRates.PREMIUM_RATE_DROP_ITEMS);
			htm.replace("%prem_rate_spoil%", ConfigRates.PREMIUM_RATE_DROP_SPOIL);
			htm.replace("%prem_currency%", ConfigRates.PREMIUM_RATE_DROP_CURRENCY);
			if (player.getPremiumService() != 0)
				htm.replace("%expires%", String.valueOf(DATE_FORMAT.format(player.getPremServiceData())));
			
			player.sendPacket(htm);
			return true;
		}
		return false;
	}
	
	@Override
	public String[] getVoicedCommandList()
	{
		return VOICED_COMMANDS;
	}
}