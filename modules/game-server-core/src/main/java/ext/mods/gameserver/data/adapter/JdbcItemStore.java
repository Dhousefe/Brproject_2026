package ext.mods.gameserver.data.adapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import ext.mods.commons.jdbc.DatabaseDialect;
import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.ItemRecord;
import ext.mods.gameserver.data.repository.ItemSaveRecord;
import ext.mods.gameserver.data.repository.ItemStore;
import ext.mods.gameserver.enums.items.ItemLocation;

/** JDBC adapter for item instances and item augmentations. */
public final class JdbcItemStore implements ItemStore
{
	private static final String LOAD_ITEMS = "SELECT i.object_id,i.item_id,i.count,i.enchant_level,i.owner_id,i.custom_type1,i.custom_type2,i.loc,i.loc_data,i.mana_left,i.time,a.attributes AS augmentation_id,a.skill_id AS augmentation_skill_id,a.skill_level AS augmentation_skill_level FROM items i LEFT JOIN augmentations a ON a.item_oid=i.object_id WHERE i.owner_id=? AND (i.loc=? OR i.loc=?) ORDER BY i.loc_data";
	private static final String LOAD_ITEMS_BY_LOCATION = "SELECT i.object_id,i.item_id,i.count,i.enchant_level,i.owner_id,i.custom_type1,i.custom_type2,i.loc,i.loc_data,i.mana_left,i.time,a.attributes AS augmentation_id,a.skill_id AS augmentation_skill_id,a.skill_level AS augmentation_skill_level FROM items i LEFT JOIN augmentations a ON a.item_oid=i.object_id WHERE i.owner_id=? AND i.loc=?";
	private static final String DELETE_ITEM = "DELETE FROM items WHERE object_id=?";
	private static final String DELETE_AUGMENTATION = "DELETE FROM augmentations WHERE item_oid=?";
	private static final String DELETE_PET_ITEM = "DELETE FROM pets WHERE item_obj_id=?";

	@Override
	public List<ItemRecord> loadItems(int ownerId, ItemLocation location, ItemLocation equippedLocation) throws SQLException
	{
		return load(LOAD_ITEMS, ownerId, location, equippedLocation);
	}

	@Override
	public List<ItemRecord> loadItems(int ownerId, ItemLocation location) throws SQLException
	{
		return load(LOAD_ITEMS_BY_LOCATION, ownerId, location, null);
	}

	private static List<ItemRecord> load(String sql, int ownerId, ItemLocation location, ItemLocation equippedLocation) throws SQLException
	{
		final List<ItemRecord> result = new ArrayList<>();
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(sql))
		{
			ps.setInt(1, ownerId);
			ps.setString(2, location.name());
			if (equippedLocation != null)
				ps.setString(3, equippedLocation.name());
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					result.add(toRecord(rs));
			}
		}
		return result;
	}

	private static ItemRecord toRecord(ResultSet rs) throws SQLException
	{
		final int augmentationId = rs.getInt("augmentation_id");
		final boolean hasAugmentation = !rs.wasNull();
		return new ItemRecord(rs.getInt("object_id"), rs.getInt("item_id"), rs.getInt("count"), rs.getInt("enchant_level"), rs.getInt("owner_id"), rs.getInt("custom_type1"), rs.getInt("custom_type2"), ItemLocation.valueOf(rs.getString("loc")), rs.getInt("loc_data"), rs.getInt("mana_left"), rs.getLong("time"), hasAugmentation ? augmentationId : null, hasAugmentation ? rs.getInt("augmentation_skill_id") : null, hasAugmentation ? rs.getInt("augmentation_skill_level") : null);
	}

	@Override
	public void saveItems(Collection<ItemSaveRecord> items) throws SQLException
	{
		if (items.isEmpty())
			return;

		try (Connection con = ConnectionPool.getConnection(); PreparedStatement saveItem = con.prepareStatement(getUpsertItemSql()); PreparedStatement saveAugmentation = con.prepareStatement(getUpsertAugmentationSql()); PreparedStatement deleteItem = con.prepareStatement(DELETE_ITEM); PreparedStatement deleteAugmentation = con.prepareStatement(DELETE_AUGMENTATION); PreparedStatement deletePet = con.prepareStatement(DELETE_PET_ITEM))
		{
			for (ItemSaveRecord item : items)
			{
				if (item.deleteItem())
				{
					deleteItem.setInt(1, item.objectId());
					deleteItem.addBatch();
					if (item.deleteAugmentation())
					{
						deleteAugmentation.setInt(1, item.objectId());
						deleteAugmentation.addBatch();
					}
					if (item.deletePet())
					{
						deletePet.setInt(1, item.objectId());
						deletePet.addBatch();
					}
					continue;
				}

				saveItem.setInt(1, item.ownerId());
				saveItem.setInt(2, item.objectId());
				saveItem.setInt(3, item.itemId());
				saveItem.setInt(4, item.count());
				saveItem.setInt(5, item.enchantLevel());
				saveItem.setString(6, item.location().name());
				saveItem.setInt(7, item.locationSlot());
				saveItem.setInt(8, item.customType1());
				saveItem.setInt(9, item.customType2());
				saveItem.setInt(10, item.manaLeft());
				saveItem.setLong(11, item.time());
				saveItem.addBatch();

				if (item.deleteAugmentation())
				{
					deleteAugmentation.setInt(1, item.objectId());
					deleteAugmentation.addBatch();
				}
				else if (item.augmentationId() != null)
				{
					saveAugmentation.setInt(1, item.objectId());
					saveAugmentation.setInt(2, item.augmentationId());
					saveAugmentation.setInt(3, item.augmentationSkillId() == null ? 0 : item.augmentationSkillId());
					saveAugmentation.setInt(4, item.augmentationSkillLevel() == null ? 0 : item.augmentationSkillLevel());
					saveAugmentation.addBatch();
				}
			}

			saveItem.executeBatch();
			saveAugmentation.executeBatch();
			deleteItem.executeBatch();
			deleteAugmentation.executeBatch();
			deletePet.executeBatch();
		}
	}

	private static String getUpsertItemSql()
	{
		return DatabaseDialect.upsert("items", "owner_id,object_id,item_id,count,enchant_level,loc,loc_data,custom_type1,custom_type2,mana_left,time", "?,?,?,?,?,?,?,?,?,?,?", "object_id", "owner_id,count,loc,loc_data,enchant_level,custom_type1,custom_type2,mana_left,time");
	}

	private static String getUpsertAugmentationSql()
	{
		return DatabaseDialect.upsert("augmentations", "item_oid,attributes,skill_id,skill_level", "?,?,?,?", "item_oid", "attributes,skill_id,skill_level");
	}

	@Override
	public void deletePet(int itemObjectId) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(DELETE_PET_ITEM))
		{
			ps.setInt(1, itemObjectId);
			ps.executeUpdate();
		}
	}
}
