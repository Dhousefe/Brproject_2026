package ext.mods.loginserver.data.adapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import ext.mods.commons.logging.CLogger;
import ext.mods.commons.pool.ConnectionPool;
import ext.mods.loginserver.data.repository.AccountStore;
import ext.mods.loginserver.model.Account;

/** JDBC adapter for the login account persistence port. */
public final class JdbcAccountStore implements AccountStore
{
	private static final CLogger LOGGER = new CLogger(JdbcAccountStore.class.getName());

	private static final String SELECT_ACCOUNT = "SELECT password, access_level, last_server FROM accounts WHERE login = ?";
	private static final String INSERT_ACCOUNT = "INSERT INTO accounts (login, password, last_active) VALUES (?, ?, ?)";
	private static final String UPDATE_ACCOUNT_LAST_TIME = "UPDATE accounts SET last_active = ? WHERE login = ?";
	private static final String UPDATE_ACCOUNT_LAST_SERVER = "UPDATE accounts SET last_server = ? WHERE login = ?";
	private static final String UPDATE_ACCOUNT_ACCESS_LEVEL = "UPDATE accounts SET access_level = ? WHERE login = ?";
	private static final String COUNT_ACCOUNTS = "SELECT COUNT(*) FROM accounts";

	private static final String CREATE_EXTERNAL_IDENTITIES =
		"CREATE TABLE IF NOT EXISTS external_account_identities (" +
		"  provider VARCHAR(16) NOT NULL," +
		"  provider_user_id VARCHAR(20) NOT NULL," +
		"  account_login VARCHAR(45) NOT NULL," +
		"  created_at BIGINT NOT NULL," +
		"  PRIMARY KEY (provider, provider_user_id)," +
		"  CONSTRAINT external_account_identities_account_login_key UNIQUE (account_login)," +
		"  CONSTRAINT external_account_identities_account_login_fkey FOREIGN KEY (account_login) REFERENCES accounts (login) ON DELETE CASCADE" +
		")";

	private static final String SELECT_EXTERNAL_ACCOUNT =
		"SELECT a.login, a.password, a.access_level, a.last_server FROM external_account_identities e JOIN accounts a ON e.account_login = a.login WHERE e.provider = ? AND e.provider_user_id = ?";
	private static final String CHECK_EXTERNAL_IDENTITY =
		"SELECT 1 FROM external_account_identities WHERE provider = ? AND account_login = ?";
	private static final String CHECK_ACCOUNT_EXISTS =
		"SELECT 1 FROM accounts WHERE login = ?";
	private static final String INSERT_EXTERNAL_IDENTITY =
		"INSERT INTO external_account_identities (provider, provider_user_id, account_login, created_at) VALUES (?, ?, ?, ?)";

	public JdbcAccountStore()
	{
		initExternalIdentitiesTable();
	}

