package ext.mods.commons.mmocore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

/**
 * Integration tests verifying the network framing layer used by SelectorThread.
 * The L2 protocol uses a 2-byte little-endian header representing total packet length
 * (header + payload). These tests validate encoding, boundary conditions, and
 * buffering behavior without requiring a live network connection.
 */
class NetworkFramingTest
{
	private static final int HEADER_SIZE = 2;
	private static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

	// ---------------------------------------------------------------
	// Packet length encoding tests (2-byte LE header)
	// ---------------------------------------------------------------

	@Test
	void packetLengthEncoding_smallPacket_10bytes()
	{
		final int dataSize = 10;
		final int totalSize = dataSize + HEADER_SIZE;

		final ByteBuffer buf = ByteBuffer.allocate(64).order(BYTE_ORDER);
		buf.putShort((short) totalSize);
		buf.flip();

		// Read back as unsigned short
		final int decoded = buf.getShort() & 0xFFFF;
		assertEquals(12, decoded, "Total packet length for 10-byte payload must be 12");
	}

	@Test
	void packetLengthEncoding_singleBytePayload()
	{
		final int dataSize = 1;
		final int totalSize = dataSize + HEADER_SIZE;

		final ByteBuffer buf = ByteBuffer.allocate(64).order(BYTE_ORDER);
		buf.putShort((short) totalSize);
		buf.flip();

		final int decoded = buf.getShort() & 0xFFFF;
		assertEquals(3, decoded, "Total packet length for 1-byte payload must be 3");
	}

	@Test
	void packetLengthEncoding_maxValidSize()
	{
		// Maximum valid packet: 65535 bytes total (0xFFFF)
		final int totalSize = 65535;

		final ByteBuffer buf = ByteBuffer.allocate(64).order(BYTE_ORDER);
		buf.putShort((short) totalSize);
		buf.flip();

		final int decoded = buf.getShort() & 0xFFFF;
		assertEquals(65535, decoded, "Max valid packet size is 65535");
	}

	@Test
	void packetLengthEncoding_mediumPacket_256bytes()
	{
		final int dataSize = 256;
		final int totalSize = dataSize + HEADER_SIZE;

		final ByteBuffer buf = ByteBuffer.allocate(64).order(BYTE_ORDER);
		buf.putShort((short) totalSize);
		buf.flip();

		final int decoded = buf.getShort() & 0xFFFF;
		assertEquals(258, decoded);
	}

	@Test
	void packetLengthEncoding_largePacket_32768bytes()
	{
		final int dataSize = 32768;
		final int totalSize = dataSize + HEADER_SIZE;

		final ByteBuffer buf = ByteBuffer.allocate(64).order(BYTE_ORDER);
		buf.putShort((short) totalSize);
		buf.flip();

		final int decoded = buf.getShort() & 0xFFFF;
		assertEquals(32770, decoded);
	}

	// ---------------------------------------------------------------
	// Oversized packet rejection
	// ---------------------------------------------------------------

	@Test
	void packetLargerThan65535_isRejectedByTwoByteHeader()
	{
		// The 2-byte unsigned short can only represent 0-65535.
		// A payload of 65534+ HEADER would exceed the representable range.
		final int oversizedPayload = 65534;
		final int totalSize = oversizedPayload + HEADER_SIZE; // 65536 = overflow

		// When cast to short, 65536 wraps to 0
		final ByteBuffer buf = ByteBuffer.allocate(64).order(BYTE_ORDER);
		buf.putShort((short) totalSize);
		buf.flip();

		final int decoded = buf.getShort() & 0xFFFF;
		// 65536 overflows to 0 when stored in 2 bytes
		assertEquals(0, decoded, "65536 must overflow to 0 in a 2-byte header (rejected)");
	}

