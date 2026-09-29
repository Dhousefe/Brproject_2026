package ext.mods.gameserver.data.repository;

import java.util.List;

/** Complete persisted state of one offline trader. */
public record OfflineTraderData(int characterId, long time, int type, String title, List<OfflineTradeItem> items)
{
	public OfflineTraderData
	{
		items = List.copyOf(items);
	}
}
