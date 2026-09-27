package ext.mods.sellBuffEngine.persistence;

import java.util.List;

import ext.mods.sellBuffEngine.ShopObject;

/** Persistence port for player-owned buff shops. */
public interface BuffShopStore
{
	void saveShop(ShopObject shop);

	List<ShopObject> loadShops();

	void removeShop(int ownerId);

	void addAdenaToOfflinePlayer(int ownerId, int adenaToAdd);
}
