package ext.mods.loginserver.crypt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NewCryptTest
{
	@Test
	void appendAndVerifyChecksum_roundTrip()
	{
		final byte[] raw = new byte[16];
		for (int i = 0; i < 12; i++)
			raw[i] = (byte) (i + 1);
		NewCrypt.appendChecksum(raw);
		assertTrue(NewCrypt.verifyChecksum(raw));
	}
	
	@Test
	void verifyChecksum_rejectsOddSize()
	{
		assertFalse(NewCrypt.verifyChecksum(new byte[5]));
		assertFalse(NewCrypt.verifyChecksum(new byte[2]));
	}
	
	@Test
	void verifyChecksum_rejectsTampered()
	{
		final byte[] raw = new byte[16];
		NewCrypt.appendChecksum(raw);
		raw[0] ^= 0x55;
		assertFalse(NewCrypt.verifyChecksum(raw));
	}
}
