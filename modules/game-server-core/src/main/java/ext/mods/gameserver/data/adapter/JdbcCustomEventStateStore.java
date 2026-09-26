package ext.mods.gameserver.data.adapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import ext.mods.commons.jdbc.DatabaseDialect;
import ext.mods.commons.logging.CLogger;
import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.CustomEventStateStore;

/** JDBC adapter for the custom event state persistence port. */
public final class JdbcCustomEventStateStore implements CustomEventStateStore
{
	private static final CLogger LOGGER = new CLogger(JdbcCustomEventStateStore.class.getName());

	private static final String LOAD_STATUS = "SELECT status FROM events_custom_data WHERE event_name = ?";

	private static String saveStatusSql()
	{
		return DatabaseDialect.upsert("events_custom_data", "event_name,status", "?,?", "event_name", "status");
	}

	@Override
	public boolean isEnabled(String eventName)
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(LOAD_STATUS))
		{
			ps.setString(1, eventName);
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next() && rs.getInt("status") > 0;
			}
		}
		catch (Exception e)
		{
			LOGGER.warn("Failed to load custom event state for " + eventName + ".", e);
			return false;
		}
	}

	@Override
	public void setEnabled(String eventName, boolean enabled)
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(saveStatusSql()))
		{
			ps.setString(1, eventName);
			ps.setInt(2, enabled ? 1 : 0);
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			LOGGER.warn("Failed to save custom event state for " + eventName + ".", e);
		}
	}
}
