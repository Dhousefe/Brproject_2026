package ext.mods.benchmark.combat;

import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collection;

/**
 * Runner executavel para disparar o microbenchmark JMH de tempo de reacao,
 * recuperacao de ataque e balanceamento por DEX.
 */
public final class PlayerReactionBenchmarkRunner
{
    public static void main(String[] args) throws RunnerException
    {
        System.out.println("====================================================================");
        System.out.println("🚀 BrProject-2026 - Player Movement Reaction & DEX Balance Benchmark");
        System.out.println("Avaliacao: Formula DEX Throughput, Attack Lock Window & Target Switching");
        System.out.println("====================================================================");

        final Options opt = new OptionsBuilder()
            .include(PlayerReactionBenchmark.class.getSimpleName())
            .warmupIterations(1)
            .measurementIterations(2)
            .warmupTime(org.openjdk.jmh.runner.options.TimeValue.seconds(1))
            .measurementTime(org.openjdk.jmh.runner.options.TimeValue.seconds(2))
            .threads(4)
            .forks(1)
            .build();

        final Collection<RunResult> results = new Runner(opt).run();

        System.out.println("\n====================================================================");
        System.out.println("📊 RELATORIO COMPARATIVO: REACAO DE COMBATE E BALANCEAMENTO POR DEX");
        System.out.println("====================================================================");

        for (RunResult result : results)
        {
            final String benchmarkName = result.getParams().getBenchmark();
            final double score = result.getPrimaryResult().getScore();
            final String unit = result.getPrimaryResult().getScoreUnit();
            final double error = result.getPrimaryResult().getScoreError();

            final String shortName = benchmarkName.substring(benchmarkName.lastIndexOf('.') + 1);

            System.out.printf("🔹 %-44s : %,14.2f ± %,10.2f %s%n",
                shortName, score, error, unit);
        }

        System.out.println("====================================================================");
    }
}
