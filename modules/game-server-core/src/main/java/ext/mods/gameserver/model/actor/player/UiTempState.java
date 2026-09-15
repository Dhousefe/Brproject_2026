package ext.mods.gameserver.model.actor.player;

import java.util.HashMap;
import java.util.Map;

import ext.mods.gameserver.model.actor.Player;

/** Multi-select item bag for email / UI flows. */
public final class UiTempState
{
	public static final PlayerAttachments.Key<Map<Integer, Long>> SELECTED_ITEMS = PlayerAttachments.Key.of("ui.tempSelectedItems");
	
	private UiTempState()
	{
	}
	
	private static Map<Integer, Long> bag(Player player)
	{
		Map<Integer, Long> map = PlayerAttachments.get(player.getObjectId(), SELECTED_ITEMS);
		if (map == null)
		{
			map = new HashMap<>();
			PlayerAttachments.put(player.getObjectId(), SELECTED_ITEMS, map);
		}
		return map;
	}
	
	public static void addSelectedItem(Player player, int objectId, long amount)
	{
		if (player != null)
			bag(player).put(objectId, amount);
	}
	
	public static Map<Integer, Long> selectedItems(Player player)
	{
		return player == null ? Map.of() : bag(player);
	}
	
	public static void removeSelectedItem(Player player, int objectId)
	{
		if (player != null)
			bag(player).remove(objectId);
	}
	
	public static void clearSelectedItems(Player player)
	{
		if (player != null)
			PlayerAttachments.remove(player.getObjectId(), SELECTED_ITEMS);
	}
}
