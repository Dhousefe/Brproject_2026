package ext.mods.gameserver.data.service;

import java.sql.SQLException;

import ext.mods.extensions.listener.manager.GameListenerManager;
import ext.mods.gameserver.data.adapter.JdbcCharacterStore;
import ext.mods.gameserver.data.repository.CharacterStore;
import ext.mods.gameserver.data.sql.PlayerInfoTable;

/**
 * Application service for character lifecycle operations.
 *
 * <p>It keeps cache/listener side effects outside the JDBC adapter while the
 * adapter owns the atomic database transaction.</p>
 */
public final class CharacterLifecycleService
{
	private static final CharacterStore STORE = new JdbcCharacterStore();

	private CharacterLifecycleService()
	{
	}

	public static int findClanId(int objectId) throws SQLException
	{
		return STORE.findClanId(objectId);
	}

	public static void updateDeleteTime(int objectId, long deleteTime) throws SQLException
	{
		STORE.updateDeleteTime(objectId, deleteTime);
	}

	public static void deleteCharacter(int objectId) throws SQLException
	{
		if (objectId < 0)
			return;

		STORE.deleteCharacter(objectId);
		PlayerInfoTable.getInstance().removePlayer(objectId);
		GameListenerManager.getInstance().notifyCharacterDelete(objectId);
	}
}
