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

import java.util.List;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorSpecies;

import ext.mods.gameserver.data.manager.RecipeSearchEngine.RecipeEntry;

/**
 * Dhousefe - L2JBR 22/10/2026
 */
final class RecipeSimdHelper
{
	private static final VectorSpecies<Integer> SPECIES = IntVector.SPECIES_256;

	private RecipeSimdHelper()
	{
	}

	public static void searchSimd(
		int[] grades,
		int[] craftLevels,
		int size,
		int gradeFilter,
		int levelFilter,
		String[] tokens,
		String[] searchNamesLower,
		RecipeEntry[] entries,
		List<RecipeEntry> results
	)
	{
		int vectorBound = SPECIES.loopBound(size);
		int i = 0;

		IntVector vGradeExpected = gradeFilter >= 0 ? IntVector.broadcast(SPECIES, gradeFilter) : null;
		IntVector vLevelExpected = levelFilter > 0 ? IntVector.broadcast(SPECIES, levelFilter) : null;

		for (; i < vectorBound; i += SPECIES.length())
		{
			VectorMask<Integer> mask = SPECIES.maskAll(true);

			if (vGradeExpected != null)
			{
				IntVector vGrades = IntVector.fromArray(SPECIES, grades, i);
				mask = mask.and(vGrades.compare(jdk.incubator.vector.VectorOperators.EQ, vGradeExpected));
			}

			if (vLevelExpected != null && mask.anyTrue())
			{
				IntVector vLevels = IntVector.fromArray(SPECIES, craftLevels, i);
				mask = mask.and(vLevels.compare(jdk.incubator.vector.VectorOperators.EQ, vLevelExpected));
			}

			if (!mask.anyTrue())
				continue;

			long maskBits = mask.toLong();
			for (int lane = 0; lane < SPECIES.length(); lane++)
			{
				if ((maskBits & (1L << lane)) != 0L)
				{
					int idx = i + lane;
					if (matchesText(idx, tokens, searchNamesLower))
					{
						results.add(entries[idx]);
					}
				}
			}
		}

		// Tail loop
		for (; i < size; i++)
		{
			if (gradeFilter >= 0 && grades[i] != gradeFilter)
				continue;
			if (levelFilter > 0 && craftLevels[i] != levelFilter)
				continue;

			if (matchesText(i, tokens, searchNamesLower))
			{
				results.add(entries[i]);
			}
		}
	}

	private static boolean matchesText(int index, String[] tokens, String[] searchNamesLower)
	{
		if (tokens == null || tokens.length == 0)
			return true;

		final String target = searchNamesLower[index];
		for (String token : tokens)
		{
			if (!target.contains(token))
				return false;
		}
		return true;
	}
}
