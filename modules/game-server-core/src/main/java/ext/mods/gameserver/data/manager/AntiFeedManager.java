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

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import ext.mods.Config;
import ext.mods.gameserver.model.actor.Creature;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.network.GameClient;
import ext.mods.config.ConfigProject;

public final class AntiFeedManager
{
	public static final int GAME_ID = 0;
	public static final int OLYMPIAD_ID = 1;
	public static final int CTF_ID = 2;
	public static final int DM_ID = 3;
	public static final int LM_ID = 4;
	public static final int TVT_ID = 5;
	
	private final Map<Integer, Long> _lastDeathTimes = new ConcurrentHashMap<>();
	private final Map<Integer, Map<Integer, AtomicInteger>> _eventIPs = new ConcurrentHashMap<>();
	
	protected AntiFeedManager()
	{
	}
	
	/**
	 * Set time of the last player's death to current
	 * @param objectId Player's objectId
	 */
	public void setLastDeathTime(int objectId)
	{
		_lastDeathTimes.put(objectId, System.currentTimeMillis());
	}
	
	/**
	 * Check if current kill should be counted as non-feeded.
	 * @param attacker Attacker character
	 * @param target Target character
	 * @return True if kill is non-feeded.
	 */
	public boolean check(Creature attacker, Creature target)
	{
		if (!ConfigProject.ANTIFEED_ENABLE)
			return true;
		
		if (target == null)
			return false;
		
		final Player targetPlayer = target.getActingPlayer();
		if (targetPlayer == null)
			return false;
		
		if (targetPlayer.getClient().isDetached())
			return false;
		
		if ((ConfigProject.ANTIFEED_INTERVAL > 0) && _lastDeathTimes.containsKey(targetPlayer.getObjectId()) && ((System.currentTimeMillis() - _lastDeathTimes.get(targetPlayer.getObjectId())) < ConfigProject.ANTIFEED_INTERVAL))
			return false;
		
		if (ConfigProject.ANTIFEED_DUALBOX && (attacker != null))
		{
			final Player attackerPlayer = attacker.getActingPlayer();
			if (attackerPlayer == null)
				return false;
			
			final GameClient targetClient = targetPlayer.getClient();
			final GameClient attackerClient = attackerPlayer.getClient();
			final InetAddress targetAddr = targetClient.getInetAddress();
			final InetAddress attackerAddr = attackerClient.getInetAddress();
			if ((targetAddr == null) || (attackerAddr == null))
				return !ConfigProject.ANTIFEED_DISCONNECTED_AS_DUALBOX;
			
			return !targetAddr.equals(attackerAddr);
		}
		return true;
	}
	
	/**
	 * Clears all timestamps
	 */
	public void clear()
	{
		_lastDeathTimes.clear();
	}
	
	/**
	 * Register new event for dualbox check. Should be called only once.
	 * @param eventId
	 */
	public void registerEvent(int eventId)
	{
		_eventIPs.putIfAbsent(eventId, new ConcurrentHashMap<>());
	}
	
	/**
	 * @param eventId
	 * @param player
	 * @param max
	 * @return If number of all simultaneous connections from player's IP address lower than max then increment connection count and return true.<br>
	 *         False if number of all simultaneous connections from player's IP address higher than max.
	 */
	public final boolean tryAddPlayer(int eventId, Player player, int max)
	{
		return tryAddClient(eventId, player.getClient(), max);
	}
	
	/**
	 * @param eventId
	 * @param client
	 * @param max
	 * @return If number of all simultaneous connections from player's IP address lower than max then increment connection count and return true.<br>
	 *         False if number of all simultaneous connections from player's IP address higher than max.
	 */
	public boolean tryAddClient(int eventId, GameClient client, int max)
	{
		if (client == null)
			return false;
			
		final Map<Integer, AtomicInteger> event = _eventIPs.get(eventId);
		if (event == null)
			return false;
			
		final InetAddress addr = client.getInetAddress();
		if (addr == null)
			return false;
			
		final Integer addrHash = addr.hashCode();
		AtomicInteger connectionCount = event.get(addrHash);
		
		if (connectionCount == null)
		{
			connectionCount = new AtomicInteger();
			AtomicInteger existingValue = event.putIfAbsent(addrHash, connectionCount);
			if (existingValue != null)
				connectionCount = existingValue;
		}
		
		if (connectionCount.get() + 1 <= max)
		{
			connectionCount.incrementAndGet();
			return true;
		}
		
		return false;
	}
	
	/**
	 * Decreasing number of active connection from player's IP address
	 * @param eventId
	 * @param player
	 * @return true if success and false if any problem detected.
	 */
	public boolean removePlayer(int eventId, Player player)
	{
		return removeClient(eventId, player.getClient());
	}
	
	/**
	 * Decreasing number of active connection from player's IP address
	 * @param eventId
	 * @param client
	 * @return true if success and false if any problem detected.
	 */
	public boolean removeClient(int eventId, GameClient client)
	{
		if (client == null)
			return false;
			
		final Map<Integer, AtomicInteger> event = _eventIPs.get(eventId);
		if (event == null)
			return false;
			
		final InetAddress addr = client.getInetAddress();
		if (addr == null)
			return false;
			
		final Integer addrHash = addr.hashCode();
		return event.computeIfPresent(addrHash, (k, v) ->
		{
			if ((v == null) || (v.decrementAndGet() == 0))
				return null;
			return v;
		}) != null;
	}
	
	/**
	 * Remove player connection IP address from all registered events lists.
	 * @param client
	 */
	public void onDisconnect(GameClient client)
	{
		if (client == null || client.getInetAddress() == null)
			return;
		
		_eventIPs.forEach((k, v) -> removeClient(k, client));
	}
	
	/**
	 * Clear all entries for this eventId.
	 * @param eventId
	 */
	public void clear(int eventId)
	{
		final var event = _eventIPs.get(eventId);
		if (event != null)
			event.clear();
	}
	
	/**
	 * @param player
	 * @param max
	 * @return maximum number of allowed connections (whitelist + max)
	 */
	public int getLimit(Player player, int max)
	{
		return getLimit(player.getClient(), max);
	}
	
	/**
	 * @param client
	 * @param max
	 * @return maximum number of allowed connections (whitelist + max)
	 */
	public int getLimit(GameClient client, int max)
	{
		if (client == null)
			return max;
		
		return max;
	}
	
	public static final AntiFeedManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final AntiFeedManager INSTANCE = new AntiFeedManager();
	}
}