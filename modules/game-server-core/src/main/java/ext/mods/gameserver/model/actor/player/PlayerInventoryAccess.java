/*
 * Copyleft © 2024-2026 L2Brproject
 *
 * This file is part of L2Brproject derived from aCis409/RusaCis3.8
 *
 * L2Brproject is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License.
 *
 * L2Brproject is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package ext.mods.gameserver.model.actor.player;

import ext.mods.commons.util.ArraysUtil;
import ext.mods.commons.random.Rnd;
import ext.mods.config.ConfigPlayers;
import ext.mods.config.ConfigRates;
import ext.mods.extensions.listener.manager.InventoryListenerManager;
import ext.mods.extensions.listener.manager.PlayerListenerManager;
import ext.mods.gameserver.data.manager.CursedWeaponManager;
import ext.mods.gameserver.data.xml.ItemData;
import ext.mods.gameserver.enums.Paperdoll;
import ext.mods.gameserver.enums.actors.WeightPenalty;
import ext.mods.gameserver.enums.items.EtcItemType;
import ext.mods.gameserver.enums.items.ItemLocation;
import ext.mods.gameserver.enums.items.WeaponType;
import ext.mods.gameserver.enums.skills.Stats;
import ext.mods.gameserver.enums.ZoneId;
import ext.mods.gameserver.handler.IItemHandler;
import ext.mods.gameserver.handler.ItemHandler;
import ext.mods.gameserver.model.World;
import ext.mods.gameserver.model.actor.Creature;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.Npc;
import ext.mods.gameserver.model.item.instance.ItemInstance;
import ext.mods.gameserver.model.item.kind.Item;
import ext.mods.gameserver.model.item.kind.Weapon;
import ext.mods.gameserver.model.itemcontainer.PcFreight;
import ext.mods.gameserver.model.itemcontainer.PcInventory;
import ext.mods.gameserver.model.itemcontainer.PcWarehouse;
import ext.mods.gameserver.network.SystemMessageId;
import ext.mods.gameserver.network.serverpackets.EnchantResult;
import ext.mods.gameserver.network.serverpackets.ExStorageMaxCount;
import ext.mods.gameserver.network.serverpackets.SystemMessage;
import ext.mods.gameserver.skills.Formulas;

/**
 * Holds inventory, equipment, weight, paperdoll, adena, crystal, warehouse and freight methods
 * that previously lived on {@link Player}. Player keeps thin delegation stubs; logic lives here.
 */
public final class PlayerInventoryAccess
{
	private final Player _player;
	private PcInventory _directInventory;

	public PlayerInventoryAccess(Player player)
	{
		_player = player;
	}

	/**
	 * Set the direct inventory reference to break circular delegation.
	 * Called from Player field initialization order.
	 */
	public void setDirectInventory(PcInventory inv)
	{
		_directInventory = inv;
	}

	// ---------------------------------------------------------------------
	// Inventory accessors
	// ---------------------------------------------------------------------

	public PcInventory getInventory()
	{
		return _directInventory != null ? _directInventory : _player.getInventory();
	}

	public int getCurrentWeight()
	{
		return getInventory().getTotalWeight();
	}

	public int getWeightLimit()
	{
		return (int) _player.getStatus().calcStat(Stats.WEIGHT_LIMIT, 69000 * Formulas.CON_BONUS[_player.getStatus().getCON()] * ConfigPlayers.WEIGHT_LIMIT, _player, null);
	}

	public WeightPenalty getWeightPenalty()
	{
		return _player.getWeightPenalty();
	}

	public int getArmorGradePenalty()
	{
		return _player.getArmorGradePenalty();
	}

	public boolean getWeaponGradePenalty()
	{
		return _player.getWeaponGradePenalty();
	}

	public void refreshWeightPenalty()
	{
		_player.refreshWeightPenalty();
	}

	public void refreshExpertisePenalty()
	{
		_player.refreshExpertisePenalty();
	}

	public boolean isOverweight()
	{
		return _player.isOverweight();
	}

	// ---------------------------------------------------------------------
	// Equipment / equippable item
	// ---------------------------------------------------------------------

