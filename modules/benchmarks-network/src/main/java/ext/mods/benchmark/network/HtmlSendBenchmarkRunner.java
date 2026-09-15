package ext.mods.benchmark.network;

import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collection;

/**
 * Runner executavel para disparar o microbenchmark JMH de envio de HTML.
 */
public final class HtmlSendBenchmarkRunner
{
	public static void main(String[] args) throws RunnerException
	{
		System.out.println("====================================================================");
		System.out.println("🚀 BrProject-2026 - HTML & ShowBoard Throughput JMH Benchmark");
		System.out.println("Avaliacao: Legacy vs Optimized ShowBoard Encoding & Dispatch Batching");
		System.out.println("====================================================================");

		final Options opt = new OptionsBuilder()
			.include(HtmlSendThroughputBenchmark.class.getSimpleName())
			.warmupIterations(1)
			.measurementIterations(2)
			.warmupTime(org.openjdk.jmh.runner.options.TimeValue.seconds(1))
			.measurementTime(org.openjdk.jmh.runner.options.TimeValue.seconds(2))
			.threads(4)
			.forks(1)
			.build();

		final Collection<RunResult> results = new Runner(opt).run();

		System.out.println("\n====================================================================");
		System.out.println("📊 RELATORIO FINAL DE PERFORMANCE DE ENVIO DE HTML (OPS/SEC)");
		System.out.println("====================================================================");

		double legacyEncodeScore = 0;
		double optEncodeScore = 0;
		double legacySendScore = 0;
		double optSendScore = 0;

		for (RunResult result : results)
		{
			final String benchmarkName = result.getParams().getBenchmark();
			final double score = result.getPrimaryResult().getScore();
			final String unit = result.getPrimaryResult().getScoreUnit();
			final double error = result.getPrimaryResult().getScoreError();

			final String shortName = benchmarkName.substring(benchmarkName.lastIndexOf('.') + 1);

			if (shortName.contains("LegacyShowBoardEncode"))
				legacyEncodeScore = score;
			else if (shortName.contains("OptimizedShowBoardEncode"))
				optEncodeScore = score;
			else if (shortName.contains("LegacyMultiPacketSend"))
				legacySendScore = score;
			else if (shortName.contains("OptimizedBatchedPacketSend"))
				optSendScore = score;

			System.out.printf("🔹 %-38s : %,14.2f ± %,10.2f %s%n",
				shortName, score, error, unit);
		}

		System.out.println("--------------------------------------------------------------------");
		if (legacyEncodeScore > 0 && optEncodeScore > 0)
		{
			final double encodeSpeedup = ((optEncodeScore - legacyEncodeScore) / legacyEncodeScore) * 100.0;
			System.out.printf("⚡ Ganho de Vazao na Codificacao de ShowBoard : %+.2f%%%n", encodeSpeedup);
		}
		if (legacySendScore > 0 && optSendScore > 0)
		{
			final double sendSpeedup = ((optSendScore - legacySendScore) / legacySendScore) * 100.0;
			System.out.printf("⚡ Ganho de Vazao no Despacho Netty Batching   : %+.2f%%%n", sendSpeedup);
		}
		System.out.println("====================================================================");
	}
}
