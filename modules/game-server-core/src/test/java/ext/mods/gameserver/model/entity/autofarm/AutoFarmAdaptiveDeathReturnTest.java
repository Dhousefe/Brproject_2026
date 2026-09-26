package ext.mods.gameserver.model.entity.autofarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Suíte de testes unitários para a função inteligente de Retorno pós-morte e Anti-Delevel do AutoFarm.
 * Valida:
 * 1. Morte isolada/acidental por monstro não altera o nível do spot.
 * 2. Mortes consecutivas (>= 2) acionam recuo inteligente de 3 níveis abaixo da área anterior.
 * 3. Morte por PvP/PK não penaliza o nível de monstros do AutoFarm.
 * 4. Mortes consecutivas persistentes regridem de forma cumulativa com piso seguro (mínimo nível 10).
 * 5. Sequência de abates bem-sucedidos (10 kills) zera as mortes consecutivas e estabiliza o farm.
 */
class AutoFarmAdaptiveDeathReturnTest
{
	private AutoFarmProfile _profile;

	@BeforeEach
	void setUp()
	{
		_profile = new AutoFarmProfile(null);
	}

	@Test
	@DisplayName("UC-Return-1: Morte isolada (< 2) não aciona recuo de nível")
	void testSingleDeathDoesNotTriggerAdaptiveStepback()
	{
		final int monsterLevel = 60;
		_profile.recordMonsterDeath(monsterLevel);

		assertEquals(1, _profile.getConsecutiveMonsterDeaths());
		assertEquals(0, _profile.getAdaptiveLevelOffset());
	}

	@Test
	@DisplayName("UC-Return-2: Duas mortes consecutivas recuam 3 níveis abaixo do monstro anterior")
	void testConsecutiveDeathsTriggerThreeLevelStepback()
	{
		final int killerLevel = 60;
		_profile.recordMonsterDeath(killerLevel);
		_profile.recordMonsterDeath(killerLevel);

		assertEquals(2, _profile.getConsecutiveMonsterDeaths());
		assertEquals(3, _profile.getAdaptiveLevelOffset());

		final int targetLevel = _profile.getAdaptiveTargetLevel(60);
		assertEquals(57, targetLevel, "O spot adaptativo deve ser de monstros nível 57 (60 - 3)");
	}

	@Test
	@DisplayName("UC-Return-3: Três mortes consecutivas recuam mais 3 níveis (total -6)")
	void testPersistentDeathsAccumulateStepback()
	{
		_profile.recordMonsterDeath(70);
		_profile.recordMonsterDeath(70);
		_profile.recordMonsterDeath(67);

		assertEquals(3, _profile.getConsecutiveMonsterDeaths());
		assertEquals(6, _profile.getAdaptiveLevelOffset());

		final int targetLevel = _profile.getAdaptiveTargetLevel(70);
		assertEquals(61, targetLevel, "O nível do alvo deve recuar 6 níveis (67 - 6 = 61)");
	}

	@Test
	@DisplayName("UC-Return-4: Piso de segurança mínimo é nível 10")
	void testSafetyFloorMinimumLevelTen()
	{
		_profile.recordMonsterDeath(12);
		_profile.recordMonsterDeath(12);
		_profile.recordMonsterDeath(10);

		final int targetLevel = _profile.getAdaptiveTargetLevel(12);
		assertEquals(10, targetLevel, "O nível nunca deve ser inferior a 10");
	}

	@Test
	@DisplayName("UC-Return-5: Morte em PvP não penaliza o nível de monstros")
	void testPvpDeathDoesNotTriggerLevelPenalty()
	{
		_profile.recordPvpDeath();
		_profile.recordPvpDeath();

		assertEquals(0, _profile.getConsecutiveMonsterDeaths(), "Morte por PvP não deve acumular mortes de monstros");
		assertEquals(0, _profile.getAdaptiveLevelOffset());
	}

	@Test
	@DisplayName("UC-Return-6: 10 monstros mortos com sucesso estabilizam o farm e zeram penalidades")
	void testSuccessfulKillsResetDeathStreak()
	{
		_profile.recordMonsterDeath(50);
		_profile.recordMonsterDeath(50);
		assertEquals(3, _profile.getAdaptiveLevelOffset());

		// Jogador mata 10 monstros no novo spot
		for (int i = 0; i < 10; i++)
		{
			_profile.recordMonsterKill();
		}

		assertEquals(0, _profile.getConsecutiveMonsterDeaths(), "O contador de mortes deve ser zerado após 10 kills");
		assertEquals(0, _profile.getAdaptiveLevelOffset(), "A penalidade de nível deve ser resetada");
	}
}
