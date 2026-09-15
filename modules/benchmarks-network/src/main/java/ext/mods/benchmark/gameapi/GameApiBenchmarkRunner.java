package ext.mods.benchmark.gameapi;

import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collection;

/**
 * Runner executavel para disparar o microbenchmark JMH da Game API.
 */
public final class GameApiBenchmarkRunner
{
    public static void main(String[] args) throws RunnerException
    {
        System.out.println("====================================================================");
        System.out.println("🚀 BrProject-2026 - Game API JMH Microbenchmark");
        System.out.println("Avaliacao: HMAC Verification, NonceCache, RateLimiter, ClanChatRing");
        System.out.println("====================================================================");

        final Options opt = new OptionsBuilder()
            .include(GameApiBenchmark.class.getSimpleName())
            .warmupIterations(1)
            .measurementIterations(2)
            .warmupTime(org.openjdk.jmh.runner.options.TimeValue.seconds(1))
            .measurementTime(org.openjdk.jmh.runner.options.TimeValue.seconds(2))
            .threads(4)
            .forks(1)
            .build();

        final Collection<RunResult> results = new Runner(opt).run();

        System.out.println("\n====================================================================");
        System.out.println("📊 RELATORIO COMPARATIVO DE PERFORMANCE DA GAME API (OPS/SEC)");
        System.out.println("====================================================================");

        for (RunResult result : results)
        {
            final String benchmarkName = result.getParams().getBenchmark();
            final double score = result.getPrimaryResult().getScore();
            final String unit = result.getPrimaryResult().getScoreUnit();
            final double error = result.getPrimaryResult().getScoreError();

            final String shortName = benchmarkName.substring(benchmarkName.lastIndexOf('.') + 1);

            System.out.printf("🔹 %-38s : %,14.2f ± %,10.2f %s%n",
                shortName, score, error, unit);
        }

        System.out.println("====================================================================");
    }
}
