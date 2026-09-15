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
package ext.mods.gameserver.handler.admincommandhandlers;

import java.util.StringTokenizer;

import ext.mods.gameserver.handler.IAdminCommandHandler;
import ext.mods.gameserver.model.World;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.network.serverpackets.ExRedSky;
import ext.mods.gameserver.network.serverpackets.SSQInfo;
import ext.mods.gameserver.taskmanager.GameTimeTaskManager;

/**
 * Handlers administrativos para controle de horário do servidor, transição de dia/noite e efeitos de céu celestial.
 * Totalmente compatível com cliente Fermata e clientes clássicos Interlude (Zero Regressão).
 */
public class AdminFermataSkyTime implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_settime",
		"admin_sunrise",
		"admin_sunset",
		"admin_setsky",
		"admin_redsky"
	};
	
	@Override
	public void useAdminCommand(String command, Player player)
	{
		if (player == null)
			return;
		
		final StringTokenizer st = new StringTokenizer(command, " ");
		final String actualCommand = st.nextToken();
		
		switch (actualCommand)
		{
			case "admin_settime":
			{
				if (!st.hasMoreTokens())
				{
					player.sendMessage("Uso: //settime <horas 0-23> [minutos 0-59]");
					player.sendMessage("Horário atual: " + GameTimeTaskManager.getInstance().getGameTimeFormated() + " (" + (GameTimeTaskManager.getInstance().isNight() ? "Noite" : "Dia") + ")");
					return;
				}
				
				try
				{
					int hours = Integer.parseInt(st.nextToken());
					int minutes = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 0;
					
					if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59)
					{
						player.sendMessage("Horário inválido. Horas: 0-23, Minutos: 0-59.");
						return;
					}
					
					GameTimeTaskManager.getInstance().setGameTime(hours, minutes);
					player.sendMessage("Horário do mundo alterado para: " + GameTimeTaskManager.getInstance().getGameTimeFormated() + " (" + (GameTimeTaskManager.getInstance().isNight() ? "Noite" : "Dia") + ")");
				}
				catch (Exception e)
				{
					player.sendMessage("Uso: //settime <horas 0-23> [minutos 0-59]");
				}
				break;
			}
			
			case "admin_sunrise":
			{
				GameTimeTaskManager.getInstance().forceSunRise();
				player.sendMessage("Nascer do sol ativado (06:00). Iluminação diurna sincronizada com todos os clientes.");
				break;
			}
			
			case "admin_sunset":
			{
				GameTimeTaskManager.getInstance().forceSunSet();
				player.sendMessage("Pôr do sol ativado (00:00). Iluminação noturna sincronizada com todos os clientes.");
				break;
			}
			
			case "admin_setsky":
			{
				if (!st.hasMoreTokens())
				{
					player.sendMessage("Uso: //setsky <regular|dawn|dusk|red>");
					return;
				}
				
				final String skyType = st.nextToken().toLowerCase();
				final SSQInfo ssqPacket = switch (skyType)
				{
					case "regular", "normal" -> SSQInfo.REGULAR_SKY_PACKET;
					case "dusk" -> SSQInfo.DUSK_SKY_PACKET;
					case "dawn" -> SSQInfo.DAWN_SKY_PACKET;
					case "red" -> SSQInfo.RED_SKY_PACKET;
					default -> null;
				};
				
				if (ssqPacket == null)
				{
					player.sendMessage("Tipo de céu desconhecido: " + skyType + ". Use: regular, dusk, dawn, red.");
					return;
				}
				
				for (Player p : World.getInstance().getPlayers())
				{
					if (p.isOnline())
						p.sendPacket(ssqPacket);
				}
				player.sendMessage("Skybox celestial alterado para: " + skyType.toUpperCase() + " para todos os jogadores online.");
				break;
			}
			
			case "admin_redsky":
			{
				if (!st.hasMoreTokens())
				{
					player.sendMessage("Uso: //redsky <segundos>");
					return;
				}
				
				try
				{
					int seconds = Integer.parseInt(st.nextToken());
					if (seconds <= 0)
					{
						player.sendMessage("Duração deve ser maior que 0 segundos.");
						return;
					}
					
					final ExRedSky redSky = new ExRedSky(seconds);
					for (Player p : World.getInstance().getPlayers())
					{
						if (p.isOnline())
							p.sendPacket(redSky);
					}
					player.sendMessage("Céu vermelho ativado por " + seconds + " segundos.");
				}
				catch (Exception e)
				{
					player.sendMessage("Uso: //redsky <segundos>");
				}
				break;
			}
		}
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
