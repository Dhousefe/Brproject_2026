package ext.mods.gameserver.data.service;

import java.sql.SQLException;
import java.util.List;

import ext.mods.gameserver.data.adapter.JdbcGameServerRegistrationStore;
import ext.mods.gameserver.data.repository.GameServerRegistration;
import ext.mods.gameserver.data.repository.GameServerRegistrationStore;

/** Application boundary for LoginServer gameserver registration persistence. */
public final class GameServerRegistrationPersistenceService
{
	private static final GameServerRegistrationStore STORE = new JdbcGameServerRegistrationStore();

	private GameServerRegistrationPersistenceService()
	{
	}

	public static List<GameServerRegistration> load() throws SQLException
	{
		return STORE.load();
	}

	public static void save(GameServerRegistration registration) throws SQLException
	{
		STORE.save(registration);
	}

	public static void delete(int serverId) throws SQLException
	{
		STORE.delete(serverId);
	}

	public static void deleteAll() throws SQLException
	{
		STORE.deleteAll();
	}
}