	public void useEquippableItem(ItemInstance item, boolean abortAttack)
	{
		final boolean isEquipped = item.isEquipped();
		final int oldInvLimit = _player.getStatus().getInventoryLimit();

		if (item.getItem() instanceof Weapon)
			item.unChargeAllShots();

		if (isEquipped)
		{
			if (item.getEnchantLevel() > 0)
				_player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.EQUIPMENT_S1_S2_REMOVED).addNumber(item.getEnchantLevel()).addItemName(item));
			else
				_player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_DISARMED).addItemName(item));

			getInventory().unequipItemInBodySlotAndRecord(item);

			InventoryListenerManager.getInstance().notifyUnequip(item.getItem().getBodyPart(), item, _player);
		}
		else
		{
			getInventory().equipItemAndRecord(item);

			if (item.isEquipped())
			{
				if (item.getEnchantLevel() > 0)
					_player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_S2_EQUIPPED).addNumber(item.getEnchantLevel()).addItemName(item));
				else
					_player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_EQUIPPED).addItemName(item));

				InventoryListenerManager.getInstance().notifyEquip(item.getItem().getBodyPart(), item, _player);

				if ((item.getItem().getBodyPart() & Item.SLOT_ALLWEAPON) != 0)
					_player.rechargeShots(true, true);
			}
			else
			{
				_player.sendPacket(SystemMessageId.CANNOT_EQUIP_ITEM_DUE_TO_BAD_CONDITION);
			}
		}

		refreshExpertisePenalty();
		_player.broadcastUserInfo();

		if (abortAttack)
			_player.getAttack().stop();

		if (_player.getStatus().getInventoryLimit() != oldInvLimit)
			_player.sendPacket(new ExStorageMaxCount(_player));
	}

	public boolean disarmWeapon(boolean leftHandIncluded)
	{
		if (_player.isCursedWeaponEquipped())
			return false;

		_player.getAttack().stop();

		ItemInstance[] unequipped = getInventory().unequipItemInBodySlotAndRecord(Item.SLOT_R_HAND);
		if (!ArraysUtil.isEmpty(unequipped))
		{
			SystemMessage sm;
			if (unequipped[0].getEnchantLevel() > 0)
				sm = SystemMessage.getSystemMessage(SystemMessageId.EQUIPMENT_S1_S2_REMOVED).addNumber(unequipped[0].getEnchantLevel()).addItemName(unequipped[0]);
			else
				sm = SystemMessage.getSystemMessage(SystemMessageId.S1_DISARMED).addItemName(unequipped[0]);

			_player.sendPacket(sm);
		}

		if (leftHandIncluded)
		{
			unequipped = getInventory().unequipItemInBodySlotAndRecord(Item.SLOT_L_HAND);
			if (!ArraysUtil.isEmpty(unequipped))
			{
				SystemMessage sm;
				if (unequipped[0].getEnchantLevel() > 0)
					sm = SystemMessage.getSystemMessage(SystemMessageId.EQUIPMENT_S1_S2_REMOVED).addNumber(unequipped[0].getEnchantLevel()).addItemName(unequipped[0]);
				else
					sm = SystemMessage.getSystemMessage(SystemMessageId.S1_DISARMED).addItemName(unequipped[0]);

				_player.sendPacket(sm);
			}
		}

		_player.broadcastUserInfo();

		return true;
	}

	public void checkItemRestriction()
	{
		for (final ItemInstance item : getInventory().getPaperdollItems())
		{
			if (item.getItem().checkCondition(_player, _player, false))
				continue;

			useEquippableItem(item, item.isWeapon());
		}
	}

	// ---------------------------------------------------------------------
	// Active weapon / secondary weapon
	// ---------------------------------------------------------------------

	public ItemInstance getActiveWeaponInstance()
	{
		return getInventory().getItemFrom(Paperdoll.RHAND);
	}

	public Weapon getActiveWeaponItem()
	{
		final ItemInstance item = getActiveWeaponInstance();
		return (item == null) ? _player.getTemplate().getFists() : (Weapon) item.getItem();
	}

	public WeaponType getAttackType()
	{
		return getActiveWeaponItem().getItemType();
	}

	public ItemInstance getSecondaryWeaponInstance()
	{
		return getInventory().getItemFrom(Paperdoll.LHAND);
	}

	public Item getSecondaryWeaponItem()
	{
		final ItemInstance item = getSecondaryWeaponInstance();
		return (item == null) ? null : item.getItem();
	}

	// ---------------------------------------------------------------------
	// Active enchant item
	// ---------------------------------------------------------------------

	public ItemInstance getActiveEnchantItem()
	{
		return _player.getActiveEnchantItem();
	}

	public void setActiveEnchantItem(ItemInstance scroll)
	{
		_player.setActiveEnchantItem(scroll);
	}

	public void cancelActiveEnchant()
	{
		if (getActiveEnchantItem() == null)
			return;

		setActiveEnchantItem(null);

		_player.sendPacket(EnchantResult.CANCELLED);
		_player.sendPacket(SystemMessageId.ENCHANT_SCROLL_CANCELLED);
	}

	public int getEnchantEffect()
	{
		final ItemInstance wpn = getActiveWeaponInstance();
		return (wpn == null) ? 0 : Math.min(127, wpn.getEnchantLevel());
	}

	// ---------------------------------------------------------------------
	// Arrows
	// ---------------------------------------------------------------------

	public void reduceArrowCount()
	{
		final ItemInstance arrows = getSecondaryWeaponInstance();
		if (arrows == null)
			return;

		getInventory().destroyItem(arrows, 1);
	}

	public boolean checkAndEquipArrows()
	{
		final ItemInstance arrows = getInventory().findArrowForBow(getActiveWeaponItem());
		if (arrows == null)
			return false;

		if (arrows.getLocation() == ItemLocation.PAPERDOLL)
			return true;

		getInventory().setPaperdollItem(Paperdoll.LHAND, arrows);

		return true;
	}

	// ---------------------------------------------------------------------
	// Adena / ancient adena
	// ---------------------------------------------------------------------

	public int getAdena()
	{
		return getInventory().getAdena();
	}

	public int getAncientAdena()
	{
		return getInventory().getAncientAdena();
	}

	public void addAdena(int count, boolean sendMessage)
	{
		if (sendMessage)
			_player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.EARNED_S1_ADENA).addNumber(count));

		getInventory().addAdena(count);
	}

	public boolean reduceAdena(int count, boolean sendMessage)
	{
		if (count > getAdena())
		{
			if (sendMessage)
				_player.sendPacket(SystemMessageId.YOU_NOT_ENOUGH_ADENA);

			return false;
		}

		if (count > 0)
		{
			if (!getInventory().reduceAdena(count))
				return false;

			if (sendMessage)
				_player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_DISAPPEARED_ADENA).addNumber(count));
		}
		return true;
	}

	public void addAncientAdena(int count, boolean sendMessage)
	{
		if (sendMessage)
			_player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.EARNED_S2_S1_S).addItemName(PcInventory.ANCIENT_ADENA_ID).addNumber(count));

		getInventory().addAncientAdena(count);
	}

	public boolean reduceAncientAdena(int count, boolean sendMessage)
	{
		if (count > getAncientAdena())
		{
			if (sendMessage)
				_player.sendPacket(SystemMessageId.YOU_NOT_ENOUGH_ADENA);

			return false;
		}

		if (count > 0)
		{
			if (!getInventory().reduceAncientAdena(count))
				return false;

			if (sendMessage)
			{
				if (count > 1)
					_player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S2_S1_DISAPPEARED).addItemName(PcInventory.ANCIENT_ADENA_ID).addItemNumber(count));
				else
					_player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_DISAPPEARED).addItemName(PcInventory.ANCIENT_ADENA_ID));
			}
		}
		return true;
	}

	// ---------------------------------------------------------------------
	// Add / destroy / drop / transfer
	// ---------------------------------------------------------------------

	public void addItem(ItemInstance item, boolean sendMessage)
	{
		if (item.getCount() < 1)
			return;

		if (sendMessage)
		{
			if (item.getCount() > 1)
				_player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_PICKED_UP_S2_S1).addItemName(item).addNumber(item.getCount()));
			else if (item.getEnchantLevel() > 0)
				_player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_PICKED_UP_A_S1_S2).addNumber(item.getEnchantLevel()).addItemName(item));
			else
				_player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_PICKED_UP_S1).addItemName(item));
		}

		final ItemInstance newItem = getInventory().addItem(item);
		if (newItem == null)
			return;

		if (CursedWeaponManager.getInstance().isCursed(newItem.getItemId()))
			CursedWeaponManager.getInstance().activate(_player, newItem);
		else if (item.getItem().getItemType() == EtcItemType.ARROW && getAttackType() == WeaponType.BOW && !getInventory().hasItemIn(Paperdoll.LHAND))
			checkAndEquipArrows();
	}

	public ItemInstance addItem(int itemId, int count, boolean sendMessage)
	{
		if (count < 1)
			return null;

		final Item item = ItemData.getInstance().getTemplate(itemId);
		if (item == null)
			return null;

		if (item.getItemType() == EtcItemType.HERB)
		{
			final ItemInstance herb = new ItemInstance(0, itemId);

			final IItemHandler handler = ItemHandler.getInstance().getHandler(herb.getEtcItem());
			if (handler != null)
				handler.useItem(_player, herb, false);

			herb.destroyMe();
			return null;
		}

		if (sendMessage)
		{
			if (count > 1)
				_player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_PICKED_UP_S2_S1).addItemName(itemId).addItemNumber(count));
			else
				_player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_PICKED_UP_S1).addItemName(itemId));
		}

		final ItemInstance newItem = getInventory().addItem(itemId, count);
		if (newItem == null)
			return null;

		if (CursedWeaponManager.getInstance().isCursed(newItem.getItemId()))
			CursedWeaponManager.getInstance().activate(_player, newItem);
		else if (item.getItemType() == EtcItemType.ARROW && getAttackType() == WeaponType.BOW && !getInventory().hasItemIn(Paperdoll.LHAND))
			checkAndEquipArrows();

		return newItem;
	}

	public ItemInstance addEarnedItem(int itemId, int count, boolean sendMessage)
	{
		if (count < 1)
			return null;

		final Item item = ItemData.getInstance().getTemplate(itemId);
		if (item == null)
			return null;

		if (item.getItemType() == EtcItemType.HERB)
		{
			final ItemInstance herb = new ItemInstance(0, itemId);

			final IItemHandler handler = ItemHandler.getInstance().getHandler(herb.getEtcItem());
			if (handler != null)
				handler.useItem(_player, herb, false);

			herb.destroyMe();
			return null;
		}

		if (sendMessage)
		{
			if (count > 1)
				_player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.EARNED_S2_S1_S).addItemName(itemId).addItemNumber(count));
			else
				_player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.EARNED_ITEM_S1).addItemName(itemId));
		}

		final ItemInstance newItem = getInventory().addItem(itemId, count);
		if (newItem == null)
			return null;

		if (CursedWeaponManager.getInstance().isCursed(newItem.getItemId()))
			CursedWeaponManager.getInstance().activate(_player, newItem);
		else if (item.getItemType() == EtcItemType.ARROW && getAttackType() == WeaponType.BOW && !getInventory().hasItemIn(Paperdoll.LHAND))
			checkAndEquipArrows();

		return newItem;
	}

	public boolean destroyItem(ItemInstance item, boolean sendMessage)
	{
		return destroyItem(item, item.getCount(), sendMessage);
	}

	public boolean destroyItem(ItemInstance item, int count, boolean sendMessage)
	{
		item = getInventory().destroyItem(item, count);
		if (item == null)
		{
			if (sendMessage)
				_player.sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);

			return false;
		}

		if (sendMessage)
		{
			if (item.isShadowItem())
				_player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1S_REMAINING_MANA_IS_NOW_0).addItemName(item));
			else if (count > 1)
				_player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S2_S1_DISAPPEARED).addItemName(item).addItemNumber(count));
			else
				_player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_DISAPPEARED).addItemName(item));
		}
		PlayerListenerManager.getInstance().notifyItemDestroy(_player, item);
		return true;
	}

	public boolean destroyItem(int objectId, int count, boolean sendMessage)
	{
		final ItemInstance item = getInventory().getItemByObjectId(objectId);
		if (item == null)
		{
			if (sendMessage)
				_player.sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);

			return false;
		}

		return destroyItem(item, count, sendMessage);
	}

	public boolean destroyItemByItemId(int itemId, int count, boolean sendMessage)
	{
		if (itemId == 57)
			return reduceAdena(count, sendMessage);

		final ItemInstance item = getInventory().getItemByItemId(itemId);

		if (item == null || item.getCount() < count || getInventory().destroyItemByItemId(itemId, count) == null)
		{
			if (sendMessage)
				_player.sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);

			return false;
		}

		if (sendMessage)
		{
			if (item.isShadowItem())
				_player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1S_REMAINING_MANA_IS_NOW_0).addItemName(itemId));
			else if (count > 1)
				_player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S2_S1_DISAPPEARED).addItemName(itemId).addItemNumber(count));
			else
				_player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_DISAPPEARED).addItemName(itemId));
		}
		return true;
	}

	/**
	 * Validates and delegates to the Playable superclass implementation.
	 * Player.transferItem() should call this for validation, then call super.transferItem().
	 */
	public boolean canTransferItem(int objectId, int amount)
	{
		return checkItemManipulation(objectId, amount) != null;
	}

	public boolean dropItem(ItemInstance item, boolean sendMessage)
	{
		item = getInventory().dropItem(item);
		if (item == null)
		{
			if (sendMessage)
				_player.sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);

			return false;
		}

		item.dropMe(_player);

		if (sendMessage)
			_player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_DROPPED_S1).addItemName(item));

		return true;
	}

	public ItemInstance dropItem(int objectId, int count, int x, int y, int z, boolean sendMessage)
	{
		final ItemInstance item = getInventory().dropItem(objectId, count);
		if (item == null)
		{
			if (sendMessage)
				_player.sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);

			return null;
		}

		if (_player.getInstanceMap() != null)
		{
			item.setInstanceMap(_player.getInstanceMap(), true);
		}
		else
		{
			item.setInstanceMap(_player.getInstanceMap(), false);
		}

		item.dropMe(_player, x, y, z);

		if (sendMessage)
			_player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_DROPPED_S1).addItemName(item));

		return item;
	}

	public ItemInstance checkItemManipulation(int objectId, int count)
	{
		if (World.getInstance().getObject(objectId) == null)
			return null;

		final ItemInstance item = getInventory().getItemByObjectId(objectId);
		if (item == null || item.getOwnerId() != _player.getObjectId())
			return null;

		if (count < 1 || (count > 1 && !item.isStackable()) || count > item.getCount())
			return null;

		if (_player.getSummon() != null && _player.getSummon().getControlItemId() == objectId || _player.getMountObjectId() == objectId)
			return null;

		if (getActiveEnchantItem() != null && getActiveEnchantItem().getObjectId() == objectId)
			return null;

		if (item.isAugmented() && _player.getCast().isCastingNow())
			return null;

		return item;
	}

	public ItemInstance validateItemManipulation(int objectId)
	{
		final ItemInstance item = getInventory().getItemByObjectId(objectId);

		if (item == null || item.getOwnerId() != _player.getObjectId())
			return null;

		if (_player.getSummon() != null && _player.getSummon().getControlItemId() == objectId || _player.getMountObjectId() == objectId)
			return null;

		if (getActiveEnchantItem() != null && getActiveEnchantItem().getObjectId() == objectId)
			return null;

		if (CursedWeaponManager.getInstance().isCursed(item.getItemId()))
			return null;

		return item;
	}

	// ---------------------------------------------------------------------
	// Inventory enable/disable guard
	// ---------------------------------------------------------------------

	/**
	 * Disable inventory access for 1.5s. Thin delegation stub: the field
	 * {@code _inventoryDisable} and the 1500 ms reset task live on {@link Player}.
	 * Forwarding straight to {@link Player} (no re-delegation) is required to
	 * avoid the infinite recursion that would otherwise build up between
	 * {@code Player.isInventoryDisabled()} and this method.
	 */
	public void tempInventoryDisable()
	{
		_player.tempInventoryDisable();
	}

	public boolean isInventoryDisabled()
	{
		return _player.isInventoryDisabled();
	}

	// ---------------------------------------------------------------------
	// Warehouse / Freight
	// ---------------------------------------------------------------------

	public PcWarehouse getWarehouse()
	{
		return _player.getWarehouse();
	}

	public void clearWarehouse()
	{
		_player.clearWarehouse();
	}

	public PcFreight getFreight()
	{
		return _player.getFreight();
	}

	public void clearFreight()
	{
		_player.clearFreight();
	}

	public PcFreight getDepositedFreight(int objectId)
	{
		return _player.getDepositedFreight(objectId);
	}

	public void clearDepositedFreight()
	{
		_player.clearDepositedFreight();
	}

	// ---------------------------------------------------------------------
	// Death drop logic (inventory-touching)
	// ---------------------------------------------------------------------

	public void onDieDropItem(Creature killer)
	{
		if (killer == null)
			return;

		final Player pk = killer.getActingPlayer();
		if (_player.getKarma() <= 0 && pk != null && pk.getClan() != null && _player.getClan() != null && pk.getClan().isAtWarWith(_player.getClanId()))
			return;

		if ((!_player.isInsideZone(ZoneId.PVP) || pk == null) && (!_player.isGM() || ConfigPlayers.KARMA_DROP_GM))
		{
			final boolean isKillerNpc = (killer instanceof Npc);
			final int pkLimit = ConfigPlayers.KARMA_PK_LIMIT;

			int dropEquip = 0;
			int dropEquipWeapon = 0;
			int dropItem = 0;
			int dropLimit = 0;
			int dropPercent = 0;

			if (_player.getKarma() > 0 && _player.getPkKills() >= pkLimit)
			{
				dropPercent = ConfigRates.KARMA_RATE_DROP;
				dropEquip = ConfigRates.KARMA_RATE_DROP_EQUIP;
				dropEquipWeapon = ConfigRates.KARMA_RATE_DROP_EQUIP_WEAPON;
				dropItem = ConfigRates.KARMA_RATE_DROP_ITEM;
				dropLimit = ConfigRates.KARMA_DROP_LIMIT;
			}
			else if (isKillerNpc && _player.getStatus().getLevel() > 4 && !_player.isFestivalParticipant())
			{
				dropPercent = ConfigRates.PLAYER_RATE_DROP;
				dropEquip = ConfigRates.PLAYER_RATE_DROP_EQUIP;
				dropEquipWeapon = ConfigRates.PLAYER_RATE_DROP_EQUIP_WEAPON;
				dropItem = ConfigRates.PLAYER_RATE_DROP_ITEM;
				dropLimit = ConfigRates.PLAYER_DROP_LIMIT;
			}

			if (dropPercent > 0 && Rnd.get(100) < dropPercent)
			{
				int dropCount = 0;
				int itemDropPercent = 0;

				for (final ItemInstance itemDrop : getInventory().getItems())
				{
					if (!itemDrop.isDropable() || itemDrop.isShadowItem() || itemDrop.getItemId() == 57 || itemDrop.getItem().getType2() == Item.TYPE2_QUEST || (_player.getSummon() != null && _player.getSummon().getControlItemId() == itemDrop.getItemId()) || ArraysUtil.contains(ConfigPlayers.KARMA_NONDROPPABLE_ITEMS, itemDrop.getItemId()) || ArraysUtil.contains(ConfigPlayers.KARMA_NONDROPPABLE_PET_ITEMS, itemDrop.getItemId()))
						continue;

					if (itemDrop.isEquipped())
					{
						itemDropPercent = itemDrop.getItem().getType2() == Item.TYPE2_WEAPON ? dropEquipWeapon : dropEquip;
						getInventory().unequipItemInSlot(itemDrop.getLocationSlot());
					}
					else
						itemDropPercent = dropItem;

					if (Rnd.get(100) < itemDropPercent)
					{
						dropItem(itemDrop, true);

						if (++dropCount >= dropLimit)
							break;
					}
				}
			}
		}
	}
}