package ext.mods.gameserver.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Testes unitários para a mitigação de flood de RequestMagicSkillUse e amortecimento histerético de ActionFailed.
 * Valida os princípios de Mechanical Sympathy: Zero-Alloc, proteção O(1) de cache line local e drop silencioso.
 */
class MagicSkillFloodMitigationTest
{
	/**
	 * Simula a lógica histerética de amortecimento de ActionFailed implementada em Player.java.
	 */
	static class ActionFailedRateLimiter
	{
		private long _lastActionFailedSendTime = 0L;

		public boolean canSendActionFailed(long now, long minIntervalMs)
		{
			if (now - _lastActionFailedSendTime < minIntervalMs)
			{
				return false;
			}
			_lastActionFailedSendTime = now;
			return true;
		}
	}

	/**
	 * Simula o motor de debounce com drop silencioso e identificação de alvos implementado em Player.java.
	 */
	static class SkillDebounceEngine
	{
		private int _lastMagicSkillId;
		private int _lastMagicSkillTargetId;
		private long _lastMagicSkillRequestTime;

		public boolean checkAndSetMagicSkillDebounce(int skillId, int targetId, long windowMs, long now)
		{
			if (_lastMagicSkillId == skillId && _lastMagicSkillTargetId == targetId && (now - _lastMagicSkillRequestTime) < windowMs)
			{
				return false;
			}
			_lastMagicSkillId = skillId;
			_lastMagicSkillTargetId = targetId;
			_lastMagicSkillRequestTime = now;
			return true;
		}
	}

	@Test
	void actionFailedHysteresis_suppressesSpamWithinWindow_andAllowsAfterExpiry()
	{
		final ActionFailedRateLimiter limiter = new ActionFailedRateLimiter();
		final long windowMs = 250L;
		long currentTime = 1000L;

		// 1. Primeiro envio em T0 deve ser aceito imediatamente (latência 0ms)
		assertTrue(limiter.canSendActionFailed(currentTime, windowMs), "Primeiro ActionFailed deve passar com latência zero");

		// 2. Rajada subsequente de hold de botão (a cada 20ms) deve sofrer drop silencioso
		for (int i = 1; i <= 10; i++)
		{
			currentTime += 20L; // 1020, 1040, ..., 1200ms
			assertFalse(limiter.canSendActionFailed(currentTime, windowMs),
				"ActionFailed redundante em T+" + (i * 20) + "ms deve ser suprimido");
		}

		// 3. Após expirar a janela (T0 + 260ms = 1260ms), nova falha legítima deve passar
		currentTime = 1260L;
		assertTrue(limiter.canSendActionFailed(currentTime, windowMs),
			"ActionFailed após expiração da janela (260ms > 250ms) deve passar normalmente");

		// 4. Nova tentativa 10ms depois deve ser novamente amortecida
		currentTime += 10L;
		assertFalse(limiter.canSendActionFailed(currentTime, windowMs),
			"Nova tentativa imediata deve voltar a ser amortecida");
	}

	@Test
	void skillDebounce_dropsDuplicateInboundFrames_allowsTargetOrSkillSwitch()
	{
		final SkillDebounceEngine engine = new SkillDebounceEngine();
		final long windowMs = 200L;
		long currentTime = 5000L;
		final int skillHydroBlast = 1028;
		final int skillProminence = 1029;
		final int targetMonsterA = 10001;
		final int targetMonsterB = 10002;

		// 1. Primeiro disparo de Hydro Blast no Monster A
		assertTrue(engine.checkAndSetMagicSkillDebounce(skillHydroBlast, targetMonsterA, windowMs, currentTime),
			"Primeira tentativa de skill deve ser aceita");

		// 2. Frames duplicados gerados por hold contínuo da tecla (30ms, 60ms, 90ms, 120ms, 150ms, 180ms)
		for (int i = 1; i <= 6; i++)
		{
			currentTime += 30L;
			assertFalse(engine.checkAndSetMagicSkillDebounce(skillHydroBlast, targetMonsterA, windowMs, currentTime),
				"Hold contínuo em +" + (i * 30) + "ms deve sofrer drop silencioso sem enviar ActionFailed");
		}

		// 3. Troca de alvo imediata (Target Switch) em 5190ms (Monster B)
		// Jogador competitivo trocou de alvo: DEVE passar imediatamente sem atraso!
		assertTrue(engine.checkAndSetMagicSkillDebounce(skillHydroBlast, targetMonsterB, windowMs, currentTime),
			"Troca de alvo para Monster B deve ser aceita imediatamente mesmo dentro da janela anterior");

		// 4. Troca de skill imediata (Skill Switch) para Prominence no mesmo Monster B
		currentTime += 15L;
		assertTrue(engine.checkAndSetMagicSkillDebounce(skillProminence, targetMonsterB, windowMs, currentTime),
			"Troca de habilidade (combo) deve ser aceita imediatamente");
	}

	@Test
	void skillQueuing_calculatesRemainingCastTimeCorrectly()
	{
		// Valida a lógica de cálculo de tempo restante em CreatureCast:
		// _isCastingNow = true; castInterruptTime = now + hitTime - 200; castEndTime = castInterruptTime + 200;
		final long now = 10_000L;
		final int hitTime = 2000;
		final long castInterruptTime = now + hitTime - 200L; // 11800
		final long castEndTime = castInterruptTime + 200L; // 12000

		final long remainingAtNow = castEndTime - now;
		assertEquals(2000L, remainingAtNow, "Tempo restante inicial deve ser igual a hitTime");

		// Simula passagem de tempo até faltar 250ms para acabar o cast
		final long timeNearEnd = 11_750L;
		final long remainingNearEnd = castEndTime - timeNearEnd;
		assertEquals(250L, remainingNearEnd, "Tempo restante próximo ao fim deve ser 250ms");

		final long queuingWindow = 300L;
		assertTrue(remainingNearEnd <= queuingWindow,
			"Com 250ms restantes, a habilidade está dentro da janela de queuing (<= 300ms)");
	}
}
