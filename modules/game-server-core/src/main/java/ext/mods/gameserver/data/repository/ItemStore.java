package ext.mods.gameserver.data.repository;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

import ext.mods.gameserver.enums.items.ItemLocation;

/** Persistence contract for owned items and their augmentations. */
public interface ItemStore
{
	List<ItemRecord> loadItems(int ownerId, ItemLocation location, ItemLocation equippedLocation) throws SQLException;

	List<ItemRecord> loadItems(int ownerId, ItemLocation location) throws SQLException;

	void saveItems(Collection<ItemSaveRecord> items) throws SQLException;

	void deletePet(int itemObjectId) throws SQLException;
}
