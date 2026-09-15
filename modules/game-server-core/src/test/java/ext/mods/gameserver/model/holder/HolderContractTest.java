package ext.mods.gameserver.model.holder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for model/holder value objects.
 * Verifies structural integrity of holder classes before and after extraction.
 */
class HolderContractTest
{
	// ========================================================================
	// IntIntHolder
	// ========================================================================

	@Nested
	@DisplayName("IntIntHolder")
	class IntIntHolderTests
	{
		@Test
		@DisplayName("stores and retrieves id and value correctly")
		void storesIdAndValue()
		{
			final IntIntHolder holder = new IntIntHolder(42, 99);

			assertEquals(42, holder.getId());
			assertEquals(99, holder.getValue());
		}

		@Test
		@DisplayName("setId and setValue update fields")
		void settersWork()
		{
			final IntIntHolder holder = new IntIntHolder(1, 2);
			holder.setId(10);
			holder.setValue(20);

			assertEquals(10, holder.getId());
			assertEquals(20, holder.getValue());
		}

		@Test
		@DisplayName("equals(int,int) matches id and value")
		void equalsIntInt()
		{
			final IntIntHolder holder = new IntIntHolder(5, 10);

			assertTrue(holder.equals(5, 10));
			assertFalse(holder.equals(5, 11));
			assertFalse(holder.equals(6, 10));
		}

		@Test
		@DisplayName("toString contains id and value")
		void toStringFormat()
		{
			final IntIntHolder holder = new IntIntHolder(7, 3);
			final String str = holder.toString();

			assertTrue(str.contains("7"));
			assertTrue(str.contains("3"));
		}

		@Test
		@DisplayName("has getId and getValue methods")
		void hasExpectedMethods() throws Exception
		{
			final Method getId = IntIntHolder.class.getMethod("getId");
			final Method getValue = IntIntHolder.class.getMethod("getValue");

			assertEquals(int.class, getId.getReturnType());
			assertEquals(int.class, getValue.getReturnType());
		}

		@Test
		@DisplayName("extends Object (is a standalone holder, not subclass of Location)")
		void classHierarchy()
		{
			assertEquals(Object.class, IntIntHolder.class.getSuperclass());
		}
	}

	// ========================================================================
	// BalanceHolder
	// ========================================================================

	@Nested
	@DisplayName("BalanceHolder")
	class BalanceHolderTests
	{
		@Test
		@DisplayName("stores pAtk, mAtk, pDef, mDef modifiers")
		void storesModifiers()
		{
			final BalanceHolder holder = new BalanceHolder(1.5, 2.0, 0.8, 1.2);

			assertEquals(1.5, holder._pAtkMod);
			assertEquals(2.0, holder._mAtkMod);
			assertEquals(0.8, holder._pDefMod);
			assertEquals(1.2, holder._mDefMod);
		}

		@Test
		@DisplayName("has exactly 4 double fields for balance modifiers")
		void fieldCount()
		{
			int doubleFieldCount = 0;
			for (Field f : BalanceHolder.class.getDeclaredFields())
			{
				if (f.getType() == double.class)
					doubleFieldCount++;
			}
			assertEquals(4, doubleFieldCount);
		}

		@Test
		@DisplayName("fields are public for direct access")
		void fieldsArePublic()
		{
			for (Field f : BalanceHolder.class.getDeclaredFields())
			{
				if (f.getType() == double.class)
					assertTrue(Modifier.isPublic(f.getModifiers()), f.getName() + " should be public");
			}
		}
	}

	// ========================================================================
	// BalanceName
	// ========================================================================

	@Nested
	@DisplayName("BalanceName")
	class BalanceNameTests
	{
		@Test
		@DisplayName("getName returns known class name for valid id")
		void getNameValid()
		{
			assertEquals("Duelist", BalanceName.getName(88));
			assertEquals("Cardinal", BalanceName.getName(97));
			assertEquals("Maestro", BalanceName.getName(118));
		}

		@Test
		@DisplayName("getName returns Unknown for invalid classId")
		void getNameInvalid()
		{
			final String result = BalanceName.getName(999);
			assertTrue(result.startsWith("Unknown"));
		}

