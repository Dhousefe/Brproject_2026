/*
 * Copyleft © 2024-2026 L2Brproject
 * This file is part of L2Brproject derived from aCis409/RusaCis3.8
 * L2Brproject is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License.
 * L2Brproject is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 */
package ext.mods.gameserver.model.actor.player;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import ext.mods.extensions.listener.manager.GameListenerManager;
import ext.mods.extensions.listener.manager.PlayerListenerManager;
import ext.mods.gameserver.model.actor.Player;

/**
 * Static per-player typed attachment registry keyed by character {@code objectId}.
 * <p>
 * <b>Preferred pattern</b> for new mod runtime state — do <em>not</em> add feature fields
 * to {@link Player}. See {@code docs/adr/0002-player-attachments-for-mod-state.md} and
 * {@code docs/PLAYER_POLICY.md}.
 * <p>
 * <b>Logout / delete clear contract:</b>
 * <ul>
 * <li>When {@link #install()} has been called (platform bootstrap via
 * {@code FirstPartyFeaturesExtension}), all attachments for a player are cleared on:
 * <ul>
 * <li>player exit — {@code Player.deleteMe()} → {@code PlayerListenerManager.notifyPlayerExit}</li>
 * <li>character delete — {@code GameListenerManager.notifyCharacterDelete}</li>
 * </ul>
 * </li>
 * <li>Callers may still invoke {@link #clear(Player)} / {@link #clear(int)} defensively.</li>
 * <li>If a custom bootstrap skips {@link #install()}, <b>callers must clear</b> on logout
 * or risk retaining strong references until process exit.</li>
 * </ul>
 * <p>
 * This utility intentionally avoids {@code Player.getAttachments()} so {@code Player.java}
 * does not grow surface area. Prefer {@link #of(Player)}.
 */
public final class PlayerAttachments
{
	private static final ConcurrentHashMap<Integer, ConcurrentHashMap<Key<?>, Object>> BY_OBJECT_ID = new ConcurrentHashMap<>();
	private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
	
	private PlayerAttachments()
	{
	}
	
	/**
	 * Typed attachment key. Own keys as {@code private static final} constants inside the feature module
	 * to avoid name collisions across mods.
	 *
	 * @param <T> value type stored under this key
	 */
	public static final class Key<T>
	{
		private final String name;
		
		private Key(String name)
		{
			this.name = Objects.requireNonNull(name, "name");
		}
		
		/**
		 * @param name unique key name (prefer {@code mod-name.feature.slot})
		 */
		public static <T> Key<T> of(String name)
		{
			return new Key<>(name);
		}
		
		public String name()
		{
			return name;
		}
		
		@Override
		public boolean equals(Object o)
		{
			if (this == o)
				return true;
			if (!(o instanceof Key<?> other))
				return false;
			return name.equals(other.name);
		}
		
		@Override
		public int hashCode()
		{
			return name.hashCode();
		}
		
		@Override
		public String toString()
		{
			return "PlayerAttachments.Key[" + name + "]";
		}
	}
	
	/**
	 * Scoped view for a single player (sugar over static put/get/remove/clear).
	 */
	public static final class View
	{
		private final int objectId;
		
		private View(int objectId)
		{
			this.objectId = objectId;
		}
		
		public int objectId()
		{
			return objectId;
		}
		
		public <T> void put(Key<T> key, T value)
		{
			PlayerAttachments.put(objectId, key, value);
		}
		
		public <T> T get(Key<T> key)
		{
			return PlayerAttachments.get(objectId, key);
		}
		
		public <T> T remove(Key<T> key)
		{
			return PlayerAttachments.remove(objectId, key);
		}
		
		public void clear()
		{
			PlayerAttachments.clear(objectId);
		}
	}
	
	/**
	 * @return attachment view for {@code player}; never null
	 */
	public static View of(Player player)
	{
		Objects.requireNonNull(player, "player");
		return new View(player.getObjectId());
	}
	
	/**
	 * @return attachment view for raw objectId (e.g. character-delete path)
	 */
	public static View of(int objectId)
	{
		return new View(objectId);
	}
	
	public static <T> void put(Player player, Key<T> key, T value)
	{
		Objects.requireNonNull(player, "player");
		put(player.getObjectId(), key, value);
	}
	
	public static <T> void put(int objectId, Key<T> key, T value)
	{
		Objects.requireNonNull(key, "key");
		if (value == null)
		{
			remove(objectId, key);
			return;
		}
		bag(objectId).put(key, value);
	}
	
	@SuppressWarnings("unchecked")
	public static <T> T get(Player player, Key<T> key)
	{
		Objects.requireNonNull(player, "player");
		return get(player.getObjectId(), key);
	}
	
	@SuppressWarnings("unchecked")
	public static <T> T get(int objectId, Key<T> key)
	{
		Objects.requireNonNull(key, "key");
		final ConcurrentHashMap<Key<?>, Object> bag = BY_OBJECT_ID.get(objectId);
		if (bag == null)
			return null;
		return (T) bag.get(key);
	}
	
	@SuppressWarnings("unchecked")
	public static <T> T remove(Player player, Key<T> key)
	{
		Objects.requireNonNull(player, "player");
		return remove(player.getObjectId(), key);
	}
	
	@SuppressWarnings("unchecked")
	public static <T> T remove(int objectId, Key<T> key)
	{
		Objects.requireNonNull(key, "key");
		final ConcurrentHashMap<Key<?>, Object> bag = BY_OBJECT_ID.get(objectId);
		if (bag == null)
			return null;
		final T previous = (T) bag.remove(key);
		if (bag.isEmpty())
			BY_OBJECT_ID.remove(objectId, bag);
		return previous;
	}
	
	/**
	 * Removes all attachments for the player. Safe to call if none exist.
	 */
	public static void clear(Player player)
	{
		if (player == null)
			return;
		clear(player.getObjectId());
	}
	
	/**
	 * Removes all attachments for {@code objectId}. Safe to call if none exist.
	 */
	public static void clear(int objectId)
	{
		BY_OBJECT_ID.remove(objectId);
	}
	
	/**
	 * Registers exit/delete listeners so bags are cleared automatically.
	 * Idempotent; safe to call more than once.
	 * <p>
	 * Platform entry: {@code FirstPartyFeaturesExtension#onEnable}.
	 */
	public static void install()
	{
		if (!INSTALLED.compareAndSet(false, true))
			return;
		
		PlayerListenerManager.getInstance().registerExitListener(PlayerAttachments::clear);
		GameListenerManager.getInstance().registerCharacterDeleteListener(PlayerAttachments::clear);
	}
	
	/** @return whether {@link #install()} has completed successfully */
	public static boolean isInstalled()
	{
		return INSTALLED.get();
	}
	
	/** Test/diagnostic: number of players with a non-empty bag. */
	public static int trackedPlayerCount()
	{
		return BY_OBJECT_ID.size();
	}
	
	private static ConcurrentHashMap<Key<?>, Object> bag(int objectId)
	{
		return BY_OBJECT_ID.computeIfAbsent(objectId, id -> new ConcurrentHashMap<>());
	}
}
