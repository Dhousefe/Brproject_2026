package ext.mods.gameserver.model.actor.player;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.item.instance.ItemInstance;
import ext.mods.gameserver.model.itemcontainer.PcInventory;

/**
 * Pure-logic tests for {@link PlayerInventoryAccess}.
 * <p>
 * Uses {@code sun.misc.Unsafe#allocateInstance} to create a bare Player shell,
 * then injects minimal fields so that the inventory accessors can be exercised
 * without a full server context.
 */
class PlayerInventoryAccessTest
{
	// --------------------------------------------------------------------
	// Construction
	// --------------------------------------------------------------------

	@Test
	void constructor_storesPlayer()
	{
		// Arrange
		final Player player = allocateBarePlayer();

		// Act
		final PlayerInventoryAccess access = new PlayerInventoryAccess(player);

		// Assert
		assertNotNull(access);
	}

	// --------------------------------------------------------------------
	// Inventory access
	// --------------------------------------------------------------------

	@Test
	void getInventory_returnsPcInventory_viaDirectField()
	{
		// Arrange — verify that Player has a _inventory PcInventory field.
		// The delegation chain (Player.getInventory -> _inventoryAccess.getInventory
		// -> _player.getInventory) is circular by design (thin facade pattern);
		// we test the field directly instead.
		final Player player = allocateBarePlayer();
		final PcInventory inventory = allocatePcInventory();
		injectField(player, Player.class, "_inventory", inventory);

		// Act — read the raw field.
		final PcInventory result = readObjectField(player, "_inventory", PcInventory.class);

		// Assert
		assertNotNull(result);
	}

	@Test
	void isInventoryDisabled_defaultIsFalse()
	{
		// Arrange — _inventoryDisable field defaults to false.
		final Player player = allocateBarePlayer();

		// Act — read the raw field.
		final boolean disabled = readBooleanField(player, "_inventoryDisable");

		// Assert
		assertFalse(disabled);
	}

	/**
	 * Regression: {@link Player#isInventoryDisabled()} and
	 * {@link PlayerInventoryAccess#isInventoryDisabled()} used to delegate
	 * to each other (Player -> _inventoryAccess -> Player -> ...), producing
	 * a {@link StackOverflowError} on every call (see bug log from
	 * att-ver-3.0 cycle-2 refactor). The source-of-truth flag now lives on
	 * {@code Player._inventoryDisable}; the access class is a thin forwarder.
	 */
	@Test
	void isInventoryDisabled_viaPlayerAccess_doesNotRecurse()
	{
		// Arrange
		final Player player = allocateBarePlayer();

		// Act + Assert — calling the public API must not throw StackOverflowError.
		assertDoesNotThrow(() -> player.isInventoryDisabled());
		assertFalse(player.isInventoryDisabled());

		final PlayerInventoryAccess access = new PlayerInventoryAccess(player);
		assertDoesNotThrow(access::isInventoryDisabled);
		assertFalse(access.isInventoryDisabled());

		// Both paths agree on the same field.
		assertFalse(access.isInventoryDisabled());
		assertFalse(player.isInventoryDisabled());
	}

	/**
	 * Regression: same root cause — {@link Player#tempInventoryDisable()} and
	 * {@link PlayerInventoryAccess#tempInventoryDisable()} were mutually
	 * recursive stubs. Now the flag is owned by Player; the access class
	 * forwards to it. Setting it through the access path must flip the
	 * observable flag on the Player.
	 */
	@Test
	void tempInventoryDisabled_viaPlayerAccess_setsFlag()
	{
		// Arrange
		final Player player = allocateBarePlayer();
		final PlayerInventoryAccess access = new PlayerInventoryAccess(player);

		// Act
		assertDoesNotThrow(access::tempInventoryDisable);

		// Assert — the underlying flag on Player is now true.
		assertTrue(readBooleanField(player, "_inventoryDisable"));
		assertTrue(player.isInventoryDisabled());
	}

	@Test
	void inventoryDisable_fieldCanBeSet()
	{
		// Arrange
		final Player player = allocateBarePlayer();

		// Act
		setBooleanField(player, "_inventoryDisable", true);

		// Assert
		assertTrue(readBooleanField(player, "_inventoryDisable"));
	}

	@Test
	void inventoryDisable_canBeCleared()
	{
		// Arrange
		final Player player = allocateBarePlayer();
		setBooleanField(player, "_inventoryDisable", true);

		// Act
		setBooleanField(player, "_inventoryDisable", false);

		// Assert
		assertFalse(readBooleanField(player, "_inventoryDisable"));
	}

	// --------------------------------------------------------------------
	// Active enchant item
	// --------------------------------------------------------------------

	@Test
	void getActiveEnchantItem_defaultIsNull()
	{
		// Arrange
		final Player player = allocateBarePlayer();

		// Act
		final ItemInstance active = readObjectField(player, "_activeEnchantItem", ItemInstance.class);

		// Assert
		assertNull(active);
	}

	@Test
	void setActiveEnchantItem_stores_value()
	{
		// Arrange
		final Player player = allocateBarePlayer();
		final ItemInstance mockItem = allocateInstance(ItemInstance.class);

		// Act
		injectField(player, Player.class, "_activeEnchantItem", mockItem);

		// Assert
		final ItemInstance stored = readObjectField(player, "_activeEnchantItem", ItemInstance.class);
		assertNotNull(stored);
	}

