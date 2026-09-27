package ext.mods.gameserver.data.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import ext.mods.gameserver.data.adapter.JdbcItemStore;
import ext.mods.gameserver.data.repository.ItemRecord;
import ext.mods.gameserver.data.repository.ItemSaveRecord;
import ext.mods.gameserver.data.repository.ItemStore;
import ext.mods.gameserver.enums.items.ItemLocation;
import ext.mods.gameserver.model.Augmentation;
import ext.mods.gameserver.model.item.instance.ItemInstance;
import ext.mods.gameserver.model.item.kind.Weapon;

/** Application boundary for item persistence. */
public final class ItemPersistenceService
{
	private static final ItemStore STORE = new JdbcItemStore();

	private ItemPersistenceService()
	{
	}

	public static List<ItemRecord> loadItems(int ownerId, ItemLocation location, ItemLocation equippedLocation) throws SQLException
	{
		return STORE.loadItems(ownerId, location, equippedLocation);
	}

	public static List<ItemRecord> loadItems(int ownerId, ItemLocation location) throws SQLException
	{
		return STORE.loadItems(ownerId, location);
	}

	public static void saveItems(Collection<ItemInstance> items) throws SQLException
	{
		final List<ItemSaveRecord> records = new ArrayList<>(items.size());
		for (ItemInstance item : items)
		{
			final boolean weapon = item.getItem() instanceof Weapon;
			final boolean deleteItem = item.getCount() <= 0 || item.getLocation() == ItemLocation.VOID;
			final boolean deleteAugmentation = weapon && (item.getCount() <= 0 || item.getAugmentation() == null);
			final Augmentation augmentation = weapon ? item.getAugmentation() : null;
			records.add(new ItemSaveRecord(item.getObjectId(), item.getItemId(), item.getCount(), item.getEnchantLevel(), item.getOwnerId(), item.getCustomType1(), item.getCustomType2(), item.getLocation(), item.getLocationSlot(), item.getManaLeft(), item.getTime(), augmentation == null ? null : augmentation.getId(), augmentation == null || augmentation.getSkill() == null ? null : augmentation.getSkill().getId(), augmentation == null || augmentation.getSkill() == null ? null : augmentation.getSkill().getLevel(), deleteItem, deleteAugmentation, item.getCount() <= 0 && item.isSummonItem()));
		}
		STORE.saveItems(records);
	}

	public static void deletePet(int itemObjectId) throws SQLException
	{
		STORE.deletePet(itemObjectId);
	}
}
