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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import ext.mods.commons.logging.CLogger;
import ext.mods.gameserver.data.manager.RecipeSearchEngine.RecipeEntry;
import ext.mods.gameserver.data.xml.NpcData;
import ext.mods.gameserver.data.xml.RecipeData;
import ext.mods.gameserver.enums.DropType;
import ext.mods.gameserver.model.actor.template.NpcTemplate;
import ext.mods.gameserver.model.holder.IntIntHolder;
import ext.mods.gameserver.model.item.DropCategory;
import ext.mods.gameserver.model.item.DropData;
import ext.mods.gameserver.model.location.Location;
import ext.mods.gameserver.model.records.Recipe;
import ext.mods.gameserver.model.records.custom.RecipeDropHolder;
import ext.mods.gameserver.model.spawn.ASpawn;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * Dhousefe - L2JBR 22/10/2026
 */
public class RecipeDropManager
{
	private static final CLogger LOGGER = new CLogger(RecipeDropManager.class.getName());

	public record RecipeMaterialUsage(
		RecipeEntry recipeEntry,
		int quantityNeeded
	) {}

	// Recipe item ID -> List of monsters that drop/spoil this recipe
	private final Int2ObjectMap<List<RecipeDropHolder>> _recipeDrops = new Int2ObjectOpenHashMap<>();

	// Any item ID (materials, ingredients) -> List of monsters that drop/spoil this item
	private final Int2ObjectMap<List<RecipeDropHolder>> _itemDrops = new Int2ObjectOpenHashMap<>();

	// Product Item ID -> Recipe that crafts this product (e.g. Crafted Leather, Cokes, etc.)
	private final Int2ObjectMap<Recipe> _productToRecipe = new Int2ObjectOpenHashMap<>();

	// Material Item ID -> List of recipes that use this material
	private final Int2ObjectMap<List<RecipeMaterialUsage>> _materialToRecipes = new Int2ObjectOpenHashMap<>();

	private volatile boolean _initialized = false;

	protected RecipeDropManager()
	{
	}

	public synchronized void initialize()
	{
		if (_initialized)
			return;

		long start = System.currentTimeMillis();
		_recipeDrops.clear();
		_itemDrops.clear();
		_productToRecipe.clear();
		_materialToRecipes.clear();

		// 1. Index reverse recipe map: product item id -> recipe
		if (RecipeData.getInstance().getRecipes() != null)
		{
			for (Recipe recipe : RecipeData.getInstance().getRecipes())
			{
				if (recipe.product() != null)
				{
					_productToRecipe.put(recipe.product().getId(), recipe);
				}
			}
		}

		// 2. Index drops and spoils for all known NPC templates
		for (NpcTemplate template : NpcData.getInstance().getTemplates())
		{
			if (template == null || template.getDropData() == null || template.getDropData().isEmpty())
				continue;

			int npcId = template.getNpcId();
			String npcName = template.getName();
			int npcLevel = template.getLevel();

			Location spawnLoc = resolveNpcLocation(npcId);
			int locX = spawnLoc != null ? spawnLoc.getX() : 0;
			int locY = spawnLoc != null ? spawnLoc.getY() : 0;
			int locZ = spawnLoc != null ? spawnLoc.getZ() : 0;

			for (DropCategory category : template.getDropData())
			{
				if (category.getDropType() == DropType.HERB)
					continue;

				boolean isSpoil = (category.getDropType() == DropType.SPOIL);

				for (DropData drop : category.getAllDrops())
				{
					int itemId = drop.getItemId();
					double chancePercent = drop.getChance() / 10000.0;

					RecipeDropHolder holder = new RecipeDropHolder(
						npcId,
						npcName,
						npcLevel,
						isSpoil,
						chancePercent,
						drop.getMinDrop(),
						drop.getMaxDrop(),
						locX,
						locY,
						locZ
					);

					// If it is a recipe item, index in _recipeDrops
					if (RecipeData.getInstance().getRecipeByItemId(itemId) != null)
					{
						List<RecipeDropHolder> rList = _recipeDrops.computeIfAbsent(itemId, k -> new ArrayList<>());
						rList.add(holder);
					}

					// Index in general _itemDrops for materials navigation
					List<RecipeDropHolder> iList = _itemDrops.computeIfAbsent(itemId, k -> new ArrayList<>());
					iList.add(holder);
				}
			}
		}

		// Sort recipe drops by chance desc
		for (List<RecipeDropHolder> list : _recipeDrops.values())
		{
			list.sort(Comparator.comparingDouble(RecipeDropHolder::chancePercent).reversed());
		}

		// Sort item drops by chance desc
		for (List<RecipeDropHolder> list : _itemDrops.values())
		{
			list.sort(Comparator.comparingDouble(RecipeDropHolder::chancePercent).reversed());
		}

		// 3. Index material item id -> recipes using it (Deduplicated by product item ID)
		if (RecipeData.getInstance().getRecipes() != null)
		{
			final Int2ObjectMap<Int2ObjectMap<RecipeMaterialUsage>> tempMatMap = new Int2ObjectOpenHashMap<>();

			for (Recipe recipe : RecipeData.getInstance().getRecipes())
			{
				if (recipe.materials() == null || recipe.product() == null)
					continue;

				int productId = recipe.product().getId();
				RecipeEntry entry = RecipeSearchEngine.getInstance().getEntryByRecipeItemId(recipe.recipeId());
				if (entry == null)
					continue;

				boolean newHasDrops = _recipeDrops.containsKey(recipe.recipeId());

				for (IntIntHolder mat : recipe.materials())
				{
					Int2ObjectMap<RecipeMaterialUsage> prodMap = tempMatMap.computeIfAbsent(mat.getId(), k -> new Int2ObjectOpenHashMap<>());
					RecipeMaterialUsage existing = prodMap.get(productId);

					if (existing == null)
					{
						prodMap.put(productId, new RecipeMaterialUsage(entry, mat.getValue()));
					}
					else
					{
						boolean existingHasDrops = _recipeDrops.containsKey(existing.recipeEntry().recipeItemId());
						// Prefer the recipe that has catalogued mob drops on the server
						if (!existingHasDrops && newHasDrops)
						{
							prodMap.put(productId, new RecipeMaterialUsage(entry, mat.getValue()));
						}
						else if (existingHasDrops == newHasDrops)
						{
							// If both have drops or neither has drops, prefer higher success rate or dwarven craft
							if (entry.successRate() > existing.recipeEntry().successRate()
								|| (entry.successRate() == existing.recipeEntry().successRate() && entry.isDwarven() && !existing.recipeEntry().isDwarven()))
							{
								prodMap.put(productId, new RecipeMaterialUsage(entry, mat.getValue()));
							}
						}
					}
				}
			}

			// Transfer deduplicated lists to _materialToRecipes
			for (Int2ObjectMap.Entry<Int2ObjectMap<RecipeMaterialUsage>> entry : tempMatMap.int2ObjectEntrySet())
			{
				List<RecipeMaterialUsage> list = new ArrayList<>(entry.getValue().values());
				list.sort(Comparator.comparingInt((RecipeMaterialUsage u) -> u.recipeEntry().grade().getId()).reversed()
					.thenComparingInt(u -> u.recipeEntry().craftLevel())
					.thenComparing(u -> u.recipeEntry().productName()));
				_materialToRecipes.put(entry.getIntKey(), list);
			}
		}

		_initialized = true;
		LOGGER.info("RecipeDropManager: indexed {} recipes and {} items with mob drops in {} ms.", _recipeDrops.size(), _itemDrops.size(), (System.currentTimeMillis() - start));
	}

