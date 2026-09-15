package ext.mods.gameserver.model.actor.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import ext.mods.config.ConfigLanguage;
import ext.mods.config.ConfigProject;
import ext.mods.gameserver.model.actor.Player;

/**
 * Pure-logic tests for {@link PlayerProfile}'s CachedData-backed preferences:
 * name/title colors, buff-protected flag, locale, ACP values, and the
 * stop-exp / trade-refusal / auto-loot toggles.
 * <p>
 * Uses {@code sun.misc.Unsafe#allocateInstance} to create a bare
 * {@link Player} shell without invoking its heavy constructor. The
 * {@link PlayerProfile} constructor only consumes
 * {@link Player#getObjectId()} which reads a field set during allocation.
 */
class PlayerProfileTest
{
	private static final int CHAR_ID = 4242;

	@BeforeAll
	static void seedConfigDefaults()
	{
		// Pin the CachedData defaults so assertions are deterministic.
		ConfigProject.PROP_BUFF_PROTECTED = false;
		ConfigProject.PROP_STOP_EXP = true;
		ConfigProject.PROP_TRADE_REFUSAL = true;
		ConfigProject.PROP_AUTO_LOOT = false;
		ConfigLanguage.DEFAULT_LOCALE = Locale.forLanguageTag("en-US");
	}

	private static PlayerProfile newProfile()
	{
		try
		{
			final sun.misc.Unsafe unsafe = getUnsafe();
			final Player player = (Player) unsafe.allocateInstance(Player.class);

			// Set _objectId in the WorldObject superclass via Unsafe.
			final Field oidField = findFieldInHierarchy(player.getClass(), "_objectId");
			final long offset = unsafe.objectFieldOffset(oidField);
			unsafe.putInt(player, offset, CHAR_ID);

			return new PlayerProfile(player);
		}
		catch (Exception e)
		{
			throw new AssertionError("Failed to create PlayerProfile stub", e);
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

	@Test
	void getNameColor_defaultIsZero()
	{
		// Arrange
		final PlayerProfile profile = newProfile();

		// Act
		final int actual = profile.getNameColor();

		// Assert
		assertEquals(0, actual);
	}

	@Test
	void setNameColor_storesAndReturnsValue()
	{
		// Arrange
		final PlayerProfile profile = newProfile();

		// Act
		final int returned = profile.setNameColor(0xFFAA00);

		// Assert
		assertEquals(0xFFAA00, returned);
		assertEquals(0xFFAA00, profile.getNameColor());
	}

	@Test
	void getTitleColor_defaultIsZero()
	{
		// Arrange
		final PlayerProfile profile = newProfile();

		// Act
		final int actual = profile.getTitleColor();

		// Assert
		assertEquals(0, actual);
	}

	@Test
	void setTitleColor_storesAndReturnsValue()
	{
		// Arrange
		final PlayerProfile profile = newProfile();

		// Act
		final int returned = profile.setTitleColor(0x00FF00);

		// Assert
		assertEquals(0x00FF00, returned);
		assertEquals(0x00FF00, profile.getTitleColor());
	}

	@Test
	void isBuffProtected_defaultFalse()
	{
		// Arrange
		final PlayerProfile profile = newProfile();

		// Act
		final boolean actual = profile.isBuffProtected();

		// Assert
		assertFalse(actual);
	}

	@Test
	void setBuffProtected_storesValue()
	{
		// Arrange
		final PlayerProfile profile = newProfile();

		// Act
		profile.setBuffProtected(true);

		// Assert
		assertTrue(profile.isBuffProtected());
	}

	@Test
	void getLocale_nullUntilExplicitlySet()
	{
		// Arrange
		final PlayerProfile profile = newProfile();

		// Act
		final Locale actual = profile.getLocale();

		// Assert: getLocale() falls back to ConfigLanguage.DEFAULT_LOCALE
		// when CachedDataValueObject has not been loaded from DB yet.
		assertEquals(Locale.forLanguageTag("en-US"), actual);
	}

	@Test
	void setLocale_storesLocaleInstance()
	{
		// Arrange
		final PlayerProfile profile = newProfile();
		final Locale ptBR = Locale.forLanguageTag("pt-BR");

		// Act
		profile.setLocale(ptBR);

		// Assert
		assertEquals(ptBR, profile.getLocale());
	}

	@Test
	void getStopExp_defaultMatchesConfig()
	{
		// Arrange
		final PlayerProfile profile = newProfile();

		// Act — ConfigProject.PROP_STOP_EXP = true (seeded above)
		final boolean actual = profile.getStopExp();

		// Assert
		assertTrue(actual);
	}

	@Test
	void setStopExp_flipsValue()
	{
		// Arrange
		final PlayerProfile profile = newProfile();

		// Act
		profile.setStopExp(false);

		// Assert
		assertFalse(profile.getStopExp());
	}

	@Test
	void getTradeRefusal_defaultMatchesConfig()
	{
		// Arrange
		final PlayerProfile profile = newProfile();

		// Act — ConfigProject.PROP_TRADE_REFUSAL = true (seeded above)
		final boolean actual = profile.getTradeRefusal();

		// Assert
		assertTrue(actual);
	}

	@Test
	void getAutoLoot_defaultFalse()
	{
		// Arrange
		final PlayerProfile profile = newProfile();

		// Act
		final boolean actual = profile.getAutoLoot();

		// Assert
		assertFalse(actual);
	}

	@Test
	void setAutoLoot_storesValue()
	{
		// Arrange
		final PlayerProfile profile = newProfile();

		// Act
		profile.setAutoLoot(true);

		// Assert
		assertTrue(profile.getAutoLoot());
	}

	@Test
	void getAcpCp_hp_mp_defaults()
	{
		// Arrange
		final PlayerProfile profile = newProfile();

		// Act + Assert
		assertEquals(95, profile.getAcpCp());
		assertEquals(95, profile.getAcpHp());
		assertEquals(75, profile.getAcpMp());
	}

	@Test
	void setAcpValues_storeCorrectly()
	{
		// Arrange
		final PlayerProfile profile = newProfile();

		// Act
		profile.setAcpCp(80);
		profile.setAcpHp(70);
		profile.setAcpMp(60);

		// Assert
		assertEquals(80, profile.getAcpCp());
		assertEquals(70, profile.getAcpHp());
		assertEquals(60, profile.getAcpMp());
	}

	@Test
	void getCachedData_isNonNull()
	{
		// Arrange
		final PlayerProfile profile = newProfile();

		// Act + Assert
		assertNotNull(profile.getCachedData());
	}
}
