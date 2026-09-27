package ext.mods.gameserver.data.repository;

import java.sql.SQLException;
import java.util.Optional;

/** Persistence contract for account premium state. */
public interface PremiumStore
{
	/** Creates or updates the premium state for an account. */
	void upsert(String accountName, int premiumService, long endDate) throws SQLException;

	/** Returns the current premium state, when the account has a row. */
	Optional<PremiumRecord> find(String accountName) throws SQLException;

	/** Clears an existing premium state without creating a missing row. */
	void expire(String accountName) throws SQLException;

	/** Removes the premium row for an account. */
	void delete(String accountName) throws SQLException;

	record PremiumRecord(int premiumService, long endDate)
	{
	}
}
