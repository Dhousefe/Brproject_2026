package ext.mods.gameserver.model.actor.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Pure-logic tests for {@link PlayerRecom} helpers (no full server / JDBC).
 */
class PlayerRecomTest
{
	@Test
	void clampRecomHave_bounds()
	{
		assertEquals(0, PlayerRecom.clampRecomHave(-1));
		assertEquals(0, PlayerRecom.clampRecomHave(0));
		assertEquals(128, PlayerRecom.clampRecomHave(128));
		assertEquals(255, PlayerRecom.clampRecomHave(255));
		assertEquals(255, PlayerRecom.clampRecomHave(999));
	}
	
	@Test
	void clampRecomLeft_bounds()
	{
		assertEquals(0, PlayerRecom.clampRecomLeft(-5));
		assertEquals(0, PlayerRecom.clampRecomLeft(0));
		assertEquals(3, PlayerRecom.clampRecomLeft(3));
		assertEquals(9, PlayerRecom.clampRecomLeft(9));
		assertEquals(9, PlayerRecom.clampRecomLeft(10));
	}
	
	@Test
	void reverseGiveRecomState_removesTargetAndRestoresLeft()
	{
		final List<Integer> chars = new ArrayList<>();
		chars.add(100);
		chars.add(200);
		chars.add(300);
		
		final int restored = PlayerRecom.reverseGiveRecomState(chars, 200, 5);
		
		assertEquals(5, restored);
		assertEquals(List.of(100, 300), chars);
		assertFalse(chars.contains(200));
	}
	
	@Test
	void reverseGiveRecomState_clampsPreviousLeftAndIgnoresMissingTarget()
	{
		final List<Integer> chars = new ArrayList<>();
		chars.add(1);
		
		final int restored = PlayerRecom.reverseGiveRecomState(chars, 99, 50);
		
		assertEquals(9, restored); // clamp 50 -> 9
		assertTrue(chars.contains(1));
		assertEquals(1, chars.size());
	}
	
	@Test
	void reverseGiveRecomState_emptyListAndZeroLeft()
	{
		final List<Integer> chars = new ArrayList<>();
		final int restored = PlayerRecom.reverseGiveRecomState(chars, 42, 0);
		assertEquals(0, restored);
		assertTrue(chars.isEmpty());
	}
}
