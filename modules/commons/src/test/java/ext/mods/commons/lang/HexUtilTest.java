package ext.mods.commons.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HexUtilTest
{
	@Test
	void fillHex_padsWithZeros()
	{
		assertEquals("00", HexUtil.fillHex(0, 2));
		assertEquals("0a", HexUtil.fillHex(10, 2));
		assertEquals("00ff", HexUtil.fillHex(255, 4));
		assertEquals("0000", HexUtil.fillHex(0, 4));
	}

	@Test
	void printData_includesOffsetHexAndAscii()
	{
		byte[] data = "Hello".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
		String out = HexUtil.printData(data);

		assertTrue(out.startsWith("0000: "), "should start with offset: " + out);
		assertTrue(out.contains("48 65 6c 6c 6f"), "should contain hex bytes: " + out);
		assertTrue(out.contains("Hello"), "should contain ASCII: " + out);
	}

	@Test
	void printData_lenLimitsBytes()
	{
		byte[] data = { 0x00, 0x01, 0x02, 0x03 };
		String out = HexUtil.printData(data, 2);

		assertTrue(out.contains("00 01"), out);
		assertTrue(out.contains("0000: "), out);
		// only first two bytes processed
		assertEquals(1, out.lines().count());
	}

	@Test
	void printData_nonPrintableAsDot()
	{
		byte[] data = { 0x00, 0x01 };
		String out = HexUtil.printData(data);
		assertTrue(out.contains(".."), "non-printable should be dots: " + out);
	}
}
