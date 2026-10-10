package ext.mods.gameserver.model.actor.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ext.mods.gameserver.enums.IntentionType;
import ext.mods.gameserver.model.location.Location;

/**
 * Testes para validar o fluxo de concorrência e transição de intenções ao lidar com
 * spam-clicks de ataque, recolhimento de itens com inventário cheio e interações de movimento.
 */
class PlayerSpamClickMovementTest
{
	@Test
	@DisplayName("Transição de intenção para MOVE_TO deve sobrescrever intenção de PICK_UP")
	void moveTo_shouldOverridePickUpIntention()
	{
		final Intention current = new Intention();
		current.updateAsPickUp(12345, false);
		assertEquals(IntentionType.PICK_UP, current.getType());

		// Ao clicar no chão para mover, a intenção deve ser atualizada para MOVE_TO
		final Location targetLoc = new Location(1000, 2000, -3000);
		current.updateAsMoveTo(targetLoc, null);

		assertEquals(IntentionType.MOVE_TO, current.getType());
		assertEquals(targetLoc, current.getLoc());
		assertNull(current.getBoat());
	}

	@Test
	@DisplayName("Transição de intenção para MOVE_TO deve sobrescrever intenção agendada de ATTACK")
	void moveTo_shouldOverrideScheduledAttackIntention()
	{
		final Intention next = new Intention();
		// Simula ataque agendado por spam-click enquanto o swing estava acontecendo
		next.updateAsAttack(null, false, false, true);
		assertEquals(IntentionType.ATTACK, next.getType());

		// Jogador clica no chão para andar antes de terminar o ataque: nextIntention vira MOVE_TO
		final Location targetLoc = new Location(500, 500, 0);
		next.updateAsMoveTo(targetLoc, null);

		assertEquals(IntentionType.MOVE_TO, next.getType());
		assertEquals(targetLoc, next.getLoc());
	}

	@Test
	@DisplayName("Intention reset para IDLE deve limpar alvos e estados")
	void idle_shouldResetState()
	{
		final Intention intention = new Intention();
		intention.updateAsPickUp(999, true);
		assertEquals(IntentionType.PICK_UP, intention.getType());

		intention.updateAsIdle();
		assertEquals(IntentionType.IDLE, intention.getType());
		assertNull(intention.getFinalTarget());
	}

	@Test
	@DisplayName("Cópia segura de intenção MOVE_TO preserva destino e zera barco")
	void moveTo_copyMaintainsLocationIntegrity()
	{
		final Intention original = new Intention();
		final Location loc = new Location(15000, 25000, -3500);
		original.updateAsMoveTo(loc, null);

		final Intention copied = new Intention();
		copied.updateUsing(original);

		assertEquals(IntentionType.MOVE_TO, copied.getType());
		assertEquals(loc.getX(), copied.getLoc().getX());
		assertEquals(loc.getY(), copied.getLoc().getY());
		assertEquals(loc.getZ(), copied.getLoc().getZ());
		assertNull(copied.getBoat());
	}
}
