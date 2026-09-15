package ext.mods.gameserver.model.actor.player;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import ext.mods.extensions.api.IDungeonSession;
import ext.mods.gameserver.model.actor.Attackable;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.player.DressMeState;
import ext.mods.gameserver.model.actor.player.TournamentState;

class PlayerAttachmentsTest
{
	private static final int OID = 42;
	private static final int OID_B = 99;
	
	private static final PlayerAttachments.Key<String> K = PlayerAttachments.Key.of("test.slot");
	private static final PlayerAttachments.Key<Boolean> HERO = PlayerAttachments.Key.of("playergod.heroAura");
	private static final PlayerAttachments.Key<Boolean> DUMMY = PlayerAttachments.Key.of("player.dummy");
	private static final PlayerAttachments.Key<IDungeonSession> DUNGEON = PlayerAttachments.Key.of("dungeon.session");
	private static final PlayerAttachments.Key<Boolean> DRESSME = PlayerAttachments.Key.of("dressme.active");
	private static final PlayerAttachments.Key<Boolean> TOUR = PlayerAttachments.Key.of("tournament.in");
	private static final PlayerAttachments.Key<int[]> TOUR_SAVE = PlayerAttachments.Key.of("tournament.save");
	private static final PlayerAttachments.Key<Integer> BB = PlayerAttachments.Key.of("battleboss.eventId");
	private static final PlayerAttachments.Key<Map<Integer, Long>> TEMP = PlayerAttachments.Key.of("ui.tempSelectedItems");
	private static final PlayerAttachments.Key<Long> AGATHION_TIME = PlayerAttachments.Key.of("agathion.lastSummonTime");
	
	@AfterEach
	void clear()
	{
		PlayerAttachments.clear(OID);
		PlayerAttachments.clear(OID_B);
	}
	
	@Test
	void putGetRemove()
	{
		PlayerAttachments.put(OID, K, "hello");
		assertEquals("hello", PlayerAttachments.get(OID, K));
		assertEquals("hello", PlayerAttachments.remove(OID, K));
		assertNull(PlayerAttachments.get(OID, K));
	}
	
	@Test
	void clearObjectId()
	{
		PlayerAttachments.put(OID, K, "x");
		PlayerAttachments.clear(OID);
		assertNull(PlayerAttachments.get(OID, K));
	}
	
	@Test
	void putNull_removesValue()
	{
		PlayerAttachments.put(OID, K, "keep");
		PlayerAttachments.put(OID, K, null);
		assertNull(PlayerAttachments.get(OID, K));
	}
	
	@Test
	void remove_missingKeyOrBag_returnsNull()
	{
		assertNull(PlayerAttachments.remove(OID, K));
		assertNull(PlayerAttachments.get(OID, K));
	}
	
	@Test
	void clear_missingObjectId_isSafe()
	{
		PlayerAttachments.clear(OID);
		assertNull(PlayerAttachments.get(OID, K));
		assertEquals(0, PlayerAttachments.trackedPlayerCount());
	}
	
	@Test
	void isolation_betweenObjectIds()
	{
		PlayerAttachments.put(OID, K, "a");
		PlayerAttachments.put(OID_B, K, "b");
		assertEquals("a", PlayerAttachments.get(OID, K));
		assertEquals("b", PlayerAttachments.get(OID_B, K));
		PlayerAttachments.clear(OID);
		assertNull(PlayerAttachments.get(OID, K));
		assertEquals("b", PlayerAttachments.get(OID_B, K));
	}
	
	@Test
	void trackedPlayerCount_reflectsNonEmptyBags()
	{
		final int before = PlayerAttachments.trackedPlayerCount();
		PlayerAttachments.put(OID, K, "x");
		assertTrue(PlayerAttachments.trackedPlayerCount() >= before + 1);
		PlayerAttachments.remove(OID, K);
		// empty bag is dropped from map
		assertEquals(before, PlayerAttachments.trackedPlayerCount());
	}
	
