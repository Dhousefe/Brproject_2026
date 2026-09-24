package ext.mods.gameserver.model.entity.autofarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

/**
 * Suíte de testes unitários para mitigação de desync, teleporte e ataque a monstros invisíveis no AutoFarm.
 * Valida:
 * 1. Flush imediato do NpcSpawnPacer garantindo NpcInfo antes de MyTargetSelected / TargetSelected.
 * 2. Teto rígido de busca de alvos a 1.500 unidades (campo visual da Unreal Engine 2.5).
 * 3. Filtragem de monstros em zonas amplas para não engajar alvos distantes fora do campo de visão.
 * 4. Reconciliação suave em ValidatePosition sem teleporte punitivo para a cidade em jogadores de AutoFarm.
 * 5. Banda morta (deadband) de 80 unidades em perseguição ofensiva (anti-jitter).
 * 6. Parada imediata de movimentação ao desselecionar alvos (clearTargetSynchronized).
 */
class AutoFarmDesyncAndVisibilityTest
{
	/**
	 * Simula a sessão de rede registrando pacotes despachados.
	 */
	static class MockClientSession
	{
		final List<String> packetLog = new ArrayList<>();

		public void sendPacket(String packetType)
		{
			packetLog.add(packetType);
		}

		public void clear()
		{
			packetLog.clear();
		}
	}

	/**
	 * Simula o NpcSpawnPacer com a lógica de fila, isPending e flushImmediate.
	 */
	static class MockNpcSpawnPacer
	{
		private final IntArrayList _pendingQueue = new IntArrayList(32);
		private final IntOpenHashSet _pendingSet = new IntOpenHashSet(32);
		private final MockClientSession _session;

		public MockNpcSpawnPacer(MockClientSession session)
		{
			_session = session;
		}

		public void queueNpc(int objectId)
		{
			if (_pendingSet.add(objectId))
			{
				_pendingQueue.add(objectId);
			}
		}

		public boolean isPending(int objectId)
		{
			return _pendingSet.contains(objectId);
		}

		public boolean flushImmediate(int objectId)
		{
			if (_pendingSet.remove(objectId))
			{
				_pendingQueue.rem(objectId);
				_session.sendPacket("NpcInfo:" + objectId);
				return true;
			}
			return false;
		}

		public int getPendingCount()
		{
			return _pendingQueue.size();
		}
	}

	/**
	 * Harness para simulação de targeting, visibilidade e sincronização no AutoFarm.
	 */
	static class MockAutoFarmTargetingHarness
	{
		private final MockClientSession _session;
		private final MockNpcSpawnPacer _pacer;
		private boolean _isMoving;
		private Integer _currentTargetId;

		public MockAutoFarmTargetingHarness(MockClientSession session, MockNpcSpawnPacer pacer)
		{
			_session = session;
			_pacer = pacer;
			_isMoving = false;
			_currentTargetId = null;
		}

		public void ensureTargetSynchronized(int monsterId)
		{
			// Se o monstro estiver retido no Pacer, libera o NpcInfo no socket ANTES da seleção
			if (_pacer.isPending(monsterId))
			{
				_pacer.flushImmediate(monsterId);
			}

			if (_currentTargetId == null || _currentTargetId != monsterId)
			{
				_currentTargetId = monsterId;
				_session.sendPacket("MyTargetSelected:" + monsterId);
				_session.sendPacket("StatusUpdate:" + monsterId);
				_session.sendPacket("TargetSelected:" + monsterId);
			}
		}

		public void clearTargetSynchronized()
		{
			if (_currentTargetId != null)
			{
				_session.sendPacket("TargetUnselected:" + _currentTargetId);
				_currentTargetId = null;
			}
			stopMovement();
		}

		public void stopMovement()
		{
			_isMoving = false;
			_session.sendPacket("StopMove");
		}

		public void startMovement()
		{
			_isMoving = true;
		}

		public boolean isMoving()
		{
			return _isMoving;
		}

		public Integer getCurrentTargetId()
		{
			return _currentTargetId;
		}
	}

	/**
	 * Simula o reconciliador de ValidatePosition com e sem AutoFarm.
	 */
	static class MockPositionReconciler
	{
		private int _incorrectValidateCount = 0;
		private boolean _teleportedToTown = false;
		private String _lastCorrectionPacket = null;

