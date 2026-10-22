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
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import ext.mods.commons.logging.CLogger;
import ext.mods.gameserver.data.xml.ItemData;
import ext.mods.gameserver.data.xml.RecipeData;
import ext.mods.gameserver.enums.items.CrystalType;
import ext.mods.gameserver.model.item.kind.Item;
import ext.mods.gameserver.model.records.Recipe;

/**
 * Dhousefe - L2JBR 22/10/2026
 */
public class RecipeSearchEngine
{
	private static final CLogger LOGGER = new CLogger(RecipeSearchEngine.class.getName());

	public record RecipeEntry(
		int recipeListId,
		int recipeItemId,
		int productItemId,
		int craftLevel,
		CrystalType grade,
		boolean isDwarven,
		String productName,
		String icon,
		int successRate,
		int mpCost
	) {}

	// Structure of Arrays (Contiguous primitive memory for CPU cache line alignment)
	private int[] _recipeListIds;
	private int[] _recipeItemIds;
	private int[] _productItemIds;
	private int[] _craftLevels;
	private int[] _grades; // 0=None, 1=D, 2=C, 3=B, 4=A, 5=S
	private int[] _isDwarven;
	private int[] _successRates;
	private int[] _mpCosts;
	private String[] _productNames;
	private String[] _icons;
	private String[] _searchNamesLower;

	private RecipeEntry[] _entries;
	private int _size;
	private volatile boolean _initialized = false;

	// Vector API availability flag (lazy checked)
	private static final boolean VECTOR_API_AVAILABLE = checkVectorApi();

	private static boolean checkVectorApi()
	{
		try
		{
			Class.forName("jdk.incubator.vector.IntVector");
			return true;
		}
		catch (Throwable t)
		{
			return false;
		}
	}

	protected RecipeSearchEngine()
	{
	}

	public synchronized void initialize()
	{
		if (_initialized)
			return;

		long start = System.currentTimeMillis();
		Collection<Recipe> recipes = RecipeData.getInstance().getRecipes();
		if (recipes == null || recipes.isEmpty())
		{
			LOGGER.warn("RecipeSearchEngine: No recipes found in RecipeData.");
			return;
		}

		_size = recipes.size();
		// Pad array length to multiple of 8 for clean loop unrolling
		int paddedLength = (_size + 7) & ~7;

		_recipeListIds = new int[paddedLength];
		_recipeItemIds = new int[paddedLength];
		_productItemIds = new int[paddedLength];
		_craftLevels = new int[paddedLength];
		_grades = new int[paddedLength];
		_isDwarven = new int[paddedLength];
		_successRates = new int[paddedLength];
		_mpCosts = new int[paddedLength];
		_productNames = new String[paddedLength];
		_icons = new String[paddedLength];
		_searchNamesLower = new String[paddedLength];
		_entries = new RecipeEntry[_size];

		int index = 0;
		for (Recipe recipe : recipes)
		{
			int rListId = recipe.id();
			int rItemId = recipe.recipeId();
			int pItemId = recipe.product() != null ? recipe.product().getId() : 0;
			int cLevel = recipe.level();
			boolean isDwarf = recipe.isDwarven();
			int sRate = recipe.successRate();
			int mp = recipe.mpCost();

			Item prodItem = ItemData.getInstance().getTemplate(pItemId);
			Item recipeItem = ItemData.getInstance().getTemplate(rItemId);

			CrystalType grade = prodItem != null ? prodItem.getCrystalType() : CrystalType.NONE;
			String pName = prodItem != null ? prodItem.getName() : (recipe.alias() != null ? recipe.alias() : "Unknown Recipe");
			String icon = prodItem != null ? prodItem.getIcon() : (recipeItem != null ? recipeItem.getIcon() : "icon.etc_recipe_white_i00");

			_recipeListIds[index] = rListId;
			_recipeItemIds[index] = rItemId;
			_productItemIds[index] = pItemId;
			_craftLevels[index] = cLevel;
			_grades[index] = grade.getId();
			_isDwarven[index] = isDwarf ? 1 : 0;
			_successRates[index] = sRate;
			_mpCosts[index] = mp;
			_productNames[index] = pName;
			_icons[index] = icon;
			_searchNamesLower[index] = (pName + " " + (recipeItem != null ? recipeItem.getName() : "")).toLowerCase();

			_entries[index] = new RecipeEntry(
				rListId,
				rItemId,
				pItemId,
				cLevel,
				grade,
				isDwarf,
				pName,
				icon,
				sRate,
				mp
			);
			index++;
		}

		_initialized = true;
		LOGGER.info("RecipeSearchEngine: indexed {} recipes in {} ms (Vector API available: {}).", _size, (System.currentTimeMillis() - start), VECTOR_API_AVAILABLE);
	}

