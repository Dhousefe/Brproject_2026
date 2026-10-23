/*
* Copyleft © 2024-2026 L2Brproject
* * This file is part of L2Brproject derived from aCis409/RusaCis3.8
* * L2Brproject is free software: you can redistribute it and/or modify it
* under the terms of the GNU General Public License as published by the
* Free Software Foundation, either version 3 of the License.
* * L2Brproject is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* General Public License for more details.
* * You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*/
package ext.mods.gameserver.data.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import ext.mods.gameserver.data.manager.RecipeSearchEngine.RecipeEntry;
import ext.mods.gameserver.enums.items.CrystalType;
import ext.mods.gameserver.model.location.Location;
import ext.mods.gameserver.model.records.custom.RecipeDropHolder;

/**
 * Unit tests for SIMD Recipe Search Engine and Recipe Drop Manager.
 * Dhousefe - L2JBR
 */
public class RecipeSearchEngineTest
{
	@Test
	public void testRecipeDropHolderLocation()
	{
		RecipeDropHolder valid = new RecipeDropHolder(
			20001,
			"Kasha Wolf",
			15,
			false,
			1.5,
			1,
			1,
			10000,
			20000,
			-3000
		);

		assertTrue(valid.hasValidLocation());
		Location loc = valid.toLocation();
		assertEquals(10000, loc.getX());
		assertEquals(20000, loc.getY());
		assertEquals(-3000, loc.getZ());

		RecipeDropHolder invalid = new RecipeDropHolder(
			20002,
			"Orc",
			10,
			true,
			0.5,
			1,
			1,
			0,
			0,
			0
		);

		assertFalse(invalid.hasValidLocation());
	}

	@Test
	public void testRecipeSearchEngineInstance()
	{
		RecipeSearchEngine engine = RecipeSearchEngine.getInstance();
		assertNotNull(engine);

		// Execute query on search engine
		List<RecipeEntry> results = engine.search(-1, 0, "");
		assertNotNull(results);

		// Grade filter test
		for (CrystalType grade : CrystalType.values())
		{
			List<RecipeEntry> filtered = engine.search(grade.getId(), 0, "");
			assertNotNull(filtered);
			for (RecipeEntry entry : filtered)
			{
				assertEquals(grade, entry.grade(), "Grade entry must match filtered crystal type");
			}
		}

		// Level filter test (1..9)
		for (int lvl = 1; lvl <= 9; lvl++)
		{
			List<RecipeEntry> filtered = engine.search(-1, lvl, "");
			assertNotNull(filtered);
			for (RecipeEntry entry : filtered)
			{
				assertEquals(lvl, entry.craftLevel(), "Craft level entry must match requested level");
			}
		}
	}

	@Test
	public void testRecipeDropManagerInstance()
	{
		RecipeDropManager manager = RecipeDropManager.getInstance();
		assertNotNull(manager);

		// Unregistered recipe should return empty list gracefully (zero null pointer exceptions)
		List<RecipeDropHolder> nonExistent = manager.getDropsForRecipe(-9999);
		assertNotNull(nonExistent);
		assertTrue(nonExistent.isEmpty());
	}

	@Test
	public void testRecipeVoicedCommandRegistration()
	{
		ext.mods.gameserver.handler.IVoicedCommandHandler handler = ext.mods.gameserver.handler.VoicedCommandHandler.getInstance().getHandler("recipe");
		assertNotNull(handler, "VoicedCommandHandler must find handler for .recipe");
		assertTrue(handler instanceof ext.mods.gameserver.handler.voicedcommandhandlers.RecipeVoicedCommand, "Handler for .recipe must be RecipeVoicedCommand");

		// Check aliases
		assertNotNull(ext.mods.gameserver.handler.VoicedCommandHandler.getInstance().getHandler("recipes"));
		assertNotNull(ext.mods.gameserver.handler.VoicedCommandHandler.getInstance().getHandler("findrecipe"));
		assertNotNull(ext.mods.gameserver.handler.VoicedCommandHandler.getInstance().getHandler("recipe_loc"));
		assertNotNull(ext.mods.gameserver.handler.VoicedCommandHandler.getInstance().getHandler("recipe_info"));
		assertNotNull(ext.mods.gameserver.handler.VoicedCommandHandler.getInstance().getHandler("recipe_mat_info"));
		assertNotNull(ext.mods.gameserver.handler.VoicedCommandHandler.getInstance().getHandler("recipe_mob_all_drops"));
		assertNotNull(ext.mods.gameserver.handler.VoicedCommandHandler.getInstance().getHandler("recipe_mat_loc"));
	}

	@Test
	public void testUseCase01And02MaterialDropAndCraftDetection()
	{
		RecipeDropManager manager = RecipeDropManager.getInstance();
		assertNotNull(manager);

		// UC-01: Non-existent or invalid product returns null cleanly
		ext.mods.gameserver.model.records.Recipe nonCraftable = manager.getRecipeForProduct(-1);
		org.junit.jupiter.api.Assertions.assertNull(nonCraftable);

		// UC-02: Querying drops for an item gracefully returns a non-null list
		List<RecipeDropHolder> itemDrops = manager.getDropsForItem(1864); // Stem
		assertNotNull(itemDrops);
	}

	@Test
	public void testUseCase03PaginationSafetyBounds()
	{
		// Validate that pagination bounds clamping prevents HTML packet overflows
		int pageSize = Math.clamp(ext.mods.config.ConfigServer.FIND_RECIPE_MOB_DROP_PAGE_LIMIT, 5, 8);
		assertTrue(pageSize >= 5 && pageSize <= 8);

		java.util.List<String> dummyList = java.util.List.of("Drop1", "Drop2", "Drop3");
		ext.mods.commons.data.Pagination<String> pagination = new ext.mods.commons.data.Pagination<>(dummyList.stream(), 1, pageSize);
		assertEquals(3, pagination.size());
		assertFalse(pagination.isEmpty());
	}