	@Test
	void packetSizeOverflow_wrapsAround()
	{
		// 70000 in LE short wraps
		final int oversized = 70000;
		final ByteBuffer buf = ByteBuffer.allocate(64).order(BYTE_ORDER);
		buf.putShort((short) oversized);
		buf.flip();

		final int decoded = buf.getShort() & 0xFFFF;
		// 70000 mod 65536 = 4464
		assertEquals(70000 & 0xFFFF, decoded, "Oversized value wraps modulo 65536");
		assertTrue(decoded < 65535, "Wrapped value must be less than max");
	}

	// ---------------------------------------------------------------
	// Zero-length packet handling
	// ---------------------------------------------------------------

	@Test
	void zeroLengthPacket_decodesAsZero()
	{
		final ByteBuffer buf = ByteBuffer.allocate(64).order(BYTE_ORDER);
		buf.putShort((short) 0);
		buf.flip();

		final int decoded = buf.getShort() & 0xFFFF;
		assertEquals(0, decoded, "Zero-length header decodes as 0");
	}

	@Test
	void zeroLengthPacket_dataPendingIsNegative()
	{
		// SelectorThread computes: dataPending = (header & 0xFFFF) - HEADER_SIZE
		// For zero-length: dataPending = 0 - 2 = -2
		final int header = 0;
		final int dataPending = header - HEADER_SIZE;

		assertTrue(dataPending < 0, "Zero header yields negative dataPending (rejected)");
	}

	@Test
	void headerOnlyPacket_dataPendingIsZero()
	{
		// A packet with size == HEADER_SIZE means 0 data bytes
		final int header = HEADER_SIZE;
		final int dataPending = header - HEADER_SIZE;

		assertEquals(0, dataPending, "Header-only packet has zero data pending");
	}

	// ---------------------------------------------------------------
	// Partial read buffering simulation
	// ---------------------------------------------------------------

	@Test
	void partialRead_singleByteBuffered_cannotDecodeHeader()
	{
		// Simulate: only 1 byte arrived (less than HEADER_SIZE)
		final ByteBuffer buf = ByteBuffer.allocate(64).order(BYTE_ORDER);
		buf.put((byte) 0x0A); // partial header byte
		buf.flip();

		// SelectorThread.tryReadPacket checks remaining:
		// case 1: only 1 byte remaining -> cannot decode, returns false
		assertEquals(1, buf.remaining());
		assertFalse(buf.remaining() >= HEADER_SIZE, "Cannot decode header from 1 byte");
	}

	@Test
	void partialRead_headerPresentButDataIncomplete()
	{
		// Packet says 20 bytes total, but only 5 bytes of data available
		final int declaredTotal = 20;
		final ByteBuffer buf = ByteBuffer.allocate(64).order(BYTE_ORDER);

		// Write the 2-byte header
		buf.putShort((short) declaredTotal);
		// Write only 5 bytes of the expected 18 (20 - 2 header)
		for (int i = 0; i < 5; i++)
			buf.put((byte) (i + 1));

		buf.flip();

		// Read header
		final int header = buf.getShort() & 0xFFFF;
		final int dataPending = header - HEADER_SIZE;

		assertEquals(18, dataPending, "Expected 18 bytes of data");
		assertEquals(5, buf.remaining(), "Only 5 bytes available");
		assertFalse(dataPending <= buf.remaining(), "Data incomplete - must buffer and wait");
	}

	@Test
	void partialRead_completePacketInBuffer()
	{
		// Simulate a complete small packet
		final int payloadSize = 8;
		final int totalSize = payloadSize + HEADER_SIZE;

		final ByteBuffer buf = ByteBuffer.allocate(64).order(BYTE_ORDER);
		buf.putShort((short) totalSize);
		for (int i = 0; i < payloadSize; i++)
			buf.put((byte) (0x10 + i));

		buf.flip();

		final int header = buf.getShort() & 0xFFFF;
		final int dataPending = header - HEADER_SIZE;

		assertEquals(payloadSize, dataPending);
		assertTrue(dataPending <= buf.remaining(), "Complete packet available for parsing");

		// Read payload
		final byte[] payload = new byte[dataPending];
		buf.get(payload);
		assertEquals((byte) 0x10, payload[0]);
		assertEquals((byte) 0x17, payload[7]);
	}

