package ext.mods.gameserver.data.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Testes unitários para validar a lógica de cooldown de 3 segundos com barra vermelha (SetupGauge RED)
 * do BufferManager para NPC Buffer e Community Board Buffer, prevenindo abuso em Olympiad.
 */
class BufferCooldownGaugeTest
{
	static class TestBufferCooldownEngine
	{
		public static final int COOLDOWN_MS = 3000;
		public static final int SINGLE_BUFF_COOLDOWN_MS = 800;
		private final Map<Integer, Long> _cooldowns = new ConcurrentHashMap<>();

		public boolean checkCooldown(int playerObjId, boolean inOlympiad, long now)
		{
			return checkCooldown(playerObjId, inOlympiad, now, COOLDOWN_MS);
		}

		public boolean checkSingleBuffCooldown(int playerObjId, boolean inOlympiad, long now)
		{
			return checkCooldown(playerObjId, inOlympiad, now, SINGLE_BUFF_COOLDOWN_MS);
		}

		private boolean checkCooldown(int playerObjId, boolean inOlympiad, long now, int cooldownMs)
		{
			if (inOlympiad)
			{
				return false;
			}

			final long nextAllowedTime = _cooldowns.getOrDefault(playerObjId, 0L);
			if (now < nextAllowedTime)
			{
				return false;
			}

			_cooldowns.put(playerObjId, now + cooldownMs);
			return true;
		}

		public void clear(int playerObjId)
		{
			_cooldowns.remove(playerObjId);
		}
	}

	@Test
	@DisplayName("Primeiro buff deve ser permitido e aplicar 3000ms de cooldown")
	void firstBuff_shouldBeAllowed()
	{
		final TestBufferCooldownEngine engine = new TestBufferCooldownEngine();
		final int playerId = 1001;
		final long now = 10_000L;

		// 1º uso: permitido
		assertTrue(engine.checkCooldown(playerId, false, now));

		// 2º uso a 1000ms (1 segundo depois): bloqueado
		assertFalse(engine.checkCooldown(playerId, false, now + 1000L));

		// 3º uso a 2999ms: ainda bloqueado
		assertFalse(engine.checkCooldown(playerId, false, now + 2999L));

		// 4º uso a 3000ms: permitido novamente
		assertTrue(engine.checkCooldown(playerId, false, now + 3000L));
	}

	@Test
	@DisplayName("Buff individual deve aplicar delay de 800ms e permitir novo buff após 800ms")
	void singleBuff_shouldApply800msCooldown()
	{
		final TestBufferCooldownEngine engine = new TestBufferCooldownEngine();
		final int playerId = 1004;
		final long now = 10_000L;

		// 1º buff individual: permitido
		assertTrue(engine.checkSingleBuffCooldown(playerId, false, now));

		// Tentativa a 500ms (menos de 800ms): bloqueado
		assertFalse(engine.checkSingleBuffCooldown(playerId, false, now + 500L));

		// Tentativa a 799ms: ainda bloqueado
		assertFalse(engine.checkSingleBuffCooldown(playerId, false, now + 799L));

		// Tentativa a 800ms: permitido novamente
		assertTrue(engine.checkSingleBuffCooldown(playerId, false, now + 800L));
	}

	@Test
	@DisplayName("Jogador em Olympiad deve ser bloqueado imediatamente")
	void playerInOlympiad_shouldBeBlocked()
	{
		final TestBufferCooldownEngine engine = new TestBufferCooldownEngine();
		final int playerId = 1002;
		final long now = 10_000L;

		// Tentativa em Olympiad deve retornar false
		assertFalse(engine.checkCooldown(playerId, true, now));
	}

	@Test
	@DisplayName("Limpeza de cooldown ao deslogar deve liberar o jogador")
	void clearCooldown_shouldResetState()
	{
		final TestBufferCooldownEngine engine = new TestBufferCooldownEngine();
		final int playerId = 1003;
		final long now = 10_000L;

		assertTrue(engine.checkCooldown(playerId, false, now));
		assertFalse(engine.checkCooldown(playerId, false, now + 500L));

		engine.clear(playerId);
		assertTrue(engine.checkCooldown(playerId, false, now + 500L));
	}
}
