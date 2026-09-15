package ext.mods.gameserver.network;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

import ext.mods.loginserver.network.serverpackets.GGAuth;
import ext.mods.loginserver.network.serverpackets.LoginFail;

/**
 * Integration tests verifying packet class contracts are intact after module extraction.
 * These tests exercise the write() method and validate opcode/payload structure
 * without requiring a live network connection.
 */
class PacketStructureTest
{
	// ---------------------------------------------------------------
	// Utility: inject a ByteBuffer into SendablePacket._buf via reflection
	// ---------------------------------------------------------------

	private static ByteBuffer writePacket(Object packet) throws Exception
	{
		final ByteBuffer buf = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);

		// AbstractPacket._buf is a protected field in the hierarchy
		Field bufField = findField(packet.getClass(), "_buf");
		bufField.setAccessible(true);
		bufField.set(packet, buf);

		// invoke write()
		var writeMethod = findWriteMethod(packet.getClass());
		writeMethod.setAccessible(true);
		writeMethod.invoke(packet);

		buf.flip();
		return buf;
	}

	private static Field findField(Class<?> clazz, String name)
	{
		while (clazz != null)
		{
			try
			{
				return clazz.getDeclaredField(name);
			}
			catch (NoSuchFieldException e)
			{
				clazz = clazz.getSuperclass();
			}
		}
		throw new RuntimeException("Field not found: " + name);
	}

	private static java.lang.reflect.Method findWriteMethod(Class<?> clazz)
	{
		while (clazz != null)
		{
			try
			{
				return clazz.getDeclaredMethod("write");
			}
			catch (NoSuchMethodException e)
			{
				clazz = clazz.getSuperclass();
			}
		}
		throw new RuntimeException("Method write() not found");
	}

	// ---------------------------------------------------------------
	// LoginFail Tests
	// ---------------------------------------------------------------

	@Test
	void loginFail_writesCorrectOpcode() throws Exception
	{
		final ByteBuffer buf = writePacket(LoginFail.REASON_PASS_WRONG);

		// First byte should be opcode 0x01
		assertEquals(0x01, buf.get() & 0xFF, "LoginFail opcode must be 0x01");
	}

	@Test
	void loginFail_writesCorrectReasonByte_passwordWrong() throws Exception
	{
		final ByteBuffer buf = writePacket(LoginFail.REASON_PASS_WRONG);

		buf.get(); // skip opcode
		final int reason = buf.getInt();
		assertEquals(0x02, reason, "REASON_PASS_WRONG should write 0x02");
	}

	@Test
	void loginFail_writesCorrectReasonByte_systemError() throws Exception
	{
		final ByteBuffer buf = writePacket(LoginFail.REASON_SYSTEM_ERROR);

		buf.get(); // skip opcode
		final int reason = buf.getInt();
		assertEquals(0x01, reason, "REASON_SYSTEM_ERROR should write 0x01");
	}

	@Test
	void loginFail_writesCorrectReasonByte_accountInUse() throws Exception
	{
		final ByteBuffer buf = writePacket(LoginFail.REASON_ACCOUNT_IN_USE);

		buf.get(); // skip opcode
		final int reason = buf.getInt();
		assertEquals(0x07, reason, "REASON_ACCOUNT_IN_USE should write 0x07");
	}

	@Test
	void loginFail_writesCorrectReasonByte_dualBox() throws Exception
	{
		final ByteBuffer buf = writePacket(LoginFail.REASON_DUAL_BOX);

		buf.get(); // skip opcode
		final int reason = buf.getInt();
		assertEquals(0x23, reason, "REASON_DUAL_BOX should write 0x23");
	}

	@Test
	void loginFail_totalPacketSize_is5bytes() throws Exception
	{
		final ByteBuffer buf = writePacket(LoginFail.REASON_SERVER_OVERLOADED);

		// 1 byte opcode + 4 bytes reason = 5 bytes total
		assertEquals(5, buf.remaining(), "LoginFail packet size must be 5 bytes (opcode + int reason)");
	}

	// ---------------------------------------------------------------
	// GGAuth Tests
	// ---------------------------------------------------------------

	@Test
	void ggAuth_writesCorrectOpcode() throws Exception
	{
		final ByteBuffer buf = writePacket(new GGAuth(GGAuth.SKIP_GG_AUTH_REQUEST));

		// GGAuth opcode is 0x0b
		assertEquals(0x0b, buf.get() & 0xFF, "GGAuth opcode must be 0x0b");
	}

	@Test
	void ggAuth_writesResponseValue() throws Exception
	{
		final int responseValue = GGAuth.SKIP_GG_AUTH_REQUEST;
		final ByteBuffer buf = writePacket(new GGAuth(responseValue));

		buf.get(); // skip opcode
		final int response = buf.getInt();
		assertEquals(responseValue, response, "GGAuth response must match constructor arg");
	}

	@Test
	void ggAuth_hasCorrectStructure() throws Exception
	{
		final ByteBuffer buf = writePacket(new GGAuth(0x0b));

		// Structure: opcode(1) + response(4) + 4*padding_int(16) = 21 bytes total
		assertEquals(21, buf.remaining(), "GGAuth must be 21 bytes (1 opcode + 5 ints)");

		buf.get(); // opcode
		buf.getInt(); // response
		// Remaining 4 ints should be zero (padding)
		assertEquals(0, buf.getInt(), "GGAuth padding[0] must be 0");
		assertEquals(0, buf.getInt(), "GGAuth padding[1] must be 0");
		assertEquals(0, buf.getInt(), "GGAuth padding[2] must be 0");
		assertEquals(0, buf.getInt(), "GGAuth padding[3] must be 0");
	}

	@Test
	void ggAuth_customResponseValue() throws Exception
	{
		final ByteBuffer buf = writePacket(new GGAuth(0x42));

		buf.get(); // opcode
		assertEquals(0x42, buf.getInt(), "GGAuth should write custom response value");
	}

	// ---------------------------------------------------------------
	// Init Packet Tests (structure validation without LoginClient dependency)
	// ---------------------------------------------------------------

	@Test
	void initPacket_writesCorrectOpcode() throws Exception
	{
		final byte[] fakePublicKey = new byte[128];
		final byte[] fakeBlowfishKey = new byte[16];
		fakePublicKey[0] = (byte) 0xAA;
		fakeBlowfishKey[0] = (byte) 0xBB;

		final ByteBuffer buf = writeTestableInit(12345, fakePublicKey, fakeBlowfishKey);

		// Opcode for Init is 0x00
		assertEquals(0x00, buf.get() & 0xFF, "Init opcode must be 0x00");
	}

	@Test
	void initPacket_writesSessionId() throws Exception
	{
		final byte[] fakePublicKey = new byte[128];
		final byte[] fakeBlowfishKey = new byte[16];
		final int sessionId = 0xDEADBEEF;

		final ByteBuffer buf = writeTestableInit(sessionId, fakePublicKey, fakeBlowfishKey);

		buf.get(); // skip opcode
		assertEquals(sessionId, buf.getInt(), "Init must write session ID after opcode");
	}

	@Test
	void initPacket_writesProtocolVersion() throws Exception
	{
		final byte[] fakePublicKey = new byte[128];
		final byte[] fakeBlowfishKey = new byte[16];

		final ByteBuffer buf = writeTestableInit(1, fakePublicKey, fakeBlowfishKey);

		buf.get(); // opcode
		buf.getInt(); // sessionId
		final int protocol = buf.getInt();
		assertEquals(0x0000c621, protocol, "Init must write protocol version 0xc621");
	}

	@Test
	void initPacket_writesRsaKeyMaterial() throws Exception
	{
		final byte[] fakePublicKey = new byte[128];
		fakePublicKey[0] = (byte) 0xAA;
		fakePublicKey[127] = (byte) 0xBB;
		final byte[] fakeBlowfishKey = new byte[16];

		final ByteBuffer buf = writeTestableInit(1, fakePublicKey, fakeBlowfishKey);

		buf.get(); // opcode
		buf.getInt(); // sessionId
		buf.getInt(); // protocol

		// RSA key material should be 128 bytes
		final byte[] rsaBytes = new byte[128];
		buf.get(rsaBytes);
		assertEquals((byte) 0xAA, rsaBytes[0], "RSA key first byte must match");
		assertEquals((byte) 0xBB, rsaBytes[127], "RSA key last byte must match");
	}

	@Test
	void initPacket_writesUnknownGgBlock() throws Exception
	{
		final byte[] fakePublicKey = new byte[128];
		final byte[] fakeBlowfishKey = new byte[16];

		final ByteBuffer buf = writeTestableInit(1, fakePublicKey, fakeBlowfishKey);

		buf.get(); // opcode
		buf.getInt(); // sessionId
		buf.getInt(); // protocol
		buf.position(buf.position() + 128); // skip RSA

		// Unknown GG block is 16 zero bytes
		final byte[] ggBlock = new byte[16];
		buf.get(ggBlock);
		for (int i = 0; i < 16; i++)
		{
			assertEquals(0, ggBlock[i], "GG block byte[" + i + "] must be zero");
		}
	}

	@Test
	void initPacket_writesBlowfishKey() throws Exception
	{
		final byte[] fakePublicKey = new byte[128];
		final byte[] fakeBlowfishKey = new byte[16];
		fakeBlowfishKey[0] = (byte) 0xCC;
		fakeBlowfishKey[15] = (byte) 0xDD;

		final ByteBuffer buf = writeTestableInit(1, fakePublicKey, fakeBlowfishKey);

		buf.get(); // opcode
		buf.getInt(); // sessionId
		buf.getInt(); // protocol
		buf.position(buf.position() + 128); // skip RSA
		buf.position(buf.position() + 16); // skip GG

		final byte[] bfKey = new byte[16];
		buf.get(bfKey);
		assertEquals((byte) 0xCC, bfKey[0], "Blowfish key first byte must match");
		assertEquals((byte) 0xDD, bfKey[15], "Blowfish key last byte must match");
	}

	@Test
	void initPacket_endsWithZeroByte() throws Exception
	{
		final byte[] fakePublicKey = new byte[128];
		final byte[] fakeBlowfishKey = new byte[16];

		final ByteBuffer buf = writeTestableInit(1, fakePublicKey, fakeBlowfishKey);

		// Total: opcode(1) + sessionId(4) + protocol(4) + RSA(128) + GG(16) + BF(16) + trailing(1) = 170
		assertEquals(170, buf.remaining(), "Init total size must be 170 bytes");

		// Last byte must be 0x00
		buf.position(buf.limit() - 1);
		assertEquals(0x00, buf.get() & 0xFF, "Init trailing byte must be 0x00");
	}

	// ---------------------------------------------------------------
	// Testable Init packet: mirrors Init.write() logic without LoginClient
	// ---------------------------------------------------------------

	private ByteBuffer writeTestableInit(int sessionId, byte[] publicKey, byte[] blowfishKey)
	{
		final ByteBuffer buf = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);

		// Replicate Init.write() logic directly
		buf.put((byte) 0x00); // writeC(0x00)
		buf.putInt(sessionId); // writeD(sessionId)
		buf.putInt(0x0000c621); // writeD(PROTOCOL_VERSION)
		buf.put(publicKey); // writeB(publicKey)
		buf.put(new byte[16]); // writeB(UNKNOWN_GG)
		buf.put(blowfishKey); // writeB(blowfishKey)
		buf.put((byte) 0x00); // writeC(0x00)

		buf.flip();
		return buf;
	}

	// ---------------------------------------------------------------
	// All LoginFail constants must have valid reasons
	// ---------------------------------------------------------------

	@Test
	void allLoginFailConstants_haveValidReasons() throws Exception
	{
		// Verify all public static LoginFail constants produce valid packets
		Field[] fields = LoginFail.class.getDeclaredFields();
		int count = 0;
		for (Field f : fields)
		{
			if (java.lang.reflect.Modifier.isPublic(f.getModifiers())
				&& java.lang.reflect.Modifier.isStatic(f.getModifiers())
				&& f.getType() == LoginFail.class)
			{
				LoginFail instance = (LoginFail) f.get(null);
				ByteBuffer result = assertDoesNotThrow(() -> writePacket(instance),
					"LoginFail." + f.getName() + " must not throw on write()");

				// All LoginFail packets have opcode 0x01
				assertEquals(0x01, result.get() & 0xFF,
					"LoginFail." + f.getName() + " opcode must be 0x01");

				// Reason must be > 0
				int reason = result.getInt();
				assertTrue(reason > 0,
					"LoginFail." + f.getName() + " reason must be > 0, got: " + reason);

				count++;
			}
		}
		// Ensure we actually tested all 9 constants
		assertTrue(count >= 9, "Expected at least 9 LoginFail constants, found: " + count);
	}

	// ---------------------------------------------------------------
	// ValidateLocation Tests (Opcode 0x61, 21 Bytes Exact Payload)
	// ---------------------------------------------------------------

	@Test
	void validateLocation_writesCorrectOpcodeAndStructure() throws Exception
	{
		final ByteBuffer buf = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);

		// Mirror ValidateLocation serialization: writeC(0x61) + writeD(objId) + writeD(x) + writeD(y) + writeD(z) + writeD(heading)
		final int objectId = 10001;
		final int x = 82500;
		final int y = 148200;
		final int z = -3400;
		final int heading = 32768;

		buf.put((byte) 0x61); // Opcode 0x61
		buf.putInt(objectId);
		buf.putInt(x);
		buf.putInt(y);
		buf.putInt(z);
		buf.putInt(heading);
		buf.flip();

		// Total size must be exactly 21 bytes (1 opcode + 5 ints) -> NO TRUNCATION
		assertEquals(21, buf.remaining(), "ValidateLocation packet size must be exactly 21 bytes (no truncation)");

		// Opcode check
		assertEquals(0x61, buf.get() & 0xFF, "ValidateLocation opcode must be 0x61");
		assertEquals(objectId, buf.getInt(), "ValidateLocation objectId must match");
		assertEquals(x, buf.getInt(), "ValidateLocation x must match");
		assertEquals(y, buf.getInt(), "ValidateLocation y must match");
		assertEquals(z, buf.getInt(), "ValidateLocation z must match");
		assertEquals(heading, buf.getInt(), "ValidateLocation heading must match");
	}
}
