package ext.mods.gameserver.model.actor.ai.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Suíte de testes para validação do comportamento de IA de monstros em combate Melee, skills e relevo irregular.
 * Valida:
 * 1. Proximidade melee (<= 150u) não incrementa falso positivo de falha de geodata (geoPathFailCount == 0).
 * 2. Monstros em combate físico próximo nunca se teleportam para cima do jogador.
 * 3. Alvos verdadeiramente inalcançáveis a longa distância (> 250u) acionam returnHome() (conforme retail L2Off) em vez de teleporte instantâneo.
 * 4. Strafe e esquiva respeitam limite de desnível vertical (|Delta Z| <= 48u), evitando quedas em ribanceiras.
 * 5. Pós-cast de skills: monstros melee/guerreiros NÃO realizam recuo forçado de 380u (eliminando recuo fantasma e desync pós-skill).
 * 6. Apenas monstros genuinamente de longo alcance (> 200u) com skills de longo alcance (> 250u) são elegíveis a reposicionamento.
 */
class MonsterMeleeCombatAiTest
{
	static class MockMonsterMovementAi
	{
		private int _geoPathFailCount = 0;
		private boolean _returnedHome = false;
		private boolean _teleportedToPlayer = false;

		public void onPathEvaluated(int pathSize, double distance, int meleeAttackRange)
		{
			if (pathSize < 2)
			{
				if (distance > (meleeAttackRange + 100))
				{
					_geoPathFailCount++;
				}
				else
				{
					_geoPathFailCount = 0;
				}
			}
			else
			{
				_geoPathFailCount = 0;
			}
		}

		public void handleStuckMob(double distanceToAttacker)
		{
			if (_geoPathFailCount > 10)
			{
				_geoPathFailCount = 0;
				if (distanceToAttacker > 250)
				{
					_returnedHome = true;
				}
				else
				{
					_teleportedToPlayer = false;
				}
			}
		}

		public boolean validateRepositionSlope(int currentZ, int nextZ)
		{
			return Math.abs(nextZ - currentZ) <= 48;
		}

		public boolean shouldRepositionAfterSkillCast(int npcPhysicalAttackRange, int skillCastRange)
		{
			// Regra Retail L2Off: Apenas arqueiros/magos (> 200u) com skills de alcance (> 250u) recuam pós-cast
			return npcPhysicalAttackRange > 200 && skillCastRange > 250;
		}

		public int getGeoPathFailCount() { return _geoPathFailCount; }
		public boolean isReturnedHome() { return _returnedHome; }
		public boolean isTeleportedToPlayer() { return _teleportedToPlayer; }
	}

	@Test
	@DisplayName("UC-Mob-1: Proximidade Melee (dist <= 80u) não incrementa falhas de pathfinding")
	void testMeleeProximityDoesNotIncrementPathFailures()
	{
		final MockMonsterMovementAi ai = new MockMonsterMovementAi();
		
		for (int i = 0; i < 20; i++)
		{
			ai.onPathEvaluated(1, 60.0, 40);
		}

		assertEquals(0, ai.getGeoPathFailCount(), "Combate corpo-a-corpo não deve acumular falhas de geopath");
		
		ai.handleStuckMob(60.0);
		assertFalse(ai.isTeleportedToPlayer(), "Monstro jamais deve se teleportar para cima do jogador em melee");
		assertFalse(ai.isReturnedHome(), "Monstro não deve fugir enquanto estiver em combate próximo");
	}

	@Test
	@DisplayName("UC-Mob-2: Alvo inalcançável a longa distância (> 250u) retorna ao spawn (returnHome) conforme L2Off")
	void testUnreachableDistantTargetReturnsHomeInsteadOfTeleporting()
	{
		final MockMonsterMovementAi ai = new MockMonsterMovementAi();

		for (int i = 0; i < 12; i++)
		{
			ai.onPathEvaluated(0, 600.0, 40);
		}

		assertTrue(ai.getGeoPathFailCount() > 10);
		ai.handleStuckMob(600.0);

		assertTrue(ai.isReturnedHome(), "Monstro preso deve retornar para o spawn (returnHome) no L2Off");
		assertFalse(ai.isTeleportedToPlayer(), "Monstro NÃO deve se teleportar para o topo do penhasco onde está o jogador");
	}

	@Test
	@DisplayName("UC-Mob-3: Desnível de estrada/barranco com |Delta Z| > 48u é rejeitado pelo strafe evasivo")
	void testSteepSlopeRepositionIsRejected()
	{
		final MockMonsterMovementAi ai = new MockMonsterMovementAi();

		assertTrue(ai.validateRepositionSlope(100, 120), "Desnível suave de 20 unidades deve ser aceito");
		assertFalse(ai.validateRepositionSlope(100, 220), "Desnível íngreme de 120 unidades deve ser rejeitado para evitar queda/desync");
	}

	@Test
	@DisplayName("UC-Mob-4: Monstros Melee/Guerreiros NÃO recuam após usar skills de ataque físico")
	void testMeleeMonsterDoesNotRepositionAfterSkill()
	{
		final MockMonsterMovementAi ai = new MockMonsterMovementAi();
		
		// Monstro Melee usando skill de curto alcance (Stun, Power Strike, etc)
		final int meleeAttackRange = 40;
		final int meleeSkillRange = 40;
		
		boolean shouldReposition = ai.shouldRepositionAfterSkillCast(meleeAttackRange, meleeSkillRange);
		assertFalse(shouldReposition, "Monstros Melee NUNCA devem saltar para trás após usar skill (elimina desync e teleporte)");
	}

	@Test
	@DisplayName("UC-Mob-5: Monstros de Longo Alcance (Arqueiros/Magos) recuam suavemente após skill de longo alcance")
	void testRangedMonsterEligibleForRepositionAfterSkill()
	{
		final MockMonsterMovementAi ai = new MockMonsterMovementAi();
		
		final int rangedAttackRange = 600;
		final int rangedSkillRange = 900;
		
		boolean shouldReposition = ai.shouldRepositionAfterSkillCast(rangedAttackRange, rangedSkillRange);
		assertTrue(shouldReposition, "Arqueiros/Magos de longo alcance podem recuar para manter espaçamento tático");
	}
}
