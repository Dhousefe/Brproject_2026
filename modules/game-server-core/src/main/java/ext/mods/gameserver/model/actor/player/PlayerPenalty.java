package ext.mods.gameserver.model.actor.player;

import ext.mods.config.ConfigPlayers;
import ext.mods.gameserver.data.SkillTable;
import ext.mods.gameserver.enums.Paperdoll;
import ext.mods.gameserver.enums.actors.WeightPenalty;
import ext.mods.gameserver.enums.items.EtcItemType;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.item.instance.ItemInstance;
import ext.mods.gameserver.model.item.kind.Item;
import ext.mods.gameserver.model.itemcontainer.listeners.ItemPassiveSkillsListener;
import ext.mods.gameserver.network.serverpackets.EtcStatusUpdate;
import ext.mods.gameserver.network.serverpackets.SkillList;
import ext.mods.gameserver.network.serverpackets.UserInfo;
import ext.mods.gameserver.enums.skills.Stats;
import ext.mods.gameserver.skills.L2Skill;

/**
 * Weight / expertise grade penalties for a {@link Player}.
 */
public final class PlayerPenalty
{
	private final Player _owner;
	
	private WeightPenalty _weightPenalty = WeightPenalty.NONE;
	private int _armorGradePenalty;
	private boolean _weaponGradePenalty;
	
	public PlayerPenalty(Player owner)
	{
		_owner = owner;
	}
	
	public WeightPenalty getWeightPenalty()
	{
		return _weightPenalty;
	}
	
	public int getArmorGradePenalty()
	{
		return _armorGradePenalty;
	}
	
	public boolean getWeaponGradePenalty()
	{
		return _weaponGradePenalty;
	}
	
	public void refreshWeightPenalty()
	{
		final int weightLimit = _owner.getWeightLimit();
		if (weightLimit <= 0)
			return;
		
		final double ratio = (_owner.getCurrentWeight() - _owner.getStatus().calcStat(Stats.WEIGHT_PENALTY, 0, _owner, null)) / weightLimit;
		
		final WeightPenalty newWeightPenalty;
		if (ratio < 0.5)
			newWeightPenalty = WeightPenalty.NONE;
		else if (ratio < 0.666)
			newWeightPenalty = WeightPenalty.LEVEL_1;
		else if (ratio < 0.8)
			newWeightPenalty = WeightPenalty.LEVEL_2;
		else if (ratio < 1)
			newWeightPenalty = WeightPenalty.LEVEL_3;
		else
			newWeightPenalty = WeightPenalty.LEVEL_4;
		
		if (_weightPenalty != newWeightPenalty)
		{
			_weightPenalty = newWeightPenalty;
			
			_owner.sendPacket(new UserInfo(_owner));
			_owner.sendPacket(new EtcStatusUpdate(_owner));
			_owner.broadcastCharInfo();
		}
	}
	
	public void refreshExpertisePenalty()
	{
		if (!ConfigPlayers.EXPERTISE_PENALTY)
			return;
		
		final int expertiseLevel = _owner.getSkillLevel(L2Skill.SKILL_EXPERTISE);
		
		int armorPenalty = 0;
		boolean weaponPenalty = false;
		
		for (final ItemInstance item : _owner.getInventory().getPaperdollItems())
		{
			if (item.getItemType() != EtcItemType.ARROW && item.getItem().getCrystalType().getId() > expertiseLevel)
			{
				if (item.isWeapon())
					weaponPenalty = true;
				else
					armorPenalty += (item.getItem().getBodyPart() == Item.SLOT_FULL_ARMOR) ? 2 : 1;
			}
		}
		
		armorPenalty = Math.min(armorPenalty, 4);
		
		if (_weaponGradePenalty != weaponPenalty || _armorGradePenalty != armorPenalty)
		{
			_weaponGradePenalty = weaponPenalty;
			_armorGradePenalty = armorPenalty;
			
			if (_weaponGradePenalty || _armorGradePenalty > 0)
				_owner.addSkill(SkillTable.getInstance().getInfo(4267, 1), false);
			else
				_owner.removeSkill(4267, false);
			
			_owner.sendPacket(new SkillList(_owner));
			_owner.sendPacket(new EtcStatusUpdate(_owner));
			
			final ItemInstance item = _owner.getActiveWeaponInstance();
			if (item != null)
			{
				if (_weaponGradePenalty)
					ItemPassiveSkillsListener.getInstance().onUnequip(Paperdoll.NULL, item, _owner);
				else
					ItemPassiveSkillsListener.getInstance().onEquip(Paperdoll.NULL, item, _owner);
			}
		}
	}
	
	public boolean isOverweight()
	{
		return _weightPenalty.ordinal() > 2 || _owner.getStatus().isOverburden();
	}
}
