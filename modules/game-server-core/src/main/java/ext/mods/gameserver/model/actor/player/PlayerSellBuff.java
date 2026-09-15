package ext.mods.gameserver.model.actor.player;

import java.util.ArrayList;
import java.util.List;

import ext.mods.commons.cached.CachedDataValueString;
import ext.mods.gameserver.data.SkillTable;
import ext.mods.gameserver.model.SellBuffHolder;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.skills.L2Skill;

/**
 * Sell-buffs list for a {@link Player} (att-ver-3.0 batch).
 * CachedData key "sellbuff" must be registered before CachedData.load().
 */
public final class PlayerSellBuff
{
	private final Player _owner;
	
	private CachedDataValueString _sellBuffList;
	private boolean _isSellingBuffs;
	private List<SellBuffHolder> _sellingBuffs;
	
	public PlayerSellBuff(Player owner)
	{
		_owner = owner;
	}
	
	/** Register CachedData key before bulk load. */
	public void ensureCache()
	{
		if (_sellBuffList != null)
			return;
		_sellBuffList = _owner.getCachedData().newString("sellbuff");
	}
	
	public boolean isSellingBuffs()
	{
		return _isSellingBuffs;
	}
	
	public void setSellingBuffs(boolean value)
	{
		_isSellingBuffs = value;
	}
	
	public String getSellBuffList()
	{
		ensureCache();
		return _sellBuffList.get();
	}
	
	public String setSellBuffList(String value)
	{
		ensureCache();
		String currentList = _sellBuffList.get();
		if (currentList != null && currentList.contains(value))
			return currentList;
		
		String updatedList = (currentList == null || currentList.isEmpty()) ? value : currentList + ";" + value;
		
		_sellBuffList.set(updatedList);
		return updatedList;
	}
	
	public List<SellBuffHolder> getSellingBuffs()
	{
		if (_sellingBuffs == null)
		{
			_sellingBuffs = new ArrayList<>();
			loadSellingBuffs();
		}
		return _sellingBuffs;
	}
	
	private void loadSellingBuffs()
	{
		ensureCache();
		if (_sellBuffList == null)
			return;
		
		String sellBuffData = _sellBuffList.get();
		String[] items = sellBuffData.split(";");
		for (String item : items)
		{
			if (item.isEmpty())
				continue;
			String[] values = item.split(",");
			if (values.length < 3)
				continue;
			
			int skillId = Integer.parseInt(values[0]);
			int skillLvl = Integer.parseInt(values[1]);
			int skillPrice = Integer.parseInt(values[2]);
			
			L2Skill skill = SkillTable.getInstance().getInfo(skillId, skillLvl);
			if (skill != null && skill.getId() == skillId)
			{
				SellBuffHolder holder = new SellBuffHolder(skillId, skillLvl, skillPrice);
				if (!_sellingBuffs.contains(holder))
					_sellingBuffs.add(holder);
			}
		}
	}
	
	public void saveSellingBuffs()
	{
		ensureCache();
		if (_sellingBuffs == null)
			return;
		
		StringBuilder sellBuffData = new StringBuilder();
		for (SellBuffHolder holder : _sellingBuffs)
			sellBuffData.append(holder.getSkillId()).append(",").append(holder.getSkillLvl()).append(",").append(holder.getPrice()).append(";");
		
		_sellBuffList.set(sellBuffData.toString());
	}
}
