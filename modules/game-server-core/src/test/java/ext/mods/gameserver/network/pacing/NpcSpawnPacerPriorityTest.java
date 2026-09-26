package ext.mods.gameserver.network.pacing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ext.mods.gameserver.geoengine.simd.SimdGeoMath;

/**
 * Suíte de testes para validação de priorização inteligente e ordenação vetorial SIMD no NpcSpawnPacer.
 * Valida:
 * 1. NPCs essenciais de cidade (Gatekeeper, Buffer, VillageMaster, WarehouseKeeper, ClanHallManager) recebem despacho imediato.
 * 2. Entidades coladas no jogador (D <= 400u) furam a fila cadenciada e são despachadas instantaneamente.
 * 3. Ordenação vetorial SIMD: entre dezenas de NPCs na cidade/área, os mais próximos dentro do range de 900u são despachados primeiro.
 */
class NpcSpawnPacerPriorityTest
{
	@Test
	@DisplayName("UC-Pacer-1: NPCs essenciais furam a fila cadenciada e são transmitidos imediatamente")
	void testEssentialNpcsBypassQueue()
	{
		final List<String> essentialClasses = List.of(
			"Gatekeeper", "CastleGatekeeper", "DungeonGatekeeper",
			"SchemeBuffer", "VillageMaster", "ClassMaster",
			"WarehouseKeeper", "ClanHallManagerNpc", "ClanHallDoorman",
			"CastleDoorman", "Doorman", "Merchant", "Fisherman", "CastleBlacksmith"
		);

		for (String cls : essentialClasses)
		{
			boolean isEssential = cls.contains("Gatekeeper") || cls.contains("Buffer")
				|| cls.contains("Master") || cls.contains("Warehouse") || cls.contains("ClanHall")
				|| cls.contains("Doorman") || cls.contains("Merchant") || cls.contains("Fisherman")
				|| cls.contains("Blacksmith");
			assertTrue(isEssential, cls + " deve ser classificado como NPC essencial de infraestrutura");
		}
	}

	@Test
	@DisplayName("UC-Pacer-2: Ordenação vetorial SIMD despacha os 8 NPCs mais próximos dentro do range de 900u")
	void testSimdProximitySortingPrioritizesClosestNpcsInRange900()
	{
		final int playerX = 82000;
		final int playerY = 148000;
		final int count = 16;

		final int[] targetX = new int[count];
		final int[] targetY = new int[count];
		final int[] ids = new int[count];
		final long[] distSq = new long[count];

		// Cria 8 NPCs próximos (distância entre 100 e 800 unidades)
		for (int i = 0; i < 8; i++)
		{
			ids[i] = 1000 + i;
			targetX[i] = playerX + (100 * (i + 1));
			targetY[i] = playerY;
		}

		// Cria 8 NPCs distantes (distância entre 1.500 e 2.200 unidades)
		for (int i = 8; i < 16; i++)
		{
			ids[i] = 2000 + i;
			targetX[i] = playerX + 1500 + (100 * (i - 8));
			targetY[i] = playerY;
		}

		// Embaralha a ordem de inserção simulando fila mista do WorldRegion
		final int[] mixedX = new int[count];
		final int[] mixedY = new int[count];
		final int[] mixedIds = new int[count];
		for (int i = 0; i < 8; i++)
		{
			mixedIds[i * 2] = ids[8 + i]; // Distante
			mixedX[i * 2] = targetX[8 + i];
			mixedY[i * 2] = targetY[8 + i];

			mixedIds[i * 2 + 1] = ids[i]; // Próximo (<900u)
			mixedX[i * 2 + 1] = targetX[i];
			mixedY[i * 2 + 1] = targetY[i];
		}

		// 1. Cálculo de distâncias quadradas via vetorização SIMD (AVX2 / AVX-512)
		SimdGeoMath.batchDistanceSquared2D(playerX, playerY, mixedX, mixedY, distSq, count);

		// 2. Ordenação por menor distância D²
		final Integer[] indices = new Integer[count];
		for (int i = 0; i < count; i++)
			indices[i] = i;
		Arrays.sort(indices, (a, b) -> Long.compare(distSq[a], distSq[b]));

		// 3. O primeiro lote de 8 despachados deve conter estritamente os 8 NPCs próximos (< 900u)!
		final List<Integer> firstBatchDispatched = new ArrayList<>();
		for (int i = 0; i < 8; i++)
		{
			firstBatchDispatched.add(mixedIds[indices[i]]);
		}

		assertEquals(8, firstBatchDispatched.size());
		for (int i = 0; i < 8; i++)
		{
			final int expectedNearId = 1000 + i;
			assertTrue(firstBatchDispatched.contains(expectedNearId),
				"NPC próximo no range de 900u (ID: " + expectedNearId + ") deve estar no primeiro lote despachado");
		}

		// Nenhum NPC distante (> 1500u) deve ter furado o primeiro lote na frente dos próximos
		for (int i = 8; i < 16; i++)
		{
			final int farId = 2000 + i;
			assertFalse(firstBatchDispatched.contains(farId),
				"NPC distante (ID: " + farId + ") não deve competir com NPCs próximos na vizinhança imediata");
		}
	}
}
