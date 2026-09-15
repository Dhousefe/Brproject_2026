package ext.mods.loginserver.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

import ext.mods.commons.mmocore.ReceivablePacket;
import ext.mods.loginserver.crypt.BlowfishEngine;
import ext.mods.loginserver.crypt.LoginCrypt;
import ext.mods.loginserver.crypt.NewCrypt;
import ext.mods.loginserver.enums.LoginClientState;
import ext.mods.loginserver.network.clientpackets.AuthGameGuard;
import ext.mods.loginserver.network.clientpackets.RequestAuthLogin;
import ext.mods.loginserver.network.clientpackets.RequestServerList;
import ext.mods.loginserver.network.clientpackets.RequestServerLogin;

/**
 * Integration tests verifying the login protocol sequence:
 * - LoginPacketHandler dispatches opcodes correctly per state
 * - SessionKey generation produces unique keys
 * - LoginCrypt encrypt/decrypt round-trips correctly
 * - BlowfishEngine encrypt/decrypt is symmetric
 */
class LoginProtocolSequenceTest
{
	// ---------------------------------------------------------------
	// LoginPacketHandler dispatch tests
	// ---------------------------------------------------------------

	private final LoginPacketHandler handler = new LoginPacketHandler();

	private LoginClient createMockClient(LoginClientState state) throws Exception
	{
		// LoginClient requires MMOConnection + LoginController which is hard to create without real infra.
		// Use Unsafe to bypass the constructor and inject state via reflection.
		return createStubClient(state);
	}

	@Test
	void handler_connectedState_opcode07_returnsAuthGameGuard() throws Exception
	{
		final LoginClient client = createMockClient(LoginClientState.CONNECTED);
		final ByteBuffer buf = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
		buf.put((byte) 0x07); // AuthGameGuard opcode
		buf.put(new byte[32]); // padding
		buf.flip();

		final ReceivablePacket<LoginClient> packet = handler.handlePacket(buf, client);
		assertNotNull(packet, "AuthGameGuard packet must be returned for opcode 0x07 in CONNECTED state");
		assertTrue(packet instanceof AuthGameGuard, "Must be AuthGameGuard instance");
	}

	@Test
	void handler_connectedState_unknownOpcode_returnsNull() throws Exception
	{
		final LoginClient client = createMockClient(LoginClientState.CONNECTED);
		final ByteBuffer buf = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
		buf.put((byte) 0x00); // RequestAuthLogin opcode (invalid in CONNECTED state)
		buf.put(new byte[32]);
		buf.flip();

		final ReceivablePacket<LoginClient> packet = handler.handlePacket(buf, client);
		assertNull(packet, "Unknown opcode in CONNECTED state must return null");
	}

	@Test
	void handler_authedGgState_opcode00_returnsRequestAuthLogin() throws Exception
	{
		final LoginClient client = createMockClient(LoginClientState.AUTHED_GG);
		final ByteBuffer buf = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
		buf.put((byte) 0x00); // RequestAuthLogin opcode
		buf.put(new byte[32]);
		buf.flip();

		final ReceivablePacket<LoginClient> packet = handler.handlePacket(buf, client);
		assertNotNull(packet, "RequestAuthLogin must be returned for opcode 0x00 in AUTHED_GG state");
		assertTrue(packet instanceof RequestAuthLogin, "Must be RequestAuthLogin instance");
	}

	@Test
	void handler_authedGgState_invalidOpcode_returnsNull() throws Exception
	{
		final LoginClient client = createMockClient(LoginClientState.AUTHED_GG);
		final ByteBuffer buf = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
		buf.put((byte) 0x07); // AuthGameGuard opcode (invalid in AUTHED_GG state)
		buf.put(new byte[32]);
		buf.flip();

		final ReceivablePacket<LoginClient> packet = handler.handlePacket(buf, client);
		assertNull(packet, "Wrong opcode in AUTHED_GG state must return null");
	}

	@Test
	void handler_authedLoginState_opcode05_returnsRequestServerList() throws Exception
	{
		final LoginClient client = createMockClient(LoginClientState.AUTHED_LOGIN);
		final ByteBuffer buf = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
		buf.put((byte) 0x05); // RequestServerList opcode
		buf.put(new byte[32]);
		buf.flip();

		final ReceivablePacket<LoginClient> packet = handler.handlePacket(buf, client);
		assertNotNull(packet, "RequestServerList must be returned for opcode 0x05 in AUTHED_LOGIN state");
		assertTrue(packet instanceof RequestServerList, "Must be RequestServerList instance");
	}

