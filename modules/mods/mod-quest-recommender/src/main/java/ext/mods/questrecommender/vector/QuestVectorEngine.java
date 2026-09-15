package ext.mods.questrecommender.vector;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

/**
 * Motor de busca e similaridade vetorial projetado com Mechanical Sympathy.
 *
 * Características de Hardware & JVM:
 * 1. Vetores armazenados em um único array plano de float contíguo (evita perseguição de ponteiros e cache-miss).
 * 2. Loop de dot product desenhado de forma simples para acionar auto-vetorização SIMD (AVX-2 / AVX-512) no C2 Compiler.
 * 3. Zero GC allocation durante as consultas de ranking: buffers reutilizáveis e IntArrayList de Fastutil.
 */
public final class QuestVectorEngine
{
	public static final int DIMENSIONS = 8;
	// Dimensões do vetor:
	// 0: Level Norm (0.0 a 1.0)
	// 1: Fighter Affinity (0.0 a 1.0)
	// 2: Mage Affinity (0.0 a 1.0)
	// 3: Adena Reward Weight (0.0 a 1.0)
	// 4: Exp Reward Weight (0.0 a 1.0)
	// 5: Item/Equipment Reward Weight (0.0 a 1.0)
	// 6: Repeatable Flag (0.0 ou 1.0)
	// 7: Party/Raid Flag (0.0 ou 1.0)

	private final int[] _questIds;
	private final float[] _questVectors; // Matriz achatada: questIndex * DIMENSIONS
	private final int _totalQuests;

	public QuestVectorEngine(int[] questIds, float[] questVectors)
	{
		_questIds = questIds;
		_questVectors = questVectors;
		_totalQuests = questIds.length;
	}

	/**
	 * Retorna o número total de quests indexadas.
	 */
	public int getTotalQuests()
	{
		return _totalQuests;
	}

	/**
	 * Executa o cálculo de similaridade e pontuação vetorial para o perfil do jogador.
	 *
	 * @param playerVector vetor de 8 dimensões normalizado do jogador.
	 * @param eligibleQuests lista de índices ou IDs elegíveis (excluindo quests não repetíveis completas).
	 * @param outScores array primitivo pré-alocado para receber as pontuações (tamanho >= totalQuests).
	 */
	public void computeScores(float[] playerVector, float[] outScores)
	{
		final float p0 = playerVector[0];
		final float p1 = playerVector[1];
		final float p2 = playerVector[2];
		final float p3 = playerVector[3];
		final float p4 = playerVector[4];
		final float p5 = playerVector[5];
		final float p6 = playerVector[6];
		final float p7 = playerVector[7];

		final float[] vectors = _questVectors;
		final int total = _totalQuests;

		// Loop plano com contiguidade de cache L1 (1 linha de 64 bytes armazena 16 floats ou 2 quests completas)
		for (int i = 0; i < total; i++)
		{
			final int offset = i * DIMENSIONS;
			final float dot = (p0 * vectors[offset])
				+ (p1 * vectors[offset + 1])
				+ (p2 * vectors[offset + 2])
				+ (p3 * vectors[offset + 3])
				+ (p4 * vectors[offset + 4])
				+ (p5 * vectors[offset + 5])
				+ (p6 * vectors[offset + 6])
				+ (p7 * vectors[offset + 7]);

			outScores[i] = dot;
		}
	}

	/**
	 * Obtém os Top-K quest IDs ordenados por score decrescente.
	 * Zero heap allocation se buffers forem providos.
	 */
	public void getTopK(float[] scores, boolean[] validMask, int k, IntList outQuestIds)
	{
		outQuestIds.clear();
		final int total = _totalQuests;

		for (int step = 0; step < k; step++)
		{
			float maxScore = -Float.MAX_VALUE;
			int bestIdx = -1;

			for (int i = 0; i < total; i++)
			{
				if (validMask != null && !validMask[i])
					continue;

				final float s = scores[i];
				if (s > maxScore)
				{
					maxScore = s;
					bestIdx = i;
				}
			}

			if (bestIdx >= 0)
			{
				outQuestIds.add(_questIds[bestIdx]);
				// Marca como consumido temporariamente
				scores[bestIdx] = -Float.MAX_VALUE;
			}
			else
			{
				break;
			}
		}
	}

	public int getQuestId(int index)
	{
		return _questIds[index];
	}
}
