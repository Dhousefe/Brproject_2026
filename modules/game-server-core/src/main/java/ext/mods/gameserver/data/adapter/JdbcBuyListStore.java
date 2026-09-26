package ext.mods.gameserver.data.adapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import ext.mods.commons.jdbc.DatabaseDialect;
import ext.mods.commons.logging.CLogger;
import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.BuyListStore;

/** JDBC adapter for limited-stock NPC buy list persistence. */
public final class JdbcBuyListStore implements BuyListStore
{
	private static final CLogger LOGGER = new CLogger(JdbcBuyListStore.class.getName());

	private static final String LOAD_RESTOCKS = "SELECT buylist_id, item_id, count, next_restock_time FROM buylists";
	private static final String DELETE_RESTOCK = "DELETE FROM buylists WHERE buylist_id=? AND item_id=?";

	private static String saveRestockSql()
	{
		return DatabaseDialect.upsert("buylists", "buylist_id,item_id,count,next_restock_time", "?,?,?,?", "buylist_id,item_id", "count,next_restock_time");
	}

	@Override
	public List<RestockRecord> loadRestocks()
	{
		final List<RestockRecord> restocks = new ArrayList<>();
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(LOAD_RESTOCKS); ResultSet rs = ps.executeQuery())
		{
			while (rs.next())
				restocks.add(new RestockRecord(rs.getInt("buylist_id"), rs.getInt("item_id"), rs.getInt("count"), rs.getLong("next_restock_time")));
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to load NPC buy list restocks.", e);
		}
		return restocks;
	}

	@Override
	public void saveRestock(RestockRecord restock)
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(saveRestockSql()))
		{
			ps.setInt(1, restock.buyListId());
			ps.setInt(2, restock.itemId());
			ps.setInt(3, restock.count());
			ps.setLong(4, restock.nextRestockTime());
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to save NPC buy list restock.", e);
		}
	}

	@Override
	public void deleteRestock(int buyListId, int itemId)
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(DELETE_RESTOCK))
		{
			ps.setInt(1, buyListId);
			ps.setInt(2, itemId);
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to delete NPC buy list restock.", e);
		}
	}
}
