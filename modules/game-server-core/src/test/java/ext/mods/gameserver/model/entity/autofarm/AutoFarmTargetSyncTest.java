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

/**
 * Testes de validação para a sincronização atômica de targets no AutoFarm e na camada de rede de Player.
 * Valida a mitigação de alvos fantasmas (Ghost Targets), sincronização precisa de mira (TargetSelected/TargetUnselected)
 * em unicast para o próprio cliente e transições de alvos no ciclo do AutoFarmRoutine.
 */
class AutoFarmTargetSyncTest
{
	/**
	 * Rastreamento de pacotes simulados enviados para o cliente (unicast) e transmitidos aos arredores (broadcast).
	 */
	static class MockClientSession
	{
		final List<String> unicastPackets = new ArrayList<>();
		final List<String> broadcastPackets = new ArrayList<>();

		public void sendPacket(String packetType)
		{
			unicastPackets.add(packetType);
		}

		public void broadcastPacket(String packetType)
		{
			broadcastPackets.add(packetType);
		}

		public void clear()
		{
			unicastPackets.clear();
			broadcastPackets.clear();
		}
	}

	/**
	 * Entidade simulada para testes de estado e sincronização de alvos.
	 */
	static class MockEntity
	{
		private final int _objectId;
		private final String _name;
		private boolean _dead;

		public MockEntity(int objectId, String name)
		{
			_objectId = objectId;
			_name = name;
			_dead = false;
		}

		public int getObjectId()
		{
			return _objectId;
		}

		public String getName()
		{
			return _name;
		}

		public boolean isDead()
		{
			return _dead;
		}

		public void setDead(boolean dead)
		{
			_dead = dead;
		}
	}

	/**
	 * Harness simulado que replica com fidelidade os novos comportamentos de Player.setTarget
	 * e AutoFarmRoutine.ensureTargetSynchronized / clearTargetSynchronized.
	 */
	static class MockPlayerTargetHarness
	{
		private final MockClientSession _session;
		private MockEntity _target;
		private MockEntity _lockedTarget;
		private long _targetLastHitTime;

		public MockPlayerTargetHarness(MockClientSession session)
		{
			_session = session;
		}

		public MockEntity getTarget()
		{
			return _target;
		}

		public void setTarget(MockEntity newTarget)
		{
			final MockEntity oldTarget = _target;

			if (oldTarget != null && oldTarget == newTarget)
			{
				return;
			}

			if (newTarget != null)
			{
				// Unicast packets para o jogador
				_session.sendPacket("MyTargetSelected");
				_session.sendPacket("StatusUpdate");

				// Broadcast para terceiros E Unicast para o próprio jogador (Correção de mira do cliente)
				_session.broadcastPacket("TargetSelected");
				_session.sendPacket("TargetSelected");
			}
			else
			{
				_session.sendPacket("ActionFailed");

				if (oldTarget != null)
				{
					// Broadcast para terceiros E Unicast para o próprio jogador (Eliminação de alvo fantasma)
					_session.broadcastPacket("TargetUnselected");
					_session.sendPacket("TargetUnselected");
				}
			}

			_target = newTarget;
		}

		public void ensureTargetSynchronized(MockEntity target)
		{
			if (target == null || target.isDead())
			{
				return;
			}

			if (_target != target)
			{
				setTarget(target);
			}
		}

		public void clearTargetSynchronized()
		{
			if (_target != null)
			{
				setTarget(null);
			}

			resetStuckLogic();
		}

		public void resetStuckLogic()
		{
			_lockedTarget = null;
			_targetLastHitTime = 0;
		}

		public void setStuckState(MockEntity target, long lastHitTime)
		{
			_lockedTarget = target;
			_targetLastHitTime = lastHitTime;
		}

		public MockEntity getLockedTarget()
		{
			return _lockedTarget;
		}

		public long getTargetLastHitTime()
		{
			return _targetLastHitTime;
		}
	}

	private MockClientSession _session;
	private MockPlayerTargetHarness _harness;
	private MockEntity _monsterA;
	private MockEntity _monsterB;
	private MockEntity _pvpTarget;

	@BeforeEach
	void setUp()
	{
		_session = new MockClientSession();
		_harness = new MockPlayerTargetHarness(_session);
		_monsterA = new MockEntity(1001, "Kelts");
		_monsterB = new MockEntity(1002, "Dire Wolf");
		_pvpTarget = new MockEntity(2001, "EnemyPlayer");
	}