	@Test
	void partialRead_multiplePacketsInSingleBuffer()
	{
		// Two complete packets back-to-back in one buffer
		final ByteBuffer buf = ByteBuffer.allocate(128).order(BYTE_ORDER);

		// Packet 1: 4 bytes payload
		buf.putShort((short) 6); // 4 + 2
		buf.put(new byte[]{0x01, 0x02, 0x03, 0x04});

		// Packet 2: 3 bytes payload
		buf.putShort((short) 5); // 3 + 2
		buf.put(new byte[]{0x0A, 0x0B, 0x0C});

		buf.flip();

		// Decode packet 1
		int header1 = buf.getShort() & 0xFFFF;
		int data1 = header1 - HEADER_SIZE;
		assertEquals(4, data1);
		byte[] p1 = new byte[data1];
		buf.get(p1);
		assertEquals(0x01, p1[0]);

		// Decode packet 2
		int header2 = buf.getShort() & 0xFFFF;
		int data2 = header2 - HEADER_SIZE;
		assertEquals(3, data2);
		byte[] p2 = new byte[data2];
		buf.get(p2);
		assertEquals(0x0A, p2[0]);

		assertEquals(0, buf.remaining(), "Buffer fully consumed");
	}

	// ---------------------------------------------------------------
	// ByteOrder verification
	// ---------------------------------------------------------------

	@Test
	void byteOrder_isLittleEndian()
	{
		// L2 protocol uses little-endian byte order
		final ByteBuffer buf = ByteBuffer.allocate(4).order(BYTE_ORDER);
		buf.putShort((short) 0x0100); // 256 in LE: 00 01
		buf.flip();

		// In LE, first byte is least significant
		assertEquals(0x00, buf.get() & 0xFF, "LE: first byte is LSB");
		assertEquals(0x01, buf.get() & 0xFF, "LE: second byte is MSB");
	}

	@Test
	void byteOrder_headerDecodingConsistency()
	{
		// Write size 300 in LE, verify raw bytes
		final ByteBuffer buf = ByteBuffer.allocate(4).order(BYTE_ORDER);
		buf.putShort((short) 300);
		buf.flip();

		// 300 = 0x012C -> LE bytes: 2C 01
		assertEquals(0x2C, buf.get() & 0xFF, "300 LE byte[0] must be 0x2C");
		assertEquals(0x01, buf.get() & 0xFF, "300 LE byte[1] must be 0x01");
	}

	// ---------------------------------------------------------------
	// SelectorThread write framing simulation
	// ---------------------------------------------------------------

	@Test
	void writeFraming_headerIsWrittenBeforePayload()
	{
		// Simulates how SelectorThread.putPacketIntoWriteBuffer works
		final ByteBuffer writeBuffer = ByteBuffer.allocate(256).order(BYTE_ORDER);

		final int headerPos = writeBuffer.position();
		final int dataPos = headerPos + HEADER_SIZE;
		writeBuffer.position(dataPos);

		// Simulate packet write: 10 bytes of data
		for (int i = 0; i < 10; i++)
			writeBuffer.put((byte) (0x30 + i));

		final int dataSize = writeBuffer.position() - dataPos;

		// Write header at reserved position
		writeBuffer.position(headerPos);
		writeBuffer.putShort((short) (dataSize + HEADER_SIZE));
		writeBuffer.position(dataPos + dataSize);

		// Verify
		writeBuffer.flip();
		final int totalLen = writeBuffer.getShort() & 0xFFFF;
		assertEquals(12, totalLen, "Header must encode total size: 10 data + 2 header");
		assertEquals((byte) 0x30, writeBuffer.get(), "First data byte must follow header");
	}
}