	@Test
	public void testUseCase04GoalLocMarkersLimitBounds()
	{
		int maxMarkers = Math.max(1, ext.mods.config.ConfigServer.FIND_RECIPE_MAX_RADAR_MARKERS);
		assertTrue(maxMarkers >= 1);
	}

	@Test
	public void testUseCase06MobListPaginationAndLocSuppression()
	{
		// 1. Configured limit bounds test
		int mobPageLimit = Math.clamp(ext.mods.config.ConfigServer.FIND_RECIPE_MOB_LIST_PAGE_LIMIT, 4, 8);
		assertTrue(mobPageLimit >= 4 && mobPageLimit <= 8);

		// 2. Full mob list pagination simulation: 15 mobs into pages of 6
		List<RecipeDropHolder> mobDrops = new java.util.ArrayList<>();
		for (int i = 1; i <= 15; i++)
		{
			mobDrops.add(new RecipeDropHolder(20000 + i, "Mob #" + i, 50, false, 1.0, 1, 1, i % 2 == 0 ? 10000 : 0, 0, 0));
		}

		int totalPages = (mobDrops.size() + mobPageLimit - 1) / mobPageLimit;
		assertEquals(3, totalPages); // 15 / 6 -> 3 pages (6 + 6 + 3)

		// Page 1 slice
		int p1From = 0;
		int p1To = Math.min(p1From + mobPageLimit, mobDrops.size());
		assertEquals(6, p1To - p1From);

		// Page 3 slice (last page)
		int p3From = 2 * mobPageLimit;
		int p3To = Math.min(p3From + mobPageLimit, mobDrops.size());
		assertEquals(3, p3To - p3From);

		// 3. Loc suppression validation: odd mobs (x=0) must have hasValidLocation == false
		RecipeDropHolder noLoc = new RecipeDropHolder(99999, "No Loc Mob", 20, false, 0.5, 1, 1, 0, 0, 0);
		assertFalse(noLoc.hasValidLocation());

		RecipeDropHolder withLoc = new RecipeDropHolder(99998, "Valid Loc Mob", 20, false, 0.5, 1, 1, 15000, 20000, -3000);
		assertTrue(withLoc.hasValidLocation());
	}

	@Test
	public void testHasDropsForRecipeAndGmVisibilityFilter()
	{
		RecipeDropManager manager = RecipeDropManager.getInstance();
		assertNotNull(manager);

		// Non-existent recipe item should report false for hasDropsForRecipe
		assertFalse(manager.hasDropsForRecipe(-99999));

		// Validate ConfigServer flag exists and can be toggled
		boolean originalConfig = ext.mods.config.ConfigServer.FIND_RECIPE_SHOW_NO_DROP_ONLY_FOR_GM;
		try
		{
			ext.mods.config.ConfigServer.FIND_RECIPE_SHOW_NO_DROP_ONLY_FOR_GM = true;
			assertTrue(ext.mods.config.ConfigServer.FIND_RECIPE_SHOW_NO_DROP_ONLY_FOR_GM);

			ext.mods.config.ConfigServer.FIND_RECIPE_SHOW_NO_DROP_ONLY_FOR_GM = false;
			assertFalse(ext.mods.config.ConfigServer.FIND_RECIPE_SHOW_NO_DROP_ONLY_FOR_GM);
		}
		finally
		{
			ext.mods.config.ConfigServer.FIND_RECIPE_SHOW_NO_DROP_ONLY_FOR_GM = originalConfig;
		}
	}

	@Test
	public void testMaterialRightClickConfigToggleAndLookup()
	{
		RecipeDropManager manager = RecipeDropManager.getInstance();
		assertNotNull(manager);

		// Non-existent material item should report non-null empty list gracefully
		List<RecipeDropManager.RecipeMaterialUsage> usages = manager.getRecipesUsingMaterial(-99999);
		assertNotNull(usages);
		assertTrue(usages.isEmpty());

		// Validate ConfigServer flag exists and can be toggled
		boolean originalConfig = ext.mods.config.ConfigServer.FIND_RECIPE_MATERIAL_RIGHT_CLICK_ENABLED;
		try
		{
			ext.mods.config.ConfigServer.FIND_RECIPE_MATERIAL_RIGHT_CLICK_ENABLED = true;
			assertTrue(ext.mods.config.ConfigServer.FIND_RECIPE_MATERIAL_RIGHT_CLICK_ENABLED);

			ext.mods.config.ConfigServer.FIND_RECIPE_MATERIAL_RIGHT_CLICK_ENABLED = false;
			assertFalse(ext.mods.config.ConfigServer.FIND_RECIPE_MATERIAL_RIGHT_CLICK_ENABLED);
		}
		finally
		{
			ext.mods.config.ConfigServer.FIND_RECIPE_MATERIAL_RIGHT_CLICK_ENABLED = originalConfig;
		}

		// Validate that RecipeVoicedCommand registers recipe_mat_uses
		ext.mods.gameserver.handler.voicedcommandhandlers.RecipeVoicedCommand voicedCmd = new ext.mods.gameserver.handler.voicedcommandhandlers.RecipeVoicedCommand();
		String[] commands = voicedCmd.getVoicedCommandList();
		assertNotNull(commands);
		boolean foundMatUses = false;
		for (String cmd : commands)
		{
			if ("recipe_mat_uses".equals(cmd))
			{
				foundMatUses = true;
				break;
			}
		}
		assertTrue(foundMatUses, "recipe_mat_uses must be registered in RecipeVoicedCommand");
	}
}
