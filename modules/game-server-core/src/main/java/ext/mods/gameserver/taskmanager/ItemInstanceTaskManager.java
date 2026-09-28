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
package ext.mods.gameserver.taskmanager;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import ext.mods.commons.logging.CLogger;
import ext.mods.commons.pool.ThreadPool;

import ext.mods.gameserver.data.service.ItemPersistenceService;
import ext.mods.gameserver.model.item.instance.ItemInstance;

/**
 * Lazy save items upon database. Delete old items.
 */
public class ItemInstanceTaskManager implements Runnable
{
	private static final CLogger LOGGER = new CLogger(ItemInstanceTaskManager.class.getName());
	
	private final Set<ItemInstance> _items = ConcurrentHashMap.newKeySet();
	
	protected ItemInstanceTaskManager()
	{
		ThreadPool.scheduleAtFixedRate(this, 60000L, 60000L);
	}
	
	@Override
	public final void run()
	{
		updateItems(_items);
	}
	
	/**
	 * Add an {@link ItemInstance} into the {@link Set}.
	 * @param item : The {@link ItemInstance} to add.
	 */
	public void add(ItemInstance item)
	{
		_items.add(item);
	}
	
	/**
	 * @param item : The {@link ItemInstance} to check.
	 * @return True if the items {@link Set} contains the specified element, or false otherwise.
	 */
	public boolean contains(ItemInstance item)
	{
		return _items.contains(item);
	}
	
	/**
	 * Remove {@link ItemInstance}s based on the set parameter.
	 * @param items : The {@link Set} of {@link ItemInstance}s to remove.
	 */
	public void removeItems(Set<ItemInstance> items)
	{
		_items.removeAll(items);
	}
	
	/**
	 * Run the database process for the specific {@link ItemInstance} {@link Set}.<br>
	 * <br>
	 * Clear the {@link Set} afterwards.
	 * @param items : The {@link Set} of {@link ItemInstance}s to affect.
	 */
	public void updateItems(Set<ItemInstance> items)
	{
		if (items.isEmpty())
			return;
		
		try
		{
			ItemPersistenceService.saveItems(items);
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't manage items.", e);
		}
		
		items.clear();
	}

	/**
	 * Manually trigger the task. Used by shutdown process.
	 */
	public void save()
	{
		updateItems(_items);
	}
	
	public static final ItemInstanceTaskManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final ItemInstanceTaskManager INSTANCE = new ItemInstanceTaskManager();
	}
}
