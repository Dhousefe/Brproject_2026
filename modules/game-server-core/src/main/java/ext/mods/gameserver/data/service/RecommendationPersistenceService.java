package ext.mods.gameserver.data.service;

import java.sql.SQLException;
import java.util.List;

import ext.mods.gameserver.data.adapter.JdbcRecommendationStore;
import ext.mods.gameserver.data.repository.RecommendationStore;

/** Application boundary for player recommendation persistence. */
public final class RecommendationPersistenceService
{
	private static final RecommendationStore STORE = new JdbcRecommendationStore();

	private RecommendationPersistenceService()
	{
	}

	public static List<Integer> loadGivenRecommendations(int giverObjectId) throws SQLException
	{
		return STORE.loadGivenRecommendations(giverObjectId);
	}

	public static void addRecommendation(int giverObjectId, int targetObjectId, int targetRecomHave, int giverRecomLeft) throws SQLException
	{
		STORE.addRecommendation(giverObjectId, targetObjectId, targetRecomHave, giverRecomLeft);
	}
}
