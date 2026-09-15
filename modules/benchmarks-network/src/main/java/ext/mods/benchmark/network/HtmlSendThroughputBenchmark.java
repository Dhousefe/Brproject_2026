package ext.mods.benchmark.network;

import io.netty.channel.embedded.EmbeddedChannel;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * JMH Microbenchmark para avaliar o ganho de vazao (Throughput / OPS/SEC)
 * no subsistema de envio de HTML para o cliente (ShowBoard & Community Board).
 *
 * Cenarios avaliados:
 * 1. benchmarkLegacyShowBoardEncode: Codificacao classica do ShowBoard (8 strings UTF-16LE em laco char-a-char).
 * 2. benchmarkOptimizedShowBoardEncode: Codificacao otimizada com STATIC_HEADER_BYTES pre-calculado.
 * 3. benchmarkLegacyMultiPacketSend: Despacho com flushes individuais Netty (3 flushes por tela de HTML).
 * 4. benchmarkOptimizedBatchedPacketSend: Despacho em lote Netty (2 writes + 1 flush consolidado).
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(4)
@State(Scope.Benchmark)
public class HtmlSendThroughputBenchmark
{
	private static final String[] HEADERS = {
		"bypass _bbshome",
		"bypass _bbsgetfav",
		"bypass _bbsloc",
		"bypass _bbsclan",
		"bypass _bbsmemo",
		"bypass _maillist_0_1_0_",
		"bypass _friendlist_0_",
		"bypass _bbsgetfav_add"
	};

	private static final byte[] STATIC_HEADER_BYTES;
	static
	{
		int totalBytes = 0;
		for (String h : HEADERS)
			totalBytes += (h.length() + 1) * 2;

		final ByteBuffer buf = ByteBuffer.allocate(totalBytes);
		for (String h : HEADERS)
		{
			for (int i = 0; i < h.length(); i++)
				buf.putChar(h.charAt(i));
			buf.putChar('\000');
		}
		STATIC_HEADER_BYTES = buf.array();
	}

	private String _sampleHtml;
	private EmbeddedChannel _channel;

	@Setup(Level.Trial)
	public void setup()
	{
		// HTML representativo de uma aba de loja da Community Board (~3500 chars)
		final StringBuilder sb = new StringBuilder(4000);
		sb.append("<html><body><center><table width=600 border=0 cellpadding=2 cellspacing=2>");
		for (int i = 0; i < 20; i++)
		{
			sb.append("<tr><td>Item ").append(i).append("</td>");
			sb.append("<td><button value=\"Buy\" action=\"bypass _bbsmultisell;page shop;").append(3000 + i).append("\" width=60 height=20></td></tr>");
		}
		sb.append("</table></center></body></html>");
		_sampleHtml = sb.toString();

		_channel = new EmbeddedChannel();
	}

	@TearDown(Level.Trial)
	public void tearDown()
	{
		if (_channel != null && _channel.isOpen())
		{
			_channel.finishAndReleaseAll();
			_channel.close();
		}
	}

	@Benchmark
	public void benchmarkLegacyShowBoardEncode(Blackhole bh)
	{
		final ByteBuffer buf = ByteBuffer.allocate(16384);
		buf.put((byte) 0x6e);
		buf.put((byte) 0x01);

		for (String h : HEADERS)
		{
			for (int i = 0; i < h.length(); i++)
				buf.putChar(h.charAt(i));
			buf.putChar('\000');
		}

		final String payload = "101\u0008" + _sampleHtml;
		for (int i = 0; i < payload.length(); i++)
			buf.putChar(payload.charAt(i));
		buf.putChar('\000');

		buf.flip();
		bh.consume(buf);
	}

	@Benchmark
	public void benchmarkOptimizedShowBoardEncode(Blackhole bh)
	{
		final ByteBuffer buf = ByteBuffer.allocate(16384);
		buf.put((byte) 0x6e);
		buf.put((byte) 0x01);

		buf.put(STATIC_HEADER_BYTES);

		final String payload = "101\u0008" + _sampleHtml;
		for (int i = 0; i < payload.length(); i++)
			buf.putChar(payload.charAt(i));
		buf.putChar('\000');

		buf.flip();
		bh.consume(buf);
	}

	@Benchmark
	public void benchmarkLegacyMultiPacketSend(Blackhole bh)
	{
		// Simula 3 envios de ShowBoard (101, 102, 103) cada um com seu flush individual
		_channel.writeAndFlush("pkt1");
		_channel.writeAndFlush("pkt2");
		_channel.writeAndFlush("pkt3");

		Object out;
		while ((out = _channel.readOutbound()) != null)
		{
			bh.consume(out);
		}
	}

	@Benchmark
	public void benchmarkOptimizedBatchedPacketSend(Blackhole bh)
	{
		// Simula 3 envios de ShowBoard com batching (2 writes + 1 flush consolidado)
		_channel.write("pkt1");
		_channel.write("pkt2");
		_channel.writeAndFlush("pkt3");

		Object out;
		while ((out = _channel.readOutbound()) != null)
		{
			bh.consume(out);
		}
	}
}
