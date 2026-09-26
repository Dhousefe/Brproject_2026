package ext.mods.gameserver.data.repository;

import java.util.List;

/** Persistence port for limited-stock NPC buy lists. */
public interface BuyListStore
{
	List<RestockRecord> loadRestocks();

	void saveRestock(RestockRecord restock);

	void deleteRestock(int buyListId, int itemId);

	record RestockRecord(int buyListId, int itemId, int count, long nextRestockTime)
	{
	}
}
