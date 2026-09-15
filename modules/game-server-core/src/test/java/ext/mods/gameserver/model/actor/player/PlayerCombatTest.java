package ext.mods.gameserver.model.actor.player;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import ext.mods.gameserver.enums.ZoneId;
import ext.mods.gameserver.model.actor.Creature;
import ext.mods.gameserver.model.actor.Player;

/**
 * Pure-logic tests for {@link PlayerCombat}.
 * <p>
 * The real {@link Player} is too heavy to instantiate without a full server;
 * we use {@code sun.misc.Unsafe#allocateInstance} to create a bare Player shell,
 * then inject the sub-components needed by each method under test.
 */
class PlayerCombatTest
{
	/**
	 * Create a bare Player with the minimum injected fields so that
	 * PlayerCombat methods can execute without NPE on primitive-returning paths.
	 */
	private static Player newWiredPlayer()
	{
		try
		{
			final sun.misc.Unsafe unsafe = getUnsafe();
			final Player player = (Player) unsafe.allocateInstance(Player.class);

			// Inject _zones byte array (Creature) so isInsideZone doesn't NPE.
			final byte[] zones = new byte[ZoneId.VALUES.length];
			putFieldInHierarchy(player, "_zones", zones);

			// Inject PlayerPvP so getPvpFlag/getKarma/getSiegeState don't NPE.
			final PlayerPvP pvp = new PlayerPvP(player);
			putField(player, Player.class, "_pvp", pvp);

			// Inject PlayerDeath so getDeathPenaltyBuffLevel doesn't NPE.
			final PlayerDeath death = new PlayerDeath(player);
			putField(player, Player.class, "_death", death);

			// Inject PlayerClan (allocated bare) so getClan/isClanLeader don't NPE.
			final PlayerClan clan = (PlayerClan) unsafe.allocateInstance(PlayerClan.class);
			putField(player, Player.class, "_clanComponent", clan);

			return player;
		}
		catch (Exception e)
		{
			throw new AssertionError("Failed to create wired Player", e);
		}
	}

