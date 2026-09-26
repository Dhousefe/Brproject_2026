package ext.mods.gameserver.data.adapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import ext.mods.commons.jdbc.DatabaseDialect;
import ext.mods.commons.logging.CLogger;
import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.ClanHallAuctionStore;

/** JDBC adapter for Clan Hall auction persistence. */
public final class JdbcClanHallAuctionStore implements ClanHallAuctionStore
{
	private static final CLogger LOGGER = new CLogger(JdbcClanHallAuctionStore.class.getName());

	private static final String LOAD_BIDS = "SELECT bidder_name, clan_oid, clan_name, max_bid, time_bid FROM auctions WHERE clanhall_id=? ORDER BY max_bid DESC";
	private static final String UPDATE_DATE = "UPDATE clanhall SET endDate=? WHERE id=?";
	private static final String DELETE_BIDS = "DELETE FROM auctions WHERE clanhall_id=?";
	private static final String DELETE_BID = "DELETE FROM auctions WHERE clanhall_id=? AND clan_oid=?";
	private static final String UPDATE_SELLER = "UPDATE clanhall SET sellerBid=?, sellerName=?, sellerClanName=?, endDate=? WHERE id=?";

	@Override
	public List<BidRecord> loadBids(int clanHallId)
	{
		final List<BidRecord> bids = new ArrayList<>();
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(LOAD_BIDS))
		{
			ps.setInt(1, clanHallId);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					bids.add(new BidRecord(clanHallId, rs.getString("bidder_name"), rs.getInt("clan_oid"), rs.getString("clan_name"), rs.getInt("max_bid"), rs.getLong("time_bid")));
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to load Clan Hall auction bids for {}.", e, clanHallId);
		}
		return bids;
	}

	@Override
	public void updateEndDate(int clanHallId, long endDate)
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(UPDATE_DATE))
		{
			ps.setLong(1, endDate);
			ps.setInt(2, clanHallId);
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to update Clan Hall auction end date for {}.", e, clanHallId);
		}
	}

	@Override
	public void deleteBids(int clanHallId)
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(DELETE_BIDS))
		{
			ps.setInt(1, clanHallId);
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to delete Clan Hall auction bids for {}.", e, clanHallId);
		}
	}

	@Override
	public void deleteBid(int clanHallId, int clanId)
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(DELETE_BID))
		{
			ps.setInt(1, clanHallId);
			ps.setInt(2, clanId);
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to delete Clan Hall auction bid for clan {}.", e, clanId);
		}
	}

	@Override
	public void saveBid(BidRecord bid)
	{
		final String sql = DatabaseDialect.upsert("auctions", "clanhall_id,bidder_name,clan_oid,clan_name,max_bid,time_bid", "?,?,?,?,?,?", "clanhall_id,clan_oid", "bidder_name,clan_name,max_bid,time_bid");
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(sql))
		{
			ps.setInt(1, bid.clanHallId());
			ps.setString(2, bid.bidderName());
			ps.setInt(3, bid.clanId());
			ps.setString(4, bid.clanName());
			ps.setInt(5, bid.bid());
			ps.setLong(6, bid.bidTime());
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to save Clan Hall auction bid for clan {}.", e, bid.clanId());
		}
	}

	@Override
	public void updateSeller(SellerRecord seller)
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(UPDATE_SELLER))
		{
			ps.setInt(1, seller.bid());
			ps.setString(2, seller.sellerName());
			ps.setString(3, seller.sellerClanName());
			ps.setLong(4, seller.endDate());
			ps.setInt(5, seller.clanHallId());
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to update Clan Hall auction seller for {}.", e, seller.clanHallId());
		}
	}
}
