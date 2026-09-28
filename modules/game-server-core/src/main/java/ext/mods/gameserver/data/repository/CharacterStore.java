package ext.mods.gameserver.data.repository;

import java.sql.SQLException;
import java.util.List;

/**
 * Persistence contract for character lifecycle operations.
 *
 * <p>Implementations own SQL, dialect details and transaction boundaries.
 * Callers should use this port for deletion scheduling, restoration and full
 * character deletion; they must not depend on a JDBC connection directly.</p>
 */
public interface CharacterStore
{
	/** Inserts the initial characters row for a newly created player. */
	void insert(CharacterState state) throws SQLException;

	/** Updates the mutable characters row during autosave/logout. */
	void update(CharacterState state) throws SQLException;

	/** Updates only connection status and the last access timestamp. */
	void updateOnlineStatus(int objectId, int online, long lastAccess) throws SQLException;

	/** Updates only the noblesse flag. */
	void updateNobless(int objectId, boolean noble) throws SQLException;

	/** Restores the basic character row without exposing JDBC to game logic. */
	CharacterState restore(int objectId) throws SQLException;

	/** Returns the other characters belonging to an account. */
	List<CharacterSummary> findAccountCharacters(String accountName, int excludedObjectId) throws SQLException;

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
