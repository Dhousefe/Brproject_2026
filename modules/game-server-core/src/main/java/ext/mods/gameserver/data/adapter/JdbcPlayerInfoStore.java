package ext.mods.gameserver.data.adapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.PlayerDirectoryEntry;
import ext.mods.gameserver.data.repository.PlayerInfoStore;

/** JDBC adapter for the character directory read model. */
public final class JdbcPlayerInfoStore implements PlayerInfoStore
{
	private static final String LOAD = "SELECT account_name,obj_Id,char_name,accesslevel FROM characters";

	@Override
	public List<PlayerDirectoryEntry> load() throws SQLException
	{
		final List<PlayerDirectoryEntry> entries = new ArrayList<>();
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(LOAD); ResultSet rs = ps.executeQuery())
		{
			while (rs.next())
				entries.add(new PlayerDirectoryEntry(rs.getInt("obj_Id"), rs.getString("account_name"), rs.getString("char_name"), rs.getInt("accesslevel")));
		}
		return entries;
	}
}
