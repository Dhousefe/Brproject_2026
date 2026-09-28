package ext.mods.gameserver.data.service;

import java.sql.SQLException;
import java.util.List;

import ext.mods.gameserver.data.adapter.JdbcCharacterSelectionStore;
import ext.mods.gameserver.data.repository.CharacterSelection;
import ext.mods.gameserver.data.repository.CharacterSelectionStore;

/** Application boundary for the character-selection read model. */
public final class CharacterSelectionPersistenceService
{
	private static final CharacterSelectionStore STORE = new JdbcCharacterSelectionStore();

	private CharacterSelectionPersistenceService()
	{
	}

	public static List<CharacterSelection> findByAccount(String accountName) throws SQLException
	{
		return STORE.findByAccount(accountName);
	}
}
