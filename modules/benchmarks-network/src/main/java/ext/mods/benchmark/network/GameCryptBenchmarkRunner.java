package ext.mods.benchmark.network;

import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collection;

/**
 * Runner executavel para a comparacao JMH: Synchronized vs Mechanical Sympathy (Lock-Free)
 */
public final class GameCryptBenchmarkRunner
{
	public static void main(String[] args) throws RunnerException
	{
		System.out.println("====================================================================");
		System.out.println("🚀 BrProject-2026 - GameCrypt Mechanical Sympathy JMH Benchmark");
		System.out.println("Comparacao: Synchronized Monitor (Baseline) vs Lock-Free (Candidate)");
		System.out.println("Cargas de Teste: InventoryUpdate (38B) e UserInfo (485B)");
		System.out.println("====================================================================");

		final Options opt = new OptionsBuilder()
			.include(GameCryptBenchmark.class.getSimpleName())
			.warmupIterations(2)
			.measurementIterations(3)
			.threads(1)
			.forks(1)
			.build();

		final Collection<RunResult> results = new Runner(opt).run();

		System.out.println("\n====================================================================");
		System.out.println("📊 RELATORIO FINAL DE PERFORMANCE - JMH BENCHMARK");
		System.out.println("====================================================================");

		for (RunResult result : results)
		{
			final String benchmarkName = result.getParams().getBenchmark();
			final double score = result.getPrimaryResult().getScore();
			final String unit = result.getPrimaryResult().getScoreUnit();
			final double error = result.getPrimaryResult().getScoreError();
			final String shortName = benchmarkName.substring(benchmarkName.lastIndexOf('.') + 1);

			System.out.printf("🔹 %-50s : %,12.3f ± %,8.3f %s%n",
				shortName, score, error, unit);
		}

		System.out.println("====================================================================");
	}
}