package ext.mods.gameserver.data.adapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import ext.mods.commons.jdbc.DatabaseDialect;
import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.ServerMemoStore;

/** JDBC adapter for the global server memo table. */
public final class JdbcServerMemoStore implements ServerMemoStore
{
	private static final String LOAD = "SELECT var,value FROM server_memo";
	private static final String DELETE = "DELETE FROM server_memo WHERE var=?";

	@Override
	public Map<String, String> load() throws SQLException
	{
		final Map<String, String> memos = new LinkedHashMap<>();
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(LOAD); ResultSet rs = ps.executeQuery())
		{
			while (rs.next())
				memos.put(rs.getString("var"), rs.getString("value"));
		}
		return memos;
	}

	@Override
	public void upsert(String key, String value) throws SQLException
	{
		final String sql = DatabaseDialect.upsert("server_memo", "var,value", "?,?", "var", "value");
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(sql))
		{
			ps.setString(1, key);
			ps.setString(2, value);
			ps.executeUpdate();
		}
	}

	@Override
	public void delete(String key) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(DELETE))
		{
			ps.setString(1, key);
			ps.executeUpdate();
		}
	}
}
