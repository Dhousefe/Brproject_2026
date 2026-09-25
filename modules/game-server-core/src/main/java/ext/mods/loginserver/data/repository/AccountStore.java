package ext.mods.loginserver.data.repository;

import ext.mods.loginserver.model.Account;

/**
 * Persistence port for login accounts.
 *
 * <p>The login flow depends on this contract instead of knowing which JDBC
 * adapter or database vendor stores the account.</p>
 */
public interface AccountStore
{
	Account getAccount(String login);

	Account createAccount(String login, String hashedPassword, long currentTime);

	boolean setAccountLastTime(String login, long currentTime);

	void setAccountAccessLevel(String login, int level);

	void setAccountLastServer(String login, int serverId);

	int getAccountCount();

	boolean hasExternalIdentity(String provider, String login);

	Account getExternalAccount(String provider, String providerUserId);

	ExternalAuthResolution resolveOrCreateExternalAccount(String provider, String providerUserId, String derivedLogin);

	enum ExternalAuthStatus
	{
		SUCCESS,
		CONFLICT,
		CREATION_FAILED
	}

	final class ExternalAuthResolution
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
}
