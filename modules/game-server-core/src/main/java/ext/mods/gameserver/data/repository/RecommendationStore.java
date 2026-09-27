package ext.mods.gameserver.data.repository;

import java.sql.SQLException;
import java.util.List;

/** Persistence contract for player recommendation state. */
public interface RecommendationStore
{
	/** Loads the character ids recommended by the giver. */
	List<Integer> loadGivenRecommendations(int giverObjectId) throws SQLException;

	/**
	 * Records a recommendation and updates both affected counters atomically.
	 */
	void addRecommendation(int giverObjectId, int targetObjectId, int targetRecomHave, int giverRecomLeft) throws SQLException;
}
