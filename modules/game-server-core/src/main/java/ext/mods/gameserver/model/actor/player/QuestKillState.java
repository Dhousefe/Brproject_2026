package ext.mods.gameserver.model.actor.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ext.mods.gameserver.data.xml.NpcData;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.template.NpcTemplate;
import ext.mods.quests.QuestData;
import ext.mods.quests.QuestManager;
import ext.mods.quests.holder.QuestHolder;
import ext.mods.quests.holder.QuestObjective;

/** Custom quest kill counters + memo flags (was on Player). */
public final class QuestKillState
{
	public static final PlayerAttachments.Key<Map<Integer, Map<Integer, Long>>> KILLS =
		PlayerAttachments.Key.of("quest.killCounts");
	
	private QuestKillState()
	{
	}
	
	private static Map<Integer, Map<Integer, Long>> bag(Player player)
	{
		Map<Integer, Map<Integer, Long>> map = PlayerAttachments.get(player.getObjectId(), KILLS);
		if (map == null)
		{
			map = new HashMap<>();
			PlayerAttachments.put(player.getObjectId(), KILLS, map);
		}
		return map;
	}
	
	public static long getKillCount(Player player, int questId, int npcId)
	{
		if (player == null)
			return 0L;
		return bag(player).getOrDefault(questId, Collections.emptyMap()).getOrDefault(npcId, 0L);
	}
	
	public static void increase(Player player, int questId, int npcId)
	{
		if (player == null)
			return;
		final long newCount = getKillCount(player, questId, npcId) + 1;
		bag(player).computeIfAbsent(questId, k -> new HashMap<>()).put(npcId, newCount);
		player.getMemos().set("quest_" + questId + "_mob_" + npcId, newCount);
		
		if (isNotifyHtml(player))
			QuestManager.getInstance().showMenuQuest(player, 1);
		else if (isNotifyChat(player))
		{
			final QuestHolder quest = QuestData.getInstance().getQuest(questId);
			if (quest == null)
				return;
			for (QuestObjective obj : quest.getObjectivesForClass(player.getClassId().getId()))
			{
				if (obj.getNpcId() != npcId)
					continue;
				final int requiredCount = obj.getCount();
				final NpcTemplate npc = NpcData.getInstance().getTemplate(npcId);
				if (npc == null)
					continue;
				final String npcName = npc.getName().length() > 32 ? npc.getName().substring(0, 32) : npc.getName();
				player.sendMessage("[Quest: " + quest.getName() + "] Matou " + newCount + "/" + requiredCount + " " + npcName
					+ (newCount >= requiredCount ? " (Objetivo completo!)" : ""));
			}
		}
	}
	
	public static void reset(Player player, int questId, int npcId)
	{
		if (player == null)
			return;
		final Map<Integer, Long> kills = bag(player).get(questId);
		if (kills != null)
		{
			kills.remove(npcId);
			if (kills.isEmpty())
				bag(player).remove(questId);
		}
		player.getMemos().unset("quest_" + questId + "_mob_" + npcId);
	}
	
	public static void load(Player player)
	{
		if (player == null)
			return;
		for (String key : player.getMemos().keySet())
		{
			if (key.startsWith("quest_") && key.contains("_mob_"))
			{
				try
				{
					final String[] parts = key.split("_");
					final int questId = Integer.parseInt(parts[1]);
					final int npcId = Integer.parseInt(parts[3]);
					final long count = player.getMemos().getLong(key, 0L);
					bag(player).computeIfAbsent(questId, k -> new HashMap<>()).put(npcId, count);
				}
				catch (Exception e)
				{
					System.err.println("Erro ao carregar kill count de quest: " + key);
				}
			}
		}
	}
	
	public static void setCompleted(Player player, int questId, boolean completed)
	{
		if (player != null)
			player.getMemos().set("quest_" + questId + "_completed", completed);
	}
	
	public static boolean isCompleted(Player player, int questId)
	{
		return player != null && player.getMemos().getBool("quest_" + questId + "_completed", false);
	}
	
	public static void unset(Player player, int questId)
	{
		if (player == null)
			return;
		bag(player).remove(questId);
		final List<String> keysToRemove = new ArrayList<>();
		for (String key : player.getMemos().keySet())
		{
			if (key.startsWith("quest_" + questId + "_"))
				keysToRemove.add(key);
		}
		for (String key : keysToRemove)
			player.getMemos().unset(key);
	}
	
	public static Set<Integer> activeQuestIds(Player player)
	{
		final Set<Integer> ids = new HashSet<>();
		if (player == null)
			return ids;
		ids.addAll(bag(player).keySet());
		for (String key : player.getMemos().keySet())
		{
			if (key.startsWith("quest_") && key.contains("_completed"))
			{
				final int questId = Integer.parseInt(key.split("_")[1]);
				if (!isCompleted(player, questId))
					ids.add(questId);
			}
		}
		return ids;
	}
	
	public static boolean isNotifyHtml(Player player)
	{
		return player != null && player.getMemos().getBool("quest_notify_html", false);
	}
	
	public static void setNotifyHtml(Player player, boolean value)
	{
		if (player != null)
			player.getMemos().set("quest_notify_html", value);
	}
	
	public static boolean isNotifyChat(Player player)
	{
		return player != null && player.getMemos().getBool("quest_notify_chat", false);
	}
	
	public static void setNotifyChat(Player player, boolean value)
	{
		if (player != null)
			player.getMemos().set("quest_notify_chat", value);
	}
}
