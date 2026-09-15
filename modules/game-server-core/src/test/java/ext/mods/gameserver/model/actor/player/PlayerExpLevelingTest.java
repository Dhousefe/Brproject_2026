package ext.mods.gameserver.model.actor.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import ext.mods.config.ConfigLanguage;
import ext.mods.config.ConfigProject;
import ext.mods.gameserver.model.actor.Creature;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.container.npc.RewardInfo;
import ext.mods.gameserver.model.actor.status.PlayerStatus;

/**
 * Pure-logic tests for {@link PlayerExpLeveling}:
 * <ul>
 *   <li>{@code addExpAndSp} gates exp gains via the owner's {@code stopExp} flag.</li>
 *   <li>{@code removeExpAndSp} always delegates unchanged to {@link PlayerStatus}.</li>
 * </ul>
 * <p>
 * Uses {@code sun.misc.Unsafe#allocateInstance} to construct a bare
 * {@link Player} shell, then injects a {@link PlayerProfile} (so that
 * {@link Player#getStopExp()} works) and a recording {@link PlayerStatus}
 * subclass (so we can capture the values passed downstream).
 */
class PlayerExpLevelingTest
{
	@BeforeAll
	static void seedConfigDefaults()
	{
		// Ensure CachedData-backed booleans receive a deterministic default.
		ConfigProject.PROP_STOP_EXP = true;
		ConfigProject.PROP_TRADE_REFUSAL = true;
		ConfigProject.PROP_AUTO_LOOT = false;
		ConfigProject.PROP_BUFF_PROTECTED = false;
		ConfigLanguage.DEFAULT_LOCALE = java.util.Locale.forLanguageTag("en-US");
	}

	@Test
	void addExpAndSp_stopExpTrue_addsBothExpAndSp()
	{
		// Arrange
		final RecordingPlayerStatus status = new RecordingPlayerStatus();
		final Player owner = newStubPlayer(true, status);
		final PlayerExpLeveling leveling = new PlayerExpLeveling(owner);

		// Act
		leveling.addExpAndSp(1000L, 50);

		// Assert
		assertEquals(1, status.addExpAndSpCount);
		assertEquals(1000L, status.lastAddExp);
		assertEquals(50, status.lastAddSp);
	}

	@Test
	void addExpAndSp_stopExpFalse_addsZeroExpButKeepsSp()
	{
		// Arrange
		final RecordingPlayerStatus status = new RecordingPlayerStatus();
		final Player owner = newStubPlayer(false, status);
		final PlayerExpLeveling leveling = new PlayerExpLeveling(owner);

		// Act
		leveling.addExpAndSp(1000L, 50);

		// Assert
		assertEquals(1, status.addExpAndSpCount);
		assertEquals(0L, status.lastAddExp, "exp must be zeroed when stopExp is off");
		assertEquals(50, status.lastAddSp, "sp must pass through unchanged");
	}

	@Test
	void addExpAndSp_withRewards_stopExpTrue_passesBoth()
	{
		// Arrange
		final RecordingPlayerStatus status = new RecordingPlayerStatus();
		final Player owner = newStubPlayer(true, status);
		final PlayerExpLeveling leveling = new PlayerExpLeveling(owner);
		final Map<Creature, RewardInfo> rewards = new HashMap<>();

		// Act
		leveling.addExpAndSp(9000L, 75, rewards);

		// Assert
		assertEquals(1, status.addExpAndSpRewardsCount);
		assertEquals(9000L, status.lastAddExp);
		assertEquals(75, status.lastAddSp);
		assertEquals(rewards, status.lastRewards);
	}

	@Test
	void addExpAndSp_withRewards_stopExpFalse_zerosExp()
	{
		// Arrange
		final RecordingPlayerStatus status = new RecordingPlayerStatus();
		final Player owner = newStubPlayer(false, status);
		final PlayerExpLeveling leveling = new PlayerExpLeveling(owner);
		final Map<Creature, RewardInfo> rewards = new HashMap<>();

		// Act
		leveling.addExpAndSp(9000L, 75, rewards);

		// Assert
		assertEquals(1, status.addExpAndSpRewardsCount);
		assertEquals(0L, status.lastAddExp);
		assertEquals(75, status.lastAddSp);
		assertEquals(rewards, status.lastRewards);
	}

	@Test
	void removeExpAndSp_delegatesUnchanged()
	{
		// Arrange
		final RecordingPlayerStatus status = new RecordingPlayerStatus();
		final Player owner = newStubPlayer(true, status);
		final PlayerExpLeveling leveling = new PlayerExpLeveling(owner);

		// Act
		leveling.removeExpAndSp(500L, 10);

		// Assert
		assertEquals(1, status.removeExpAndSpCount);
		assertEquals(500L, status.lastRemoveExp);
		assertEquals(10, status.lastRemoveSp);
	}

