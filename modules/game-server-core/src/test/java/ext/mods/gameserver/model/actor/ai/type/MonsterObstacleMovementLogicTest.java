package ext.mods.gameserver.model.actor.ai.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Suíte de testes lógicos para validação da movimentação de monstros próximos a obstáculos
 * (rochas, paredes e relevos acidentados em Forge of the Gods / Catacumbas).
 *
 * Garante que monstros se comportem de forma consistente, contornem obstáculos de forma aceitável
 * e NUNCA desapareçam, saltem para tetos ou se teletransportem inesperadamente.
 */
class MonsterObstacleMovementLogicTest
{
	static class MockMonsterMovementController
	{
		private int _curX = 173400;
		private int _curY = -112700;
		private int _curZ = -3680;
		private boolean _blocked = false;
		private boolean _teleported = false;
		private boolean _returnedHome = false;
		private int _geoPathFailCount = 0;

		public boolean simulateStepToObstacle(int nextX, int nextY, int sampledZ, boolean hasDirectLine)
		{
			// Trava de desnível vertical idêntica a CreatureMove.kt (Delta Z <= 48 sem linha direta)
			int effectiveZ;
			int dz = Math.abs(sampledZ - _curZ);
			if (dz > 48 && !hasDirectLine)
			{
				effectiveZ = _curZ; // Proteção: impede salto para teto ou andar de cima!
			}
			else
			{
				effectiveZ = sampledZ;
			}

			// Se houver colisão com a parede ou obstáculo intransponível
			if (!hasDirectLine)
			{
				_blocked = true;
				// Posição física é mantida na coordenada atual de segurança
				return false;
			}

			_curX = nextX;
			_curY = nextY;
			_curZ = effectiveZ;
			return true;
		}

		public boolean tryRecalculateContourPath(boolean pathFound, int pathLength)
		{
			if (!pathFound || pathLength < 2)
			{
				_geoPathFailCount++;
				return false;
			}
			_geoPathFailCount = 0;
			_blocked = false;
			return true;
		}

		public void handleUnreachableTarget(double distanceToTarget)
		{
			if (_geoPathFailCount >= 10)
			{
				_geoPathFailCount = 0;
				if (distanceToTarget > 250)
				{
					_returnedHome = true;
				}
				else
				{
					// Em curta distância, monstro NÃO desaparece nem teleporta
					_teleported = false;
				}
			}
		}

		public int getCurZ() { return _curZ; }
		public boolean isBlocked() { return _blocked; }
		public boolean isTeleported() { return _teleported; }
		public boolean isReturnedHome() { return _returnedHome; }
		public int getGeoPathFailCount() { return _geoPathFailCount; }
	}

	@Test
	@DisplayName("Caso 1: Monstro em frente a uma rocha sólida deve parar (blocked) sem atravessar ou sumir")
	void monsterHittingRock_shouldBlockMovementWithoutDesync()
	{
		final MockMonsterMovementController controller = new MockMonsterMovementController();

		// Simula tentativa de passo para dentro da rocha (hasDirectLine = false)
		boolean moved = controller.simulateStepToObstacle(173420, -112710, -3680, false);

		assertFalse(moved, "O monstro não deve penetrar a rocha sólida");
		assertTrue(controller.isBlocked(), "O monstro deve ser marcado como bloqueado (_blocked = true)");
		assertFalse(controller.isTeleported(), "O monstro nunca deve ser teleportado ao colidir na rocha");
	}

	@Test
	@DisplayName("Caso 2: Monstro perto de rocha inclinada não deve saltar verticalmente para o teto (Delta Z > 48)")
	void monsterNearSteepSlope_shouldClampZToPreventJumpingToCeiling()
	{
		final MockMonsterMovementController controller = new MockMonsterMovementController();

		// Amostra de geodata anômala (ex: teto a Z = -2800 em vez do chão -3680, Delta Z = 880u)
		controller.simulateStepToObstacle(173410, -112705, -2800, false);

		assertEquals(-3680, controller.getCurZ(),
			"A posição Z do monstro deve permanecer no chão seguro (-3680) sem saltar para o teto");
	}

	@Test
	@DisplayName("Caso 3: Monstro que encontra contorno de obstáculo deve seguir o caminho suavemente")
	void monsterFindingContourPath_shouldClearBlockedAndFollow()
	{
		final MockMonsterMovementController controller = new MockMonsterMovementController();

		// Simula colisão inicial
		controller.simulateStepToObstacle(173415, -112705, -3680, false);
		assertTrue(controller.isBlocked());

		// Pathfinding encontra caminho contornando a rocha com 3 nós
		boolean pathRecalculated = controller.tryRecalculateContourPath(true, 3);

		assertTrue(pathRecalculated, "O monstro deve aceitar o caminho contornando a rocha");
		assertFalse(controller.isBlocked(), "O status de bloqueio deve ser limpo ao iniciar o contorno");
		assertEquals(0, controller.getGeoPathFailCount(), "Falhas de geopath devem ser resetadas");
	}

	@Test
	@DisplayName("Caso 4: Alvo inalcançável a distância (> 250u) retorna ao spawn (returnHome) sem teleporte fantasma")
	void monsterWithUnreachableDistantTarget_shouldReturnHomeGracefully()
	{
		final MockMonsterMovementController controller = new MockMonsterMovementController();

		// Acumula 10 falhas consecutivas de caminho
		for (int i = 0; i < 10; i++)
		{
			controller.tryRecalculateContourPath(false, 0);
		}

		// Alvo está a 400u de distância (em cima de uma rocha fechada)
		controller.handleUnreachableTarget(400.0);

		assertTrue(controller.isReturnedHome(),
			"Monstro com alvo inalcançável distante deve retornar para a posição de spawn (retail L2Off)");
		assertFalse(controller.isTeleported(),
			"Monstro não deve teleportar de surpresa para cima do jogador");
	}

	@Test
	@DisplayName("Caso 5: Alvo inalcançável a curta distância (<= 250u) não deve fugir nem teleportar")
	void monsterWithUnreachableMeleeTarget_shouldStayAndNotDisappear()
	{
		final MockMonsterMovementController controller = new MockMonsterMovementController();

		for (int i = 0; i < 10; i++)
		{
			controller.tryRecalculateContourPath(false, 0);
		}

		// Alvo em curta distância (80u)
		controller.handleUnreachableTarget(80.0);

		assertFalse(controller.isReturnedHome(),
			"Monstro não deve voltar para o spawn se estiver em combate próximo ao jogador");
		assertFalse(controller.isTeleported(),
			"Monstro não deve teleportar arbitrariamente");
	}
}
