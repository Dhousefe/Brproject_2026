package ext.mods.gameserver.data.repository;

import java.sql.SQLException;
import java.util.List;

/** Persistence port for registered gameserver identities. */
public interface GameServerRegistrationStore
{
	List<GameServerRegistration> load() throws SQLException;

	void save(GameServerRegistration registration) throws SQLException;

	void delete(int serverId) throws SQLException;

	void deleteAll() throws SQLException;
}
