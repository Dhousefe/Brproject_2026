package ext.mods.benchmark.quest;

import ext.mods.questrecommender.vector.QuestVectorEngine;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Microbenchmark JMH comparando a abordagem tradicional de Streams/Objetos Heap
 * versus o motor vetorial otimizado com Mechanical Sympathy e Fastutil (contiguidade de memória e SIMD).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class QuestRecommenderBenchmark
{
    private static final int QUEST_COUNT = 150;
    private static final int TOP_K = 4;

    // Estado Otimizado: Mechanical Sympathy + Fastutil
    private QuestVectorEngine _vectorEngine;
    private float[] _playerVector;
    private float[] _scoresBuffer;
    private boolean[] _validMask;
    private IntList _outQuestIds;

    // Estado Legado: Objetos no Heap + Streams
    private static class LegacyQuestProfile
    {
        int questId;
        float[] vector;
        boolean valid;

        LegacyQuestProfile(int id, float[] v, boolean val)
        {
            this.questId = id;
            this.vector = v;
            this.valid = val;
        }

        double computeSimilarity(float[] pVec)
        {
            double dot = 0.0;
            for (int i = 0; i < pVec.length; i++)
            {
                dot += vector[i] * pVec[i];
            }
            return dot;
        }
    }

    private List<LegacyQuestProfile> _legacyProfiles;

    @Setup(Level.Trial)
    public void setup()
    {
        final int[] ids = new int[QUEST_COUNT];
        final float[] flatVectors = new float[QUEST_COUNT * QuestVectorEngine.DIMENSIONS];
        _legacyProfiles = new ArrayList<>(QUEST_COUNT);

        for (int i = 0; i < QUEST_COUNT; i++)
        {
            ids[i] = i + 1;
            final float[] v = new float[QuestVectorEngine.DIMENSIONS];
            final int offset = i * QuestVectorEngine.DIMENSIONS;

            for (int d = 0; d < QuestVectorEngine.DIMENSIONS; d++)
            {
                float val = (float) ((i + d) % 10) / 10.0f;
                flatVectors[offset + d] = val;
                v[d] = val;
            }

            _legacyProfiles.add(new LegacyQuestProfile(ids[i], v, i % 3 != 0));
        }

        _vectorEngine = new QuestVectorEngine(ids, flatVectors);

        _playerVector = new float[]{0.35f, 0.9f, 0.2f, 0.8f, 0.9f, 0.7f, 0.5f, 0.3f};
        _scoresBuffer = new float[QUEST_COUNT];
        _validMask = new boolean[QUEST_COUNT];
        for (int i = 0; i < QUEST_COUNT; i++)
        {
            _validMask[i] = (i % 3 != 0);
        }
        _outQuestIds = new IntArrayList(TOP_K);
    }

    /**
     * Candidato Otimizado: Mechanical Sympathy, Zero Alocação de Heap, Fastutil IntList e array plano de float.
     */
    @Benchmark
    public int benchmarkOptimizedMechanicalSympathy()
    {
        _vectorEngine.computeScores(_playerVector, _scoresBuffer);
        _vectorEngine.getTopK(_scoresBuffer, _validMask, TOP_K, _outQuestIds);
        return _outQuestIds.size();
    }

    /**
     * Baseline Legado: Criação de Streams, boxing de Double/Integer, filtros lambda e collectors.
     */
    @Benchmark
    public int benchmarkLegacyStreamSearch()
    {
        final List<Integer> topIds = _legacyProfiles.stream()
            .filter(q -> q.valid)
            .sorted((a, b) -> Double.compare(b.computeSimilarity(_playerVector), a.computeSimilarity(_playerVector)))
            .limit(TOP_K)
            .map(q -> q.questId)
            .toList();

        return topIds.size();
    }
}
