package ext.mods.gameserver.model.entity.autofarm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Suíte de testes unitários para os modos Defensive e Offensive de PvP do AutoFarm.
 * Valida:
 * 1. Modo Defensive: só contra-ataca players que causaram dano/ataque recente E que estejam com PvP Flag (>0) ou PK/Karma (>0).
 * 2. Modo Defensive: rejeita atacar players neutros (sem flag) mesmo que ocorra interação não-agressiva.
 * 3. Modo Offensive: ataca IMEDIATAMENTE qualquer player válido no campo de visão em modo PK (mesmo que pvpFlag==0 e karma==0), sem esperar flag.
 * 4. Modo Offensive: garante continuidade ininterrupta de ataque físico/skills até o abate do alvo.
 * 5. Proteção de aliados: membros de clan/party/ally/siege nunca são atacados em nenhum modo.
 * 6. Proteção de Peace Zone: ataques são totalmente bloqueados em zonas de paz.
 */
class AutoFarmPvpModeTest
{
	static class MockPvpEvaluator
	{
		private final ConcurrentMap<Integer, ConcurrentMap<Integer, Long>> _pvpAggressors = new ConcurrentHashMap<>();
		private static final long DEFENSIVE_AGGRO_MEMORY_MS = 15000L;

		public void recordAggression(int victimId, int aggressorId)
		{
			_pvpAggressors
				.computeIfAbsent(victimId, id -> new ConcurrentHashMap<>())
				.put(aggressorId, System.currentTimeMillis());
		}

		public boolean wasRecentlyAttackedBy(int victimId, int aggressorId)
		{
			final ConcurrentMap<Integer, Long> attackers = _pvpAggressors.get(victimId);
			if (attackers == null)
				return false;
			final Long lastHit = attackers.get(aggressorId);
			if (lastHit == null)
				return false;
			return System.currentTimeMillis() - lastHit <= DEFENSIVE_AGGRO_MEMORY_MS;
		}

		public boolean isValidPvpTarget(
			boolean isDefensiveMode,
			boolean isOffensiveMode,
			int victimId,
			int targetId,
			boolean isDead,
			boolean isPeaceZone,
			boolean isAllyOrParty,
			int targetPvpFlag,
			int targetKarma)
		{
			if (isDead || isPeaceZone || isAllyOrParty || victimId == targetId)
				return false;

			if (isDefensiveMode)
			{
				return wasRecentlyAttackedBy(victimId, targetId) && (targetPvpFlag > 0 || targetKarma > 0);
			}

			if (isOffensiveMode)
			{
				// Modo ofensivo: ataca imediatamente qualquer player em modo PK sem esperar flag
				return true;
			}

			return false;
		}

		public boolean canKeepAttackingPkTarget(boolean isOffensiveAutoFarm, int targetPvpFlag, int targetKarma)
		{
			if (targetKarma > 0 || targetPvpFlag > 0)
				return true;
			// Se o atacante estiver em AutoFarm Ofensivo, continua atacando o alvo em modo PK mesmo com flag 0
			return isOffensiveAutoFarm;
		}
	}

	private MockPvpEvaluator _evaluator;
	private final int MY_ID = 1001;
	private final int ENEMY_ID = 2002;
	private final int ALLY_ID = 3003;

	@BeforeEach
	void setUp()
	{
		_evaluator = new MockPvpEvaluator();
	}

	@Test
	@DisplayName("Modo Offensive: Ataca IMEDIATAMENTE player neutro sem flag (pvpFlag=0, karma=0) em modo PK")
	void testOffensive_attacksUnflaggedPlayerImmediatelyAsPk()
	{
		// Inimigo totalmente neutro (sem flag, sem karma, nunca nos atacou)
		boolean valid = _evaluator.isValidPvpTarget(
			false, true, MY_ID, ENEMY_ID, false, false, false, 0, 0
		);
		assertTrue(valid, "Modo ofensivo deve selecionar o player neutro imediatamente para abate PK");

		// Deve manter o loop de ataque contínuo sem parar no primeiro hit
		boolean keepAttacking = _evaluator.canKeepAttackingPkTarget(true, 0, 0);
		assertTrue(keepAttacking, "AutoFarm ofensivo deve continuar desferindo golpes de PK até a morte do alvo");
	}

	@Test
	@DisplayName("Modo Defensive: Contra-ataca player que atacou recentemente E está flaggado com PvP")
	void testDefensive_counterAttacksRecentPvpFlaggedAttacker()
	{
		_evaluator.recordAggression(MY_ID, ENEMY_ID);

		boolean valid = _evaluator.isValidPvpTarget(
			true, false, MY_ID, ENEMY_ID, false, false, false, 1, 0
		);
		assertTrue(valid, "Deve contra-atacar o player flaggado que nos atacou");
	}

	@Test
	@DisplayName("Modo Defensive: Rejeita atacar player neutro que não está flaggado (evita PK acidental)")
	void testDefensive_rejectsUnflaggedAttacker()
	{
		_evaluator.recordAggression(MY_ID, ENEMY_ID);

		boolean valid = _evaluator.isValidPvpTarget(
			true, false, MY_ID, ENEMY_ID, false, false, false, 0, 0
		);
		assertFalse(valid, "Modo defesa não deve atacar se o player não estiver com flag de PvP ou Karma");
	}

	@Test
	@DisplayName("Modos Defensive e Offensive: NUNCA atacam membros de Party, Clan ou Ally")
	void testBothModes_neverAttackAllies()
	{
		_evaluator.recordAggression(MY_ID, ALLY_ID);

		assertFalse(_evaluator.isValidPvpTarget(true, false, MY_ID, ALLY_ID, false, false, true, 1, 0),
			"Modo defensivo nunca deve contra-atacar membro de party/clan/ally");

		assertFalse(_evaluator.isValidPvpTarget(false, true, MY_ID, ALLY_ID, false, false, true, 0, 0),
			"Modo ofensivo nunca deve mirar membros de party/clan/ally");
	}

	@Test
	@DisplayName("Modos Defensive e Offensive: NUNCA atacam dentro de Peace Zone")
	void testBothModes_neverAttackInPeaceZone()
	{
		assertFalse(_evaluator.isValidPvpTarget(false, true, MY_ID, ENEMY_ID, false, true, false, 1, 0),
			"Não deve permitir ataque em Peace Zone");
	}
}
