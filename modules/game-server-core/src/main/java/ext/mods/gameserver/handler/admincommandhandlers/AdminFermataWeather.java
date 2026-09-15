package ext.mods.gameserver.handler.admincommandhandlers;

import java.util.StringTokenizer;

import ext.mods.gameserver.data.manager.FermataWeatherManager;
import ext.mods.gameserver.handler.IAdminCommandHandler;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.fermata.WeatherProfile;

/**
 * Comando administrativo para controle em tempo real do motor de clima do cliente Fermata.
 */
public class AdminFermataWeather implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_setweather",
		"admin_restoreweather",
		"admin_myweather",
		"admin_myweather_restore"
	};
	
	@Override
	public void useAdminCommand(String command, Player player)
	{
		if (player == null)
		{
			return;
		}
		
		final StringTokenizer st = new StringTokenizer(command, " ");
		final String actualCommand = st.nextToken();
		
		switch (actualCommand)
		{
			case "admin_setweather":
			{
				if (!st.hasMoreTokens())
				{
					player.sendMessage("Uso: //setweather <clear|cloudy|rain|storm|snow|blizzard>");
					return;
				}
				
				final String presetName = st.nextToken().toLowerCase();
				final WeatherProfile profile = resolveProfile(presetName);
				if (profile == null)
				{
					player.sendMessage("Perfil desconhecido: " + presetName + ". Use: clear, cloudy, rain, storm, snow, blizzard.");
					return;
				}
				
				FermataWeatherManager.getInstance().setGlobalWeather(profile);
				player.sendMessage("Clima global Fermata definido para: " + presetName.toUpperCase());
				break;
			}
			
			case "admin_restoreweather":
			{
				FermataWeatherManager.getInstance().restoreGlobalWeather();
				player.sendMessage("Clima global Fermata restaurado para o controle do cliente.");
				break;
			}
			
			case "admin_myweather":
			{
				if (!st.hasMoreTokens())
				{
					player.sendMessage("Uso: //myweather <clear|cloudy|rain|storm|snow|blizzard>");
					return;
				}
				
				final String presetName = st.nextToken().toLowerCase();
				final WeatherProfile profile = resolveProfile(presetName);
				if (profile == null)
				{
					player.sendMessage("Perfil desconhecido: " + presetName);
					return;
				}
				
				final boolean ok = FermataWeatherManager.getInstance().setWeatherForPlayer(player, profile);
				if (ok)
				{
					player.sendMessage("Seu clima Fermata foi alterado para: " + presetName.toUpperCase());
				}
				else
				{
					player.sendMessage("Seu cliente não possui o recurso de clima Fermata ativo (Bit 1 desligado).");
				}
				break;
			}
			
			case "admin_myweather_restore":
			{
				final boolean ok = FermataWeatherManager.getInstance().restoreWeatherForPlayer(player);
				if (ok)
				{
					player.sendMessage("Seu clima Fermata foi restaurado para o padrão do cliente.");
				}
				else
				{
					player.sendMessage("Seu cliente não possui o recurso de clima Fermata ativo.");
				}
				break;
			}
		}
	}
	
	private static WeatherProfile resolveProfile(String name)
	{
		return switch (name)
		{
			case "clear", "sunny" -> WeatherProfile.CLEAR;
			case "cloudy", "overcast" -> WeatherProfile.CLOUDY;
			case "rain", "light_rain" -> WeatherProfile.LIGHT_RAIN;
			case "storm", "thunderstorm", "heavy_rain" -> WeatherProfile.THUNDERSTORM;
			case "snow", "light_snow" -> WeatherProfile.LIGHT_SNOW;
			case "blizzard", "heavy_snow" -> WeatherProfile.BLIZZARD;
			default -> null;
		};
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