	@Test
	void removeExpAndSp_stopExpFalse_stillDelegates()
	{
		// Arrange: removeExpAndSp ignores the stopExp flag entirely.
		final RecordingPlayerStatus status = new RecordingPlayerStatus();
		final Player owner = newStubPlayer(false, status);
		final PlayerExpLeveling leveling = new PlayerExpLeveling(owner);

		// Act
		leveling.removeExpAndSp(300L, 5);

		// Assert
		assertEquals(1, status.removeExpAndSpCount);
		assertEquals(300L, status.lastRemoveExp);
		assertEquals(5, status.lastRemoveSp);
	}

	// --------------------------------------------------------------------
	// Stub wiring
	// --------------------------------------------------------------------

	/**
	 * Create a bare {@link Player} with only the fields needed by
	 * {@link PlayerExpLeveling}: {@code _profile} (stopExp gate) and
	 * {@code _status} (delegation target).
	 */
	private static Player newStubPlayer(boolean stopExp, RecordingPlayerStatus status)
	{
		try
		{
			final sun.misc.Unsafe unsafe = getUnsafe();
			final Player player = (Player) unsafe.allocateInstance(Player.class);

			// Inject objectId so PlayerProfile's CachedData doesn't blow up.
			putFieldInHierarchy(player, "_objectId", Integer.valueOf(1));

			// Create a real PlayerProfile wired to this player.
			final PlayerProfile profile = new PlayerProfile(player);
			profile.setStopExp(stopExp);
			putField(player, Player.class, "_profile", profile);

			// Inject the recording status.
			putFieldInHierarchy(player, "_status", status);

			return player;
		}
		catch (Exception e)
		{
			throw new AssertionError("Failed to create stub Player", e);
		}
	}

	private static sun.misc.Unsafe getUnsafe()
	{
		try
		{
			final Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			return (sun.misc.Unsafe) f.get(null);
		}
		catch (Exception e)
		{
			throw new AssertionError("Cannot obtain Unsafe", e);
		}
	}

	private static void putField(Object target, Class<?> declaredIn, String name, Object value) throws Exception
	{
		final sun.misc.Unsafe unsafe = getUnsafe();
		final Field f = declaredIn.getDeclaredField(name);
		final long offset = unsafe.objectFieldOffset(f);
		unsafe.putObject(target, offset, value);
	}

	private static void putIntField(Object target, Class<?> declaredIn, String name, int value) throws Exception
	{
		final sun.misc.Unsafe unsafe = getUnsafe();
		final Field f = declaredIn.getDeclaredField(name);
		final long offset = unsafe.objectFieldOffset(f);
		unsafe.putInt(target, offset, value);
	}

	private static Field findFieldInHierarchy(Class<?> clazz, String name)
	{
		Class<?> current = clazz;
		while (current != null)
		{
			try
			{
				return current.getDeclaredField(name);
			}
			catch (NoSuchFieldException e)
			{
				current = current.getSuperclass();
			}
		}
		throw new AssertionError("Field " + name + " not found in hierarchy of " + clazz.getName());
	}

	private static void putFieldInHierarchy(Object target, String name, Object value) throws Exception
	{
		final Field f = findFieldInHierarchy(target.getClass(), name);
		final sun.misc.Unsafe unsafe = getUnsafe();
		final long offset = unsafe.objectFieldOffset(f);
		if (f.getType() == int.class)
			unsafe.putInt(target, offset, ((Number) value).intValue());
		else
			unsafe.putObject(target, offset, value);
	}

	// --------------------------------------------------------------------
	// Recording PlayerStatus subclass
	// --------------------------------------------------------------------

	/**
	 * Records all delegated calls from {@link PlayerExpLeveling} without
	 * actually modifying player state. Passes {@code null} to the
	 * super-constructor which only stores the actor reference.
	 */
	static final class RecordingPlayerStatus extends PlayerStatus
	{
		int addExpAndSpCount = 0;
		int addExpAndSpRewardsCount = 0;
		int removeExpAndSpCount = 0;

		long lastAddExp;
		int lastAddSp;
		Map<Creature, RewardInfo> lastRewards;

		long lastRemoveExp;
		int lastRemoveSp;

		RecordingPlayerStatus()
		{
			super(null);
		}

		@Override
		public boolean addExpAndSp(long addToExp, int addToSp)
		{
			addExpAndSpCount++;
			lastAddExp = addToExp;
			lastAddSp = addToSp;
			return true;
		}

		@Override
		public boolean addExpAndSp(long addToExp, int addToSp, Map<Creature, RewardInfo> rewards)
		{
			addExpAndSpRewardsCount++;
			lastAddExp = addToExp;
			lastAddSp = addToSp;
			lastRewards = rewards;
			return true;
		}

		@Override
		public boolean removeExpAndSp(long removeExp, int removeSp)
		{
			removeExpAndSpCount++;
			lastRemoveExp = removeExp;
			lastRemoveSp = removeSp;
			return true;
		}
	}
}
