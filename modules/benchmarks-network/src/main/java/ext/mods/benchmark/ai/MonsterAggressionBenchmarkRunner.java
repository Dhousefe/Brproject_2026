package ext.mods.benchmark.ai;

import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collection;

/**
 * Runner executavel para disparar o microbenchmark JMH de agressividade e filtros forEach de monstros.
 */
public final class MonsterAggressionBenchmarkRunner
{
    public static void main(String[] args) throws RunnerException
    {
        System.out.println("====================================================================");
        System.out.println("🚀 BrProject-2026 - Monster Aggression & forEach Loop JMH Benchmark");
        System.out.println("Avaliacao: Perception Scan, AABB Fast Reject, AggroList & Proximity");
        System.out.println("====================================================================");

        final Options opt = new OptionsBuilder()
            .include(MonsterAggressionBenchmark.class.getSimpleName())
            .warmupIterations(1)
            .measurementIterations(2)
            .warmupTime(org.openjdk.jmh.runner.options.TimeValue.seconds(1))
            .measurementTime(org.openjdk.jmh.runner.options.TimeValue.seconds(2))
            .threads(4)
            .forks(1)
            .build();

        final Collection<RunResult> results = new Runner(opt).run();

        System.out.println("\n====================================================================");
        System.out.println("📊 RELATORIO COMPARATIVO: FILTROS DE AGRESSIVIDADE DE MONSTROS");
        System.out.println("====================================================================");

        for (RunResult result : results)
        {
            final String benchmarkName = result.getParams().getBenchmark();
            final double score = result.getPrimaryResult().getScore();
            final String unit = result.getPrimaryResult().getScoreUnit();
            final double error = result.getPrimaryResult().getScoreError();

            final String shortName = benchmarkName.substring(benchmarkName.lastIndexOf('.') + 1);

            System.out.printf("🔹 %-48s : %,14.2f ± %,10.2f %s%n",
                shortName, score, error, unit);
        }

        System.out.println("====================================================================");
    }
}
