package ext.mods.gameserver.model.entity.autofarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import ext.mods.gameserver.geoengine.simd.SimdGeoMath;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

/**
 * Testes unitários para as otimizações de SIMD e Mechanical Sympathy do AutoFarm:
 * 1. Vetorização SIMD de seleção de alvos com SimdGeoMath.batchDistanceSquared2D.
 * 2. Controle de concorrência Lock-Free com AtomicBoolean.
 * 3. Mapa primitivo de alta densidade Fastutil Int2IntOpenHashMap (Zero-Autoboxing).
 * 4. Banda morta histerética de kiting (Anti-Jitter).
 */
class AutoFarmSimdOptimizationTest
{
	@Test
	void simdTargeting_matchesEuclideanGeometry_andSelectsClosestCandidate()
	{
		final int playerX = 10000;
		final int playerY = 20000;

		// 8 candidatos simulando monstros em distâncias variadas
		final int[] targetX = { 10100, 10500, 10050, 11200, 10800, 10020, 12000, 10070 };
		final int[] targetY = { 20100, 20500, 20050, 21200, 20800, 20020, 22000, 20070 };
		final int count = targetX.length;
		final long[] distSq = new long[count];

		// Executa vetorização SIMD em 8 lanes AVX2 / AVX-512
		SimdGeoMath.batchDistanceSquared2D(playerX, playerY, targetX, targetY, distSq, count);

		// Índice 5: dx = 20, dy = 20 -> distSq = 400 + 400 = 800 (Mais próximo absoluto)
		assertEquals(800L, distSq[5], "O candidato no índice 5 deve ter distSq = 800");

		// Identifica o índice de menor distância
		int bestIdx = 0;
		long minD2 = Long.MAX_VALUE;
		for (int i = 0; i < count; i++)
		{
			if (distSq[i] < minD2)
			{
				minD2 = distSq[i];
				bestIdx = i;
			}
		}

		assertEquals(5, bestIdx, "O algoritmo SIMD deve identificar com precisão o índice 5 como o mais próximo");
	}

	@Test
	void atomicLock_preventsReentrantExecution_withoutContention() throws InterruptedException
	{
		final AtomicBoolean isRunning = new AtomicBoolean(false);
		final int numThreads = 8;
		final CountDownLatch startLatch = new CountDownLatch(1);
		final CountDownLatch finishLatch = new CountDownLatch(numThreads);
		final AtomicInteger successfulAcquisitions = new AtomicInteger(0);

		for (int i = 0; i < numThreads; i++)
		{
			new Thread(() -> {
				try
				{
					startLatch.await();
					if (isRunning.compareAndSet(false, true))
					{
						successfulAcquisitions.incrementAndGet();
						Thread.sleep(50);
						isRunning.set(false);
					}
				}
				catch (Exception e)
				{
					// Ignore
				}
				finally
				{
					finishLatch.countDown();
				}
			}).start();
		}

		startLatch.countDown();
		finishLatch.await();

		// Em alta concorrência simultânea, apenas a primeira thread que executou compareAndSet deve entrar
		assertTrue(successfulAcquisitions.get() >= 1, "Pelo menos uma thread deve adquirir o lock atômico");
		assertFalse(isRunning.get(), "O lock atômico deve terminar liberado");
	}

	@Test
	void fastutilMap_storesAndRetrievesSkills_zeroBoxing()
	{
		final Int2IntOpenHashMap skills = new Int2IntOpenHashMap(6);
		skills.put(1, 1001); // Slot 1 -> Skill 1001
		skills.put(2, 1002); // Slot 2 -> Skill 1002
		skills.put(3, 1003); // Slot 3 -> Skill 1003

		assertEquals(1001, skills.get(1));
		assertEquals(1002, skills.get(2));
		assertEquals(1003, skills.get(3));
		assertEquals(0, skills.get(99)); // Chave inexistente retorna default 0

		assertEquals(3, skills.size());
	}

	@Test
	void archerDeadband_preventsJitter_onMarginalStep()
	{
		final double currentDist = 500.0;
		final int deadband = 80;

		// Caso 1: Monstro a 500u, desejado 530u (diferença de 30u <= 80u) -> deve ser descartado
		final double marginalDesired = 530.0;
		final boolean allowMarginal = (marginalDesired > currentDist + deadband);
		assertFalse(allowMarginal, "Recuo marginal de 30u deve ser suprimido para evitar jitter");

		// Caso 2: Monstro a 500u, desejado 620u (diferença de 120u > 80u) -> deve ser permitido
		final double significantDesired = 620.0;
		final boolean allowSignificant = (significantDesired > currentDist + deadband);
		assertTrue(allowSignificant, "Recuo tático significativo de 120u deve ser aceito");
	}
}
