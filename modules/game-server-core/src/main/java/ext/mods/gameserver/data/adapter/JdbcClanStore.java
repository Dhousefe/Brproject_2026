package ext.mods.gameserver.data.adapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import ext.mods.commons.jdbc.DatabaseDialect;
import ext.mods.commons.logging.CLogger;
import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.ClanStore;

/** JDBC adapter for the clan persistence port. */
public final class JdbcClanStore implements ClanStore
{
	private static final CLogger LOGGER = new CLogger(JdbcClanStore.class.getName());

	private static final String LOAD_CLANS = "SELECT * FROM clan_data";
	private static final String DELETE_CLAN = "DELETE FROM clan_data WHERE clan_id=?";
	private static final String DELETE_CLAN_PRIVS = "DELETE FROM clan_privs WHERE clan_id=?";
	private static final String DELETE_CLAN_SKILLS = "DELETE FROM clan_skills WHERE clan_id=?";
	private static final String DELETE_CLAN_SUBPLEDGES = "DELETE FROM clan_subpledges WHERE clan_id=?";
	private static final String DELETE_CLAN_WARS = "DELETE FROM clan_wars WHERE clan1=? OR clan2=?";
	private static final String DELETE_CLAN_SIEGES = "DELETE FROM siege_clans WHERE clan_id=?";
	private static final String RESET_CASTLE_TAX = "UPDATE castle SET currentTaxPercent=0, nextTaxPercent=0 WHERE id=?";

	private static final String UPDATE_WAR_TIME = "UPDATE clan_wars SET expiry_time=? WHERE clan1=? AND clan2=?";
	private static final String DELETE_WAR = "DELETE FROM clan_wars WHERE clan1=? AND clan2=?";
	private static final String DELETE_OLD_WARS = "DELETE FROM clan_wars WHERE expiry_time > 0 AND expiry_time <= ?";
	private static final String LOAD_WARS = "SELECT * FROM clan_wars";
	private static final String LOAD_RANK = "SELECT clan_id FROM clan_data ORDER BY reputation_score DESC LIMIT ?";
	private static final String CLEAR_GRADUATES = "UPDATE clan_data SET graduates=NULL";

	@Override
	public List<ClanRecord> loadClans()
	{
		final List<ClanRecord> clans = new ArrayList<>();
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(LOAD_CLANS); ResultSet rs = ps.executeQuery())
		{
			while (rs.next())
			{
				clans.add(new ClanRecord(rs.getInt("clan_id"), rs.getInt("leader_id"), rs.getString("clan_name"), rs.getInt("clan_level"), rs.getInt("hasCastle"),
					rs.getInt("ally_id"), rs.getString("ally_name"), rs.getLong("ally_penalty_expiry_time"), rs.getInt("ally_penalty_type"),
					rs.getLong("char_penalty_expiry_time"), rs.getLong("dissolving_expiry_time"), rs.getInt("crest_id"), rs.getInt("crest_large_id"),
					rs.getInt("ally_crest_id"), rs.getInt("reputation_score"), rs.getInt("auction_bid_at"), rs.getInt("new_leader_id"),
					rs.getString("notice"), rs.getBoolean("enabled"), rs.getString("introduction"), rs.getString("graduates")));
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't load clans.", e);
		}
		return clans;
	}

	@Override
	public List<WarRecord> loadWars(long now)
	{
		final List<WarRecord> wars = new ArrayList<>();
		try (Connection con = ConnectionPool.getConnection())
		{
			try (PreparedStatement ps = con.prepareStatement(DELETE_OLD_WARS))
			{
				ps.setLong(1, now);
				ps.executeUpdate();
			}

			try (PreparedStatement ps = con.prepareStatement(LOAD_WARS); ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					wars.add(new WarRecord(rs.getInt("clan1"), rs.getInt("clan2"), rs.getLong("expiry_time")));
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't load clan wars.", e);
		}
		return wars;
	}

	@Override
	public void saveWar(int clanId1, int clanId2)
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(DatabaseDialect.upsert("clan_wars", "clan1,clan2", "?,?", "clan1,clan2", "clan1")))
		{
			ps.setString(1, Integer.toString(clanId1));
			ps.setString(2, Integer.toString(clanId2));
			ps.execute();
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't save clan war.", e);
		}
	}

	@Override
	public void updateWarExpiry(int clanId1, int clanId2, long expiryTime)
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(UPDATE_WAR_TIME))
		{
			ps.setLong(1, expiryTime);
			ps.setString(2, Integer.toString(clanId1));
			ps.setString(3, Integer.toString(clanId2));
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't update clan war expiry.", e);
		}
	}

	@Override
	public void deleteWar(int clanId1, int clanId2)
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(DELETE_WAR))
		{
			ps.setString(1, Integer.toString(clanId1));
			ps.setString(2, Integer.toString(clanId2));
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't delete clan war.", e);
		}
	}

	@Override
	public void deleteClan(ClanDeletion deletion)
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			final boolean previousAutoCommit = con.getAutoCommit();
			con.setAutoCommit(false);
			try
			{
				executeDelete(con, DELETE_CLAN, deletion.clanId());
				executeDelete(con, DELETE_CLAN_PRIVS, deletion.clanId());
				executeDelete(con, DELETE_CLAN_SKILLS, deletion.clanId());
				executeDelete(con, DELETE_CLAN_SUBPLEDGES, deletion.clanId());
				executeDeleteStrings(con, DELETE_CLAN_WARS, Integer.toString(deletion.clanId()), Integer.toString(deletion.clanId()));
				executeDelete(con, DELETE_CLAN_SIEGES, deletion.clanId());
				if (deletion.castleId() != 0)
					executeDelete(con, RESET_CASTLE_TAX, deletion.castleId());
				con.commit();
			}
			catch (Exception e)
			{
				con.rollback();
				throw e;
			}
			finally
			{
				con.setAutoCommit(previousAutoCommit);
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't delete clan {}.", e, deletion.clanId());
		}
	}

	private static void executeDelete(Connection con, String sql, int... values) throws Exception
	{
		try (PreparedStatement ps = con.prepareStatement(sql))
		{
			for (int i = 0; i < values.length; i++)
				ps.setInt(i + 1, values[i]);
			ps.executeUpdate();
		}
	}

	private static void executeDeleteStrings(Connection con, String sql, String... values) throws Exception
	{
		try (PreparedStatement ps = con.prepareStatement(sql))
		{
			for (int i = 0; i < values.length; i++)
				ps.setString(i + 1, values[i]);
			ps.executeUpdate();
		}
	}

	@Override
	public List<Integer> loadRankedClanIds(int limit)
	{
		final List<Integer> clanIds = new ArrayList<>();
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(LOAD_RANK))
		{
			ps.setInt(1, limit);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					clanIds.add(rs.getInt("clan_id"));
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't load clan ladder.", e);
		}
		return clanIds;
	}

	@Override
	public void clearGraduates()
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(CLEAR_GRADUATES))
		{
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't clear clan graduates.", e);
		}
	}
}
