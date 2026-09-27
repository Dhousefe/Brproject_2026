package ext.mods.gameserver.data.repository;

import java.sql.SQLException;

/**
 * Persistence contract for character lifecycle operations.
 *
 * <p>Implementations own SQL, dialect details and transaction boundaries.
 * Callers should use this port for deletion scheduling, restoration and full
 * character deletion; they must not depend on a JDBC connection directly.</p>
 */
public interface CharacterStore
{
	/**
	 * Returns the clan id currently stored for a character, or {@code 0} when
	 * the character is not a clan member.
	 */
	int findClanId(int objectId) throws SQLException;

	/** Updates the deletion timestamp for a character. */
	void updateDeleteTime(int objectId, long deleteTime) throws SQLException;

	/**
	 * Permanently deletes a character and all owned persistent records as one
	 * database transaction.
	 */
	void deleteCharacter(int objectId) throws SQLException;
}
