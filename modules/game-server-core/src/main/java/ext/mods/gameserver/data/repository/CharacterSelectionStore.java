package ext.mods.gameserver.data.repository;

import java.sql.SQLException;
import java.util.List;

/** Persistence port for the account character-selection read model. */
public interface CharacterSelectionStore
{
	/** Loads all selectable characters for an account, including visual state. */
	List<CharacterSelection> findByAccount(String accountName) throws SQLException;
}
