package ext.mods.benchmark.network;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * JMH Microbenchmark comparando:
 * 1. Synchronized GameCrypt (Baseline com monitor intrinseco)
 * 2. Mechanical Sympathy GameCrypt (Candidato Lock-Free, Single-Writer / EventLoop Confinement)
 *
 * Avalia throughput (ops/sec) e latencia sob tamanhos reais de pacotes L2 Interlude:
 * - 38 bytes (InventoryUpdate com 1 delta)
 * - 485 bytes (UserInfo completo do player)
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
public class GameCryptBenchmark
{
	// Tamanhos reais de pacotes Interlude
	private static final int PACKET_SIZE_INVENTORY = 38;
	private static final int PACKET_SIZE_USERINFO = 485;

	private byte[] _bufInventory;
	private byte[] _bufUserInfo;

	private SynchronizedCrypt _syncCrypt;
	private MechanicalSympathyCrypt _mechCrypt;

	@Setup(Level.Iteration)
	public void setup()
	{
		_bufInventory = new byte[PACKET_SIZE_INVENTORY];
		_bufUserInfo = new byte[PACKET_SIZE_USERINFO];

		for (int i = 0; i < PACKET_SIZE_INVENTORY; i++)
			_bufInventory[i] = (byte) (i & 0xFF);

		for (int i = 0; i < PACKET_SIZE_USERINFO; i++)
			_bufUserInfo[i] = (byte) (i & 0xFF);

		final byte[] key = new byte[] {
			(byte) 0x94, (byte) 0x35, (byte) 0x00, (byte) 0x00,
			(byte) 0xa1, (byte) 0x6c, (byte) 0x54, (byte) 0x87,
			(byte) 0x56, (byte) 0x22, (byte) 0x00, (byte) 0x00,
			(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00
		};

		_syncCrypt = new SynchronizedCrypt();
		_syncCrypt.setKey(key);

		_mechCrypt = new MechanicalSympathyCrypt();
		_mechCrypt.setKey(key);
	}

	@Benchmark
	public void benchmarkSynchronized_InventoryUpdate_38B(Blackhole bh)
	{
		_syncCrypt.encrypt(_bufInventory, 0, PACKET_SIZE_INVENTORY);
		bh.consume(_bufInventory);
	}

	@Benchmark
	public void benchmarkMechanicalSympathy_InventoryUpdate_38B(Blackhole bh)
	{
		_mechCrypt.encrypt(_bufInventory, 0, PACKET_SIZE_INVENTORY);
		bh.consume(_bufInventory);
	}

	@Benchmark
	public void benchmarkSynchronized_UserInfo_485B(Blackhole bh)
	{
		_syncCrypt.encrypt(_bufUserInfo, 0, PACKET_SIZE_USERINFO);
		bh.consume(_bufUserInfo);
	}

	@Benchmark
	public void benchmarkMechanicalSympathy_UserInfo_485B(Blackhole bh)
	{
		_mechCrypt.encrypt(_bufUserInfo, 0, PACKET_SIZE_USERINFO);
		bh.consume(_bufUserInfo);
	}

	// -----------------------------------------------------------------------------------
	// 1. Versao com Monitor Intrinseco (Synchronized Baseline)
	// -----------------------------------------------------------------------------------
	public static final class SynchronizedCrypt
	{
		private final byte[] _inKey = new byte[16];
		private final byte[] _outKey = new byte[16];
		private boolean _isEnabled = true;

		public synchronized void setKey(byte[] key)
		{
			System.arraycopy(key, 0, _inKey, 0, 16);
			System.arraycopy(key, 0, _outKey, 0, 16);
		}

		public synchronized void encrypt(byte[] raw, final int offset, final int size)
		{
			if (!_isEnabled)
				return;

			int temp = 0;
			for (int i = 0; i < size; i++)
			{
				int temp2 = raw[offset + i] & 0xFF;
				temp = temp2 ^ _outKey[i & 15] ^ temp;
				raw[offset + i] = (byte) temp;
			}

			int old = _outKey[8] & 0xff;
			old |= _outKey[9] << 8 & 0xff00;
			old |= _outKey[10] << 0x10 & 0xff0000;
			old |= _outKey[11] << 0x18 & 0xff000000;

			old += size;

			_outKey[8] = (byte) (old & 0xff);
			_outKey[9] = (byte) (old >> 0x08 & 0xff);
			_outKey[10] = (byte) (old >> 0x10 & 0xff);
			_outKey[11] = (byte) (old >> 0x18 & 0xff);
		}
	}

	// -----------------------------------------------------------------------------------
	// 2. Versao com Mechanical Sympathy Lock-Free (Single-Writer / EventLoop Confinement)
	// -----------------------------------------------------------------------------------
	public static final class MechanicalSympathyCrypt
	{
		private final byte[] _inKey = new byte[16];
		private final byte[] _outKey = new byte[16];
		private boolean _isEnabled = true;

		public void setKey(byte[] key)
		{
			System.arraycopy(key, 0, _inKey, 0, 16);
			System.arraycopy(key, 0, _outKey, 0, 16);
		}

		public void encrypt(byte[] raw, final int offset, final int size)
		{
			if (!_isEnabled)
				return;

			int temp = 0;
			for (int i = 0; i < size; i++)
			{
				int temp2 = raw[offset + i] & 0xFF;
				temp = temp2 ^ _outKey[i & 15] ^ temp;
				raw[offset + i] = (byte) temp;
			}

			int old = _outKey[8] & 0xff;
			old |= _outKey[9] << 8 & 0xff00;
			old |= _outKey[10] << 0x10 & 0xff0000;
			old |= _outKey[11] << 0x18 & 0xff000000;

			old += size;

			_outKey[8] = (byte) (old & 0xff);
			_outKey[9] = (byte) (old >> 0x08 & 0xff);
			_outKey[10] = (byte) (old >> 0x10 & 0xff);
			_outKey[11] = (byte) (old >> 0x18 & 0xff);
		}
	}
}