package ext.mods.gameserver.network.pacing;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ext.mods.config.ConfigServer;

public class NpcSpawnPacerTest
{
	@BeforeEach
	public void setUp()
	{
		ConfigServer.ENABLE_NPC_INFO_PACING = true;
		ConfigServer.NPC_INFO_IMMEDIATE_BURST_LIMIT = 8;
		ConfigServer.NPC_INFO_PACED_BATCH_SIZE = 8;
		ConfigServer.NPC_INFO_PACING_INTERVAL_MS = 40;
		ConfigServer.ENABLE_DELETE_OBJECT_COALESCING = true;
	}

	@Test
	public void testCancelPendingCoalescing()
	{
		final NpcSpawnPacer pacer = new NpcSpawnPacer(null);

		// Sem estar pendente, cancelPending deve retornar false
		Assertions.assertFalse(pacer.cancelPending(1001));

		// Desativa coalescing e verifica comportamento
		ConfigServer.ENABLE_DELETE_OBJECT_COALESCING = false;
		Assertions.assertFalse(pacer.cancelPending(1001));
	}

	@Test
	public void testClearLifecycle()
	{
		final NpcSpawnPacer pacer = new NpcSpawnPacer(null);
		Assertions.assertDoesNotThrow(pacer::clear);
	}
}
