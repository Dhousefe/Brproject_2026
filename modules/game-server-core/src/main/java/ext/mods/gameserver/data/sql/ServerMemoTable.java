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
package ext.mods.gameserver.data.sql;

import java.util.Map;

import ext.mods.commons.data.MemoSet;
import ext.mods.commons.logging.CLogger;
import ext.mods.gameserver.data.service.ServerMemoPersistenceService;

/**
 * A global, server-size, container for variables of any type, which can be then saved/restored upon server restart. It extends {@link MemoSet}.
 */
public class ServerMemoTable extends MemoSet
{
	private static final long serialVersionUID = 1L;
	
	private static final CLogger LOGGER = new CLogger(ServerMemoTable.class.getName());
	
	protected ServerMemoTable()
	{
		try
		{
			for (Map.Entry<String, String> memo : ServerMemoPersistenceService.load().entrySet())
				put(memo.getKey(), memo.getValue());
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't restore server variables.", e);
		}
		LOGGER.info("Loaded {} server variables.", size());
	}
	
	@Override
	protected void onSet(String key, String value)
	{
		try
		{
			ServerMemoPersistenceService.upsert(key, value);
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't set {} server memo.", e, key);
		}
	}
	
	@Override
	protected void onUnset(String key)
	{
		try
		{
			ServerMemoPersistenceService.delete(key);
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't unset {} server memo.", e, key);
		}
	}
	
	public static final ServerMemoTable getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final ServerMemoTable INSTANCE = new ServerMemoTable();
	}
}
