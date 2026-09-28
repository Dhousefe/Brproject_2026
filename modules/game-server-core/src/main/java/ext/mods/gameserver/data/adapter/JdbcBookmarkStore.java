package ext.mods.gameserver.data.adapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.BookmarkStore;
import ext.mods.gameserver.model.records.Bookmark;

/** JDBC adapter for player bookmarks. */
public final class JdbcBookmarkStore implements BookmarkStore
{
	private static final String LOAD = "SELECT name,obj_Id,x,y,z FROM bookmarks";
	private static final String SAVE = "INSERT INTO bookmarks (name,obj_Id,x,y,z) VALUES (?,?,?,?,?)";
	private static final String DELETE = "DELETE FROM bookmarks WHERE name=? AND obj_Id=?";

	@Override
	public List<Bookmark> load() throws SQLException
	{
		final List<Bookmark> bookmarks = new ArrayList<>();
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(LOAD); ResultSet rs = ps.executeQuery())
		{
			while (rs.next())
				bookmarks.add(new Bookmark(rs.getString("name"), rs.getInt("obj_Id"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z")));
		}
		return bookmarks;
	}

	@Override
	public void save(Bookmark bookmark) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(SAVE))
		{
			ps.setString(1, bookmark.name());
			ps.setInt(2, bookmark.id());
			ps.setInt(3, bookmark.x());
			ps.setInt(4, bookmark.y());
			ps.setInt(5, bookmark.z());
			ps.executeUpdate();
		}
	}

	@Override
	public void delete(String name, int objectId) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(DELETE))
		{
			ps.setString(1, name);
			ps.setInt(2, objectId);
			ps.executeUpdate();
		}
	}
}