	@Test
	void handler_authedLoginState_opcode02_returnsRequestServerLogin() throws Exception
	{
		final LoginClient client = createMockClient(LoginClientState.AUTHED_LOGIN);
		final ByteBuffer buf = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
		buf.put((byte) 0x02); // RequestServerLogin opcode
		buf.put(new byte[32]);
		buf.flip();

		final ReceivablePacket<LoginClient> packet = handler.handlePacket(buf, client);
		assertNotNull(packet, "RequestServerLogin must be returned for opcode 0x02 in AUTHED_LOGIN state");
		assertTrue(packet instanceof RequestServerLogin, "Must be RequestServerLogin instance");
	}

	@Test
	void handler_authedLoginState_invalidOpcode_returnsNull() throws Exception
	{
		final LoginClient client = createMockClient(LoginClientState.AUTHED_LOGIN);
		final ByteBuffer buf = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
		buf.put((byte) 0x07); // AuthGameGuard opcode (invalid here)
		buf.put(new byte[32]);
		buf.flip();

		final ReceivablePacket<LoginClient> packet = handler.handlePacket(buf, client);
		assertNull(packet, "Invalid opcode in AUTHED_LOGIN state must return null");
	}

	// ---------------------------------------------------------------
	// SessionKey generation tests
	// ---------------------------------------------------------------

	@Test
	void sessionKey_secureRandom_producesUniqueKeys()
	{
		final SessionKey key1 = SessionKey.secureRandom();
		final SessionKey key2 = SessionKey.secureRandom();

		assertNotNull(key1);
		assertNotNull(key2);

		// Extremely unlikely (1 in 2^128) that two random keys match
		assertFalse(key1.equals(key2), "Two random session keys must not be equal");
	}

	@Test
	void sessionKey_secureRandom_fieldsArePopulated()
	{
		final SessionKey key = SessionKey.secureRandom();

		// At least one field should be non-zero (probability of all-zero is 1/2^128)
		assertTrue(
			key.loginOkID1 != 0 || key.loginOkID2 != 0 || key.playOkID1 != 0 || key.playOkID2 != 0,
			"At least one session key field should be non-zero"
		);
	}

	@Test
	void sessionKey_secureRandom_multipleCallsVary()
	{
		// Generate 10 keys, at least 2 should differ in loginOkID1
		int firstVal = SessionKey.secureRandom().loginOkID1;
		boolean found = false;
		for (int i = 0; i < 10; i++)
		{
			if (SessionKey.secureRandom().loginOkID1 != firstVal)
			{
				found = true;
				break;
			}
		}
		assertTrue(found, "Multiple secureRandom() calls must produce varying loginOkID1");
	}

	@Test
	void sessionKey_checkLoginPair_matchesCorrectPair()
	{
		final SessionKey key = new SessionKey(100, 200, 300, 400);
		assertTrue(key.checkLoginPair(100, 200));
		assertFalse(key.checkLoginPair(100, 999));
		assertFalse(key.checkLoginPair(999, 200));
	}

	@Test
	void sessionKey_equals_fullCompare()
	{
		final SessionKey a = new SessionKey(1, 2, 3, 4);
		final SessionKey b = new SessionKey(1, 2, 3, 4);
		final SessionKey c = new SessionKey(1, 2, 3, 99);

		assertTrue(a.equals(b), "Identical keys must be equal");
		assertFalse(a.equals(c), "Different playOkID2 must not be equal");
		assertFalse(a.equals(null), "Null must not be equal");
	}

	// ---------------------------------------------------------------
	// LoginCrypt encrypt/decrypt round-trip tests
	// ---------------------------------------------------------------

