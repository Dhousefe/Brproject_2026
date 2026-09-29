package ext.mods.gameserver.data.adapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.OfflineTradeItem;
import ext.mods.gameserver.data.repository.OfflineTraderData;
import ext.mods.gameserver.data.repository.OfflineTraderStore;

/** JDBC adapter for offline trade and manufacture state. */
public final class JdbcOfflineTraderStore implements OfflineTraderStore
{
	private static final String CLEAR_STATUS = "DELETE FROM character_offline_trade";
	private static final String CLEAR_ITEMS = "DELETE FROM character_offline_trade_items";
	private static final String SAVE_STATUS = "INSERT INTO character_offline_trade (charId,time,type,title) VALUES (?,?,?,?)";
	private static final String SAVE_ITEM = "INSERT INTO character_offline_trade_items (charId,item,count,price,enchant) VALUES (?,?,?,?,?)";
	private static final String LOAD_STATUS = "SELECT charId,time,type,title FROM character_offline_trade";
	private static final String LOAD_ITEMS = "SELECT item,count,price,enchant FROM character_offline_trade_items WHERE charId=?";

	@Override
	public void replaceAll(List<OfflineTraderData> traders) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			final boolean previousAutoCommit = con.getAutoCommit();
			con.setAutoCommit(false);
			try (PreparedStatement clearStatus = con.prepareStatement(CLEAR_STATUS); PreparedStatement clearItems = con.prepareStatement(CLEAR_ITEMS); PreparedStatement saveStatus = con.prepareStatement(SAVE_STATUS); PreparedStatement saveItem = con.prepareStatement(SAVE_ITEM))
			{
				clearItems.executeUpdate();
				clearStatus.executeUpdate();

				for (OfflineTraderData trader : traders)
				{
					saveStatus.setInt(1, trader.characterId());
					saveStatus.setLong(2, trader.time());
					saveStatus.setInt(3, trader.type());
					saveStatus.setString(4, trader.title());
					saveStatus.executeUpdate();

					for (OfflineTradeItem item : trader.items())
					{
						saveItem.setInt(1, trader.characterId());
						saveItem.setInt(2, item.item());
						saveItem.setLong(3, item.count());
						saveItem.setLong(4, item.price());
						saveItem.setLong(5, item.enchant());
						saveItem.addBatch();
					}
					saveItem.executeBatch();
				}
				con.commit();
			}
			catch (SQLException e)
			{
				try
				{
					con.rollback();
				}
				catch (SQLException rollbackFailure)
				{
					e.addSuppressed(rollbackFailure);
				}
				throw e;
			}
			finally
			{
				con.setAutoCommit(previousAutoCommit);
			}
		}
	}

	@Override
	public List<OfflineTraderData> loadAll() throws SQLException
	{
		final List<OfflineTraderData> traders = new ArrayList<>();
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement status = con.prepareStatement(LOAD_STATUS); PreparedStatement items = con.prepareStatement(LOAD_ITEMS); ResultSet rs = status.executeQuery())
		{
			while (rs.next())
			{
				final int characterId = rs.getInt("charId");
				items.setInt(1, characterId);
				final List<OfflineTradeItem> tradeItems = new ArrayList<>();
				try (ResultSet item = items.executeQuery())
				{
					while (item.next())
						tradeItems.add(new OfflineTradeItem(item.getInt("item"), item.getLong("count"), item.getLong("price"), item.getLong("enchant")));
				}
				traders.add(new OfflineTraderData(characterId, rs.getLong("time"), rs.getInt("type"), rs.getString("title"), tradeItems));
			}
		}
		return traders;
	}

	@Override
	public void clear() throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement clearStatus = con.prepareStatement(CLEAR_STATUS); PreparedStatement clearItems = con.prepareStatement(CLEAR_ITEMS))
		{
			clearItems.executeUpdate();
			clearStatus.executeUpdate();
		}
	}
}
