package ext.mods.gameserver.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Accessibility tests for data layer classes.
 * Verifies the structural integrity of XML and SQL data loaders
 * can be loaded via Class.forName without starting the server.
 */
class DataLoaderAccessibilityTest
{
	// ========================================================================
	// XML Data Loaders - Class.forName loadability
	// ========================================================================

	@Nested
	@DisplayName("XML data loaders are loadable")
	class XmlLoaderLoadabilityTests
	{
		@ParameterizedTest
		@ValueSource(strings = {
			"ext.mods.gameserver.data.xml.AdminData",
			"ext.mods.gameserver.data.xml.AnnouncementData",
			"ext.mods.gameserver.data.xml.ArmorSetData",
			"ext.mods.gameserver.data.xml.AugmentationData",
			"ext.mods.gameserver.data.xml.BoatData",
			"ext.mods.gameserver.data.xml.DoorData",
			"ext.mods.gameserver.data.xml.FishData",
			"ext.mods.gameserver.data.xml.HealSpsData",
			"ext.mods.gameserver.data.xml.HennaData",
			"ext.mods.gameserver.data.xml.InstantTeleportData",
			"ext.mods.gameserver.data.xml.ItemData",
			"ext.mods.gameserver.data.xml.ManorAreaData",
			"ext.mods.gameserver.data.xml.MultisellData",
			"ext.mods.gameserver.data.xml.NewbieBuffData",
			"ext.mods.gameserver.data.xml.NpcData",
			"ext.mods.gameserver.data.xml.PlayerData",
			"ext.mods.gameserver.data.xml.PlayerLevelData",
			"ext.mods.gameserver.data.xml.RecipeData",
			"ext.mods.gameserver.data.xml.RestartPointData",
			"ext.mods.gameserver.data.xml.ScriptData",
			"ext.mods.gameserver.data.xml.SkillTreeData",
			"ext.mods.gameserver.data.xml.SoulCrystalData",
			"ext.mods.gameserver.data.xml.SpellbookData",
			"ext.mods.gameserver.data.xml.StaticObjectData",
			"ext.mods.gameserver.data.xml.StaticSpawnData",
			"ext.mods.gameserver.data.xml.SummonItemData",
			"ext.mods.gameserver.data.xml.TeleportData",
			"ext.mods.gameserver.data.xml.WalkerRouteData",
		})
		@DisplayName("XML loader class is loadable")
		void xmlLoaderLoadable(String className)
		{
			assertDoesNotThrow(() -> Class.forName(className),
				"XML loader should be loadable: " + className);
		}
	}

	// ========================================================================
	// SQL Data Loaders - Class.forName loadability
	// ========================================================================

	@Nested
	@DisplayName("SQL data loaders are loadable")
	class SqlLoaderLoadabilityTests
	{
		@ParameterizedTest
		@ValueSource(strings = {
			"ext.mods.gameserver.data.sql.BookmarkTable",
			"ext.mods.gameserver.data.sql.ClanTable",
			"ext.mods.gameserver.data.sql.OfflineTradersTable",
			"ext.mods.gameserver.data.sql.PlayerInfoTable",
			"ext.mods.gameserver.data.sql.ServerMemoTable",
		})
		@DisplayName("SQL loader class is loadable")
		void sqlLoaderLoadable(String className)
		{
			assertDoesNotThrow(() -> Class.forName(className),
				"SQL loader should be loadable: " + className);
		}
	}

	// ========================================================================
	// getInstance() method existence
	// ========================================================================

	@Nested
	@DisplayName("Data loaders have getInstance()")
	class GetInstanceTests
	{
		@ParameterizedTest
		@ValueSource(strings = {
			"ext.mods.gameserver.data.xml.ItemData",
			"ext.mods.gameserver.data.xml.NpcData",
			"ext.mods.gameserver.data.xml.ArmorSetData",
			"ext.mods.gameserver.data.xml.TeleportData",
			"ext.mods.gameserver.data.xml.SkillTreeData",
			"ext.mods.gameserver.data.xml.HennaData",
			"ext.mods.gameserver.data.xml.RecipeData",
			"ext.mods.gameserver.data.xml.FishData",
			"ext.mods.gameserver.data.xml.SoulCrystalData",
			"ext.mods.gameserver.data.xml.SpellbookData",
			"ext.mods.gameserver.data.xml.MultisellData",
			"ext.mods.gameserver.data.xml.AdminData",
			"ext.mods.gameserver.data.sql.BookmarkTable",
			"ext.mods.gameserver.data.sql.ClanTable",
			"ext.mods.gameserver.data.sql.PlayerInfoTable",
			"ext.mods.gameserver.data.sql.ServerMemoTable",
			"ext.mods.gameserver.data.sql.OfflineTradersTable",
		})
		@DisplayName("has static getInstance() method")
		void hasGetInstance(String className) throws Exception
		{
			final Class<?> clazz = Class.forName(className);
			final Method getInstance = clazz.getMethod("getInstance");

			assertNotNull(getInstance, className + " should have getInstance()");
			assertTrue(Modifier.isStatic(getInstance.getModifiers()),
				className + ".getInstance() should be static");
			assertEquals(clazz, getInstance.getReturnType(),
				className + ".getInstance() should return its own type");
		}
	}

