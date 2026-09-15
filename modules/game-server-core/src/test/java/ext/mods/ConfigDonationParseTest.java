package ext.mods;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ConfigDonationParseTest
{
	@Test
	void parseDonationIntArray_basic()
	{
		assertArrayEquals(new int[] { 5, 10, 15 }, Config.parseDonationIntArray("5,10,15"));
		assertArrayEquals(new int[] { 1, 2, 3 }, Config.parseDonationIntArray(" 1, 2 ,3 "));
	}
	
	@Test
	void parseDonationIntArray_blank()
	{
		assertEquals(0, Config.parseDonationIntArray("").length);
		assertEquals(0, Config.parseDonationIntArray(null).length);
	}
	
	@Test
	void parseDonationIntArray_invalidTokenBecomesZero()
	{
		assertArrayEquals(new int[] { 0, 5 }, Config.parseDonationIntArray("a,5"));
	}
	
	@Test
	void parseDonationStringArray_blankAndList()
	{
		assertArrayEquals(new String[] { "" }, Config.parseDonationStringArray(""));
		assertArrayEquals(new String[] { "a", "b" }, Config.parseDonationStringArray("a,b"));
	}
}