	private Location resolveNpcLocation(int npcId)
	{
		try
		{
			ASpawn spawn = SpawnManager.getInstance().getSpawn(npcId);
			if (spawn != null)
			{
				if (spawn.getNpc() != null)
					return new Location(spawn.getNpc().getX(), spawn.getNpc().getY(), spawn.getNpc().getZ());
				if (spawn.getSpawnLocation() != null)
					return spawn.getSpawnLocation();
			}
		}
		catch (Exception ignored)
		{
		}
		return null;
	}

	public List<RecipeDropHolder> getDropsForRecipe(int recipeItemId)
	{
		if (!_initialized)
			initialize();

		List<RecipeDropHolder> list = _recipeDrops.get(recipeItemId);
		return list != null ? list : Collections.emptyList();
	}

	/**
	 * Fast O(1) primitive-map check to determine if a recipe item has any catalogued mob drops/spoils.
	 * @param recipeItemId : the physical recipe item id.
	 * @return true if at least one monster drops or spoils this recipe item.
	 */
	public boolean hasDropsForRecipe(int recipeItemId)
	{
		if (!_initialized)
			initialize();

		List<RecipeDropHolder> list = _recipeDrops.get(recipeItemId);
		return list != null && !list.isEmpty();
	}

	public List<RecipeDropHolder> getDropsForItem(int itemId)
	{
		if (!_initialized)
			initialize();

		List<RecipeDropHolder> list = _itemDrops.get(itemId);
		return list != null ? list : Collections.emptyList();
	}

	public Recipe getRecipeForProduct(int productItemId)
	{
		if (!_initialized)
			initialize();

		return _productToRecipe.get(productItemId);
	}

	public List<RecipeMaterialUsage> getRecipesUsingMaterial(int materialItemId)
	{
		if (!_initialized)
			initialize();

		List<RecipeMaterialUsage> list = _materialToRecipes.get(materialItemId);
		return list != null ? list : Collections.emptyList();
	}

	public static RecipeDropManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder
	{
		protected static final RecipeDropManager INSTANCE = new RecipeDropManager();
	}
}
