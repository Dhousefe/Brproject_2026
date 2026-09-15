package ext.mods.benchmark.quest;

import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collection;

/**
 * Runner do microbenchmark JMH para o motor de recomendação vetorial de quests.
 */
public final class QuestRecommenderBenchmarkRunner
{
    public static void main(String[] args) throws RunnerException
    {
        System.out.println("====================================================================");
        System.out.println("🚀 BrProject-2026 - Quest Recommender Vector & Mechanical Sympathy Benchmark");
        System.out.println("Comparacao: Streams/Heap Objects (Baseline) vs Fastutil/Flat SIMD Floats (Candidato)");
        System.out.println("====================================================================");

        final Options opt = new OptionsBuilder()
            .include(QuestRecommenderBenchmark.class.getSimpleName())
            .warmupIterations(1)
            .measurementIterations(2)
            .warmupTime(org.openjdk.jmh.runner.options.TimeValue.seconds(1))
            .measurementTime(org.openjdk.jmh.runner.options.TimeValue.seconds(2))
            .threads(4)
            .forks(1)
            .build();

        final Collection<RunResult> results = new Runner(opt).run();

        System.out.println("\n====================================================================");
        System.out.println("📊 RELATORIO COMPARATIVO DE DESEMPENHO (JMH THROUGHPUT)");
        System.out.println("====================================================================");

        double legacyScore = 0.0;
        double optScore = 0.0;

        for (RunResult result : results)
        {
            final String benchmarkName = result.getParams().getBenchmark();
            final double score = result.getPrimaryResult().getScore();
            final String unit = result.getPrimaryResult().getScoreUnit();
            final double error = result.getPrimaryResult().getScoreError();

            final String shortName = benchmarkName.substring(benchmarkName.lastIndexOf('.') + 1);

            System.out.printf("🔹 %-44s : %,14.2f ± %,10.2f %s%n",
                shortName, score, error, unit);

            if (shortName.contains("Legacy"))
            {
                legacyScore = score;
            }
            else if (shortName.contains("Optimized"))
            {
                optScore = score;
            }
        }

        if (legacyScore > 0 && optScore > 0)
        {
            final double speedup = optScore / legacyScore;
            System.out.println("--------------------------------------------------------------------");
            System.out.printf("⚡ SPEEDUP OBTIDO COM MECHANICAL SYMPATHY: %.2fx mais rapido!%n", speedup);
            System.out.println("--------------------------------------------------------------------");
        }

        System.out.println("====================================================================");
    }
}
