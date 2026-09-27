package ext.mods.gameserver.data.adapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import ext.mods.commons.logging.CLogger;
import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.BufferSchemeStore;

/** JDBC adapter for player-owned NPC buffer schemes. */
public final class JdbcBufferSchemeStore implements BufferSchemeStore
{
	private static final CLogger LOGGER = new CLogger(JdbcBufferSchemeStore.class.getName());

	private static final String LOAD_SCHEMES = "SELECT object_id,scheme_name,skills,levels FROM buffer_schemes";
	private static final String CLEAR_SCHEMES = "DELETE FROM buffer_schemes";
	private static final String INSERT_SCHEME = "INSERT INTO buffer_schemes (object_id,scheme_name,skills,levels) VALUES (?,?,?,?)";

	@Override
	public List<SchemeRecord> loadSchemes()
	{
		final List<SchemeRecord> schemes = new ArrayList<>();
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(LOAD_SCHEMES); ResultSet rs = ps.executeQuery())
		{
			while (rs.next())
				schemes.add(new SchemeRecord(rs.getInt("object_id"), rs.getString("scheme_name"), rs.getString("skills"), rs.getString("levels")));
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't load buffer schemes.", e);
		}
		return schemes;
	}

	@Override
	public void replaceSchemes(List<SchemeRecord> schemes)
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			final boolean previousAutoCommit = con.getAutoCommit();
			con.setAutoCommit(false);
			try
			{
				try (PreparedStatement clear = con.prepareStatement(CLEAR_SCHEMES))
				{
					clear.executeUpdate();
				}

				try (PreparedStatement insert = con.prepareStatement(INSERT_SCHEME))
				{
					for (SchemeRecord scheme : schemes)
					{
						insert.setInt(1, scheme.playerId());
						insert.setString(2, scheme.name());
						insert.setString(3, scheme.skills());
						insert.setString(4, scheme.levels());
						insert.addBatch();
					}
					insert.executeBatch();
				}
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
			LOGGER.error("Couldn't replace buffer schemes.", e);
		}
	}
}
