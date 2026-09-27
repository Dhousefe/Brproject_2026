package ext.mods.gameserver.data.repository;

import ext.mods.gameserver.enums.items.ItemLocation;

/** Item state prepared by the game model for persistence. */
public record ItemSaveRecord(int objectId, int itemId, int count, int enchantLevel, int ownerId, int customType1, int customType2, ItemLocation location, int locationSlot, int manaLeft, long time, Integer augmentationId, Integer augmentationSkillId, Integer augmentationSkillLevel, boolean deleteItem, boolean deleteAugmentation, boolean deletePet)
{
}