	/**
	 * Fast Mechanical Sympathy query with grade, craftLevel and text filters.
	 * @param gradeFilter : -1 for all grades, or 0..5 (NONE, D, C, B, A, S)
	 * @param levelFilter : 0 for all craft levels, or 1..9
	 * @param query : search text (empty or space-separated terms)
	 * @return matching List of RecipeEntry
	 */
	public List<RecipeEntry> search(int gradeFilter, int levelFilter, String query)
	{
		if (!_initialized)
			initialize();

		if (_size == 0)
			return Collections.emptyList();

		final String[] tokens = (query != null && !query.trim().isEmpty())
			? query.toLowerCase().trim().split("\\s+")
			: null;

		final List<RecipeEntry> results = new ArrayList<>();

		if (VECTOR_API_AVAILABLE)
		{
			try
			{
				RecipeSimdHelper.searchSimd(_grades, _craftLevels, _size, gradeFilter, levelFilter, tokens, _searchNamesLower, _entries, results);
				return results;
			}
			catch (Throwable t)
			{
				results.clear();
			}
		}

		// High-speed scalar loop with unrolling and mechanical sympathy
		searchMechanicalSympathy(gradeFilter, levelFilter, tokens, results);
		return results;
	}

	private void searchMechanicalSympathy(int gradeFilter, int levelFilter, String[] tokens, List<RecipeEntry> results)
	{
		int i = 0;
		int bound = _size - (_size % 8);

		// Unroll 8 elements per iteration for optimal L1 cache line throughput
		for (; i < bound; i += 8)
		{
			checkLane(i, gradeFilter, levelFilter, tokens, results);
			checkLane(i + 1, gradeFilter, levelFilter, tokens, results);
			checkLane(i + 2, gradeFilter, levelFilter, tokens, results);
			checkLane(i + 3, gradeFilter, levelFilter, tokens, results);
			checkLane(i + 4, gradeFilter, levelFilter, tokens, results);
			checkLane(i + 5, gradeFilter, levelFilter, tokens, results);
			checkLane(i + 6, gradeFilter, levelFilter, tokens, results);
			checkLane(i + 7, gradeFilter, levelFilter, tokens, results);
		}

		// Tail
		for (; i < _size; i++)
		{
			checkLane(i, gradeFilter, levelFilter, tokens, results);
		}
	}

	private void checkLane(int idx, int gradeFilter, int levelFilter, String[] tokens, List<RecipeEntry> results)
	{
		if (gradeFilter >= 0 && _grades[idx] != gradeFilter)
			return;
		if (levelFilter > 0 && _craftLevels[idx] != levelFilter)
			return;

		if (matchesText(idx, tokens))
		{
			results.add(_entries[idx]);
		}
	}

	private boolean matchesText(int index, String[] tokens)
	{
		if (tokens == null || tokens.length == 0)
			return true;

		final String target = _searchNamesLower[index];
		for (String token : tokens)
		{
			if (!target.contains(token))
				return false;
		}
		return true;
	}

	public RecipeEntry getEntryByRecipeItemId(int recipeItemId)
	{
		if (!_initialized)
			initialize();

		for (int i = 0; i < _size; i++)
		{
			if (_recipeItemIds[i] == recipeItemId)
				return _entries[i];
		}
		return null;
	}

	public static RecipeSearchEngine getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder
	{
		protected static final RecipeSearchEngine INSTANCE = new RecipeSearchEngine();
	}
}