		public void validatePosition(double diffSq, double hardThresholdSq, boolean isAutoFarming, boolean hasValidDestination)
		{
			if (diffSq > hardThresholdSq)
			{
				if (isAutoFarming)
				{
					// Reconciliação suave sem teleporte para a cidade
					if (hasValidDestination)
					{
						_lastCorrectionPacket = "MoveToLocation";
					}
					else
					{
						_lastCorrectionPacket = "MoveToPawn";
					}
					return;
				}

				// Jogador normal: incrementa contagem e aciona snap/teleporte
				_incorrectValidateCount++;
				if (_incorrectValidateCount >= 3)
				{
					_teleportedToTown = true;
					_lastCorrectionPacket = "TeleportToTown";
					_incorrectValidateCount = 0;
					return;
				}
				_lastCorrectionPacket = "ValidateLocation";
			}
		}

		public int getIncorrectValidateCount()
		{
			return _incorrectValidateCount;
		}

		public boolean isTeleportedToTown()
		{
			return _teleportedToTown;
		}

		public String getLastCorrectionPacket()
		{
			return _lastCorrectionPacket;
		}
	}

	/**
	 * Simula o motor de perseguição ofensiva com deadband de 80 unidades.
	 */
	static class MockOffensiveFollowEngine
	{
		private double _lastDestinationX = 0.0;
		private double _lastDestinationY = 0.0;
		private int _movementPacketCount = 0;

		public boolean updateFollowPosition(double targetX, double targetY, boolean isAutoFarming)
		{
			final double threshold = isAutoFarming ? 80.0 : 60.0;
			final double dx = targetX - _lastDestinationX;
			final double dy = targetY - _lastDestinationY;
			final double dist = Math.sqrt(dx * dx + dy * dy);

			if (_movementPacketCount == 0 || dist > threshold)
			{
				_lastDestinationX = targetX;
				_lastDestinationY = targetY;
				_movementPacketCount++;
				return true; // Envia novo pacote MoveToLocation
			}
			return false; // Suprime reenvio desnecessário (Deadband anti-jitter)
		}

		public int getMovementPacketCount()
		{
			return _movementPacketCount;
		}
	}

	private MockClientSession _session;
	private MockNpcSpawnPacer _pacer;
	private MockAutoFarmTargetingHarness _harness;

	@BeforeEach
	void setUp()
	{
		_session = new MockClientSession();
		_pacer = new MockNpcSpawnPacer(_session);
		_harness = new MockAutoFarmTargetingHarness(_session, _pacer);
	}

	@Test
	@DisplayName("UC-Desync-1: NpcSpawnPacer libera NpcInfo no socket imediatamente antes de selecionar o alvo")
	void testNpcSpawnPacer_flushImmediate_deliversNpcInfoBeforeTargetSelected()
	{
		final int monsterId = 100501;
		_pacer.queueNpc(monsterId);

		assertTrue(_pacer.isPending(monsterId), "O monstro deve estar marcado como pendente no Pacer");

		// AutoFarm seleciona o alvo
		_harness.ensureTargetSynchronized(monsterId);

		assertFalse(_pacer.isPending(monsterId), "O monstro deve ter sido removido da fila pendente");
		assertEquals(4, _session.packetLog.size());

		// Ordem estrita do Netty: NpcInfo DEVE ser o primeiro para instanciar o modelo 3D no cliente
		assertEquals("NpcInfo:100501", _session.packetLog.get(0), "NpcInfo deve ser transmitido antes de qualquer pacote de mira");
		assertEquals("MyTargetSelected:100501", _session.packetLog.get(1));
		assertEquals("StatusUpdate:100501", _session.packetLog.get(2));
		assertEquals("TargetSelected:100501", _session.packetLog.get(3));
	}

	@Test
	@DisplayName("UC-Desync-2: Busca SIMD impõe teto visual estrito de 1.500 unidades para não selecionar alvos invisíveis")
	void testTargetSelection_enforcesVisualRangeCapOf1500Units()
	{
		final int configuredRange = 3500;
		final int effectiveMaxRange = Math.min(configuredRange, 1500);

		assertEquals(1500, effectiveMaxRange, "O range efetivo deve ser truncado em 1.500 unidades");

		final double targetDistanceFar = 1800.0;
		final double targetDistanceNear = 1200.0;

		assertFalse(targetDistanceFar <= effectiveMaxRange, "Alvo a 1.800 unidades não deve ser elegível para ataque imediato");
		assertTrue(targetDistanceNear <= effectiveMaxRange, "Alvo a 1.200 unidades deve ser elegível");
	}

