package ext.mods.gameserver.data.repository;

import java.sql.SQLException;
import java.util.Map;

/** Persistence port for global server variables. */
public interface ServerMemoStore
{
	Map<String, String> load() throws SQLException;

	void upsert(String key, String value) throws SQLException;

	void delete(String key) throws SQLException;
}
