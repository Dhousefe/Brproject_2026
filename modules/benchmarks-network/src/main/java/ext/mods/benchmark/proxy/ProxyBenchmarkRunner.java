package ext.mods.benchmark.proxy;

import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collection;

/**
 * Runner executavel para disparar o microbenchmark JMH do Proxy Nativo Netty e Fail2Ban.
 */
public final class ProxyBenchmarkRunner {

    public static void main(String[] args) throws RunnerException {
        System.out.println("====================================================================");
        System.out.println("🚀 BrProject-2026 - Netty Proxy & Fail2Ban JMH Microbenchmark");
        System.out.println("Avaliacao: FixedWindowRateLimiter, ProxyBanCache O(1), PreFlight Filter");
        System.out.println("====================================================================");

        final Options opt = new OptionsBuilder()
            .include(ProxySecurityBenchmark.class.getSimpleName())
            .warmupIterations(1)
            .measurementIterations(2)
            .warmupTime(org.openjdk.jmh.runner.options.TimeValue.seconds(1))
            .measurementTime(org.openjdk.jmh.runner.options.TimeValue.seconds(2))
            .threads(4)
            .forks(1)
            .build();

        final Collection<RunResult> results = new Runner(opt).run();

        System.out.println("\n====================================================================");
        System.out.println("📊 RELATORIO COMPARATIVO DE PERFORMANCE: PROXY NETTY & FAIL2BAN");
        System.out.println("====================================================================");

        for (RunResult result : results) {
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
