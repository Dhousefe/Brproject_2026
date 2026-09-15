/*
* Copyleft © 2024-2026 L2Brproject
* * This file is part of L2Brproject derived from aCis409/RusaCis3.8
 */
package ext.mods.gameserver.handler.itemhandlers;

import java.util.List;

import ext.mods.commons.pool.ThreadPool;
import ext.mods.extensions.api.IAgathionSpec;
import ext.mods.extensions.hooks.AgathionHooks;
import ext.mods.gameserver.data.SkillTable;
import ext.mods.gameserver.data.xml.NpcData;
import ext.mods.gameserver.handler.IItemHandler;
import ext.mods.gameserver.model.actor.Playable;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.instance.Agathion;
import ext.mods.gameserver.model.actor.template.NpcTemplate;
import ext.mods.gameserver.model.item.instance.ItemInstance;
import ext.mods.gameserver.network.SystemMessageId;
import ext.mods.gameserver.skills.L2Skill;
import ext.mods.gameserver.model.actor.player.AgathionState;

public class AgathionItems implements IItemHandler
{
	@Override
	public void useItem(Playable playable, ItemInstance item, boolean forceUse)
	{
		if (!(playable instanceof Player))
			return;
		
		Player player = (Player) playable;
		final int itemId = item.getItemId();
		final long cooldown = 1000;
		
		long currentTime = System.currentTimeMillis();
		if (currentTime - AgathionState.lastSummonTime(player) < cooldown)
		{
			player.sendMessage("You must wait before performing this action again.");
			return;
		}
		AgathionState.setLastSummonTime(player, currentTime);
		
		List<? extends IAgathionSpec> agationList = AgathionHooks.get().getAgathionsByItemId(itemId);
		if (agationList == null || agationList.isEmpty())
		{
			player.sendMessage("No Agathion is associated with this item.");
			return;
		}
		
		IAgathionSpec agathionInfo = agationList.get(0);
		
		if (AgathionState.get(player) != null && player.getMemos().getInteger("agation", 0) == itemId)
		{
			AgathionState.delete(player, AgathionState.get(player));
			AgathionState.set(player, null);
			player.getMemos().unset("agation");
			player.sendMessage("Agathion unsummoned.");
		}
		else
		{
			if (AgathionState.get(player) != null)
			{
				AgathionState.delete(player, AgathionState.get(player));
				AgathionState.set(player, null);
				player.getMemos().unset("agation");
			}
			
			NpcTemplate npcTemplate = NpcData.getInstance().getTemplate(agathionInfo.getNpcId());
			if (npcTemplate == null)
			{
				player.sendMessage("Cannot spawn the Agathion. NPC template not found.");
				return;
			}
			
			L2Skill skillToCast = SkillTable.getInstance().getInfo(2046, 1);
			if (skillToCast == null)
			{
				player.sendMessage("Summoning skill not found.");
				return;
			}
			
			player.getAI().tryToCast(player, skillToCast, false, false, item.getObjectId());
			player.sendPacket(SystemMessageId.SUMMON_A_PET);
			
			ThreadPool.schedule(() ->
			{
				Agathion spawnedAgathion = AgathionState.get(player);
				if (spawnedAgathion != null)
				{
					spawnedAgathion.setInstanceMap(player.getInstanceMap(), false);
					
					if (agathionInfo.getRunSpeed())
					{
						spawnedAgathion.setRunning(true);
					}
				}
			}, skillToCast.getHitTime() + 100);
		}
	}
}
