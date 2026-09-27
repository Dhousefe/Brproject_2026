package ext.mods.gameserver.data.service;

import java.sql.SQLException;
import java.util.Optional;

import ext.mods.gameserver.data.adapter.JdbcPremiumStore;
import ext.mods.gameserver.data.repository.PremiumStore;

/** Application boundary for account premium persistence. */
public final class PremiumPersistenceService
{
	private static final PremiumStore STORE = new JdbcPremiumStore();

	private PremiumPersistenceService()
	{
	}

	public static void upsert(String accountName, int premiumService, long endDate) throws SQLException
	{
		STORE.upsert(accountName, premiumService, endDate);
	}

	public static Optional<PremiumStore.PremiumRecord> find(String accountName) throws SQLException
	{
		return STORE.find(accountName);
	}

	public static void expire(String accountName) throws SQLException
	{
		STORE.expire(accountName);
	}

	public static void delete(String accountName) throws SQLException
	{
		STORE.delete(accountName);
	}
}
