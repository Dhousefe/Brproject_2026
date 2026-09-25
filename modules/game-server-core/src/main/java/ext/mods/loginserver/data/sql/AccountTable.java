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

import ext.mods.loginserver.data.adapter.JdbcAccountStore;
import ext.mods.loginserver.data.repository.AccountStore;
import ext.mods.loginserver.model.Account;

/**
 * This class controls all generated {@link Account}s.
 */
public class AccountTable
{
	private final AccountStore _store;

	protected AccountTable()
	{
		this(new JdbcAccountStore());
	}

	AccountTable(AccountStore store)
	{
		_store = store;
	}
	
	/**
	 * @param login : The {@link String} used as login.
	 * @return A new {@link Account} whom informations are already registered into the database, or null if not existing.
	 */
	public Account getAccount(String login)
	{
		return _store.getAccount(login);
	}
	
	/**
	 * @param login : The {@link String} used as login.
	 * @param hashed : The {@link String} used as hashed password.
	 * @param currentTime : The creation timestamp of the newly generated {@link Account}.
	 * @return A new {@link Account} whom informations are saved into the database, or null if a problem occured.
	 */
	public Account createAccount(String login, String hashed, long currentTime)
	{
		return _store.createAccount(login, hashed, currentTime);
	}
	
	/**
	 * @param login : The {@link String} used as login.
	 * @param currentTime : The timestamp to refresh this {@link Account} with.
	 * @return True if the given {@link Account} last_active timestamp has been correctly refreshed on the database, false otherwise.
	 */
	public boolean setAccountLastTime(String login, long currentTime)
	{
		return _store.setAccountLastTime(login, currentTime);
	}
	
	/**
	 * Refresh access_level value of an {@link Account} on the database.
	 * @param login : The {@link String} used as login.
	 * @param level : The new level to set.
	 */
	public void setAccountAccessLevel(String login, int level)
	{
		_store.setAccountAccessLevel(login, level);
	}
	
	/**
	 * Refresh last_server value of an {@link Account} on the database.
	 * @param login : The {@link String} used as login.
	 * @param serverId : The serverId to set.
	 */
	public void setAccountLastServer(String login, int serverId)
	{
		_store.setAccountLastServer(login, serverId);
	}
	
	
	public int getAccountCount()
	{
		return _store.getAccountCount();
	}
	
	public boolean hasExternalIdentity(String provider, String login)
	{
		return _store.hasExternalIdentity(provider, login);
	}
	
	public Account getExternalAccount(String provider, String providerUserId)
	{
		return _store.getExternalAccount(provider, providerUserId);
	}
	
	public AccountStore.ExternalAuthResolution resolveOrCreateExternalAccount(String provider, String providerUserId, String derivedLogin)
	{
		return _store.resolveOrCreateExternalAccount(provider, providerUserId, derivedLogin);
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
