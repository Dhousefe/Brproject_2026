/*
* Copyleft © 2024-2026 L2Brproject
* * This file is part of L2Brproject derived from aCis409/RusaCis3.8
* * L2Brproject is free software: you can redistribute it and/or modify it
* under the terms of the GNU General Public License as published by the
* Free Software Foundation, either version 3 of the License.
* * L2Brproject is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* General Public License for more details.
* * You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
* Our main Developers, Dhousefe-L2JBR, Agazes33, Ban-L2jDev, Warman, SrEli.
* Our special thanks, Nattan Felipe, Diego Fonseca, Junin, ColdPlay, Denky, MecBew, Localhost, MundvayneHELLBOY, 
* SonecaL2, Eduardo.SilvaL2J, biLL, xpower, xTech, kakuzo, Tiagorosendo, Schuster, LucasStark, damedd
* as a contribution for the forum L2JBrasil.com
 */
package ext.mods.commons.pool;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicLong;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import ext.mods.commons.jdbc.DatabaseDriverResolver;
import ext.mods.commons.jdbc.DatabaseDriverResolver.DatabaseConfig;
import ext.mods.commons.jdbc.SqlDialect;
import ext.mods.commons.jdbc.SupportedDatabase;
import ext.mods.commons.logging.CLogger;

/**
 * Shared HikariCP pool. Credentials are injected — no dependency on {@code ext.mods.Config}.
 */
public final class ConnectionPool
{
	private ConnectionPool()
	{
		throw new IllegalStateException("Utility class");
	}
	
	private static final CLogger LOGGER = new CLogger(ConnectionPool.class.getName());
	private static final AtomicLong TOTAL_QUERIES = new AtomicLong(0);
	
	private static HikariDataSource _source;
	
	private static final boolean DEBUG_QUERIES = false;
	private static final long SLOW_QUERY_THRESHOLD_MS = 50;
	
	/**
	 * Initialize the pool with explicit JDBC credentials (preferred).
	 */
	public static void init(String jdbcUrl, String username, String password)
	{
		init(jdbcUrl, username, password, "BrProjectPool");
	}
	
	public static void init(String jdbcUrl, String username, String password, String poolName)
	{
		try
		{
			// Dynamic database driver detection
			final DatabaseConfig dbConfig = DatabaseDriverResolver.resolve(jdbcUrl, username, password);
			jdbcUrl = dbConfig.url();
			username = dbConfig.user();
			password = dbConfig.password();

			HikariConfig config = new HikariConfig();

			config.setJdbcUrl(jdbcUrl);
			config.setUsername(username);
			config.setPassword(password);

			config.addDataSourceProperty("cachePrepStmts", "true");
			config.addDataSourceProperty("prepStmtCacheSize", "250");
			config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
			config.addDataSourceProperty("useServerPrepStmts", "true");
			config.addDataSourceProperty("useLocalSessionState", "true");
			config.addDataSourceProperty("rewriteBatchedStatements", "true");
			config.addDataSourceProperty("cacheResultSetMetadata", "true");
			config.addDataSourceProperty("cacheServerConfiguration", "true");
			config.addDataSourceProperty("elideSetAutoCommits", "true");
			config.addDataSourceProperty("maintainTimeStats", "false");

			config.setMaximumPoolSize(dbConfig.maxPoolSize());
			config.setMinimumIdle(dbConfig.minIdle());
			config.setIdleTimeout(300000);
			config.setMaxLifetime(1800000);

			config.setConnectionTestQuery(dbConfig.testQuery());
			config.setLeakDetectionThreshold(60000);
			config.setPoolName(poolName != null && !poolName.isBlank() ? poolName : "BrProjectPool");
			config.setRegisterMbeans(true);

			// SQLite: WAL mode for concurrent reads, pool=3 to avoid deadlock during parallel boot
			if (dbConfig.isSqlite())
			{
				config.setMaximumPoolSize(3);
				config.setMinimumIdle(1);
				config.setConnectionTestQuery("SELECT 1");
				config.setConnectionInitSql("PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;");
				SqlDialect.setActiveDatabase(SupportedDatabase.SQLITE);
				LOGGER.info("SQLite mode: WAL + pool=3 (file: " + jdbcUrl + ")");
			}
			else
			{
				SqlDialect.setActiveDatabase(dbConfig.database());
			}

			_source = new HikariDataSource(config);

			LOGGER.info("Database: " + dbConfig.database().getDisplayName() + " | Pool: " + poolName + " | Max: " + config.getMaximumPoolSize());
			if (DEBUG_QUERIES)
			{
				LOGGER.warn("HikariCP Query Debugging is ENABLED. This may impact performance.");
			}
		}
		catch (Exception e)
		{
			LOGGER.error("HikariCP failed!", e);
		}
	}
	
	/**
	 * @deprecated Prefer {@link #init(String, String, String)}. Kept for binary compatibility;
	 *             looks up {@code -Dbrproject.db.url|user|password} or env {@code DB_URL|DB_USER|DB_PASSWORD}.
	 */
	@Deprecated
	public static void init()
	{
		final String url = firstNonBlank(System.getProperty("brproject.db.url"), System.getenv("DB_URL"));
		final String user = firstNonBlank(System.getProperty("brproject.db.user"), System.getenv("DB_USER"));
		final String pass = firstNonBlank(System.getProperty("brproject.db.password"), System.getenv("DB_PASSWORD"));
		if (url == null || user == null)
		{
			LOGGER.error("ConnectionPool.init() without credentials: set init(url,user,pass) or -Dbrproject.db.* / DB_* env.");
			return;
		}
		init(url, user, pass != null ? pass : "");
	}
	