	@Test
	void view_putGetRemoveClear()
	{
		final PlayerAttachments.View view = PlayerAttachments.of(OID);
		assertEquals(OID, view.objectId());
		view.put(K, "via-view");
		assertEquals("via-view", view.get(K));
		assertEquals("via-view", view.remove(K));
		assertNull(view.get(K));
		view.put(K, "again");
		view.clear();
		assertNull(view.get(K));
	}
	
	@Test
	void key_equalsHashCodeToString()
	{
		final PlayerAttachments.Key<String> a = PlayerAttachments.Key.of("mod.feature.slot");
		final PlayerAttachments.Key<String> b = PlayerAttachments.Key.of("mod.feature.slot");
		final PlayerAttachments.Key<String> c = PlayerAttachments.Key.of("other.slot");
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
		assertNotEquals(a, c);
		assertTrue(a.toString().contains("mod.feature.slot"));
		assertEquals("mod.feature.slot", a.name());
		assertFalse(a.equals(null));
		assertFalse(a.equals("mod.feature.slot"));
	}
	
	@Test
	void wave2_booleanAndDungeon()
	{
		PlayerAttachments.put(OID, HERO, Boolean.TRUE);
		assertTrue(Boolean.TRUE.equals(PlayerAttachments.get(OID, HERO)));
		PlayerAttachments.put(OID, DUMMY, Boolean.TRUE);
		assertTrue(Boolean.TRUE.equals(PlayerAttachments.get(OID, DUMMY)));
		
		final IDungeonSession session = new IDungeonSession()
		{
			@Override
			public void onMobKill(Attackable attackable)
			{
			}
			
			@Override
			public void pauseForDisconnect(Player player)
			{
			}
			
			@Override
			public void resumeForReconnect(Player player)
			{
			}
		};
		PlayerAttachments.put(OID, DUNGEON, session);
		assertSame(session, PlayerAttachments.get(OID, DUNGEON));
	}
	
	@Test
	void wave3_featureKeys()
	{
		PlayerAttachments.put(OID, DRESSME, Boolean.TRUE);
		assertTrue(Boolean.TRUE.equals(PlayerAttachments.get(OID, DRESSME)));
		
		PlayerAttachments.put(OID, TOUR, Boolean.TRUE);
		assertTrue(Boolean.TRUE.equals(PlayerAttachments.get(OID, TOUR)));
		
		final int[] snap = { 1, 2, 3, 10, 20, 30, 0 };
		PlayerAttachments.put(OID, TOUR_SAVE, snap);
		assertArrayEquals(snap, PlayerAttachments.get(OID, TOUR_SAVE));
		
		PlayerAttachments.put(OID, BB, 7);
		assertEquals(7, PlayerAttachments.get(OID, BB).intValue());
		PlayerAttachments.remove(OID, BB);
		assertNull(PlayerAttachments.get(OID, BB));
		
		PlayerAttachments.put(OID, AGATHION_TIME, 12345L);
		assertEquals(12345L, PlayerAttachments.get(OID, AGATHION_TIME).longValue());
		
		final Map<Integer, Long> bag = new HashMap<>();
		bag.put(99, 5L);
		PlayerAttachments.put(OID, TEMP, bag);
		assertEquals(5L, PlayerAttachments.get(OID, TEMP).get(99).longValue());
	}
	
	@Test
	void wave6_stateApiKeysAlign()
	{
		// DressMeState / TournamentState use the same attachment key names
		assertEquals("dressme.active", DressMeState.ACTIVE.name());
		assertEquals("dressme.armorSkin", DressMeState.ARMOR.name());
		assertEquals("tournament.in", TournamentState.IN.name());
		assertEquals("tournament.battle", TournamentState.BATTLE.name());
	}
	
	@Test
	void agathionAndUiTemp_keyNamesAlign()
	{
		assertEquals("agathion.lastSummonTime", AgathionState.LAST_SUMMON_TIME.name());
		assertEquals("agathion.current", AgathionState.CURRENT.name());
		assertEquals("ui.tempSelectedItems", UiTempState.SELECTED_ITEMS.name());
	}
}