	@Test
	void loginCrypt_encryptDecrypt_roundTrip() throws Exception
	{
		final byte[] key = new byte[16];
		for (int i = 0; i < 16; i++)
			key[i] = (byte) (i * 3 + 7);

		// Set up encrypt side
		final LoginCrypt encryptor = new LoginCrypt();
		encryptor.setKey(key);

		// Set up decrypt side
		final LoginCrypt decryptor = new LoginCrypt();
		decryptor.setKey(key);

		// First call to encrypt uses static key (XOR pass), skip it to test dynamic key path.
		// Call encrypt once to switch _static flag to false.
		final byte[] initPacket = new byte[64];
		initPacket[0] = 0x01;
		encryptor.encrypt(initPacket, 0, 8);

		// Now test the dynamic path (non-static)
		final byte[] original = new byte[64];
		for (int i = 0; i < 32; i++)
			original[i] = (byte) (i + 0x10);

		final byte[] packet = original.clone();
		final int encSize = encryptor.encrypt(packet, 0, 32);

		// Decrypt
		final boolean valid = decryptor.decrypt(packet, 0, encSize);
		assertTrue(valid, "Decrypted packet must pass checksum verification");

		// First 32 bytes should match original
		for (int i = 0; i < 32; i++)
		{
			assertEquals(original[i], packet[i],
				"Byte[" + i + "] must match after encrypt/decrypt round-trip");
		}
	}

	@Test
	void loginCrypt_firstEncrypt_usesStaticKey() throws Exception
	{
		final byte[] key = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
		final LoginCrypt crypt = new LoginCrypt();
		crypt.setKey(key);

		// First encrypt call uses static blowfish + XOR pass
		final byte[] data = new byte[64];
		data[0] = (byte) 0xAA;
		data[4] = (byte) 0xBB;

		final int size = crypt.encrypt(data, 0, 8);

		// Size should be padded to 8-byte boundary
		assertTrue(size > 0, "Encrypted size must be positive");
		assertEquals(0, size % 8, "Encrypted size must be 8-byte aligned");
	}

	@Test
	void loginCrypt_encryptedDataDiffersFromOriginal() throws Exception
	{
		final byte[] key = new byte[]{10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 13, 14, 15, 16};
		final LoginCrypt crypt = new LoginCrypt();
		crypt.setKey(key);

		// Skip static phase
		crypt.encrypt(new byte[64], 0, 8);

		final byte[] data = new byte[64];
		for (int i = 0; i < 32; i++)
			data[i] = (byte) 0x42;

		final byte[] backup = data.clone();
		crypt.encrypt(data, 0, 32);

		// At least some bytes should differ
		boolean differs = false;
		for (int i = 0; i < 32; i++)
		{
			if (data[i] != backup[i])
			{
				differs = true;
				break;
			}
		}
		assertTrue(differs, "Encrypted data must differ from plaintext");
	}

	// ---------------------------------------------------------------
	// BlowfishEngine symmetric encryption tests
	// ---------------------------------------------------------------

	@Test
	void blowfishEngine_encryptDecrypt_isSymmetric() throws IOException
	{
		final byte[] key = "TestBlowfish123!".getBytes();

		final BlowfishEngine encrypt = new BlowfishEngine();
		encrypt.init(true, key);

		final BlowfishEngine decrypt = new BlowfishEngine();
		decrypt.init(false, key);

		// 8 bytes = 1 block
		final byte[] plaintext = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
		final byte[] ciphertext = new byte[8];
		final byte[] decrypted = new byte[8];

		encrypt.processBlock(plaintext, 0, ciphertext, 0);
		decrypt.processBlock(ciphertext, 0, decrypted, 0);

		assertArrayEquals(plaintext, decrypted, "Blowfish decrypt(encrypt(x)) must equal x");
	}

	@Test
	void blowfishEngine_encryptProducesDifferentOutput() throws IOException
	{
		final byte[] key = "AnotherKey12345!".getBytes();

		final BlowfishEngine encrypt = new BlowfishEngine();
		encrypt.init(true, key);

		final byte[] plaintext = {0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70, (byte) 0x80};
		final byte[] ciphertext = new byte[8];

		encrypt.processBlock(plaintext, 0, ciphertext, 0);

		// Ciphertext must differ from plaintext
		boolean differs = false;
		for (int i = 0; i < 8; i++)
		{
			if (plaintext[i] != ciphertext[i])
			{
				differs = true;
				break;
			}
		}
		assertTrue(differs, "Encrypted block must differ from plaintext");
	}

	@Test
	void blowfishEngine_differentKeys_produceDifferentCiphertext() throws IOException
	{
		final byte[] key1 = "KeyAAAAAAAAAAAAA".getBytes();
		final byte[] key2 = "KeyBBBBBBBBBBBBB".getBytes();

		final BlowfishEngine enc1 = new BlowfishEngine();
		enc1.init(true, key1);

		final BlowfishEngine enc2 = new BlowfishEngine();
		enc2.init(true, key2);

		final byte[] plaintext = {0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48};
		final byte[] cipher1 = new byte[8];
		final byte[] cipher2 = new byte[8];

		enc1.processBlock(plaintext, 0, cipher1, 0);
		enc2.processBlock(plaintext, 0, cipher2, 0);

		boolean differs = false;
		for (int i = 0; i < 8; i++)
		{
			if (cipher1[i] != cipher2[i])
			{
				differs = true;
				break;
			}
		}
		assertTrue(differs, "Different keys must produce different ciphertext");
	}

