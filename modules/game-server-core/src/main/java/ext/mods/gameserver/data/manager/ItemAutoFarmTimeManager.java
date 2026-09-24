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
package ext.mods.gameserver.data.manager;

import java.util.HashMap;
import java.util.Map;

import ext.mods.Config;
import ext.mods.commons.config.ExProperties;
import ext.mods.commons.logging.CLogger;

/**
 * Administra itens configurados em items.properties que concedem tempo extra de AutoFarm.
 * Suporta configuração por item com o prefixo: ItemAutoFarmTime_<itemId>=<hours> ou <minutes>,<hours>
 */
public final class ItemAutoFarmTimeManager
{
	private static final CLogger LOGGER = new CLogger(ItemAutoFarmTimeManager.class.getName());
	private static final String ITEMS_FILE = Config.CONFIG_PATH.resolve("items.properties").toString();
	private static final String PREFIX = "ItemAutoFarmTime_";

	public static class AutoFarmTimeConfig
	{
		private final int _minutes;
		private final int _hours;

		public AutoFarmTimeConfig(int minutes, int hours)
		{
			_minutes = minutes;
			_hours = hours;
		}

		public int getMinutes()
		{
			return _minutes;
		}

		public int getHours()
		{
			return _hours;
		}

		public long toMilliseconds()
		{
			return (_hours * 3600L + _minutes * 60L) * 1000L;
		}
	}

	private final Map<Integer, AutoFarmTimeConfig> _itemConfigs = new HashMap<>();

	protected ItemAutoFarmTimeManager()
	{
		load();
	}

	public void load()
	{
		_itemConfigs.clear();
		try
		{
			final ExProperties props = Config.initProperties(ITEMS_FILE);
			for (final String key : props.stringPropertyNames())
			{
				if (!key.startsWith(PREFIX))
					continue;

				final String idStr = key.substring(PREFIX.length()).trim();
				final int itemId;
				try
				{
					itemId = Integer.parseInt(idStr);
				}
				catch (NumberFormatException e)
				{
					LOGGER.warn("ItemAutoFarmTimeManager: Invalid item ID in items.properties: {}", idStr);
					continue;
				}

				final String value = props.getProperty(key, "1").trim();
				final String[] parts = value.split("[,;]+");
				final int minutes;
				final int hours;
				if (parts.length >= 2)
				{
					minutes = parseInt(parts[0].trim(), 0);
					hours = parseInt(parts[1].trim(), 0);
				}
				else
				{
					minutes = 0;
					hours = parseInt(parts[0].trim(), 1);
				}

				if (minutes > 0 || hours > 0)
				{
					_itemConfigs.put(itemId, new AutoFarmTimeConfig(minutes, hours));
				}
			}
			LOGGER.info("ItemAutoFarmTimeManager: loaded {} item configurations.", _itemConfigs.size());
		}
		catch (Exception e)
		{
			LOGGER.warn("ItemAutoFarmTimeManager: Failed to load items.properties: {}", e.getMessage());
		}
	}

	private static int parseInt(String s, int defaultVal)
	{
		try
		{
			return Integer.parseInt(s);
		}
		catch (NumberFormatException e)
		{
			return defaultVal;
		}
	}

	public boolean isConfigured(int itemId)
	{
		return _itemConfigs.containsKey(itemId);
	}

	public AutoFarmTimeConfig getConfig(int itemId)
	{
		return _itemConfigs.get(itemId);
	}

	public long getAddedTimeMs(int itemId)
	{
		final AutoFarmTimeConfig config = _itemConfigs.get(itemId);
		return config != null ? config.toMilliseconds() : 0L;
	}

	public static ItemAutoFarmTimeManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder
	{
		protected static final ItemAutoFarmTimeManager INSTANCE = new ItemAutoFarmTimeManager();
	}
}
