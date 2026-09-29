package ext.mods.gameserver.data.repository;

import java.sql.SQLException;
import java.util.List;

/** Persistence port for offline trade and manufacture state. */
public interface OfflineTraderStore
{
	void replaceAll(List<OfflineTraderData> traders) throws SQLException;

	List<OfflineTraderData> loadAll() throws SQLException;

	void clear() throws SQLException;
}
