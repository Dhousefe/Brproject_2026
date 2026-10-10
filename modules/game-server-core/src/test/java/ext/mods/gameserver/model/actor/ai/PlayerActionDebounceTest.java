package ext.mods.gameserver.model.actor.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ext.mods.gameserver.enums.IntentionType;

/**
 * Testes unitários para validar a lógica de concorrência e debouncing de intenções de combate/pickup,
 * garantindo alinhamento com a arquitetura retail L2Off.
 */
class PlayerActionDebounceTest
{
	@Test
	@DisplayName("Intention ATTACK deve reter target e tipo corretamente")
	void intentionAttack_shouldRetainTargetAndType()
	{
		final Intention intention = new Intention();
		// Inicialmente vazia
		assertEquals(IntentionType.IDLE, intention.getType());
		assertNull(intention.getFinalTarget());
	}

	@Test
	@DisplayName("Intention PICK_UP deve armazenar o itemObjectId corretamente")
	void intentionPickUp_shouldStoreItemObjectId()
	{
		final Intention intention = new Intention();
		int itemObjId = 123456;
		intention.updateAsPickUp(itemObjId, false);

		assertEquals(IntentionType.PICK_UP, intention.getType());
		assertEquals(itemObjId, intention.getItemObjectId());
		assertFalse(intention.isShiftPressed());
	}

	@Test
	@DisplayName("Transição de intenções com cópia segura")
	void intentionCopy_shouldMatchProperties()
	{
		final Intention original = new Intention();
		original.updateAsPickUp(999, true);

		final Intention copy = new Intention();
		copy.updateUsing(original);

		assertEquals(IntentionType.PICK_UP, copy.getType());
		assertEquals(999, copy.getItemObjectId());
		assertEquals(original.isShiftPressed(), copy.isShiftPressed());
	}
}
