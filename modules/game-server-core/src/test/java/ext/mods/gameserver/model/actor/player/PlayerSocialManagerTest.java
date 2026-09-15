package ext.mods.gameserver.model.actor.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Set;

import org.junit.jupiter.api.Test;

import ext.mods.gameserver.model.actor.Player;

/**
 * Pure-logic tests for {@link PlayerSocialManager}.
 * <p>
 * Uses {@code sun.misc.Unsafe#allocateInstance} to create a bare Player shell.
 * {@link PlayerSocialManager} constructor creates sub-components (PlayerTrade,
 * PlayerPrivateStore) that capture the owner but never exercise heavy IO in
 * these tests. The fields tested here (marry request, couple, friend/block
 * lists) are entirely self-contained.
 */
class PlayerSocialManagerTest
{
	// --------------------------------------------------------------------
	// Marriage / engagement
	// --------------------------------------------------------------------

	@Test
	void isUnderMarryRequest_defaultIsFalse()
	{
		// Arrange
		final PlayerSocialManager mgr = newManager();

		// Act + Assert
		assertFalse(mgr.isUnderMarryRequest());
	}

	@Test
	void setUnderMarryRequest_changesToTrue()
	{
		// Arrange
		final PlayerSocialManager mgr = newManager();

		// Act
		mgr.setUnderMarryRequest(true);

		// Assert
		assertTrue(mgr.isUnderMarryRequest());
	}

	@Test
	void setUnderMarryRequest_resetToFalse()
	{
		// Arrange
		final PlayerSocialManager mgr = newManager();
		mgr.setUnderMarryRequest(true);

		// Act
		mgr.setUnderMarryRequest(false);

		// Assert
		assertFalse(mgr.isUnderMarryRequest());
	}

	@Test
	void getCoupleId_defaultIsZero()
	{
		// Arrange
		final PlayerSocialManager mgr = newManager();

		// Act + Assert
		assertEquals(0, mgr.getCoupleId());
	}

	@Test
	void setCoupleId_storesValue()
	{
		// Arrange
		final PlayerSocialManager mgr = newManager();

		// Act
		mgr.setCoupleId(1234);

		// Assert
		assertEquals(1234, mgr.getCoupleId());
	}

	@Test
	void getRequesterId_defaultIsZero()
	{
		// Arrange
		final PlayerSocialManager mgr = newManager();

		// Act + Assert
		assertEquals(0, mgr.getRequesterId());
	}

	@Test
	void setRequesterId_storesValue()
	{
		// Arrange
		final PlayerSocialManager mgr = newManager();

		// Act
		mgr.setRequesterId(999);

		// Assert
		assertEquals(999, mgr.getRequesterId());
	}

	// --------------------------------------------------------------------
	// Blocking all
	// --------------------------------------------------------------------

	@Test
	void isBlockingAll_defaultIsFalse()
	{
		// Arrange
		final PlayerSocialManager mgr = newManager();

		// Act + Assert
		assertFalse(mgr.isBlockingAll());
	}

	// --------------------------------------------------------------------
	// Friend list
	// --------------------------------------------------------------------

	@Test
	void getSelectedFriendList_initiallyEmpty()
	{
		// Arrange
		final PlayerSocialManager mgr = newManager();

		// Act
		final Set<Integer> friends = mgr.getSelectedFriendList();

		// Assert
		assertNotNull(friends);
		assertTrue(friends.isEmpty());
	}

	@Test
	void selectFriend_addsToSet()
	{
		// Arrange
		final PlayerSocialManager mgr = newManager();

		// Act
		mgr.selectFriend(42);

		// Assert
		assertTrue(mgr.getSelectedFriendList().contains(42));
		assertEquals(1, mgr.getSelectedFriendList().size());
	}

	@Test
	void selectFriend_duplicateIgnored()
	{
		// Arrange
		final PlayerSocialManager mgr = newManager();
		mgr.selectFriend(42);

		// Act
		mgr.selectFriend(42);

		// Assert — Set semantics.
		assertEquals(1, mgr.getSelectedFriendList().size());
	}

	@Test
	void deselectFriend_removesFromSet()
	{
		// Arrange
		final PlayerSocialManager mgr = newManager();
		mgr.selectFriend(42);

		// Act
		mgr.deselectFriend(42);

		// Assert
		assertFalse(mgr.getSelectedFriendList().contains(42));
		assertTrue(mgr.getSelectedFriendList().isEmpty());
	}

	@Test
	void deselectFriend_nonExistent_isSafe()
	{
		// Arrange
		final PlayerSocialManager mgr = newManager();

		// Act + Assert — removing a non-existent entry should not throw.
		mgr.deselectFriend(999);
		assertTrue(mgr.getSelectedFriendList().isEmpty());
	}

	// --------------------------------------------------------------------
	// Block list
	// --------------------------------------------------------------------

	@Test
	void getSelectedBlocksList_initiallyEmpty()
	{
		// Arrange
		final PlayerSocialManager mgr = newManager();

		// Act
		final Set<Integer> blocks = mgr.getSelectedBlocksList();

		// Assert
		assertNotNull(blocks);
		assertTrue(blocks.isEmpty());
	}

	@Test
	void selectBlock_addsToSet()
	{
		// Arrange
		final PlayerSocialManager mgr = newManager();

		// Act
		mgr.selectBlock(77);

		// Assert
		assertTrue(mgr.getSelectedBlocksList().contains(77));
		assertEquals(1, mgr.getSelectedBlocksList().size());
	}

	@Test
	void deselectBlock_removesFromSet()
	{
		// Arrange
		final PlayerSocialManager mgr = newManager();
		mgr.selectBlock(77);

		// Act
		mgr.deselectBlock(77);

		// Assert
		assertFalse(mgr.getSelectedBlocksList().contains(77));
		assertTrue(mgr.getSelectedBlocksList().isEmpty());
	}

	@Test
	void deselectBlock_nonExistent_isSafe()
	{
		// Arrange
		final PlayerSocialManager mgr = newManager();

		// Act + Assert
		mgr.deselectBlock(888);
		assertTrue(mgr.getSelectedBlocksList().isEmpty());
	}

	@Test
	void friendAndBlock_areIndependent()
	{
		// Arrange
		final PlayerSocialManager mgr = newManager();

		// Act
		mgr.selectFriend(1);
		mgr.selectBlock(2);

		// Assert
		assertTrue(mgr.getSelectedFriendList().contains(1));
		assertFalse(mgr.getSelectedFriendList().contains(2));
		assertTrue(mgr.getSelectedBlocksList().contains(2));
		assertFalse(mgr.getSelectedBlocksList().contains(1));
	}

	// --------------------------------------------------------------------
	// Factory
	// --------------------------------------------------------------------

	private static PlayerSocialManager newManager()
	{
		try
		{
			final sun.misc.Unsafe unsafe = getUnsafe();
			final Player player = (Player) unsafe.allocateInstance(Player.class);

			// PlayerSocialManager's constructor creates PlayerTrade and
			// PlayerPrivateStore which both capture the owner reference but do
			// no IO in their constructors.
			return new PlayerSocialManager(player);
		}
		catch (Exception e)
		{
			throw new AssertionError("Failed to create PlayerSocialManager stub", e);
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
}
