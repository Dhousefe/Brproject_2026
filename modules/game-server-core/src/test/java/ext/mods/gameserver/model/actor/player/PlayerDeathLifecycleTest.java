package ext.mods.gameserver.model.actor.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import ext.mods.gameserver.model.actor.Player;

/**
 * Pure-logic tests for {@link PlayerDeathLifecycle}.
 * <p>
 * Uses {@code sun.misc.Unsafe#allocateInstance} to create a bare Player shell
 * and injects the necessary sub-component ({@link PlayerDeath}) so that
 * fake-death / revive-request field accessors can be exercised without a full
 * server context.
 */
class PlayerDeathLifecycleTest
{
	// --------------------------------------------------------------------
	// Construction
	// --------------------------------------------------------------------

	@Test
	void constructor_storesOwner()
	{
		// Arrange
		final Player player = allocateBarePlayer();

		// Act
		final PlayerDeathLifecycle lifecycle = new PlayerDeathLifecycle(player);

		// Assert
		assertNotNull(lifecycle);
		assertSame(player, lifecycle.getOwner());
	}

	// --------------------------------------------------------------------
	// Fake death
	// --------------------------------------------------------------------

	@Test
	void isFakeDeath_defaultIsFalse()
	{
		// Arrange
		final PlayerDeath death = new PlayerDeath(null);

		// Act + Assert — default boolean is false.
		assertFalse(death.isFakeDeath());
	}

	@Test
	void setFakeDeath_changesToTrue()
	{
		// Arrange
		final PlayerDeath death = new PlayerDeath(null);

		// Act
		death.setFakeDeath(true);

		// Assert
		assertTrue(death.isFakeDeath());
	}

	@Test
	void setFakeDeath_resetToFalse()
	{
		// Arrange
		final PlayerDeath death = new PlayerDeath(null);
		death.setFakeDeath(true);

		// Act
		death.setFakeDeath(false);

		// Assert
		assertFalse(death.isFakeDeath());
	}

	// --------------------------------------------------------------------
	// Recent fake death
	// --------------------------------------------------------------------

	@Test
	void isRecentFakeDeath_defaultIsFalse()
	{
		// Arrange — recentFakeDeathEndTime defaults to 0, which is in the past.
		final PlayerDeath death = new PlayerDeath(null);

		// Act
		final long endTime = death.getRecentFakeDeathEndTime();

		// Assert — end time 0 means no recent fake death.
		assertEquals(0L, endTime);
	}

	@Test
	void setRecentFakeDeathEndTime_storesValue()
	{
		// Arrange
		final PlayerDeath death = new PlayerDeath(null);

		// Act
		death.setRecentFakeDeathEndTime(System.currentTimeMillis() + 5000L);

		// Assert
		assertTrue(death.getRecentFakeDeathEndTime() > 0);
	}

	@Test
	void clearRecentFakeDeath_resetsEndTime()
	{
		// Arrange
		final PlayerDeath death = new PlayerDeath(null);
		death.setRecentFakeDeathEndTime(System.currentTimeMillis() + 5000L);

		// Act
		death.setRecentFakeDeathEndTime(0);

		// Assert
		assertEquals(0L, death.getRecentFakeDeathEndTime());
	}

	// --------------------------------------------------------------------
	// Revive request
	// --------------------------------------------------------------------

	@Test
	void getReviveRequested_defaultIsZero()
	{
		// Arrange
		final PlayerDeath death = new PlayerDeath(null);

		// Act + Assert
		assertEquals(0, death.getReviveRequested());
	}

	@Test
	void setReviveRequested_storesValue()
	{
		// Arrange
		final PlayerDeath death = new PlayerDeath(null);

		// Act
		death.setReviveRequested(1);

		// Assert
		assertEquals(1, death.getReviveRequested());
	}

