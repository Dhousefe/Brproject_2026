package ext.mods.benchmark.network;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * JMH Microbenchmark evaluating:
 * 1. Scalar Decrypt (Baseline)
 * 2. 16-way Vector Unrolled Decrypt (Candidate)
 * 3. Multi-Buffer Broadcast Crypt simulation for 8 and 16 concurrent clients (Minio-style)
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
public class MultiBufferCryptBenchmark
{
	private static final int PACKET_SIZE = 485; // UserInfo size
	private static final int CLIENT_COUNT_16 = 16;
	
	private byte[] _wireData;
	private byte[] _scalarBuffer;
	private byte[] _vectorBuffer;
	
	private ScalarCrypt _scalarCrypt;
	private VectorCrypt _vectorCrypt;
	
	// Multi-buffer structures
	private byte[][] _broadcastPayloads;
	private VectorCrypt[] _clientCrypts;
	
	@Setup(Level.Iteration)
	public void setup()
	{
		_wireData = new byte[PACKET_SIZE];
		_scalarBuffer = new byte[PACKET_SIZE];
		_vectorBuffer = new byte[PACKET_SIZE];
		
		for (int i = 0; i < PACKET_SIZE; i++)
		{
			_wireData[i] = (byte) (i & 0xFF);
		}
		
		final byte[] key = new byte[] {
			(byte) 0x94, (byte) 0x35, (byte) 0x00, (byte) 0x00,
			(byte) 0xa1, (byte) 0x6c, (byte) 0x54, (byte) 0x87,
			(byte) 0x56, (byte) 0x22, (byte) 0x00, (byte) 0x00,
			(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00
		};
		
		_scalarCrypt = new ScalarCrypt();
		_scalarCrypt.setKey(key);
		
		_vectorCrypt = new VectorCrypt();
		_vectorCrypt.setKey(key);
		
		_broadcastPayloads = new byte[CLIENT_COUNT_16][PACKET_SIZE];
		_clientCrypts = new VectorCrypt[CLIENT_COUNT_16];
		for (int c = 0; c < CLIENT_COUNT_16; c++)
		{
			_clientCrypts[c] = new VectorCrypt();
			_clientCrypts[c].setKey(key);
		}
	}
	
	@Benchmark
	public void benchmarkScalarDecrypt_UserInfo_485B(Blackhole bh)
	{
		System.arraycopy(_wireData, 0, _scalarBuffer, 0, PACKET_SIZE);
		_scalarCrypt.decrypt(_scalarBuffer, 0, PACKET_SIZE);
		bh.consume(_scalarBuffer);
	}
	
	@Benchmark
	public void benchmarkVectorDecrypt_UserInfo_485B(Blackhole bh)
	{
		System.arraycopy(_wireData, 0, _vectorBuffer, 0, PACKET_SIZE);
		_vectorCrypt.decrypt(_vectorBuffer, 0, PACKET_SIZE);
		bh.consume(_vectorBuffer);
	}
	
	@Benchmark
	public void benchmarkMultiBufferBroadcast_16Clients(Blackhole bh)
	{
		for (int c = 0; c < CLIENT_COUNT_16; c++)
		{
			System.arraycopy(_wireData, 0, _broadcastPayloads[c], 0, PACKET_SIZE);
			_clientCrypts[c].decrypt(_broadcastPayloads[c], 0, PACKET_SIZE);
		}
		bh.consume(_broadcastPayloads);
	}
	
	// Scalar implementation
	public static final class ScalarCrypt
	{
		private final byte[] _inKey = new byte[16];
		
		public void setKey(byte[] key)
		{
			System.arraycopy(key, 0, _inKey, 0, 16);
		}
		
		public void decrypt(byte[] raw, final int offset, final int size)
		{
			int temp = 0;
			for (int i = 0; i < size; i++)
			{
				int temp2 = raw[offset + i] & 0xFF;
				raw[offset + i] = (byte) (temp2 ^ _inKey[i & 15] ^ temp);
				temp = temp2;
			}
		}
	}
	
	// Vector unrolled implementation
	public static final class VectorCrypt
	{
		private final byte[] _inKey = new byte[16];
		
		public void setKey(byte[] key)
		{
			System.arraycopy(key, 0, _inKey, 0, 16);
		}
		
		public void decrypt(byte[] raw, final int offset, final int size)
		{
			int temp = 0;
			int i = 0;
			final int unrollLimit = size - 15;
			
			for (; i < unrollLimit; i += 16)
			{
				final int base = offset + i;
				
				final int c0 = raw[base] & 0xFF;
				final int c1 = raw[base + 1] & 0xFF;
				final int c2 = raw[base + 2] & 0xFF;
				final int c3 = raw[base + 3] & 0xFF;
				final int c4 = raw[base + 4] & 0xFF;
				final int c5 = raw[base + 5] & 0xFF;
				final int c6 = raw[base + 6] & 0xFF;
				final int c7 = raw[base + 7] & 0xFF;
				final int c8 = raw[base + 8] & 0xFF;
				final int c9 = raw[base + 9] & 0xFF;
				final int c10 = raw[base + 10] & 0xFF;
				final int c11 = raw[base + 11] & 0xFF;
				final int c12 = raw[base + 12] & 0xFF;
				final int c13 = raw[base + 13] & 0xFF;
				final int c14 = raw[base + 14] & 0xFF;
				final int c15 = raw[base + 15] & 0xFF;
				
				raw[base]      = (byte) (c0  ^ _inKey[0]  ^ temp);
				raw[base + 1]  = (byte) (c1  ^ _inKey[1]  ^ c0);
				raw[base + 2]  = (byte) (c2  ^ _inKey[2]  ^ c1);
				raw[base + 3]  = (byte) (c3  ^ _inKey[3]  ^ c2);
				raw[base + 4]  = (byte) (c4  ^ _inKey[4]  ^ c3);
				raw[base + 5]  = (byte) (c5  ^ _inKey[5]  ^ c4);
				raw[base + 6]  = (byte) (c6  ^ _inKey[6]  ^ c5);
				raw[base + 7]  = (byte) (c7  ^ _inKey[7]  ^ c6);
				raw[base + 8]  = (byte) (c8  ^ _inKey[8]  ^ c7);
				raw[base + 9]  = (byte) (c9  ^ _inKey[9]  ^ c8);
				raw[base + 10] = (byte) (c10 ^ _inKey[10] ^ c9);
				raw[base + 11] = (byte) (c11 ^ _inKey[11] ^ c10);
				raw[base + 12] = (byte) (c12 ^ _inKey[12] ^ c11);
				raw[base + 13] = (byte) (c13 ^ _inKey[13] ^ c12);
				raw[base + 14] = (byte) (c14 ^ _inKey[14] ^ c13);
				raw[base + 15] = (byte) (c15 ^ _inKey[15] ^ c14);
				
				temp = c15;
			}
			
			for (; i < size; i++)
			{
				final int temp2 = raw[offset + i] & 0xFF;
				raw[offset + i] = (byte) (temp2 ^ _inKey[i & 15] ^ temp);
				temp = temp2;
			}
		}
	}
}
