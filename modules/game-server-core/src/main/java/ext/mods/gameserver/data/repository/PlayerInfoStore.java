package ext.mods.gameserver.data.repository;

import java.sql.SQLException;
import java.util.List;

/** Persistence port for the character directory cache. */
public interface PlayerInfoStore
{
	List<PlayerDirectoryEntry> load() throws SQLException;
}