		@Test
		@DisplayName("getClassIdList returns all registered class IDs")
		void classIdList()
		{
			final List<Integer> ids = BalanceName.getClassIdList();

			assertNotNull(ids);
			assertTrue(ids.size() >= 31, "Should have at least 31 class entries");
			assertTrue(ids.contains(88));
			assertTrue(ids.contains(118));
		}

		@Test
		@DisplayName("getClassMap returns consistent map")
		void classMap()
		{
			final Map<Integer, String> map = BalanceName.getClassMap();

			assertNotNull(map);
			assertEquals("Titan", map.get(113));
			assertEquals("Dominator", map.get(115));
		}
	}

	// ========================================================================
	// SkillNode (extends IntIntHolder)
	// ========================================================================

	@Nested
	@DisplayName("SkillNode hierarchy")
	class SkillNodeTests
	{
		@Test
		@DisplayName("SkillNode extends IntIntHolder")
		void inheritance()
		{
			final Class<?> skillNodeClass = assertDoesNotThrow(() ->
				Class.forName("ext.mods.gameserver.model.holder.skillnode.SkillNode"));

			assertEquals(IntIntHolder.class, skillNodeClass.getSuperclass());
		}

		@Test
		@DisplayName("SkillNode has getMinLvl method returning int")
		void hasMinLvlMethod() throws Exception
		{
			final Class<?> clazz = Class.forName("ext.mods.gameserver.model.holder.skillnode.SkillNode");
			final Method getMinLvl = clazz.getMethod("getMinLvl");

			assertEquals(int.class, getMinLvl.getReturnType());
		}

		@Test
		@DisplayName("GeneralSkillNode exists and extends SkillNode")
		void generalSkillNodeExists()
		{
			final Class<?> general = assertDoesNotThrow(() ->
				Class.forName("ext.mods.gameserver.model.holder.skillnode.GeneralSkillNode"));

			assertEquals("SkillNode", general.getSuperclass().getSimpleName());
		}

		@Test
		@DisplayName("EnchantSkillNode exists and extends IntIntHolder")
		void enchantSkillNodeExists()
		{
			final Class<?> enchant = assertDoesNotThrow(() ->
				Class.forName("ext.mods.gameserver.model.holder.skillnode.EnchantSkillNode"));

			assertEquals("IntIntHolder", enchant.getSuperclass().getSimpleName());
		}

		@Test
		@DisplayName("ClanSkillNode exists and extends GeneralSkillNode")
		void clanSkillNodeExists()
		{
			final Class<?> clan = assertDoesNotThrow(() ->
				Class.forName("ext.mods.gameserver.model.holder.skillnode.ClanSkillNode"));

			assertEquals("GeneralSkillNode", clan.getSuperclass().getSimpleName());
		}

		@Test
		@DisplayName("FishingSkillNode exists and extends SkillNode")
		void fishingSkillNodeExists()
		{
			final Class<?> fishing = assertDoesNotThrow(() ->
				Class.forName("ext.mods.gameserver.model.holder.skillnode.FishingSkillNode"));

			assertEquals("SkillNode", fishing.getSuperclass().getSimpleName());
		}
	}

	// ========================================================================
	// Holder class count verification
	// ========================================================================

	@Nested
	@DisplayName("Holder package integrity")
	class PackageIntegrityTests
	{
		@Test
		@DisplayName("IntIntHolder has getSkill and getWeight methods (domain coupling)")
		void domainMethods() throws Exception
		{
			final Method getSkill = IntIntHolder.class.getMethod("getSkill");
			final Method getWeight = IntIntHolder.class.getMethod("getWeight");

			assertNotNull(getSkill);
			assertNotNull(getWeight);
		}

		@Test
		@DisplayName("key holder classes are loadable")
		void holderClassesLoadable()
		{
			final String[] classes = {
				"ext.mods.gameserver.model.holder.IntIntHolder",
				"ext.mods.gameserver.model.holder.BalanceHolder",
				"ext.mods.gameserver.model.holder.BalanceName",
				"ext.mods.gameserver.model.holder.skillnode.SkillNode",
				"ext.mods.gameserver.model.holder.skillnode.GeneralSkillNode",
			};

			for (String className : classes)
			{
				assertDoesNotThrow(() -> Class.forName(className),
					"Class should be loadable: " + className);
			}
		}
	}
}