	private static String firstNonBlank(String a, String b)
	{
		if (a != null && !a.isBlank())
			return a;
		if (b != null && !b.isBlank())
			return b;
		return null;
	}
	
	public static void shutdown()
	{
		if (_source != null)
		{
			_source.close();
			_source = null;
		}
	}
	
	public static Connection getConnection() throws SQLException
	{
		Connection conn = _source.getConnection();
		TOTAL_QUERIES.incrementAndGet();

		if (DEBUG_QUERIES)
		{
			return wrapConnection(conn);
		}
		if (SqlDialect.isSqlite())
		{
			return wrapSqliteDialect(conn);
		}
		return conn;
	}
	
	public static long getTotalQueries()
	{
		return TOTAL_QUERIES.get();
	}
	
	public static String getStats()
	{
		if (_source == null)
			return "HikariCP: not initialized";
		try
		{
			var mx = _source.getHikariPoolMXBean();
			if (mx != null)
			{
				return String.format("HikariCP: %d active | %d idle | %d total | %d queries", mx.getActiveConnections(), mx.getIdleConnections(), mx.getTotalConnections(), TOTAL_QUERIES.get());
			}
		}
		catch (Exception ignored)
		{
		}
		return String.format("HikariCP: %d queries total", TOTAL_QUERIES.get());
	}
	
	private static Connection wrapConnection(final Connection realConnection)
	{
		return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]
		{
			Connection.class
		}, new InvocationHandler()
		{
			@Override
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
			{
				String methodName = method.getName();
				
				if ("prepareStatement".equals(methodName) && args != null && args.length > 0)
				{
					String sql = (String) args[0];
					
					LOGGER.info("QUERY PREPARED: " + sql);
					
					PreparedStatement ps = (PreparedStatement) method.invoke(realConnection, args);
					return wrapPreparedStatement(ps, sql);
				}
				
				if ("createStatement".equals(methodName))
				{
					Statement st = (Statement) method.invoke(realConnection, args);
					return wrapStatement(st);
				}
				
				return method.invoke(realConnection, args);
			}
		});
	}
	
	private static PreparedStatement wrapPreparedStatement(final PreparedStatement realPs, final String sql)
	{
		return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[]
		{
			PreparedStatement.class
		}, new InvocationHandler()
		{
			@Override
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
			{
				String methodName = method.getName();
				
				if ("execute".equals(methodName) || "executeQuery".equals(methodName) || "executeUpdate".equals(methodName) || "executeBatch".equals(methodName))
				{
					long start = System.currentTimeMillis();
					try
					{
						return method.invoke(realPs, args);
					}
					finally
					{
						long duration = System.currentTimeMillis() - start;
						if (duration > SLOW_QUERY_THRESHOLD_MS)
						{
							LOGGER.warn("SLOW QUERY (" + duration + "ms): " + sql);
						}
					}
				}
				return method.invoke(realPs, args);
			}
		});
	}
	
	private static Statement wrapStatement(final Statement realSt)
	{
		return (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(), new Class<?>[]
		{
			Statement.class
		}, new InvocationHandler()
		{
			@Override
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
			{
				String methodName = method.getName();
				
				if (("execute".equals(methodName) || "executeQuery".equals(methodName) || "executeUpdate".equals(methodName)) && args != null && args.length > 0)
				{
					String sql = (String) args[0];
					long start = System.currentTimeMillis();
					try
					{
						LOGGER.info("DIRECT QUERY: " + sql);
						return method.invoke(realSt, args);
					}
					finally
					{
						long duration = System.currentTimeMillis() - start;
						if (duration > SLOW_QUERY_THRESHOLD_MS)
						{
							LOGGER.warn("SLOW DIRECT QUERY (" + duration + "ms): " + sql);
						}
					}
				}
				return method.invoke(realSt, args);
			}
		});
	}

	/**
	 * Wraps a Connection so that prepareStatement() and createStatement().execute*()
	 * transparently rewrite MySQL/MariaDB SQL to SQLite-compatible syntax.
	 */
	private static Connection wrapSqliteDialect(final Connection realConnection)
	{
		return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] { Connection.class },
			(proxy, method, args) ->
			{
				String methodName = method.getName();

				if ("prepareStatement".equals(methodName) && args != null && args.length > 0 && args[0] instanceof String)
				{
					args[0] = SqlDialect.adapt((String) args[0]);
				}

				Object result = method.invoke(realConnection, args);

				if ("createStatement".equals(methodName) && result instanceof Statement)
				{
					return wrapSqliteStatement((Statement) result);
				}
				return result;
			});
	}

	private static Statement wrapSqliteStatement(final Statement realSt)
	{
		return (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(), new Class<?>[] { Statement.class },
			(proxy, method, args) ->
			{
				String methodName = method.getName();
				if (("execute".equals(methodName) || "executeQuery".equals(methodName) || "executeUpdate".equals(methodName))
					&& args != null && args.length > 0 && args[0] instanceof String)
				{
					args[0] = SqlDialect.adapt((String) args[0]);
				}
				return method.invoke(realSt, args);
			});
	}
}
