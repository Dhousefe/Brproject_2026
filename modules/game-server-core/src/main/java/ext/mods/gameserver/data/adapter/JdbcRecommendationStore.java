package ext.mods.gameserver.data.adapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.RecommendationStore;

/** JDBC adapter for player recommendation persistence. */
public final class JdbcRecommendationStore implements RecommendationStore
{
	private static final String LOAD_GIVEN = "SELECT target_id FROM character_recommends WHERE char_id=?";
	private static final String INSERT_RECOMMENDATION = "INSERT INTO character_recommends (char_id,target_id) VALUES (?,?)";
	private static final String UPDATE_TARGET_HAVE = "UPDATE characters SET rec_have=? WHERE obj_Id=?";
	private static final String UPDATE_GIVER_LEFT = "UPDATE characters SET rec_left=? WHERE obj_Id=?";

	@Override
	public List<Integer> loadGivenRecommendations(int giverObjectId) throws SQLException
	{
		final List<Integer> recommendations = new ArrayList<>();
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(LOAD_GIVEN))
		{
			ps.setInt(1, giverObjectId);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					recommendations.add(rs.getInt("target_id"));
			}
		}
		return recommendations;
	}

	@Override
	public void addRecommendation(int giverObjectId, int targetObjectId, int targetRecomHave, int giverRecomLeft) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			final boolean previousAutoCommit = con.getAutoCommit();
			con.setAutoCommit(false);
			try
			{
				try (PreparedStatement ps = con.prepareStatement(INSERT_RECOMMENDATION))
				{
					ps.setInt(1, giverObjectId);
					ps.setInt(2, targetObjectId);
					ps.executeUpdate();
				}

				try (PreparedStatement ps = con.prepareStatement(UPDATE_TARGET_HAVE))
				{
					ps.setInt(1, targetRecomHave);
					ps.setInt(2, targetObjectId);
					ps.executeUpdate();
				}

				try (PreparedStatement ps = con.prepareStatement(UPDATE_GIVER_LEFT))
				{
					ps.setInt(1, giverRecomLeft);
					ps.setInt(2, giverObjectId);
					ps.executeUpdate();
				}

				con.commit();
			}
			catch (SQLException e)
			{
				try
				{
					con.rollback();
				}
				catch (SQLException rollbackFailure)
				{
					e.addSuppressed(rollbackFailure);
				}
				throw e;
			}
			finally
			{
				con.setAutoCommit(previousAutoCommit);
			}
		}
	}
}
