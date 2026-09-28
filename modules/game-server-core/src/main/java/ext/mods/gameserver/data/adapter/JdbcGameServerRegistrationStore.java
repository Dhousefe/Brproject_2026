package ext.mods.gameserver.data.adapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import ext.mods.commons.jdbc.DatabaseDialect;
import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.GameServerRegistration;
import ext.mods.gameserver.data.repository.GameServerRegistrationStore;

/** JDBC adapter for the LoginServer gameserver registration table. */
public final class JdbcGameServerRegistrationStore implements GameServerRegistrationStore
{
	private static final String LOAD = "SELECT server_id,hexid,host FROM gameservers";
	private static final String DELETE = "DELETE FROM gameservers WHERE server_id=?";
	private static final String DELETE_ALL = "DELETE FROM gameservers";

	@Override
	public List<GameServerRegistration> load() throws SQLException
	{
		final List<GameServerRegistration> registrations = new ArrayList<>();
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(LOAD); ResultSet rs = ps.executeQuery())
		{
			while (rs.next())
				registrations.add(new GameServerRegistration(rs.getInt("server_id"), rs.getString("hexid"), rs.getString("host")));
		}
		return registrations;
	}

	@Override
	public void save(GameServerRegistration registration) throws SQLException
	{
		final String sql = DatabaseDialect.upsert("gameservers", "server_id,hexid,host", "?,?,?", "server_id", "hexid,host");
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(sql))
		{
			ps.setInt(1, registration.serverId());
			ps.setString(2, registration.hexId());
			ps.setString(3, registration.host());
			ps.executeUpdate();
		}
	}

	@Override
	public void delete(int serverId) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(DELETE))
		{
			ps.setInt(1, serverId);
			ps.executeUpdate();
		}
	}

	@Override
	public void deleteAll() throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(DELETE_ALL))
		{
			ps.executeUpdate();
		}
	}
}