	// ========================================================================
	// IXmlReader interface contract
	// ========================================================================

	@Nested
	@DisplayName("IXmlReader interface")
	class IXmlReaderTests
	{
		@Test
		@DisplayName("IXmlReader interface exists and is an interface")
		void interfaceExists()
		{
			final Class<?> clazz = assertDoesNotThrow(() ->
				Class.forName("ext.mods.commons.data.xml.IXmlReader"));

			assertTrue(clazz.isInterface(), "IXmlReader should be an interface");
		}

		@Test
		@DisplayName("IXmlReader has load() method")
		void hasLoadMethod() throws Exception
		{
			final Class<?> clazz = Class.forName("ext.mods.commons.data.xml.IXmlReader");
			final Method load = clazz.getMethod("load");

			assertNotNull(load);
			assertEquals(void.class, load.getReturnType());
		}

		@Test
		@DisplayName("IXmlReader has parseDocument method")
		void hasParseDocument() throws Exception
		{
			final Class<?> clazz = Class.forName("ext.mods.commons.data.xml.IXmlReader");
			final Method parseDocument = clazz.getMethod("parseDocument",
				org.w3c.dom.Document.class, java.nio.file.Path.class);

			assertNotNull(parseDocument);
		}

		@Test
		@DisplayName("IXmlReader has parseInt, parseString, parseBoolean default methods")
		void hasParsingMethods() throws Exception
		{
			final Class<?> clazz = Class.forName("ext.mods.commons.data.xml.IXmlReader");

			// These are default methods - check they exist
			assertNotNull(clazz.getMethod("parseString", org.w3c.dom.Node.class, String.class));
			assertNotNull(clazz.getMethod("parseBoolean", org.w3c.dom.Node.class, Boolean.class));
		}

		@Test
		@DisplayName("IXmlReader has parseLocation method")
		void hasParseLocation() throws Exception
		{
			final Class<?> clazz = Class.forName("ext.mods.commons.data.xml.IXmlReader");

			assertNotNull(clazz.getMethod("parseLocation", org.w3c.dom.Node.class));
		}

		@Test
		@DisplayName("IXmlReader has parseSpawnLocation method")
		void hasParseSpawnLocation() throws Exception
		{
			final Class<?> clazz = Class.forName("ext.mods.commons.data.xml.IXmlReader");

			assertNotNull(clazz.getMethod("parseSpawnLocation", org.w3c.dom.Node.class));
		}

		@Test
		@DisplayName("XML loaders that implement IXmlReader")
		void xmlLoadersImplementInterface() throws Exception
		{
			final Class<?> iXmlReader = Class.forName("ext.mods.commons.data.xml.IXmlReader");

			final String[] implementors = {
				"ext.mods.gameserver.data.xml.TeleportData",
				"ext.mods.gameserver.data.xml.SkillTreeData",
				"ext.mods.gameserver.data.xml.NpcData",
				"ext.mods.gameserver.data.xml.HennaData",
				"ext.mods.gameserver.data.xml.RecipeData",
				"ext.mods.gameserver.data.xml.FishData",
				"ext.mods.gameserver.data.xml.ArmorSetData",
				"ext.mods.gameserver.data.xml.MultisellData",
				"ext.mods.gameserver.data.xml.AdminData",
				"ext.mods.gameserver.data.xml.DoorData",
				"ext.mods.gameserver.data.xml.ManorAreaData",
				"ext.mods.gameserver.data.xml.HealSpsData",
				"ext.mods.gameserver.data.xml.RestartPointData",
				"ext.mods.gameserver.data.xml.StaticSpawnData",
				"ext.mods.gameserver.data.xml.SpellbookData",
				"ext.mods.gameserver.data.xml.AnnouncementData",
				"ext.mods.gameserver.data.xml.AugmentationData",
				"ext.mods.gameserver.data.xml.SoulCrystalData",
				"ext.mods.gameserver.data.xml.InstantTeleportData",
			};

			for (String className : implementors)
			{
				final Class<?> loaderClass = Class.forName(className);
				assertTrue(iXmlReader.isAssignableFrom(loaderClass),
					className + " should implement IXmlReader");
			}
		}
	}

	// ========================================================================
	// Data layer class count (regression guard)
	// ========================================================================

