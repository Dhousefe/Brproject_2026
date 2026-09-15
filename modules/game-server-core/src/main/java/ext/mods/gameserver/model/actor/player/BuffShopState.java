package ext.mods.gameserver.model.actor.player;

import ext.mods.gameserver.model.actor.Player;

/** Buff-shop temp price string. */
public final class BuffShopState
{
	public static final PlayerAttachments.Key<String> TEMP_PRICE = PlayerAttachments.Key.of("buffshop.tempPrice");
	
	private BuffShopState()
	{
	}
	
	public static String tempPrice(Player player)
	{
		return player == null ? null : PlayerAttachments.get(player.getObjectId(), TEMP_PRICE);
	}
	
	public static void setTempPrice(Player player, String price)
	{
		if (player == null)
			return;
		if (price == null)
			PlayerAttachments.remove(player.getObjectId(), TEMP_PRICE);
		else
			PlayerAttachments.put(player.getObjectId(), TEMP_PRICE, price);
	}
}
