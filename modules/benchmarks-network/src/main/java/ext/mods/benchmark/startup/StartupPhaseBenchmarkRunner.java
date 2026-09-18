package ext.mods.benchmark.startup;

import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collection;

/**
 * Runner executavel para o microbenchmark de velocidade de startup.
 */
public class StartupPhaseBenchmarkRunner
{
    public static void main(String[] args) throws RunnerException
    {
        System.out.println("====================================================================");
        System.out.println("🚀 BrProject-2026 - Server Startup & Critical Phases JMH Benchmark");
        System.out.println("Avaliacao: Config Parsing, XML Data Loading, SPI Discovery, Latches");
        System.out.println("====================================================================");

        final Options opt = new OptionsBuilder()
                .include(StartupPhaseBenchmark.class.getSimpleName())
                .warmupIterations(1)
                .measurementIterations(2)
                .warmupTime(org.openjdk.jmh.runner.options.TimeValue.seconds(1))
                .measurementTime(org.openjdk.jmh.runner.options.TimeValue.seconds(2))
                .threads(4)
                .forks(1)
                .build();

        final Collection<RunResult> results = new Runner(opt).run();

        System.out.println("\n====================================================================");
        System.out.println("📊 RELATORIO COMPARATIVO: FASES CRITICAS DE STARTUP SPEED");
        System.out.println("====================================================================");
        for (RunResult result : results)
        {
            final String benchmarkName = result.getParams().getBenchmark();
            final double score = result.getPrimaryResult().getScore();
            final String unit = result.getPrimaryResult().getScoreUnit();
            final double error = result.getPrimaryResult().getScoreError();
            final String shortName = benchmarkName.substring(benchmarkName.lastIndexOf('.') + 1);

            System.out.printf("🔹 %-42s : %,14.2f ± %,10.2f %s%n",
                    shortName, score, error, unit);
        }
        System.out.println("====================================================================");
    }
}
