package ext.mods.commons.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArraysUtilTest
{
	@Test
	void isEmpty_nullAndEmpty()
	{
		assertTrue(ArraysUtil.isEmpty(null));
		assertTrue(ArraysUtil.isEmpty(new String[0]));
		assertFalse(ArraysUtil.isEmpty(new String[] { "a" }));
	}

	@Test
	void contains_objectArray()
	{
		String[] arr = { "x", "y", "z" };
		assertTrue(ArraysUtil.contains(arr, "y"));
		assertFalse(ArraysUtil.contains(arr, "missing"));
		assertFalse(ArraysUtil.contains((String[]) null, "x"));
		assertFalse(ArraysUtil.contains(new String[0], "x"));
	}

	@Test
	void contains_intArray()
	{
		int[] arr = { 1, 2, 3 };
		assertTrue(ArraysUtil.contains(arr, 2));
		assertFalse(ArraysUtil.contains(arr, 9));
		assertFalse(ArraysUtil.contains((int[]) null, 1));
		assertFalse(ArraysUtil.contains(new int[0], 1));
	}

	@Test
	void contains_twoObjectArrays()
	{
		assertTrue(ArraysUtil.contains(new String[] { "a", "b" }, new String[] { "b", "c" }));
		assertFalse(ArraysUtil.contains(new String[] { "a" }, new String[] { "z" }));
		assertFalse(ArraysUtil.contains((String[]) null, new String[] { "a" }));
	}

	@Test
	void concat_mergesArrays()
	{
		String[] result = ArraysUtil.concat(new String[] { "a" }, new String[] { "b", "c" });
		assertArrayEquals(new String[] { "a", "b", "c" }, result);
	}
}
