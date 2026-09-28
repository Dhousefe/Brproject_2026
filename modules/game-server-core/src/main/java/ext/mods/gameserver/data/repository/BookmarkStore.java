package ext.mods.gameserver.data.repository;

import java.sql.SQLException;
import java.util.List;

import ext.mods.gameserver.model.records.Bookmark;

/** Persistence port for player bookmarks. */
public interface BookmarkStore
{
	List<Bookmark> load() throws SQLException;

	void save(Bookmark bookmark) throws SQLException;

	void delete(String name, int objectId) throws SQLException;
}