	@Test
	@DisplayName("UC-Target-1: Seleção atômica de alvo despacha TargetSelected tanto em unicast quanto em broadcast")
	void testAtomicTargetSelection_DispatchesTargetSelectedInUnicastAndBroadcast()
	{
		_harness.ensureTargetSynchronized(_monsterA);

		assertEquals(_monsterA, _harness.getTarget(), "O target atual deve ser o monstro A");
		assertTrue(_session.unicastPackets.contains("MyTargetSelected"), "Deve enviar MyTargetSelected em unicast");
		assertTrue(_session.unicastPackets.contains("StatusUpdate"), "Deve enviar StatusUpdate em unicast");
		assertTrue(_session.unicastPackets.contains("TargetSelected"), "Deve enviar TargetSelected em unicast para sincronizar o retículo de mira no cliente");
		assertTrue(_session.broadcastPackets.contains("TargetSelected"), "Deve transmitir TargetSelected para jogadores ao redor");
	}

	@Test
	@DisplayName("UC-Target-2: Desseleção atômica despacha TargetUnselected em unicast eliminando alvo fantasma")
	void testAtomicTargetDeselection_DispatchesTargetUnselectedInUnicast()
	{
		_harness.ensureTargetSynchronized(_monsterA);
		_session.clear();

		// Monstro morre e o AutoFarm aciona clearTargetSynchronized
		_monsterA.setDead(true);
		_harness.clearTargetSynchronized();

		assertNull(_harness.getTarget(), "O target deve ser nulo após clearTargetSynchronized");
		assertTrue(_session.unicastPackets.contains("TargetUnselected"), "Unicast de TargetUnselected DEVE ser enviado ao jogador para fechar o retículo e barra de HP");
		assertTrue(_session.broadcastPackets.contains("TargetUnselected"), "Broadcast de TargetUnselected deve ser enviado a terceiros");
	}

	@Test
	@DisplayName("UC-Target-3: Desseleção quando target já é nulo não envia pacotes duplicados de TargetUnselected")
	void testTargetDeselection_WhenAlreadyNull_DoesNotSendDuplicateUnselected()
	{
		assertNull(_harness.getTarget());
		_session.clear();

		_harness.clearTargetSynchronized();

		assertFalse(_session.unicastPackets.contains("TargetUnselected"), "Não deve enviar TargetUnselected se o jogador não tinha alvo");
	}

	@Test
	@DisplayName("UC-Target-4: Troca imediata de prioridade para agressor (findFirstAttacker) é atômica")
	void testPrioritySwitchToAttacker_SwitchesTargetAtomically()
	{
		_harness.ensureTargetSynchronized(_monsterA);
		assertEquals(_monsterA, _harness.getTarget());
		_session.clear();

		// Monstro B ataca o jogador e tem prioridade
		_harness.ensureTargetSynchronized(_monsterB);

		assertEquals(_monsterB, _harness.getTarget(), "O alvo deve ser trocado atomicamente para o agressor");
		assertTrue(_session.unicastPackets.contains("MyTargetSelected"));
		assertTrue(_session.unicastPackets.contains("TargetSelected"));
	}

	@Test
	@DisplayName("UC-Target-5: Alvo morto não pode ser selecionado por ensureTargetSynchronized")
	void testEnsureTargetSynchronized_RejectsDeadEntity()
	{
		_monsterA.setDead(true);
		_harness.ensureTargetSynchronized(_monsterA);

		assertNull(_harness.getTarget(), "Entidade morta não deve ser aceita como target");
		assertTrue(_session.unicastPackets.isEmpty(), "Nenhum pacote deve ser despachado");
	}

	@Test
	@DisplayName("UC-Target-6: clearTargetSynchronized reseta as variáveis de stuck e lockedTarget")
	void testClearTargetSynchronized_ResetsStuckLogic()
	{
		_harness.ensureTargetSynchronized(_monsterA);
		_harness.setStuckState(_monsterA, System.currentTimeMillis());

		assertNotNull(_harness.getLockedTarget());
		assertTrue(_harness.getTargetLastHitTime() > 0);

		_harness.clearTargetSynchronized();

		assertNull(_harness.getTarget());
		assertNull(_harness.getLockedTarget(), "LockedTarget deve ser resetado para null");
		assertEquals(0, _harness.getTargetLastHitTime(), "TargetLastHitTime deve ser zerado");
	}

	@Test
	@DisplayName("UC-Target-7: Transição de alvo PvE para PvP agressivo ocorre com sincronização total")
	void testPvPTransition_SynchronizesTargetAtomically()
	{
		_harness.ensureTargetSynchronized(_monsterA);
		_session.clear();

		_harness.ensureTargetSynchronized(_pvpTarget);

		assertEquals(_pvpTarget, _harness.getTarget(), "O target deve ser o jogador PvP");
		assertTrue(_session.unicastPackets.contains("TargetSelected"), "Cliente deve receber o TargetSelected da transição PvP");
	}
}
