/*
 * Copyleft © 2024-2026 L2Brproject
 * * This file is part of L2Brproject derived from aCis409/RusaCis3.8
 * * L2Brproject is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License.
 */
package ext.mods.gameserver.network;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Random;
import ext.mods.config.ConfigServer;

import static org.junit.jupiter.api.Assertions.*;

public class GameCryptVectorTest
{
	private final byte[] _testKey = new byte[] {
		(byte) 0x94, (byte) 0x35, (byte) 0x00, (byte) 0x00,
		(byte) 0xa1, (byte) 0x6c, (byte) 0x54, (byte) 0x87,
		(byte) 0x56, (byte) 0x22, (byte) 0x00, (byte) 0x00,
		(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00
	};
	
	@BeforeEach
	public void setUp()
	{
		ConfigServer.USE_BLOWFISH_CIPHER = true;
	}
	
	/**
	 * Reference scalar decryption implementation for mathematical verification.
	 */
	private static void referenceDecrypt(byte[] raw, final int offset, final int size, byte[] inKey)
	{
		int temp = 0;
		for (int i = 0; i < size; i++)
		{
			int temp2 = raw[offset + i] & 0xFF;
			raw[offset + i] = (byte) (temp2 ^ inKey[i & 15] ^ temp);
			temp = temp2;
		}
		
		int old = inKey[8] & 0xff;
		old |= inKey[9] << 8 & 0xff00;
		old |= inKey[10] << 0x10 & 0xff0000;
		old |= inKey[11] << 0x18 & 0xff000000;
		old += size;
		inKey[8] = (byte) (old & 0xff);
		inKey[9] = (byte) (old >> 0x08 & 0xff);
		inKey[10] = (byte) (old >> 0x10 & 0xff);
		inKey[11] = (byte) (old >> 0x18 & 0xff);
	}
	
	@Test
	public void testSymmetricParityAcrossVariousPacketSizes()
	{
		final int[] testSizes = new int[] {
			0, 1, 2, 7, 8, 15, 16, 17, 31, 32, 33, 38, 64, 105, 128, 255, 256, 485, 512, 1024
		};
		
		final Random random = new Random(42);
		
		for (int size : testSizes)
		{
			final GameCrypt clientEncryptor = new GameCrypt();
			clientEncryptor.setKey(_testKey);
			
			final GameCrypt serverDecryptor = new GameCrypt();
			serverDecryptor.setKey(_testKey);
			
			// Initial dummy packet to activate cipher on both sender and receiver instances
			final byte[] dummy = new byte[8];
			clientEncryptor.encrypt(dummy, 0, 8);
			serverDecryptor.encrypt(dummy, 0, 8);
			
			final byte[] original = new byte[size + 16]; // extra margin for offset testing
			random.nextBytes(original);
			
			final byte[] payload = original.clone();
			final int offset = 4;
			
			// Encrypt
			clientEncryptor.encrypt(payload, offset, size);
			
			// Decrypt with 16-way vector unrolled decryptor
			serverDecryptor.decrypt(payload, offset, size);
			
			// Verify exact byte-by-byte reconstruction
			for (int i = 0; i < size; i++)
			{
				assertEquals(original[offset + i], payload[offset + i],
					"Parity mismatch for packet size " + size + " at index " + i);
			}
		}
	}
	
	@Test
	public void testVectorVsScalarReferenceParity()
	{
		final Random random = new Random(1337);
		final int iterations = 500;
		
		final byte[] vectorKey = _testKey.clone();
		final byte[] scalarKey = _testKey.clone();
		
		final GameCrypt vectorCrypt = new GameCrypt();
		vectorCrypt.setKey(vectorKey);
		
		// Activate vector crypt
		vectorCrypt.encrypt(new byte[8], 0, 8);
		
		for (int it = 0; it < iterations; it++)
		{
			final int size = random.nextInt(1024) + 1;
			final byte[] encryptedData = new byte[size];
			random.nextBytes(encryptedData);
			
			final byte[] vectorCopy = encryptedData.clone();
			final byte[] scalarCopy = encryptedData.clone();
			
			vectorCrypt.decrypt(vectorCopy, 0, size);
			referenceDecrypt(scalarCopy, 0, size, scalarKey);
			
			assertArrayEquals(scalarCopy, vectorCopy,
				"Vector decryption deviated from scalar reference at iteration " + it + " for size " + size);
		}
	}
	
	@Test
	public void testContinuousMultiPacketStreamIntegrity()
	{
		final GameCrypt sender = new GameCrypt();
		sender.setKey(_testKey);
		
		final GameCrypt receiver = new GameCrypt();
		receiver.setKey(_testKey);
		
		// Activate both endpoints (simulating protocol handshake)
		final byte[] dummy = new byte[8];
		sender.encrypt(dummy, 0, 8);
		receiver.encrypt(dummy, 0, 8);
		
		final Random random = new Random(999);
		
		// Simulate 200 consecutive game packets of varying sizes
		for (int p = 0; p < 200; p++)
		{
			final int size = random.nextInt(500) + 1;
			final byte[] original = new byte[size];
			random.nextBytes(original);
			
			final byte[] wire = original.clone();
			sender.encrypt(wire, 0, size);
			receiver.decrypt(wire, 0, size);
			
			assertArrayEquals(original, wire, "Stream desynchronization at packet #" + p);
		}
	}
}
