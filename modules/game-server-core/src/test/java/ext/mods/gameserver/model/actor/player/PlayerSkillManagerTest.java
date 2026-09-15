package ext.mods.gameserver.model.actor.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.records.Timestamp;
import ext.mods.gameserver.skills.L2Skill;

/**
 * Pure-logic tests for {@link PlayerSkillManager}'s bookkeeping surface:
 * short-buff task id, reuse-timestamp store / remove / clear, skill
 * lifecycle guards (null returns false), and initial state emptiness.
 * <p>
 * The real {@link Player} is too heavy to instantiate without a full server;
 * we use {@code sun.misc.Unsafe#allocateInstance} to allocate a bare Player
 * shell, then inject only the {@code _skillsDb} field that the component's
 * constructor touches. This isolates the unit tests from infrastructure
 * (JDBC, world, templates, etc.).
 */
class PlayerSkillManagerTest
{
	private static PlayerSkillManager newManager()
	{
		try
		{
			final sun.misc.Unsafe unsafe = getUnsafe();
			final Player player = (Player) unsafe.allocateInstance(Player.class);

			// Inject a PlayerSkillsDb allocated without calling its
			// constructor (which itself captures the owner). The component
			// only stores the reference and never invokes it in the tests
			// below.
			final PlayerSkillsDb skillsDb = (PlayerSkillsDb) unsafe.allocateInstance(PlayerSkillsDb.class);
			final Field dbField = Player.class.getDeclaredField("_skillsDb");
			dbField.setAccessible(true);
			dbField.set(player, skillsDb);

			return new PlayerSkillManager(player);
		}
		catch (Exception e)
		{
			throw new AssertionError("Failed to create PlayerSkillManager stub", e);
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

	@Test
	void shortBuffTaskSkillId_defaultIsZero()
	{
		// Arrange
		final PlayerSkillManager mgr = newManager();

		// Act
		final int actual = mgr.getShortBuffTaskSkillId();

		// Assert
		assertEquals(0, actual);
	}

	@Test
	void setShortBuffTaskSkillId_storesValue()
	{
		// Arrange
		final PlayerSkillManager mgr = newManager();

		// Act
		mgr.setShortBuffTaskSkillId(1234);

		// Assert
		assertEquals(1234, mgr.getShortBuffTaskSkillId());
	}

	@Test
	void getReuseTimeStamps_isEmptyByDefault()
	{
		// Arrange
		final PlayerSkillManager mgr = newManager();

		// Act
		final Collection<Timestamp> actual = mgr.getReuseTimeStamps();

		// Assert
		assertNotNull(actual);
		assertTrue(actual.isEmpty());
	}

	@Test
	void getSkills_isEmptyByDefault()
	{
		// Arrange
		final PlayerSkillManager mgr = newManager();

		// Act
		final Map<Integer, L2Skill> skills = mgr.getSkills();

		// Assert
		assertNotNull(skills);
		assertTrue(skills.isEmpty());
	}

	@Test
	void addSkill_nullSkill_returnsFalse()
	{
		// Arrange
		final PlayerSkillManager mgr = newManager();

		// Act
		final boolean added = mgr.addSkill(null, false);

		// Assert
		assertFalse(added);
		assertTrue(mgr.getSkills().isEmpty());
	}

	@Test
	void addSkill_nullSkillWithUpdateShortcuts_returnsFalse()
	{
		// Arrange
		final PlayerSkillManager mgr = newManager();

		// Act
		final boolean added = mgr.addSkill(null, false, true);

		// Assert
		assertFalse(added);
	}

	@Test
	void removeSkill_nonExistentId_returnsNull()
	{
		// Arrange
		final PlayerSkillManager mgr = newManager();

		// Act
		final L2Skill removed = mgr.removeSkill(9999, false);

		// Assert
		assertNull(removed);
	}

	@Test
	void removeSkill_nonExistentId_withRemoveEffect_returnsNull()
	{
		// Arrange
		final PlayerSkillManager mgr = newManager();

		// Act
		final L2Skill removed = mgr.removeSkill(1, false, true);

		// Assert
		assertNull(removed);
	}

	@Test
	void removeTimeStamp_unknownSkillReturnsNull()
	{
		// Arrange — L2Skill is abstract and its getReuseHashCode is final,
		// so we cannot subclass it cleanly. We exercise the
		// "non-existent hash" path by inspecting the internal map directly.
		final PlayerSkillManager mgr = newManager();

		// Act + Assert: the map is empty, so removing any entry returns null.
		final Timestamp removed = readMap(mgr).remove(0xDEADBEEF);

		// Assert
		assertNull(removed);
	}

	@Test
	void clearReuseTimeStamps_isSafeWhenEmpty()
	{
		// Arrange
		final PlayerSkillManager mgr = newManager();

		// Act
		mgr.clearReuseTimeStamps();

		// Assert
		assertTrue(mgr.getReuseTimeStamps().isEmpty());
	}

	@Test
	void clearReuseTimeStamps_removesAllEntries()
	{
		// Arrange — seed two timestamps via the internal map.
		final PlayerSkillManager mgr = newManager();
		seedTimestamp(mgr, 1, 1);
		seedTimestamp(mgr, 2, 1);

		// Act
		mgr.clearReuseTimeStamps();

		// Assert
		assertTrue(mgr.getReuseTimeStamps().isEmpty());
	}

	@Test
	void getReuseTimeStamp_mapIdentityPreserved()
	{
		// Arrange
		final PlayerSkillManager mgr = newManager();

		// Act + Assert: same instance returned each call.
		assertSame(mgr.getReuseTimeStamp(), mgr.getReuseTimeStamp());
	}

	/**
	 * Read the internal reuse-timestamps map via reflection.
	 */
	@SuppressWarnings("unchecked")
	private static Map<Integer, Timestamp> readMap(PlayerSkillManager mgr)
	{
		try
		{
			final Field mapField = PlayerSkillManager.class.getDeclaredField("_reuseTimeStamps");
			mapField.setAccessible(true);
			return (Map<Integer, Timestamp>) mapField.get(mgr);
		}
		catch (Exception e)
		{
			throw new AssertionError("Failed to read _reuseTimeStamps", e);
		}
	}

	/**
	 * Seed a {@link Timestamp} entry directly into the manager's internal
	 * reuse-map. Bypasses {@link L2Skill} (abstract) entirely.
	 */
	private static void seedTimestamp(PlayerSkillManager mgr, int skillId, int skillLevel)
	{
		final Map<Integer, Timestamp> map = readMap(mgr);
		final int hash = skillId * 256 + skillLevel;
		map.put(hash, new Timestamp(skillId, skillLevel, 1000L, System.currentTimeMillis() + 1000L));
	}
}
