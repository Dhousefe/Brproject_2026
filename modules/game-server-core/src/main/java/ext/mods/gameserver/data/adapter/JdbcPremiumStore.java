package ext.mods.gameserver.data.adapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import ext.mods.commons.jdbc.DatabaseDialect;
import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.PremiumStore;

/** JDBC adapter for account premium state. */
public final class JdbcPremiumStore implements PremiumStore
{
	private static final String FIND = "SELECT premium_service,enddate FROM account_premium WHERE account_name=?";
	private static final String EXPIRE = "UPDATE account_premium SET premium_service=?,enddate=? WHERE account_name=?";
	private static final String DELETE = "DELETE FROM account_premium WHERE account_name=?";

	@Override
	public void upsert(String accountName, int premiumService, long endDate) throws SQLException
	{
		final String sql = DatabaseDialect.upsert("account_premium", "account_name,premium_service,enddate", "?,?,?", "account_name", "premium_service,enddate");
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(sql))
		{
			ps.setString(1, accountName);
			ps.setInt(2, premiumService);
			ps.setLong(3, endDate);
			ps.executeUpdate();
		}
	}

	@Override
	public Optional<PremiumRecord> find(String accountName) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(FIND))
		{
			ps.setString(1, accountName);
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next() ? Optional.of(new PremiumRecord(rs.getInt("premium_service"), rs.getLong("enddate"))) : Optional.empty();
			}
		}
	}

	@Override
	public void expire(String accountName) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(EXPIRE))
		{
			ps.setInt(1, 0);
			ps.setLong(2, 0);
			ps.setString(3, accountName);
			ps.executeUpdate();
		}
	}

	@Override
	public void delete(String accountName) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(DELETE))
		{
			ps.setString(1, accountName);
			ps.executeUpdate();
		}
	}
}
