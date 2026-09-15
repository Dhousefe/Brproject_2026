package ext.mods.benchmark.network;

import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collection;

/**
 * Runner executável para disparar a suite JMH e validar métricas de PPS (Packets Per Second).
 */
public final class BenchmarkRunner
{
	public static void main(String[] args) throws RunnerException
	{
		System.out.println("====================================================================");
		System.out.println("🚀 BrProject-2026 - Network Throughput & Broadcast JMH Benchmark");
		System.out.println("Meta de Validação: >= 250,000 PPS (Packets Per Second)");
		System.out.println("====================================================================");
		
		final Options opt = new OptionsBuilder()
			.include(NetworkBroadcastThroughputBenchmark.class.getSimpleName())
			.warmupIterations(2)
			.measurementIterations(3)
			.threads(8)
			.forks(1)
			.build();
		
		final Collection<RunResult> results = new Runner(opt).run();
		
		System.out.println("\n====================================================================");
		System.out.println("📊 RELATÓRIO FINAL DE PERFORMANCE (PPS / THROUGHPUT)");
		System.out.println("====================================================================");
		
		for (RunResult result : results)
		{
			final String benchmarkName = result.getParams().getBenchmark();
			final double score = result.getPrimaryResult().getScore();
			final String unit = result.getPrimaryResult().getScoreUnit();
			final double error = result.getPrimaryResult().getScoreError();
			
			final String shortName = benchmarkName.substring(benchmarkName.lastIndexOf('.') + 1);
			final boolean meetsTarget = score >= 250_000.0;
			final String status = meetsTarget ? "✅ APROVADO (>= 250k PPS)" : "⚠️ ABAIXO DA META (Gargalo de Lock/IO)";
			
			System.out.printf("🔹 %-35s : %,12.2f ± %,8.2f %s [%s]%n",
				shortName, score, error, unit, status);
		}
		
		System.out.println("====================================================================");
	}
}