	@Test
	void setRevivePower_storesValue()
	{
		// Arrange
		final PlayerDeath death = new PlayerDeath(null);

		// Act
		death.setRevivePower(75.0);

		// Assert
		assertEquals(75.0, death.getRevivePower(), 0.001);
	}

	@Test
	void isRevivePet_defaultIsFalse()
	{
		// Arrange
		final PlayerDeath death = new PlayerDeath(null);

		// Act + Assert
		assertFalse(death.isRevivePet());
	}

	@Test
	void setRevivePet_storesValue()
	{
		// Arrange
		final PlayerDeath death = new PlayerDeath(null);

		// Act
		death.setRevivePet(true);

		// Assert
		assertTrue(death.isRevivePet());
	}

	// --------------------------------------------------------------------
	// Death penalty buff level
	// --------------------------------------------------------------------

	@Test
	void getDeathPenaltyBuffLevel_defaultIsZero()
	{
		// Arrange
		final PlayerDeath death = new PlayerDeath(null);

		// Act + Assert
		assertEquals(0, death.getDeathPenaltyBuffLevel());
	}

	@Test
	void setDeathPenaltyBuffLevel_storesValue()
	{
		// Arrange
		final PlayerDeath death = new PlayerDeath(null);

		// Act
		death.setDeathPenaltyBuffLevel(5);

		// Assert
		assertEquals(5, death.getDeathPenaltyBuffLevel());
	}

	@Test
	void setDeathPenaltyBuffLevel_canReset()
	{
		// Arrange
		final PlayerDeath death = new PlayerDeath(null);
		death.setDeathPenaltyBuffLevel(10);

		// Act
		death.setDeathPenaltyBuffLevel(0);

		// Assert
		assertEquals(0, death.getDeathPenaltyBuffLevel());
	}

	// --------------------------------------------------------------------
	// PlayerDeathLifecycle delegates
	// --------------------------------------------------------------------

	@Test
	void isReviveRequested_lifecycle_reflectsState()
	{
		// Arrange — inject a PlayerDeath into a bare Player's _death field,
		// then create a PlayerDeathLifecycle referencing that player.
		final Player player = allocateBarePlayer();
		final PlayerDeath death = new PlayerDeath(player);
		injectField(player, Player.class, "_death", death);

		final PlayerDeathLifecycle lifecycle = new PlayerDeathLifecycle(player);
		injectField(player, Player.class, "_deathLifecycle", lifecycle);

		// Act
		death.setReviveRequested(1);

		// Assert
		assertTrue(lifecycle.isReviveRequested());
	}

	@Test
	void removeReviving_resetsState()
	{
		// Arrange
		final Player player = allocateBarePlayer();
		final PlayerDeath death = new PlayerDeath(player);
		death.setReviveRequested(1);
		death.setRevivePower(100.0);
		injectField(player, Player.class, "_death", death);

		final PlayerDeathLifecycle lifecycle = new PlayerDeathLifecycle(player);
		injectField(player, Player.class, "_deathLifecycle", lifecycle);

		// Act
		lifecycle.removeReviving();

		// Assert
		assertEquals(0, death.getReviveRequested());
		assertEquals(0.0, death.getRevivePower(), 0.001);
	}

	// --------------------------------------------------------------------
	// Helpers
	// --------------------------------------------------------------------

	private static Player allocateBarePlayer()
	{
		try
		{
			return (Player) getUnsafe().allocateInstance(Player.class);
		}
		catch (Exception e)
		{
			throw new AssertionError("Failed to allocate Player", e);
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

	private static void injectField(Object target, Class<?> declaredIn, String name, Object value)
	{
		try
		{
			final sun.misc.Unsafe unsafe = getUnsafe();
			final Field f = declaredIn.getDeclaredField(name);
			final long offset = unsafe.objectFieldOffset(f);
			unsafe.putObject(target, offset, value);
		}
		catch (Exception e)
		{
			throw new AssertionError("Failed to inject field: " + name, e);
		}
	}
}