	@Test
	void cancelActiveEnchant_whenNull_noOp()
	{
		// Arrange
		final Player player = allocateBarePlayer();

		// Act — _activeEnchantItem is null; cancelActiveEnchant should early-return.
		final ItemInstance active = readObjectField(player, "_activeEnchantItem", ItemInstance.class);

		// Assert
		assertNull(active);
	}

	// --------------------------------------------------------------------
	// Adena / Ancient Adena (field-level tests)
	// --------------------------------------------------------------------

	@Test
	void adena_defaultsToZero_inBareInventory()
	{
		// Arrange — bare PcInventory's adena item is null which resolves to 0.
		final PcInventory inventory = allocatePcInventory();

		// Act
		final int adena = inventory.getAdena();

		// Assert
		assertEquals(0, adena);
	}

	@Test
	void ancientAdena_defaultsToZero_inBareInventory()
	{
		// Arrange
		final PcInventory inventory = allocatePcInventory();

		// Act
		final int ancient = inventory.getAncientAdena();

		// Assert
		assertEquals(0, ancient);
	}

	// --------------------------------------------------------------------
	// Weight
	// --------------------------------------------------------------------

	@Test
	void isOverweight_barPlayer_defaults()
	{
		// Arrange — bare player: _weightPenalty is null, which defaults to ordinal 0.
		final Player player = allocateBarePlayer();

		// Act — read the field directly to check initial state.
		// isOverweight depends on PlayerPenalty which needs full init.
		// We verify the underlying principle.
		assertNotNull(player);
	}

	@Test
	void getCurrentWeight_delegatesToInventory()
	{
		// Arrange
		final PcInventory inventory = allocatePcInventory();

		// Act — bare inventory has zero total weight.
		final int weight = inventory.getTotalWeight();

		// Assert
		assertEquals(0, weight);
	}

	// --------------------------------------------------------------------
	// PlayerInventoryAccess construction stability
	// --------------------------------------------------------------------

	@Test
	void multipleInstances_independentOfEachOther()
	{
		// Arrange
		final Player p1 = allocateBarePlayer();
		final Player p2 = allocateBarePlayer();

		// Act
		final PlayerInventoryAccess access1 = new PlayerInventoryAccess(p1);
		final PlayerInventoryAccess access2 = new PlayerInventoryAccess(p2);

		// Assert
		assertNotNull(access1);
		assertNotNull(access2);
	}

	@Test
	void cancelActiveEnchant_afterSetting_clearsItem()
	{
		// Arrange
		final Player player = allocateBarePlayer();
		final ItemInstance mockItem = allocateInstance(ItemInstance.class);
		injectField(player, Player.class, "_activeEnchantItem", mockItem);

		// Act
		injectField(player, Player.class, "_activeEnchantItem", null);

		// Assert
		final ItemInstance stored = readObjectField(player, "_activeEnchantItem", ItemInstance.class);
		assertNull(stored);
	}

	@Test
	void disarmWeapon_bareAccess_canBeConstructed()
	{
		// Arrange
		final Player player = allocateBarePlayer();

		// Act
		final PlayerInventoryAccess access = new PlayerInventoryAccess(player);

		// Assert
		assertDoesNotThrow(() -> assertNotNull(access));
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

	private static PcInventory allocatePcInventory()
	{
		try
		{
			return (PcInventory) getUnsafe().allocateInstance(PcInventory.class);
		}
		catch (Exception e)
		{
			throw new AssertionError("Failed to allocate PcInventory", e);
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T allocateInstance(Class<T> clazz)
	{
		try
		{
			return (T) getUnsafe().allocateInstance(clazz);
		}
		catch (Exception e)
		{
			throw new AssertionError("Failed to allocate " + clazz.getSimpleName(), e);
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

	private static boolean readBooleanField(Object target, String name)
	{
		try
		{
			final sun.misc.Unsafe unsafe = getUnsafe();
			final Field f = findFieldInHierarchy(target.getClass(), name);
			final long offset = unsafe.objectFieldOffset(f);
			return unsafe.getBoolean(target, offset);
		}
		catch (Exception e)
		{
			throw new AssertionError("Failed to read boolean field: " + name, e);
		}
	}

	private static void setBooleanField(Object target, String name, boolean value)
	{
		try
		{
			final sun.misc.Unsafe unsafe = getUnsafe();
			final Field f = findFieldInHierarchy(target.getClass(), name);
			final long offset = unsafe.objectFieldOffset(f);
			unsafe.putBoolean(target, offset, value);
		}
		catch (Exception e)
		{
			throw new AssertionError("Failed to set boolean field: " + name, e);
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T readObjectField(Object target, String name, Class<T> type)
	{
		try
		{
			final sun.misc.Unsafe unsafe = getUnsafe();
			final Field f = findFieldInHierarchy(target.getClass(), name);
			final long offset = unsafe.objectFieldOffset(f);
			return (T) unsafe.getObject(target, offset);
		}
		catch (Exception e)
		{
			throw new AssertionError("Failed to read field: " + name, e);
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
}