	@Test
	void blowfishEngine_multiBlock_roundTrip() throws IOException
	{
		final byte[] key = "MultiBlock!Key!!".getBytes();

		final BlowfishEngine encrypt = new BlowfishEngine();
		encrypt.init(true, key);

		final BlowfishEngine decrypt = new BlowfishEngine();
		decrypt.init(false, key);

		// 3 blocks = 24 bytes
		final byte[] plaintext = new byte[24];
		for (int i = 0; i < 24; i++)
			plaintext[i] = (byte) (i * 11);

		final byte[] ciphertext = new byte[24];
		final byte[] decrypted = new byte[24];

		// Encrypt block by block
		for (int i = 0; i < 3; i++)
			encrypt.processBlock(plaintext, i * 8, ciphertext, i * 8);

		// Decrypt block by block
		for (int i = 0; i < 3; i++)
			decrypt.processBlock(ciphertext, i * 8, decrypted, i * 8);

		assertArrayEquals(plaintext, decrypted, "Multi-block round-trip must produce original");
	}

	@Test
	void blowfishEngine_blockSizeIs8()
	{
		final BlowfishEngine engine = new BlowfishEngine();
		assertEquals(8, engine.getBlockSize(), "Blowfish block size must be 8 bytes");
	}

	// ---------------------------------------------------------------
	// NewCrypt round-trip test
	// ---------------------------------------------------------------

	@Test
	void newCrypt_encryptDecrypt_roundTrip() throws IOException
	{
		final byte[] key = "L2CryptTestKey!!".getBytes();
		final NewCrypt crypt = new NewCrypt(key);

		// Data must be multiple of 8
		final byte[] original = new byte[16];
		for (int i = 0; i < 16; i++)
			original[i] = (byte) (i + 0x30);

		// Encrypt
		final byte[] encrypted = crypt.crypt(original);

		// Verify different from original
		boolean differs = false;
		for (int i = 0; i < 16; i++)
		{
			if (encrypted[i] != original[i])
			{
				differs = true;
				break;
			}
		}
		assertTrue(differs, "Encrypted data must differ from original");

		// Decrypt
		final byte[] decrypted = crypt.decrypt(encrypted);
		assertArrayEquals(original, decrypted, "NewCrypt decrypt(encrypt(x)) must equal x");
	}

	@Test
	void newCrypt_checksumAppendAndVerify()
	{
		final byte[] data = new byte[20];
		for (int i = 0; i < 16; i++)
			data[i] = (byte) (i * 7);

		NewCrypt.appendChecksum(data);
		assertTrue(NewCrypt.verifyChecksum(data), "Checksum must verify after append");
	}

	@Test
	void newCrypt_checksumRejectsTampered()
	{
		final byte[] data = new byte[20];
		for (int i = 0; i < 16; i++)
			data[i] = (byte) (i + 1);

		NewCrypt.appendChecksum(data);
		data[5] ^= 0xFF; // tamper
		assertFalse(NewCrypt.verifyChecksum(data), "Tampered data must fail checksum");
	}

	// ---------------------------------------------------------------
	// Stub LoginClient for testing LoginPacketHandler without real connection
	// ---------------------------------------------------------------

	/**
	 * Creates a LoginClient instance with bypassed constructor (avoids LoginController dependency)
	 * and injects the desired state via reflection.
	 */
	private static LoginClient createStubClient(LoginClientState state) throws Exception
	{
		// Use Unsafe to allocate without calling constructor (avoids LoginController.getInstance())
		final var unsafeClass = Class.forName("sun.misc.Unsafe");
		final var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
		unsafeField.setAccessible(true);
		final var unsafe = unsafeField.get(null);
		final var allocateMethod = unsafeClass.getMethod("allocateInstance", Class.class);

		final LoginClient client = (LoginClient) allocateMethod.invoke(unsafe, LoginClient.class);

		// Set the _state field via reflection
		Field stateField = LoginClient.class.getDeclaredField("_state");
		stateField.setAccessible(true);
		stateField.set(client, state);

		return client;
	}
}
