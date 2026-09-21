package ext.mods.gameserver.network.netty;

import ext.mods.commons.mmocore.MMOClient;
import ext.mods.commons.mmocore.MMOConnection;
import ext.mods.gameserver.network.GameClient;
import ext.mods.gameserver.network.serverpackets.ActionFailed;
import ext.mods.gameserver.network.serverpackets.L2GameServerPacket;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Validação de integridade concorrente de serialização com Mechanical Sympathy.
 * Simula múltiplos EventLoops do Netty serializando a mesma instância de pacote
 * (ex: ActionFailed.STATIC_PACKET ou pacotes pesados de broadcast) simultaneamente.
 */
public class BroadcastConcurrencyPacketTest
{
	private static class DummyBroadcastPacket extends L2GameServerPacket
	{
		private final int _val;

		public DummyBroadcastPacket(int val)
		{
			_val = val;
		}

		@Override
		protected void writeImpl()
		{
			writeC(0x01);
			writeD(_val);
			writeH(10);
			for (int i = 0; i < 10; i++)
			{
				writeD(i * _val);
			}
			writeS("ConcurrencyTestString");
		}
	}

	@Test
	public void testStaticSingletonConcurrencyUnderLoad() throws Exception
	{
		final int threadCount = 16;
		final int iterationsPerThread = 5000;
		final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		final CountDownLatch startLatch = new CountDownLatch(1);
		final CountDownLatch endLatch = new CountDownLatch(threadCount);
		final AtomicInteger errorCount = new AtomicInteger(0);

		for (int t = 0; t < threadCount; t++)
		{
			executor.submit(() ->
			{
				try
				{
					startLatch.await();
					final ByteBuffer buf = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);

					for (int i = 0; i < iterationsPerThread; i++)
					{
						buf.clear();
						buf.position(2); // Reserva header

						ActionFailed.STATIC_PACKET.writePacket(null, buf);

						// ActionFailed escreve apenas writeC(0x25)
						final int size = buf.position() - 2;
						final int opcode = buf.get(2) & 0xFF;

						if (size != 1 || opcode != 0x25)
						{
							errorCount.incrementAndGet();
						}
					}
				}
				catch (Throwable e)
				{
					errorCount.incrementAndGet();
					e.printStackTrace();
				}
				finally
				{
					endLatch.countDown();
				}
			});
		}

		startLatch.countDown();
		final boolean finished = endLatch.await(10, TimeUnit.SECONDS);
		executor.shutdown();

		Assertions.assertTrue(finished, "O teste concorrente excedeu o timeout!");
		Assertions.assertEquals(0, errorCount.get(), "Nao deve haver nenhum erro de colisao ou corrupcao de buffer no singleton!");
	}

	@Test
	public void testBroadcastPacketMultiThreadedIntegrity() throws Exception
	{
		final int threadCount = 16;
		final int iterationsPerThread = 2000;
		final DummyBroadcastPacket sharedPacket = new DummyBroadcastPacket(42);
		final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		final CountDownLatch startLatch = new CountDownLatch(1);
		final CountDownLatch endLatch = new CountDownLatch(threadCount);
		final AtomicInteger errorCount = new AtomicInteger(0);

		for (int t = 0; t < threadCount; t++)
		{
			final int threadId = t;
			executor.submit(() ->
			{
				try
				{
					startLatch.await();
					final ByteBuffer buf = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);

					for (int i = 0; i < iterationsPerThread; i++)
					{
						buf.clear();
						buf.position(2);

						sharedPacket.writePacket(null, buf);

						final int payloadSize = buf.position() - 2;
						buf.position(2);

						final int opcode = buf.get() & 0xFF;
						final int val = buf.getInt();
						final int count = buf.getShort() & 0xFFFF;

						if (opcode != 0x01 || val != 42 || count != 10)
						{
							errorCount.incrementAndGet();
						}

						for (int k = 0; k < 10; k++)
						{
							int elem = buf.getInt();
							if (elem != k * 42)
							{
								errorCount.incrementAndGet();
							}
						}
					}
				}
				catch (Throwable e)
				{
					errorCount.incrementAndGet();
					e.printStackTrace();
				}
				finally
				{
					endLatch.countDown();
				}
			});
		}

		startLatch.countDown();
		final boolean finished = endLatch.await(10, TimeUnit.SECONDS);
		executor.shutdown();

		Assertions.assertTrue(finished, "O teste de broadcast concorrente excedeu o timeout!");
		Assertions.assertEquals(0, errorCount.get(), "Nenhum buffer deve sofrer corrupcao cruzada durante broadcast!");
	}
}
