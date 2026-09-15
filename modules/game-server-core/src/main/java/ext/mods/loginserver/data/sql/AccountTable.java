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
package ext.mods.loginserver.data.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import ext.mods.commons.logging.CLogger;
import ext.mods.commons.pool.ConnectionPool;

import ext.mods.loginserver.model.Account;

/**
 * This class controls all generated {@link Account}s.
 */
public class AccountTable
{
	private static final CLogger LOGGER = new CLogger(AccountTable.class.getName());
	
	private static final String SELECT_ACCOUNT = "SELECT password, access_level, last_server FROM accounts WHERE login = ?";
	private static final String INSERT_ACCOUNT = "INSERT INTO accounts (login, password, last_active) VALUES (?, ?, ?)";
	private static final String UPDATE_ACCOUNT_LAST_TIME = "UPDATE accounts SET last_active = ? WHERE login = ?";
	private static final String UPDATE_ACCOUNT_LAST_SERVER = "UPDATE accounts SET last_server = ? WHERE login = ?";
	private static final String UPDATE_ACCOUNT_ACCESS_LEVEL = "UPDATE accounts SET access_level = ? WHERE login = ?";
	
	private static final String COUNT_ACCOUNTS = "SELECT COUNT(*) FROM accounts";
	
	private static final String CREATE_EXTERNAL_IDENTITIES = 
		"CREATE TABLE IF NOT EXISTS `external_account_identities` (" +
		"  `provider` VARCHAR(16) NOT NULL," +
		"  `provider_user_id` VARCHAR(20) NOT NULL," +
		"  `account_login` VARCHAR(45) NOT NULL," +
		"  `created_at` BIGINT NOT NULL," +
		"  PRIMARY KEY (`provider`, `provider_user_id`)," +
		"  CONSTRAINT `external_account_identities_account_login_key` UNIQUE (`account_login`)," +
		"  CONSTRAINT `external_account_identities_account_login_fkey` FOREIGN KEY (`account_login`) REFERENCES `accounts` (`login`) ON DELETE CASCADE" +
		")";
	
	private static final String SELECT_EXTERNAL_ACCOUNT = 
		"SELECT a.login, a.password, a.access_level, a.last_server FROM external_account_identities e JOIN accounts a ON e.account_login = a.login WHERE e.provider = ? AND e.provider_user_id = ?";

	private static final String CHECK_EXTERNAL_IDENTITY = 
		"SELECT 1 FROM external_account_identities WHERE provider = ? AND account_login = ?";

	private static final String CHECK_ACCOUNT_EXISTS = 
		"SELECT 1 FROM accounts WHERE login = ?";

	private static final String INSERT_EXTERNAL_IDENTITY = 
		"INSERT INTO external_account_identities (provider, provider_user_id, account_login, created_at) VALUES (?, ?, ?, ?)";
	
	public enum ExternalAuthStatus
	{
		SUCCESS,
		CONFLICT,
		CREATION_FAILED
	}
	
	public static final class ExternalAuthResolution
	{
		private final ExternalAuthStatus _status;
		private final Account _account;
		
		private ExternalAuthResolution(ExternalAuthStatus status, Account account)
		{
			_status = status;
			_account = account;
		}
		
		public static ExternalAuthResolution success(Account account)
		{
			return new ExternalAuthResolution(ExternalAuthStatus.SUCCESS, account);
		}
		
		public static ExternalAuthResolution conflict()
		{
			return new ExternalAuthResolution(ExternalAuthStatus.CONFLICT, null);
		}
		
		public static ExternalAuthResolution creationFailed()
		{
			return new ExternalAuthResolution(ExternalAuthStatus.CREATION_FAILED, null);
		}
		
		public ExternalAuthStatus getStatus()
		{
			return _status;
		}
		
		public Account getAccount()
		{
			return _account;
		}
	}
	
	protected AccountTable()
	{
		initExternalIdentitiesTable();
	}
	
