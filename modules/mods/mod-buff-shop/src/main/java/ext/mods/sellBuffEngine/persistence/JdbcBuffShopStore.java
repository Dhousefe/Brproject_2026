package ext.mods.sellBuffEngine.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import ext.mods.commons.jdbc.DatabaseDialect;
import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.enums.actors.Sex;
import ext.mods.gameserver.model.actor.container.player.Appearance;
import ext.mods.sellBuffEngine.ShopObject;

/** JDBC adapter for player-owned buff shops. */
public final class JdbcBuffShopStore implements BuffShopStore
{
	private static final Logger LOGGER = Logger.getLogger(JdbcBuffShopStore.class.getName());

	private static final String SAVE_SHOP = DatabaseDialect.upsert("buffshop", "ownerId,buffs,title,store_message,x,y,z,heading,class_id,sex,face,hair_style,hair_color,equipped_items", "?,?,?,?,?,?,?,?,?,?,?,?,?,?", "ownerId", "buffs,title,store_message,x,y,z,heading,class_id,sex,face,hair_style,hair_color,equipped_items");
	private static final String LOAD_SHOPS = "SELECT ownerId,buffs,title,store_message,x,y,z,heading,class_id,sex,face,hair_style,hair_color,equipped_items FROM buffshop";
	private static final String REMOVE_SHOP = "DELETE FROM buffshop WHERE ownerId=?";
	private static final String REWARD_OFFLINE_OWNER = "UPDATE items SET count=count+? WHERE item_id=57 AND owner_id=?";

	@Override
	public void saveShop(ShopObject shop)
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(SAVE_SHOP))
		{
			ps.setInt(1, shop.getOwnerId());
			ps.setString(2, shop.getBuffLine());
			ps.setString(3, shop.getTitle());
			ps.setString(4, shop.getStoreMessage());
			ps.setInt(5, shop.getX());
			ps.setInt(6, shop.getY());
			ps.setInt(7, shop.getZ());
			ps.setInt(8, shop.getHeading());
			ps.setInt(9, shop.getClassId());
			ps.setInt(10, shop.getAppearance().getSex().ordinal());
			ps.setInt(11, shop.getAppearance().getFace());
			ps.setInt(12, shop.getAppearance().getHairStyle());
			ps.setInt(13, shop.getAppearance().getHairColor());
			ps.setString(14, String.join(",", shop.getEquippedItems().stream().map(String::valueOf).toList()));
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.SEVERE, "Error saving buff shop for owner " + shop.getOwnerId(), e);
		}
	}

	@Override
	public List<ShopObject> loadShops()
	{
		final List<ShopObject> shops = new ArrayList<>();
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(LOAD_SHOPS); ResultSet rs = ps.executeQuery())
		{
			while (rs.next())
			{
				final ShopObject shop = new ShopObject(rs.getInt("ownerId"));
				shop.setTitle(rs.getString("title"));
				shop.setStoreMessage(rs.getString("store_message"));
				shop.setXYZ(rs.getInt("x"), rs.getInt("y"), rs.getInt("z"), rs.getInt("heading"));
				shop.setClassId(rs.getInt("class_id"));
				shop.setAppearance(new Appearance((byte) rs.getInt("face"), (byte) rs.getInt("hair_color"), (byte) rs.getInt("hair_style"), Sex.VALUES[rs.getInt("sex")]));

				final String equippedItems = rs.getString("equipped_items");
				final List<Integer> items = new ArrayList<>();
				if (equippedItems != null && !equippedItems.isEmpty())
				{
					for (String itemId : equippedItems.split(","))
						if (!itemId.trim().isEmpty())
							items.add(Integer.parseInt(itemId.trim()));
				}
				shop.setEquippedItems(items);

				final String buffs = rs.getString("buffs");
				if (buffs != null && !buffs.isEmpty())
					for (String buff : buffs.split(";"))
						shop.addBuff(buff);

				shops.add(shop);
			}
		}
		catch (Exception e)
		{
			LOGGER.log(Level.SEVERE, "Error loading buff shops.", e);
		}
		return shops;
	}

	@Override
	public void removeShop(int ownerId)
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(REMOVE_SHOP))
		{
			final int affectedRows;
			ps.setInt(1, ownerId);
			affectedRows = ps.executeUpdate();
			if (affectedRows == 0)
				LOGGER.warning("No buff shop found for owner " + ownerId + '.');
		}
		catch (Exception e)
		{
			LOGGER.log(Level.SEVERE, "Error removing buff shop for owner " + ownerId, e);
		}
	}

	@Override
	public void addAdenaToOfflinePlayer(int ownerId, int adenaToAdd)
	{
		if (adenaToAdd <= 0)
			return;

		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(REWARD_OFFLINE_OWNER))
		{
			ps.setInt(1, adenaToAdd);
			ps.setInt(2, ownerId);
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.SEVERE, "Error rewarding offline buff shop owner " + ownerId, e);
		}
	}
}
