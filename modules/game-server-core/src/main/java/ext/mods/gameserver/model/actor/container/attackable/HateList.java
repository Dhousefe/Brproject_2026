/*
 * Copyleft © 2024-2026 L2Brproject
 * This file is part of L2Brproject derived from aCis409/RusaCis3.8
 * L2Brproject is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License.
 *
 * L2Brproject is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package ext.mods.gameserver.model.actor.container.attackable;

import java.util.Set;

import ext.mods.commons.random.Rnd;
import ext.mods.gameserver.model.actor.Creature;
import ext.mods.gameserver.model.actor.Npc;
import ext.mods.gameserver.model.actor.instance.SiegeGuard;
import ext.mods.gameserver.scripting.script.ai.individual.DefaultNpc;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

/**
 * High-performance HateList using Fastutil Object2DoubleOpenHashMap.
 * Eliminates java.lang.Double boxing overhead and minimizes GC churn during combat.
 */
public class HateList
{
	private final Npc _owner;
	private final Object2DoubleOpenHashMap<Creature> _map;
	
	public HateList(Npc owner)
	{
		_owner = owner;
		_map = new Object2DoubleOpenHashMap<>(8, Hash.FAST_LOAD_FACTOR);
		_map.defaultReturnValue(0.0);
	}
	
	/**
	 * Add hate to the {@link Npc} owner, as stated by NPC variable SetAggressiveTime, linked to the {@link Creature} attacker.
	 * @param attacker : The {@link Creature} to hate.
	 */
	public synchronized void addDefaultHateInfo(Creature attacker)
	{
		int i0 = 0;
		
		if (_owner.isInMyTerritory())
		{
			final int aggressiveTime = DefaultNpc.getNpcIntAIParam(_owner, "SetAggressiveTime");
			if (aggressiveTime == -1)
			{
				if (_owner.getAI().getLifeTime() >= (Rnd.get(5) + 3))
					i0 = 1;
			}
			else if (aggressiveTime == 0)
				i0 = 1;
			else if (_owner.getAI().getLifeTime() > (aggressiveTime + Rnd.get(4)))
				i0 = 1;
			
			if (_owner.getAI().getLifeTime() > -1)
				i0 = 1;
		}
		
		addHateInfo(attacker, (_map.isEmpty() && i0 == 1) ? 300 : 100);
	}
	
	/**
	 * Add hate to the {@link Npc} owner, linked to the {@link Creature} attacker.
	 * @param attacker : The {@link Creature} to hate.
	 * @param hateAmount : The hate to add.
	 */
	public synchronized void addHateInfo(Creature attacker, double hateAmount)
	{
		if (attacker == null || (_owner instanceof SiegeGuard && attacker instanceof SiegeGuard))
			return;
		
		_map.addTo(attacker, hateAmount);
	}
	
	/**
	 * @return The most hated {@link Creature} of the {@link Npc} owner, or null if none is found.
	 */
	public synchronized Creature getMostHatedCreature()
	{
		if (_map.isEmpty() || _owner.isAlikeDead())
			return null;
		
		Creature mostHated = null;
		double maxHate = -Double.MAX_VALUE;
		
		final ObjectIterator<Object2DoubleMap.Entry<Creature>> it = _map.object2DoubleEntrySet().fastIterator();
		while (it.hasNext())
		{
			final Object2DoubleMap.Entry<Creature> entry = it.next();
			final double hate = entry.getDoubleValue();
			if (hate > maxHate)
			{
				maxHate = hate;
				mostHated = entry.getKey();
			}
		}
		return mostHated;
	}
	
	/**
	 * @param target : The {@link Creature} whose hate level must be returned.
	 * @return The hate level of the {@link Npc} owner against the {@link Creature} set as target.
	 */
	public synchronized double getHate(Creature target)
	{
		return (target == null) ? 0.0 : _map.getDouble(target);
	}
	
	/**
	 * Clear the hate of a {@link Creature} target without removing it from the {@link HateList}.
	 * @param target : The {@link Creature} to clean hate.
	 */
	public synchronized void stopHate(Creature target)
	{
		if (target != null)
			_map.removeDouble(target);
	}
	
	/**
	 * Reduce hate for the whole {@link HateList}.
	 * @param amount : The amount of hate to remove.
	 */
	public synchronized void reduceAllHate(double amount)
	{
		if (_map.isEmpty())
			return;
		
		final ObjectIterator<Object2DoubleMap.Entry<Creature>> it = _map.object2DoubleEntrySet().fastIterator();
		while (it.hasNext())
		{
			final Object2DoubleMap.Entry<Creature> entry = it.next();
			entry.setValue(entry.getDoubleValue() - amount);
		}
	}
	
	/**
	 * Clear the hate values of all registered hated {@link Creature}s, without dropping them.
	 */
	public synchronized void cleanAllHate()
	{
		if (_map.isEmpty())
			return;
		
		final ObjectIterator<Object2DoubleMap.Entry<Creature>> it = _map.object2DoubleEntrySet().fastIterator();
		while (it.hasNext())
		{
			it.next().setValue(0.0);
		}
	}
	
	/**
	 * Drop invalid entries from this {@link HateList}, such as :
	 * <ul>
	 * <li>Dead and alike {@link Creature}s got their hate stopped.</li>
	 * <li>Invisible and unknown {@link Creature}s are simply dropped from the {@link HateList}.</li>
	 * </ul>
	 */
	public synchronized void refresh()
	{
		if (_map.isEmpty())
			return;
		
		final ObjectIterator<Object2DoubleMap.Entry<Creature>> it = _map.object2DoubleEntrySet().fastIterator();
		while (it.hasNext())
		{
			final Creature c = it.next().getKey();
			if (c.isAlikeDead() || !c.isVisible() || !_owner.knows(c) || (c.getActingPlayer() != null && !c.getActingPlayer().getAppearance().isVisible()))
			{
				it.remove();
			}
		}
	}
	
	/**
	 * Drop out of range entries from this {@link HateList} :
	 * @param range : The range to check in.
	 */
	public synchronized void removeIfOutOfRange(int range)
	{
		if (_map.isEmpty())
			return;
		
		final ObjectIterator<Object2DoubleMap.Entry<Creature>> it = _map.object2DoubleEntrySet().fastIterator();
		while (it.hasNext())
		{
			final Creature c = it.next().getKey();
			if (!_owner.isIn3DRadius(c, range))
			{
				it.remove();
			}
		}
	}
	
	public synchronized boolean isEmpty()
	{
		return _map.isEmpty();
	}
	
	public synchronized int size()
	{
		return _map.size();
	}
	
	public synchronized void clear()
	{
		_map.clear();
	}
	
	public synchronized boolean containsKey(Creature target)
	{
		return target != null && _map.containsKey(target);
	}
	
	public synchronized Set<Creature> keySet()
	{
		return _map.keySet();
	}
}