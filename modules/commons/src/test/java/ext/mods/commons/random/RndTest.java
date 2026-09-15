package ext.mods.commons.random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RndTest
{
	@Test
	void nextInt_zeroReturnsZero()
	{
		assertEquals(0, Rnd.nextInt(0));
	}

	@Test
	void nextInt_boundIsExclusive()
	{
		for (int i = 0; i < 200; i++)
		{
			int v = Rnd.nextInt(5);
			assertTrue(v >= 0 && v < 5, "value out of range: " + v);
		}
	}

	@Test
	void nextDouble_unitInterval()
	{
		for (int i = 0; i < 200; i++)
		{
			double v = Rnd.nextDouble();
			assertTrue(v >= 0.0 && v < 1.0, "value out of range: " + v);
		}
	}

	@Test
	void nextDouble_boundIsExclusive()
	{
		for (int i = 0; i < 200; i++)
		{
			double v = Rnd.nextDouble(10.0);
			assertTrue(v >= 0.0 && v < 10.0, "value out of range: " + v);
		}
	}

	@Test
	void get_intRangeInclusive()
	{
		for (int i = 0; i < 200; i++)
		{
			int v = Rnd.get(3, 7);
			assertTrue(v >= 3 && v <= 7, "value out of range: " + v);
		}
	}

	@Test
	void calcChance_zeroApplicableNeverSucceeds()
	{
		for (int i = 0; i < 50; i++)
			assertFalse(Rnd.calcChance(0, 100));
	}

	@Test
	void calcChance_fullApplicableAlwaysSucceeds()
	{
		// applicableUnits > nextInt(total) when applicableUnits == total and nextInt is in [0, total)
		for (int i = 0; i < 50; i++)
			assertTrue(Rnd.calcChance(100, 100));
	}
}
