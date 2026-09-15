package ext.mods.benchmark.network;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import ext.mods.commons.mmocore.NioNetStackList;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * JMH Microbenchmark para medir throughput de pacotes por segundo (PPS) e latência.
 * Compara:
 * 1. Fila Sincronizada Legada (NIO sendQueue + monitor lock).
 * 2. LMAX Disruptor RingBuffer (MPSC Lock-Free).
 * 3. Netty Zero-Copy retain broadcast.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(8)
@State(Scope.Benchmark)
public class NetworkBroadcastThroughputBenchmark
{
	// -------------------------------------------------------------
	// 1. Estado para Benchmark Legado (NIO Synchronized Queue)
	// -------------------------------------------------------------
	private final NioNetStackList<byte[]> _legacySendQueue = new NioNetStackList<>();
	private final Object _queueLock = new Object();
	private volatile boolean _consumerRunning = true;
	private Thread _legacyConsumerThread;
	private final LongAdder _legacyProcessedCount = new LongAdder();
	
	// -------------------------------------------------------------
	// 2. Estado para Benchmark LMAX Disruptor (Lock-Free RingBuffer)
	// -------------------------------------------------------------
	public static final class PacketEvent
	{
		public byte[] payload;
		public int length;
		public int recipientCount;
	}
	
	private Disruptor<PacketEvent> _disruptor;
	private RingBuffer<PacketEvent> _ringBuffer;
	private final LongAdder _disruptorProcessedCount = new LongAdder();
	
	// -------------------------------------------------------------
	// 3. Estado para Benchmark Netty Zero-Copy Retain
	// -------------------------------------------------------------
	private final byte[] _samplePacketData = new byte[64];
	
	@Setup(Level.Trial)
	public void setup()
	{
		// Preenche payload de teste (64 bytes, tamanho médio de pacotes L2)
		for (int i = 0; i < _samplePacketData.length; i++)
		{
			_samplePacketData[i] = (byte) (i & 0xFF);
		}
		
		// 1. Setup Consumidor Legado
		_consumerRunning = true;
		_legacyConsumerThread = new Thread(() -> {
			while (_consumerRunning)
			{
				byte[] packet = null;
				synchronized (_queueLock)
				{
					if (!_legacySendQueue.isEmpty())
					{
						packet = _legacySendQueue.removeFirst();
					}
				}
				if (packet != null)
				{
					_legacyProcessedCount.increment();
				}
				else
				{
					Thread.yield();
				}
			}
		}, "Legacy-NIO-Consumer");
		_legacyConsumerThread.setDaemon(true);
		_legacyConsumerThread.start();
		
		// 2. Setup Disruptor
		_disruptor = new Disruptor<>(
			PacketEvent::new,
			32768, // Potência de 2
			DaemonThreadFactory.INSTANCE,
			ProducerType.MULTI,
			new BlockingWaitStrategy()
		);
		
		_disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
			_disruptorProcessedCount.increment();
		});
		
		_ringBuffer = _disruptor.start();
	}
	
	@TearDown(Level.Trial)
	public void tearDown()
	{
		_consumerRunning = false;
		if (_legacyConsumerThread != null)
		{
			_legacyConsumerThread.interrupt();
		}
		if (_disruptor != null)
		{
			_disruptor.shutdown();
		}
	}
	
	@Benchmark
	public void benchmarkLegacyNioQueue()
	{
		synchronized (_queueLock)
		{
			_legacySendQueue.addLast(_samplePacketData);
		}
	}
	
	@Benchmark
	public void benchmarkLmaxDisruptorRingBuffer()
	{
		final long sequence = _ringBuffer.next();
		try
		{
			final PacketEvent event = _ringBuffer.get(sequence);
			event.payload = _samplePacketData;
			event.length = 64;
			event.recipientCount = 100;
		}
		finally
		{
			_ringBuffer.publish(sequence);
		}
	}
	
	@Benchmark
	public void benchmarkNettyZeroCopyRetain()
	{
		final int recipientCount = 100;
		final ByteBuf buffer = PooledByteBufAllocator.DEFAULT.directBuffer(64);
		buffer.writeBytes(_samplePacketData);
		
		// Incrementa referências para 100 clientes sem alocar memória adicional
		buffer.retain(recipientCount - 1);
		
		// Simula liberação após envio em cada canal
		for (int i = 0; i < recipientCount; i++)
		{
			buffer.release();
		}
	}
}