	private static PlayerCombat newCombat()
	{
		return new PlayerCombat(newWiredPlayer());
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

	private static void putFieldInHierarchy(Object target, String name, Object value) throws Exception
	{
		final Field f = findFieldInHierarchy(target.getClass(), name);
		final sun.misc.Unsafe unsafe = getUnsafe();
		final long offset = unsafe.objectFieldOffset(f);
		unsafe.putObject(target, offset, value);
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

	// ====================================================================
	// Construction
	// ====================================================================

	@Test
	void constructor_storesOwner()
	{
		// Arrange
		final Player player = newWiredPlayer();

		// Act
		final PlayerCombat combat = new PlayerCombat(player);

		// Assert
		assertNotNull(combat);
		assertSame(player, combat.getOwner());
	}

	@Test
	void getOwner_returnsSameInstance()
	{
		// Arrange
		final Player owner = newWiredPlayer();
		final PlayerCombat combat = new PlayerCombat(owner);

		// Act + Assert
		assertSame(owner, combat.getOwner());
		assertSame(combat.getOwner(), combat.getOwner());
	}

	@Test
	void twoCombats_haveDifferentOwners()
	{
		// Arrange
		final Player owner1 = newWiredPlayer();
		final Player owner2 = newWiredPlayer();

		// Act
		final PlayerCombat c1 = new PlayerCombat(owner1);
		final PlayerCombat c2 = new PlayerCombat(owner2);

		// Assert
		assertSame(owner1, c1.getOwner());
		assertSame(owner2, c2.getOwner());
	}

	// ====================================================================
	// getRelation
	// ====================================================================

	@Test
	void getRelation_barePlayers_returnsZero()
	{
		// Arrange — both Players have no karma, no flag, no clan,
		// no siege state. Every branch in getRelation short-circuits.
		final Player owner = newWiredPlayer();
		final Player target = newWiredPlayer();
		final PlayerCombat combat = new PlayerCombat(owner);

		// Act
		final int relation = combat.getRelation(target);

		// Assert
		assertEquals(0, relation);
	}

	@Test
	void getRelation_calledTwice_isDeterministic()
	{
		// Arrange
		final Player owner = newWiredPlayer();
		final Player target = newWiredPlayer();
		final PlayerCombat combat = new PlayerCombat(owner);

		// Act
		final int first = combat.getRelation(target);
		final int second = combat.getRelation(target);

		// Assert
		assertEquals(first, second);
	}

	@Test
	void getRelation_samePlayerAsTarget_returnsZero()
	{
		// Arrange
		final Player owner = newWiredPlayer();
		final PlayerCombat combat = new PlayerCombat(owner);

		// Act
		final int relation = combat.getRelation(owner);

		// Assert
		assertEquals(0, relation);
	}

	// ====================================================================
	// isInEnchanterZone
	// ====================================================================

	@Test
	void isInEnchanterZone_barePlayer_returnsFalse()
	{
		// Arrange
		final PlayerCombat combat = newCombat();

		// Act
		final boolean inZone = combat.isInEnchanterZone();

		// Assert — bare Player has no zone flag set.
		assertFalse(inZone);
	}

	@Test
	void isInEnchanterZone_calledTwice_isDeterministic()
	{
		// Arrange
		final PlayerCombat combat = newCombat();

		// Act + Assert
		assertEquals(combat.isInEnchanterZone(), combat.isInEnchanterZone());
	}

	// ====================================================================
	// calculateDeathPenaltyBuffLevel
	// ====================================================================

	@Test
	void calculateDeathPenaltyBuffLevel_nullKiller_doesNotThrow()
	{
		// Arrange — bare player: deathPenaltyBuffLevel == 0, karma == 0,
		// DEATH_PENALTY_CHANCE defaults to 0, so Rnd.get(1,100) > 0 always.
		// With no karma and 0 chance, the outer condition is false → early return.
		final PlayerCombat combat = newCombat();

		// Act + Assert
		assertDoesNotThrow(() -> combat.calculateDeathPenaltyBuffLevel(null));
	}

	@Test
	void calculateDeathPenaltyBuffLevel_repeated_isSafe()
	{
		// Arrange
		final PlayerCombat combat = newCombat();

		// Act + Assert
		assertDoesNotThrow(() -> {
			combat.calculateDeathPenaltyBuffLevel(null);
			combat.calculateDeathPenaltyBuffLevel(null);
			combat.calculateDeathPenaltyBuffLevel(null);
		});
	}

	// ====================================================================
	// reduceDeathPenaltyBuffLevel
	// ====================================================================

	@Test
	void reduceDeathPenaltyBuffLevel_whenLevelIsZero_isSafe()
	{
		// Arrange — deathPenaltyBuffLevel is 0 → early return.
		final PlayerCombat combat = newCombat();

		// Act + Assert
		assertDoesNotThrow(() -> combat.reduceDeathPenaltyBuffLevel());
	}

	@Test
	void reduceDeathPenaltyBuffLevel_repeated_isSafe()
	{
		// Arrange
		final PlayerCombat combat = newCombat();

		// Act + Assert
		assertDoesNotThrow(() -> {
			combat.reduceDeathPenaltyBuffLevel();
			combat.reduceDeathPenaltyBuffLevel();
		});
	}

	// ====================================================================
	// removeDeathPenaltyBuffLevel
	// ====================================================================

	@Test
	void removeDeathPenaltyBuffLevel_whenLevelIsZero_isSafe()
	{
		// Arrange
		final PlayerCombat combat = newCombat();

		// Act + Assert
		assertDoesNotThrow(() -> combat.removeDeathPenaltyBuffLevel());
	}

	@Test
	void removeDeathPenaltyBuffLevel_repeated_isSafe()
	{
		// Arrange
		final PlayerCombat combat = newCombat();

		// Act + Assert
		assertDoesNotThrow(() -> {
			combat.removeDeathPenaltyBuffLevel();
			combat.removeDeathPenaltyBuffLevel();
		});
	}

	// ====================================================================
	// onKillUpdatePvPKarma
	// ====================================================================

	@Test
	void onKillUpdatePvPKarma_nullTarget_isSafe()
	{
		// Arrange
		final PlayerCombat combat = newCombat();

		// Act + Assert — early return when target is null.
		assertDoesNotThrow(() -> combat.onKillUpdatePvPKarma(null));
	}
}