	private void initExternalIdentitiesTable()
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(CREATE_EXTERNAL_IDENTITIES))
		{
			ps.execute();
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to initialize external_account_identities table.", e);
		}
	}
	
	/**
	 * @param login : The {@link String} used as login.
	 * @return A new {@link Account} whom informations are already registered into the database, or null if not existing.
	 */
	public Account getAccount(String login)
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(SELECT_ACCOUNT))
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
	
	/**
	 * @param login : The {@link String} used as login.
	 * @param hashed : The {@link String} used as hashed password.
	 * @param currentTime : The creation timestamp of the newly generated {@link Account}.
	 * @return A new {@link Account} whom informations are saved into the database, or null if a problem occured.
	 */
	public Account createAccount(String login, String hashed, long currentTime)
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(INSERT_ACCOUNT))
		{
			ps.setString(1, login);
			ps.setString(2, hashed);
			ps.setLong(3, currentTime);
			ps.execute();
		}
		catch (Exception e)
		{
			LOGGER.error("Exception auto creating account for {}.", e, login);
			return null;
		}
		
		return new Account(login, hashed, 0, 1);
	}
	
	/**
	 * @param login : The {@link String} used as login.
	 * @param currentTime : The timestamp to refresh this {@link Account} with.
	 * @return True if the given {@link Account} last_active timestamp has been correctly refreshed on the database, false otherwise.
	 */
	public boolean setAccountLastTime(String login, long currentTime)
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(UPDATE_ACCOUNT_LAST_TIME))
		{
			ps.setLong(1, currentTime);
			ps.setString(2, login);
			ps.execute();
		}
		catch (Exception e)
		{
			LOGGER.error("Exception auto creating account for {}.", e, login);
			return false;
		}
		return true;
	}
	
	/**
	 * Refresh access_level value of an {@link Account} on the database.
	 * @param login : The {@link String} used as login.
	 * @param level : The new level to set.
	 */
	public void setAccountAccessLevel(String login, int level)
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(UPDATE_ACCOUNT_ACCESS_LEVEL))
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
	
	/**
	 * Refresh last_server value of an {@link Account} on the database.
	 * @param login : The {@link String} used as login.
	 * @param serverId : The serverId to set.
	 */
	public void setAccountLastServer(String login, int serverId)
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(UPDATE_ACCOUNT_LAST_SERVER))
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
	
	
	public int getAccountCount()
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(COUNT_ACCOUNTS);
			ResultSet rs = ps.executeQuery())
		{
			if (rs.next())
				return rs.getInt(1);
		}
		catch (Exception e)
		{
			LOGGER.error("Could not count accounts.", e);
		}
		return 0;
	}
	
	public boolean hasExternalIdentity(String provider, String login)
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(CHECK_EXTERNAL_IDENTITY))
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
	
	public Account getExternalAccount(String provider, String providerUserId)
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(SELECT_EXTERNAL_ACCOUNT))
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
	
	public ExternalAuthResolution resolveOrCreateExternalAccount(String provider, String providerUserId, String derivedLogin)
	{
		// 1. Check if mapping already exists
		final Account existing = getExternalAccount(provider, providerUserId);
		if (existing != null)
			return ExternalAuthResolution.success(existing);
		
		// 2. Check if derived login is already taken without mapping (conflict 0x04)
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
			
			// 3. Create account and mapping inside a transaction
			final boolean prevAutoCommit = con.getAutoCommit();
			con.setAutoCommit(false);
			try
			{
				final long currentTime = System.currentTimeMillis();
				final String unusablePassword = "DISCORD_AUTH_" + java.util.UUID.randomUUID();
				
				try (PreparedStatement psAcc = con.prepareStatement(INSERT_ACCOUNT))
				{
					psAcc.setString(1, derivedLogin);
					psAcc.setString(2, unusablePassword);
					psAcc.setLong(3, currentTime);
					psAcc.executeUpdate();
				}
				
				try (PreparedStatement psExt = con.prepareStatement(INSERT_EXTERNAL_IDENTITY))
				{
					psExt.setString(1, provider);
					psExt.setString(2, providerUserId);
					psExt.setString(3, derivedLogin);
					psExt.setLong(4, currentTime);
					psExt.executeUpdate();
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
				if (reRead != null)
					return ExternalAuthResolution.success(reRead);
				return ExternalAuthResolution.creationFailed();
			}
			finally
			{
				con.setAutoCommit(prevAutoCommit);
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Database connection error while resolving external account for {} / {}.", e, provider, providerUserId);
			return ExternalAuthResolution.creationFailed();
		}
	}
	
	public static AccountTable getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final AccountTable INSTANCE = new AccountTable();
	}
}