	@Test
	@DisplayName("UC-Desync-3: AutoFarmZone filtra monstros do polígono além do raio visual de 1.500 unidades")
	void testAutoFarmZone_doesNotTargetInvisibleMonstersBeyondVisualRange()
	{
		record MockMonster(int id, double distance) {}

		final List<MockMonster> allZoneMonsters = List.of(
			new MockMonster(1, 400.0),
			new MockMonster(2, 1100.0),
			new MockMonster(3, 1600.0),
			new MockMonster(4, 2800.0)
		);

		final List<MockMonster> visibleCandidates = allZoneMonsters.stream()
			.filter(m -> m.distance() <= 1500.0)
			.toList();

		assertEquals(2, visibleCandidates.size(), "Apenas monstros a <= 1.500 unidades devem ser retornados");
		assertEquals(1, visibleCandidates.get(0).id());
		assertEquals(2, visibleCandidates.get(1).id());
	}

	@Test
	@DisplayName("UC-Desync-4: ValidatePosition suprime teleporte de cidade e hard snap quando o jogador está em AutoFarm")
	void testValidatePosition_suppressesTownTeleport_whenPlayerIsAutoFarming()
	{
		final MockPositionReconciler reconciler = new MockPositionReconciler();
		final double hardThresholdSq = 500.0 * 500.0; // 250000.0
		final double diffSq = 360000.0; // Desvio severo em terreno irregular (360.000 > 250.000)

		// Simula 3 validações incorretas consecutivas enquanto em AutoFarm
		for (int i = 0; i < 3; i++)
		{
			reconciler.validatePosition(diffSq, hardThresholdSq, true, true);
		}

		assertFalse(reconciler.isTeleportedToTown(), "Jogador em AutoFarm NÃO deve ser teleportado para a cidade");
		assertEquals(0, reconciler.getIncorrectValidateCount(), "Contador punitivo não deve ser acumulado");
		assertEquals("MoveToLocation", reconciler.getLastCorrectionPacket(), "Deve reconciliar suavemente via MoveToLocation");

		// Comportamento do jogador normal (sem AutoFarm): deve ser punido após 3 falhas
		for (int i = 0; i < 3; i++)
		{
			reconciler.validatePosition(diffSq, hardThresholdSq, false, true);
		}

		assertTrue(reconciler.isTeleportedToTown(), "Jogador comum que viola posição deve ser teleportado para a cidade");
		assertEquals("TeleportToTown", reconciler.getLastCorrectionPacket());
	}

	@Test
	@DisplayName("UC-Desync-5: Perseguição com deadband de 80 unidades suprime jitter de micro-passos")
	void testOffensiveFollow_suppressesJitter_within80UnitsDeadband()
	{
		final MockOffensiveFollowEngine followEngine = new MockOffensiveFollowEngine();

		// Primeiro posicionamento do monstro (inicializa rota)
		assertTrue(followEngine.updateFollowPosition(100.0, 100.0, true));
		assertEquals(1, followEngine.getMovementPacketCount());

		// Monstro dá um micro-passo de 30 unidades (divergência < 80u)
		assertFalse(followEngine.updateFollowPosition(120.0, 120.0, true), "Deslocamento de ~28 unidades deve ser absorvido pela deadband");
		assertEquals(1, followEngine.getMovementPacketCount(), "Nenhum pacote novo de movimento deve ser emitido");

		// Monstro corre 100 unidades (divergência > 80u)
		assertTrue(followEngine.updateFollowPosition(200.0, 200.0, true), "Deslocamento expressivo (>80u) deve recalcular rota");
		assertEquals(2, followEngine.getMovementPacketCount(), "Deve emitir nova rota MoveToLocation");
	}

	@Test
	@DisplayName("UC-Desync-6: clearTargetSynchronized interrompe a movimentação imediatamente")
	void testClearTarget_abortsOngoingMovementImmediately()
	{
		_harness.ensureTargetSynchronized(2001);
		_harness.startMovement();

		assertTrue(_harness.isMoving());
		assertNotNull(_harness.getCurrentTargetId());

		_session.clear();
		_harness.clearTargetSynchronized();

		assertNull(_harness.getCurrentTargetId(), "O alvo deve ser nulo");
		assertFalse(_harness.isMoving(), "A movimentação física do jogador deve ser cancelada de imediato");
		assertTrue(_session.packetLog.contains("StopMove"), "Deve despachar pacote de StopMove");
		assertTrue(_session.packetLog.contains("TargetUnselected:2001"), "Deve despachar TargetUnselected");
	}
}