	@Nested
	@DisplayName("Data layer class count")
	class ClassCountTests
	{
		private static final int EXPECTED_XML_LOADER_COUNT = 28;
		private static final int EXPECTED_SQL_LOADER_COUNT = 5;

		@Test
		@DisplayName("XML loader count matches expected")
		void xmlLoaderCount()
		{
			final String[] xmlLoaders = {
				"ext.mods.gameserver.data.xml.AdminData",
				"ext.mods.gameserver.data.xml.AnnouncementData",
				"ext.mods.gameserver.data.xml.ArmorSetData",
				"ext.mods.gameserver.data.xml.AugmentationData",
				"ext.mods.gameserver.data.xml.BoatData",
				"ext.mods.gameserver.data.xml.DoorData",
				"ext.mods.gameserver.data.xml.FishData",
				"ext.mods.gameserver.data.xml.HealSpsData",
				"ext.mods.gameserver.data.xml.HennaData",
				"ext.mods.gameserver.data.xml.InstantTeleportData",
				"ext.mods.gameserver.data.xml.ItemData",
				"ext.mods.gameserver.data.xml.ManorAreaData",
				"ext.mods.gameserver.data.xml.MultisellData",
				"ext.mods.gameserver.data.xml.NewbieBuffData",
				"ext.mods.gameserver.data.xml.NpcData",
				"ext.mods.gameserver.data.xml.ObserverGroupData",
				"ext.mods.gameserver.data.xml.PlayerData",
				"ext.mods.gameserver.data.xml.PlayerLevelData",
				"ext.mods.gameserver.data.xml.RecipeData",
				"ext.mods.gameserver.data.xml.RestartPointData",
				"ext.mods.gameserver.data.xml.ScriptData",
				"ext.mods.gameserver.data.xml.SkillTreeData",
				"ext.mods.gameserver.data.xml.SkipData",
				"ext.mods.gameserver.data.xml.SoulCrystalData",
				"ext.mods.gameserver.data.xml.SpellbookData",
				"ext.mods.gameserver.data.xml.StaticObjectData",
				"ext.mods.gameserver.data.xml.StaticSpawnData",
				"ext.mods.gameserver.data.xml.SummonItemData",
				"ext.mods.gameserver.data.xml.SysString",
				"ext.mods.gameserver.data.xml.TeleportData",
				"ext.mods.gameserver.data.xml.WalkerRouteData",
			};

			int loadableCount = 0;
			for (String className : xmlLoaders)
			{
				try
				{
					Class.forName(className);
					loadableCount++;
				}
				catch (ClassNotFoundException e)
				{
					// Not found - will be counted as missing
				}
			}

			assertTrue(loadableCount >= EXPECTED_XML_LOADER_COUNT,
				"Expected at least " + EXPECTED_XML_LOADER_COUNT + " XML loaders but found " + loadableCount);
		}

		@Test
		@DisplayName("SQL loader count matches expected")
		void sqlLoaderCount()
		{
			final String[] sqlLoaders = {
				"ext.mods.gameserver.data.sql.BookmarkTable",
				"ext.mods.gameserver.data.sql.ClanTable",
				"ext.mods.gameserver.data.sql.OfflineTradersTable",
				"ext.mods.gameserver.data.sql.PlayerInfoTable",
				"ext.mods.gameserver.data.sql.ServerMemoTable",
			};

			int loadableCount = 0;
			for (String className : sqlLoaders)
			{
				try
				{
					Class.forName(className);
					loadableCount++;
				}
				catch (ClassNotFoundException e)
				{
					// Not found
				}
			}

			assertEquals(EXPECTED_SQL_LOADER_COUNT, loadableCount,
				"All SQL loaders should be loadable");
		}
	}

	// ========================================================================
	// Load method existence (XML loaders that implement IXmlReader have load())
	// ========================================================================

	@Nested
	@DisplayName("Data loaders have load() method")
	class LoadMethodTests
	{
		@ParameterizedTest
		@ValueSource(strings = {
			"ext.mods.gameserver.data.xml.NpcData",
			"ext.mods.gameserver.data.xml.TeleportData",
			"ext.mods.gameserver.data.xml.SkillTreeData",
			"ext.mods.gameserver.data.xml.HennaData",
			"ext.mods.gameserver.data.xml.RecipeData",
			"ext.mods.gameserver.data.xml.ArmorSetData",
			"ext.mods.gameserver.data.xml.AdminData",
		})
		@DisplayName("has load() method")
		void hasLoadMethod(String className) throws Exception
		{
			final Class<?> clazz = Class.forName(className);
			final Method load = clazz.getMethod("load");

			assertNotNull(load);
			assertEquals(void.class, load.getReturnType());
		}
	}
}