	private void initExternalIdentitiesTable()
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(CREATE_EXTERNAL_IDENTITIES))
		{
			ps.execute();
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to initialize external_account_identities table.", e);
		}
	}

	@Override
	public Account getAccount(String login)
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(SELECT_ACCOUNT))
		{
			ps.setString(1, login);
			try (ResultSet rs = ps.executeQuery())
			{
				if (rs.next())
					return new Account(login, rs.getString("password"), rs.getInt("access_level"), rs.getInt("last_server"));
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Exception retrieving account infos.", e);
		}
		return null;
	}

	@Override
	public Account createAccount(String login, String hashedPassword, long currentTime)
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(INSERT_ACCOUNT))
		{
			ps.setString(1, login);
			ps.setString(2, hashedPassword);
			ps.setLong(3, currentTime);
			ps.executeUpdate();
			return new Account(login, hashedPassword, 0, 1);
		}
		catch (Exception e)
		{
			LOGGER.error("Exception auto creating account for {}.", e, login);
			return null;
		}
	}

	@Override
	public boolean setAccountLastTime(String login, long currentTime)
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(UPDATE_ACCOUNT_LAST_TIME))
		{
			ps.setLong(1, currentTime);
			ps.setString(2, login);
			return ps.executeUpdate() > 0;
		}
		catch (Exception e)
		{
			LOGGER.error("Exception updating last access for {}.", e, login);
			return false;
		}
	}

	@Override
	public void setAccountAccessLevel(String login, int level)
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(UPDATE_ACCOUNT_ACCESS_LEVEL))
		{
			ps.setInt(1, level);
			ps.setString(2, login);
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't set access level {} for {}.", e, level, login);
		}
	}

	@Override
	public void setAccountLastServer(String login, int serverId)
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(UPDATE_ACCOUNT_LAST_SERVER))
		{
			ps.setInt(1, serverId);
			ps.setString(2, login);
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't set last server.", e);
		}
	}

	@Override
	public int getAccountCount()
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(COUNT_ACCOUNTS); ResultSet rs = ps.executeQuery())
		{
			return rs.next() ? rs.getInt(1) : 0;
		}
		catch (Exception e)
		{
			LOGGER.error("Could not count accounts.", e);
			return 0;
		}
	}

	@Override
	public boolean hasExternalIdentity(String provider, String login)
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(CHECK_EXTERNAL_IDENTITY))
		{
			ps.setString(1, provider);
			ps.setString(2, login);
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next();
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Error checking external identity for {} / {}.", e, provider, login);
			return false;
		}
	}

	@Override
	public Account getExternalAccount(String provider, String providerUserId)
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(SELECT_EXTERNAL_ACCOUNT))
		{
			ps.setString(1, provider);
			ps.setString(2, providerUserId);
			try (ResultSet rs = ps.executeQuery())
			{
				if (rs.next())
					return new Account(rs.getString("login"), rs.getString("password"), rs.getInt("access_level"), rs.getInt("last_server"));
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Error retrieving external account for {} / {}.", e, provider, providerUserId);
		}
		return null;
	}

	@Override
	public ExternalAuthResolution resolveOrCreateExternalAccount(String provider, String providerUserId, String derivedLogin)
	{
		final Account existing = getExternalAccount(provider, providerUserId);
		if (existing != null)
			return ExternalAuthResolution.success(existing);

		try (Connection con = ConnectionPool.getConnection())
		{
			try (PreparedStatement psCheck = con.prepareStatement(CHECK_ACCOUNT_EXISTS))
			{
				psCheck.setString(1, derivedLogin);
				try (ResultSet rs = psCheck.executeQuery())
				{
					if (rs.next())
					{
						LOGGER.warn("Account conflict: derived login '{}' already exists without mapping to {} / {}.", derivedLogin, provider, providerUserId);
						return ExternalAuthResolution.conflict();
					}
				}
			}

			final boolean previousAutoCommit = con.getAutoCommit();
			con.setAutoCommit(false);
			try
			{
				final long currentTime = System.currentTimeMillis();
				final String unusablePassword = "DISCORD_AUTH_" + java.util.UUID.randomUUID();

				try (PreparedStatement psAccount = con.prepareStatement(INSERT_ACCOUNT))
				{
					psAccount.setString(1, derivedLogin);
					psAccount.setString(2, unusablePassword);
					psAccount.setLong(3, currentTime);
					psAccount.executeUpdate();
				}

				try (PreparedStatement psExternal = con.prepareStatement(INSERT_EXTERNAL_IDENTITY))
				{
					psExternal.setString(1, provider);
					psExternal.setString(2, providerUserId);
					psExternal.setString(3, derivedLogin);
					psExternal.setLong(4, currentTime);
					psExternal.executeUpdate();
				}

				con.commit();
				LOGGER.info("Successfully provisioned new external account '{}' for {} / {}.", derivedLogin, provider, providerUserId);
				return ExternalAuthResolution.success(new Account(derivedLogin, unusablePassword, 0, 1));
			}
			catch (Exception e)
			{
				con.rollback();
				LOGGER.warn("Concurrent creation or database error for {} / {}: {}. Attempting re-read.", provider, providerUserId, e.getMessage());
				final Account reRead = getExternalAccount(provider, providerUserId);
				return reRead != null ? ExternalAuthResolution.success(reRead) : ExternalAuthResolution.creationFailed();
			}
			finally
			{
				con.setAutoCommit(previousAutoCommit);
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Database connection error while resolving external account for {} / {}.", e, provider, providerUserId);
			return ExternalAuthResolution.creationFailed();
		}
	}
}
