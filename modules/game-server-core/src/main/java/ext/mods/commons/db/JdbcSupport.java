package ext.mods.commons.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import ext.mods.commons.logging.CLogger;
import ext.mods.commons.pool.ConnectionPool;

/**
 * Small helpers around {@link ConnectionPool} for Phase 5 repositories.
 */
public final class JdbcSupport
{
	private static final CLogger LOGGER = new CLogger(JdbcSupport.class.getName());
	
	private JdbcSupport()
	{
	}
	
	@FunctionalInterface
	public interface SqlConsumer
	{
		void accept(Connection connection) throws SQLException;
	}
	
	@FunctionalInterface
	public interface SqlFunction<R>
	{
		R apply(Connection connection) throws SQLException;
	}
	
	@FunctionalInterface
	public interface RowMapper<T>
	{
		T map(ResultSet rs) throws SQLException;
	}
	
	public static void run(SqlConsumer consumer)
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			consumer.accept(con);
		}
		catch (SQLException e)
		{
			LOGGER.error("JDBC run failed: {}", e, e.getMessage());
			throw new DbException("JDBC run failed", e);
		}
	}
	
	public static <R> R call(SqlFunction<R> function)
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			return function.apply(con);
		}
		catch (SQLException e)
		{
			LOGGER.error("JDBC call failed: {}", e, e.getMessage());
			throw new DbException("JDBC call failed", e);
		}
	}
	
	public static <T> Optional<T> queryOne(String sql, StatementBinder binder, RowMapper<T> mapper)
	{
		return call(con ->
		{
			try (PreparedStatement ps = con.prepareStatement(sql))
			{
				if (binder != null)
					binder.bind(ps);
				try (ResultSet rs = ps.executeQuery())
				{
					if (!rs.next())
						return Optional.empty();
					return Optional.ofNullable(mapper.map(rs));
				}
			}
		});
	}
	
	public static <T> List<T> queryList(String sql, StatementBinder binder, RowMapper<T> mapper)
	{
		return call(con ->
		{
			final List<T> list = new ArrayList<>();
			try (PreparedStatement ps = con.prepareStatement(sql))
			{
				if (binder != null)
					binder.bind(ps);
				try (ResultSet rs = ps.executeQuery())
				{
					while (rs.next())
						list.add(mapper.map(rs));
				}
			}
			return list;
		});
	}
	
	public static int update(String sql, StatementBinder binder)
	{
		return call(con ->
		{
			try (PreparedStatement ps = con.prepareStatement(sql))
			{
				if (binder != null)
					binder.bind(ps);
				return ps.executeUpdate();
			}
		});
	}
	
	@FunctionalInterface
	public interface StatementBinder
	{
		void bind(PreparedStatement ps) throws SQLException;
	}
}
