package ext.mods.gameserver.enums;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Verifies that all enum classes remain accessible (loadable) from outside their own module.
 *
 * <p>After the game-enums module extraction, consumers that previously accessed these enums
 * via the monolithic {@code game-server-core} classpath must still be able to load them
 * via {@code Class.forName}. This test simulates cross-module access via reflection.</p>
 *
 * <p>Each test is self-contained and does not start the server.</p>
 */
class EnumAccessibilityTest
{
	/**
	 * Full list of enum classes that MUST remain accessible across module boundaries.
	 * These 20 classes span all sub-packages.
	 */
	private static final List<String> REQUIRED_ENUM_CLASSES = List.of(
		// actors
		"ext.mods.gameserver.enums.actors.ClassId",
		"ext.mods.gameserver.enums.actors.ClassRace",
		"ext.mods.gameserver.enums.actors.ClassType",
		"ext.mods.gameserver.enums.actors.Sex",
		"ext.mods.gameserver.enums.actors.NpcRace",
		// items
		"ext.mods.gameserver.enums.items.ArmorType",
		"ext.mods.gameserver.enums.items.WeaponType",
		"ext.mods.gameserver.enums.items.ShotType",
		"ext.mods.gameserver.enums.items.CrystalType",
		"ext.mods.gameserver.enums.items.ItemLocation",
		"ext.mods.gameserver.enums.items.EtcItemType",
		"ext.mods.gameserver.enums.items.MaterialType",
		// skills
		"ext.mods.gameserver.enums.skills.Stats",
		"ext.mods.gameserver.enums.skills.ElementType",
		"ext.mods.gameserver.enums.skills.SkillType",
		"ext.mods.gameserver.enums.skills.AbnormalEffect",
		// top-level
		"ext.mods.gameserver.enums.QuestStatus",
		"ext.mods.gameserver.enums.Paperdoll",
		"ext.mods.gameserver.enums.SayType",
		"ext.mods.gameserver.enums.ZoneId"
	);

	@ParameterizedTest(name = "[{index}] {0} is loadable")
	@ValueSource(strings = {
		"ext.mods.gameserver.enums.actors.ClassId",
		"ext.mods.gameserver.enums.actors.ClassRace",
		"ext.mods.gameserver.enums.actors.ClassType",
		"ext.mods.gameserver.enums.actors.Sex",
		"ext.mods.gameserver.enums.actors.NpcRace",
		"ext.mods.gameserver.enums.items.ArmorType",
		"ext.mods.gameserver.enums.items.WeaponType",
		"ext.mods.gameserver.enums.items.ShotType",
		"ext.mods.gameserver.enums.items.CrystalType",
		"ext.mods.gameserver.enums.items.ItemLocation",
		"ext.mods.gameserver.enums.items.EtcItemType",
		"ext.mods.gameserver.enums.items.MaterialType",
		"ext.mods.gameserver.enums.skills.Stats",
		"ext.mods.gameserver.enums.skills.ElementType",
		"ext.mods.gameserver.enums.skills.SkillType",
		"ext.mods.gameserver.enums.skills.AbnormalEffect",
		"ext.mods.gameserver.enums.QuestStatus",
		"ext.mods.gameserver.enums.Paperdoll",
		"ext.mods.gameserver.enums.SayType",
		"ext.mods.gameserver.enums.ZoneId"
	})
	@DisplayName("Enum class is loadable via Class.forName")
	void enumClass_isLoadableByName(String fqcn)
	{
		assertDoesNotThrow(
			() -> Class.forName(fqcn),
			"Enum class not found on classpath: " + fqcn
		);
	}

	@Test
	@DisplayName("All required enum classes are enum types (not interfaces or abstract classes)")
	void allRequiredEnums_areActualEnumTypes() throws ClassNotFoundException
	{
		for (String fqcn : REQUIRED_ENUM_CLASSES)
		{
			Class<?> clazz = Class.forName(fqcn);
			assertNotNull(clazz, "Class.forName returned null for: " + fqcn);
			// ItemType is an interface implemented by enums; the rest should be enums
			if (!fqcn.endsWith("ItemType"))
			{
				assertTrue(clazz.isEnum(),
					fqcn + " should be an enum type but is not");
			}
		}
	}

	@Test
	@DisplayName("Enum classes have at least one enum constant (not empty after extraction)")
	void allRequiredEnums_haveAtLeastOneConstant() throws ClassNotFoundException
	{
		for (String fqcn : REQUIRED_ENUM_CLASSES)
		{
			Class<?> clazz = Class.forName(fqcn);
			if (clazz.isEnum())
			{
				Object[] constants = clazz.getEnumConstants();
				assertNotNull(constants, "Enum constants array is null for: " + fqcn);
				assertTrue(constants.length > 0,
					fqcn + " has 0 enum constants — extraction may have stripped its body");
			}
		}
	}

	@Test
	@DisplayName("Enum package sub-structure is intact (actors, items, skills sub-packages exist)")
	void subPackages_allExist() throws ClassNotFoundException
	{
		// Loading one class from each sub-package proves the package structure is intact.
		assertDoesNotThrow(() -> Class.forName("ext.mods.gameserver.enums.actors.Sex"));
		assertDoesNotThrow(() -> Class.forName("ext.mods.gameserver.enums.items.ShotType"));
		assertDoesNotThrow(() -> Class.forName("ext.mods.gameserver.enums.skills.Stats"));
		assertDoesNotThrow(() -> Class.forName("ext.mods.gameserver.enums.boats.BoatState"));
		assertDoesNotThrow(() -> Class.forName("ext.mods.gameserver.enums.duels.DuelState"));
		assertDoesNotThrow(() -> Class.forName("ext.mods.gameserver.enums.petitions.PetitionState"));
		assertDoesNotThrow(() -> Class.forName("ext.mods.gameserver.enums.bbs.ForumType"));
	}

	@Test
	@DisplayName("ItemType interface is loadable and is an interface")
	void itemTypeInterface_isLoadable() throws ClassNotFoundException
	{
		Class<?> itemType = Class.forName("ext.mods.gameserver.enums.items.ItemType");
		assertTrue(itemType.isInterface(),
			"ItemType must remain an interface — ArmorType, WeaponType, EtcItemType implement it");
	}

	@Test
	@DisplayName("ArmorType implements ItemType interface")
	void armorType_implementsItemType() throws ClassNotFoundException
	{
		Class<?> itemType = Class.forName("ext.mods.gameserver.enums.items.ItemType");
		Class<?> armorType = Class.forName("ext.mods.gameserver.enums.items.ArmorType");
		assertTrue(itemType.isAssignableFrom(armorType),
			"ArmorType must implement ItemType interface");
	}

	@Test
	@DisplayName("WeaponType implements ItemType interface")
	void weaponType_implementsItemType() throws ClassNotFoundException
	{
		Class<?> itemType = Class.forName("ext.mods.gameserver.enums.items.ItemType");
		Class<?> weaponType = Class.forName("ext.mods.gameserver.enums.items.WeaponType");
		assertTrue(itemType.isAssignableFrom(weaponType),
			"WeaponType must implement ItemType interface");
	}

	@Test
	@DisplayName("EtcItemType implements ItemType interface")
	void etcItemType_implementsItemType() throws ClassNotFoundException
	{
		Class<?> itemType = Class.forName("ext.mods.gameserver.enums.items.ItemType");
		Class<?> etcItemType = Class.forName("ext.mods.gameserver.enums.items.EtcItemType");
		assertTrue(itemType.isAssignableFrom(etcItemType),
			"EtcItemType must implement ItemType interface");
	}
}
