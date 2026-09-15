package ext.mods.gameserver.network.disruptor;

import ext.mods.commons.mmocore.ReceivablePacket;
import ext.mods.gameserver.network.GameClient;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes de integridade, ciclo de vida e concorrência do pipeline Netty + LMAX Disruptor.
 * Valida a prevenção das regressões UC01 a UC05 (Sticky Consumer, Zero Leak, Order FIFO).
 */
public class DisruptorPacketPipelineTest
{
	private static DisruptorPacketRouter router;
	
	@BeforeAll
	public static void setup()
	{
		router = DisruptorPacketRouter.getInstance();
	}
	
	@AfterAll
	public static void tearDown()
	{
		if (router != null)
		{
			router.shutdown();
		}
	}
	
	/**
	 * Pacote mock para rastrear ordem de execução e thread que processou o pacote.
	 */
	private static class MockOrderedPacket extends ReceivablePacket<GameClient>
	{
		private final int _id;
		private final List<Integer> _executionHistory;
		private final CountDownLatch _latch;
		private final List<String> _threads;
		
		public MockOrderedPacket(int id, List<Integer> executionHistory, CountDownLatch latch, List<String> threads)
		{
			_id = id;
			_executionHistory = executionHistory;
			_latch = latch;
			_threads = threads;
		}
		
		@Override
		protected boolean read()
		{
			return true;
		}
		
		@Override
		public void run()
		{
			if (_threads != null)
			{
				_threads.add(Thread.currentThread().getName());
			}
			if (_executionHistory != null)
			{
				_executionHistory.add(_id);
			}
			if (_latch != null)
			{
				_latch.countDown();
			}
		}
	}
	
	@Test
	@DisplayName("UC01 & UC02: Garantia de Ordem FIFO Sequencial e Single-Writer por Jogador")
	public void testSequentialFifoOrderPerClient() throws Exception
	{
		final GameClient client = new GameClient(null);
		final int packetCount = 100;
		final List<Integer> executedOrder = Collections.synchronizedList(new ArrayList<>());
		final List<String> workerThreads = Collections.synchronizedList(new ArrayList<>());
		final CountDownLatch latch = new CountDownLatch(packetCount);
		
		for (int i = 0; i < packetCount; i++)
		{
			final ByteBuf buffer = Unpooled.buffer(16);
			buffer.writeInt(i);
			final MockOrderedPacket packet = new MockOrderedPacket(i, executedOrder, latch, workerThreads);
			assertTrue(router.dispatch(client, packet, buffer), "O despacho deve ser aceito");
		}
		
		assertTrue(latch.await(5, TimeUnit.SECONDS), "Todos os pacotes devem ser consumidos em tempo hábil");
		assertEquals(packetCount, executedOrder.size(), "Todos os 100 pacotes devem ser executados");
		
		// 1. Verifica ordem FIFO rigorosa (0, 1, 2, ..., 99)
		for (int i = 0; i < packetCount; i++)
		{
			assertEquals(i, executedOrder.get(i).intValue(), "Ordem FIFO violada no índice " + i);
		}
		
		// 2. Verifica Single-Writer Principle: todos os pacotes do mesmo jogador devem rodar na mesma thread
		final String initialThread = workerThreads.get(0);
		for (String t : workerThreads)
		{
			assertEquals(initialThread, t, "Mais de uma thread executou pacotes do mesmo jogador concorrentemente!");
		}
	}
	
	@Test
	@DisplayName("UC04: Ciclo de Vida e Desreferenciação de Memória (Zero Buffer/Client Leak)")
	public void testByteBufReleaseAndEventClear() throws Exception
	{
		final GameClient client = new GameClient(null);
		final ByteBuf buffer = Unpooled.buffer(32);
		buffer.writeBytes(new byte[]{1, 2, 3, 4});
		
		// ByteBuf criado com refCnt = 1
		assertEquals(1, buffer.refCnt());
		
		final CountDownLatch latch = new CountDownLatch(1);
		final MockOrderedPacket packet = new MockOrderedPacket(999, null, latch, null);
		
		router.dispatch(client, packet, buffer);
		assertTrue(latch.await(3, TimeUnit.SECONDS));
		
		// Após a execução pelo Disruptor, PacketConsumer.finally deve ter chamado event.clear()
		// liberando o ByteBuf para refCnt == 0
		assertEquals(0, buffer.refCnt(), "O ByteBuf deveria ter sido liberado no clear() do evento");
	}
	
	@Test
	@DisplayName("UC05: Burst Multi-Sessão com Balanceamento Particionado")
	public void testMultiSessionBurstDistribution() throws Exception
	{
		final int numClients = 100;
		final int packetsPerClient = 20;
		final int totalPackets = numClients * packetsPerClient;
		final CountDownLatch latch = new CountDownLatch(totalPackets);
		final ConcurrentHashMap<Integer, AtomicInteger> partitionCounters = new ConcurrentHashMap<>();
		
		final ExecutorService producerPool = Executors.newFixedThreadPool(8);
		
		for (int c = 0; c < numClients; c++)
		{
			final GameClient client = new GameClient(null);
			final int partition = router.getPartitionIndex(client);
			partitionCounters.computeIfAbsent(partition, k -> new AtomicInteger(0));
			
			producerPool.submit(() -> {
				for (int p = 0; p < packetsPerClient; p++)
				{
					final ByteBuf buf = Unpooled.buffer(8);
					final MockOrderedPacket packet = new MockOrderedPacket(p, null, latch, null);
					router.dispatch(client, packet, buf);
					partitionCounters.get(partition).incrementAndGet();
				}
			});
		}
		
		assertTrue(latch.await(10, TimeUnit.SECONDS), "Todos os pacotes sob carga massiva devem ser processados");
		producerPool.shutdown();
		
		// Valida que múltiplas partições foram utilizadas (distribuição sem starvation)
		assertTrue(partitionCounters.size() >= 2, "Deveria distribuir entre múltiplas partições");
	}
}
