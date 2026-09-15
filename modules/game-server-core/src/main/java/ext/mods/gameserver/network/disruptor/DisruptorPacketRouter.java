package ext.mods.gameserver.network.disruptor;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import ext.mods.commons.logging.CLogger;
import ext.mods.commons.mmocore.ReceivablePacket;
import ext.mods.gameserver.network.GameClient;
import io.netty.buffer.ByteBuf;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Roteador particionado de pacotes via LMAX Disruptor.
 * Implementa o Single-Writer Principle mapeando cada conexão/sessão deterministicamente
 * para uma única partição (RingBuffer isolado com worker thread dedicada).
 * Garante ordem FIFO absoluta sem contenção entre sessões e sem locks globais.
 */
public final class DisruptorPacketRouter
{
	private static final CLogger LOGGER = new CLogger(DisruptorPacketRouter.class.getName());
	private static final DisruptorPacketRouter INSTANCE = new DisruptorPacketRouter();
	
	// Tamanho do RingBuffer obrigatoriamente potência de 2 (32768 slots por partição)
	private static final int RING_BUFFER_SIZE = 32768;
	
	private final int _numPartitions;
	@SuppressWarnings("unchecked")
	private final Disruptor<PacketEvent>[] _disruptors;
	private final RingBuffer<PacketEvent>[] _ringBuffers;
	private volatile boolean _started = false;
	
	public static DisruptorPacketRouter getInstance()
	{
		return INSTANCE;
	}
	
	@SuppressWarnings("unchecked")
	private DisruptorPacketRouter()
	{
		// Dimensiona partições com base nos cores disponíveis (mínimo 2, ideal até 8)
		final int availableProcessors = Runtime.getRuntime().availableProcessors();
		_numPartitions = Math.max(2, Math.min(8, Integer.highestOneBit(availableProcessors)));
		
		_disruptors = new Disruptor[_numPartitions];
		_ringBuffers = new RingBuffer[_numPartitions];
		
		final AtomicInteger workerIndex = new AtomicInteger(0);
		final ThreadFactory threadFactory = r -> {
			final Thread t = new Thread(r, "Disruptor-PacketWorker-" + workerIndex.getAndIncrement());
			t.setDaemon(true);
			return t;
		};
		
		for (int i = 0; i < _numPartitions; i++)
		{
			final int partitionId = i;
			final Disruptor<PacketEvent> disruptor = new Disruptor<>(
				PacketEvent::new,
				RING_BUFFER_SIZE,
				threadFactory,
				ProducerType.MULTI, // Netty EventLoops múltiplos produzem para a partição
				new BlockingWaitStrategy()
			);
			
			disruptor.handleEventsWith(new PacketConsumer(partitionId));
			_disruptors[partitionId] = disruptor;
			_ringBuffers[partitionId] = disruptor.start();
		}
		
		_started = true;
		LOGGER.info("✅ DisruptorPacketRouter iniciado com [{}] partições particionadas (RingBuffer: {} slots/partição).",
			_numPartitions, RING_BUFFER_SIZE);
	}
	
	/**
	 * Roteia deterministicamente o pacote de um cliente para o RingBuffer da sua partição fixa.
	 * O hash é derivado da identidade da conexão/sessão, preservando a ordem FIFO sequencial estrita.
	 */
	public boolean dispatch(GameClient client, ReceivablePacket<GameClient> packet, ByteBuf rawBuffer)
	{
		if (!_started || client == null || packet == null)
		{
			if (rawBuffer != null && rawBuffer.refCnt() > 0)
			{
				rawBuffer.release();
			}
			return false;
		}
		
		final int partitionIndex = getPartitionIndex(client);
		final RingBuffer<PacketEvent> ringBuffer = _ringBuffers[partitionIndex];
		
		final long sequence = ringBuffer.next();
		try
		{
			final PacketEvent event = ringBuffer.get(sequence);
			event.set(client, packet, rawBuffer, sequence);
		}
		finally
		{
			ringBuffer.publish(sequence);
		}
		return true;
	}
	
	/**
	 * Determina a partição Sticky para a conexão do cliente.
	 */
	public int getPartitionIndex(GameClient client)
	{
		int hash;
		if (client.getNettyConnection() != null && client.getNettyConnection().getChannel() != null)
		{
			hash = client.getNettyConnection().getChannel().id().hashCode();
		}
		else if (client.getSessionId() != null)
		{
			hash = client.getSessionId().hashCode();
		}
		else
		{
			hash = System.identityHashCode(client);
		}
		
		return Math.abs(hash) % _numPartitions;
	}
	
	public int getNumPartitions()
	{
		return _numPartitions;
	}
	
	public synchronized void shutdown()
	{
		if (!_started)
			return;
		
		LOGGER.info("Desligando DisruptorPacketRouter e finalizando partições...");
		_started = false;
		for (Disruptor<PacketEvent> disruptor : _disruptors)
		{
			if (disruptor != null)
			{
				try
				{
					disruptor.shutdown();
				}
				catch (Exception ignored)
				{
				}
			}
		}
	}
}
