package ext.mods.gameserver.data.repository;

import ext.mods.gameserver.enums.items.ItemLocation;

/** Persisted item state returned by the item repository. */
public record ItemRecord(int objectId, int itemId, int count, int enchantLevel, int ownerId, int customType1, int customType2, ItemLocation location, int locationSlot, int manaLeft, long time, Integer augmentationId, Integer augmentationSkillId, Integer augmentationSkillLevel)
{
}
