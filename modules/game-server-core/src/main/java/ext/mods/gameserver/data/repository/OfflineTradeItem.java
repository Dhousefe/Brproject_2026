package ext.mods.gameserver.data.repository;

/** Persisted item row belonging to an offline trader. */
public record OfflineTradeItem(int item, long count, long price, long enchant)
{
}
