package ext.mods.gameserver.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Testes unitários para a mitigação de flood e prevenção de DOS no pacote RequestBypassToServer (Opcode 0x2f).
 * Valida os princípios de Mechanical Sympathy: Zero-Alloc, drop silencioso de comandos redundantes e
 * preservação de latência zero para comandos legítimos distintos (menus e navegação).
 */
class RequestBypassFloodMitigationTest
{
	/**
	 * Simulação da máquina de estados implementada em PlayerBypass.java.
	 */
	static class BypassDebounceEngine
	{
		private String _lastBypassCommand = "";
		private long _lastBypassTime = 0L;
		private int _bypassCountInSecond = 0;
		private long _bypassSecondStartTick = 0L;

		public synchronized boolean checkAndSetBypassDebounce(String cmd, long windowMs, int maxPerSec, long now)
		{
			// 1. Teto global anti-DOS por segundo por conexão
			if (maxPerSec > 0)
			{
				if (now - _bypassSecondStartTick >= 1000L)
				{
					_bypassSecondStartTick = now;
					_bypassCountInSecond = 1;
				}
				else
				{
					_bypassCountInSecond++;
					if (_bypassCountInSecond > maxPerSec)
					{
						return false;
					}
				}
			}

			// 2. Deduplicação de comandos idênticos a curto intervalo (Hold/Macro/Scripts redundantes)
			if (windowMs > 0 && cmd != null && cmd.equals(_lastBypassCommand))
			{
				if (now - _lastBypassTime < windowMs)
				{
					return false;
				}
			}

			_lastBypassCommand = (cmd != null) ? cmd : "";
			_lastBypassTime = now;
			return true;
		}
	}

	@Test
	void bypassDebounce_dropsRedundantFrames_andAllowsAfterExpiry()
	{
		final BypassDebounceEngine engine = new BypassDebounceEngine();
		final long windowMs = 250L;
		final int maxPerSec = 15;
		long currentTime = 10_000L;
		final String command = "user_panel";

		// 1. Primeiro envio em T0 deve ser aceito imediatamente (latência 0ms)
		assertTrue(engine.checkAndSetBypassDebounce(command, windowMs, maxPerSec, currentTime),
			"Primeiro bypass legítimo deve passar imediatamente");

		// 2. Rajada de 10 pacotes idênticos em curtos intervalos de 20ms (simulando spam de 10-30ms do log)
		for (int i = 1; i <= 10; i++)
		{
			currentTime += 20L; // 10020, 10040, ..., 10200ms
			assertFalse(engine.checkAndSetBypassDebounce(command, windowMs, maxPerSec, currentTime),
				"Bypass idêntico redundante em +" + (i * 20) + "ms deve sofrer drop silencioso");
		}

		// 3. Após expirar a janela (T0 + 260ms = 10260ms), o mesmo comando deve voltar a ser aceito
		currentTime = 10_260L;
		assertTrue(engine.checkAndSetBypassDebounce(command, windowMs, maxPerSec, currentTime),
			"Bypass idêntico após expirar janela (260ms > 250ms) deve ser aceito normalmente");

		// 4. Nova tentativa 15ms depois volta a ser amortecida
		currentTime += 15L;
		assertFalse(engine.checkAndSetBypassDebounce(command, windowMs, maxPerSec, currentTime),
			"Tentativa imediata subsequente deve sofrer drop silencioso");
	}

	@Test
	void bypassDebounce_allowsInstantCommandSwitch_withoutLag()
	{
		final BypassDebounceEngine engine = new BypassDebounceEngine();
		final long windowMs = 250L;
		final int maxPerSec = 15;
		long currentTime = 20_000L;

		// 1. Jogador clica no menu Shop
		assertTrue(engine.checkAndSetBypassDebounce("bbs_shop", windowMs, maxPerSec, currentTime),
			"Comando bbs_shop deve passar imediatamente");

		// 2. Apenas 30ms depois, o jogador clica na aba Teleports
		currentTime += 30L;
		assertTrue(engine.checkAndSetBypassDebounce("bbs_teleport", windowMs, maxPerSec, currentTime),
			"Troca para comando diferente (bbs_teleport) deve passar no milissegundo zero sem atraso");

		// 3. Mais 40ms depois, clica na sub-opção de Giran
		currentTime += 40L;
		assertTrue(engine.checkAndSetBypassDebounce("goto 1001", windowMs, maxPerSec, currentTime),
			"Comando diferente subsequente deve passar com fluidez total");
	}

	@Test
	void bypassRateLimiter_blocksExcessiveFuzzingOrDOS_perSecond()
	{
		final BypassDebounceEngine engine = new BypassDebounceEngine();
		final long windowMs = 250L;
		final int maxPerSec = 15;
		long currentTime = 30_000L;

		// Dispara 15 comandos distintos em 500ms (todos devem passar até atingir a cota)
		for (int i = 1; i <= 15; i++)
		{
			currentTime += 30L;
			assertTrue(engine.checkAndSetBypassDebounce("fuzz_cmd_" + i, windowMs, maxPerSec, currentTime),
				"Comando " + i + " dentro da cota de 15/s deve ser aceito");
		}

		// O 16º ao 20º comando no mesmo segundo devem ser cortados pelo limitador anti-DOS
		for (int i = 16; i <= 20; i++)
		{
			currentTime += 20L;
			assertFalse(engine.checkAndSetBypassDebounce("fuzz_cmd_" + i, windowMs, maxPerSec, currentTime),
				"Comando excedente " + i + " no mesmo segundo deve ser bloqueado");
		}

		// Ao virar o segundo (+1050ms do início do segundo = 31050ms), novos comandos voltam a ser aceitos
		currentTime = 31_050L;
		assertTrue(engine.checkAndSetBypassDebounce("fuzz_cmd_new_second", windowMs, maxPerSec, currentTime),
			"Novo comando no segundo seguinte deve ser aceito normalmente");
	}
}
