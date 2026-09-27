package ext.mods.gameserver.data.repository;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

/** Persistence contract for player subclass slots. */
public interface SubclassStore
{
	/** Loads all subclass slots for a character in slot order. */
	List<SubclassRecord> load(int characterObjectId) throws SQLException;

	/** Persists the current state of all subclass slots. */
	void update(int characterObjectId, Collection<SubclassRecord> subclasses) throws SQLException;

	/** Adds one subclass slot. */
	void add(int characterObjectId, SubclassRecord subclass) throws SQLException;

	/** Wipes one subclass slot and all class-indexed state atomically. */
	void wipe(int characterObjectId, int classIndex) throws SQLException;
}
