package ext.mods.gameserver.model.actor.player;

import ext.mods.extensions.api.IDressMeSkin;
import ext.mods.gameserver.model.actor.Player;

/**
 * DressMe runtime state keyed by player objectId (PlayerAttachments).
 * Prefer this over methods on {@link Player}.
 */
public final class DressMeState
{
	public static final PlayerAttachments.Key<Long> LAST_SUMMON_TIME = PlayerAttachments.Key.of("dressme.lastSummonTime");
	public static final PlayerAttachments.Key<Boolean> ACTIVE = PlayerAttachments.Key.of("dressme.active");
	public static final PlayerAttachments.Key<IDressMeSkin> ARMOR = PlayerAttachments.Key.of("dressme.armorSkin");
	public static final PlayerAttachments.Key<IDressMeSkin> WEAPON = PlayerAttachments.Key.of("dressme.weaponSkin");
	
	private DressMeState()
	{
	}
	
	public static boolean isActive(Player player)
	{
		if (player == null)
			return false;
		final Boolean v = PlayerAttachments.get(player.getObjectId(), ACTIVE);
		return v != null && v;
	}
	
	public static void setActive(Player player, boolean active)
	{
		if (player == null)
			return;
		if (active)
			PlayerAttachments.put(player.getObjectId(), ACTIVE, Boolean.TRUE);
		else
			PlayerAttachments.remove(player.getObjectId(), ACTIVE);
	}
	
	public static IDressMeSkin armor(Player player)
	{
		return player == null ? null : PlayerAttachments.get(player.getObjectId(), ARMOR);
	}
	
	public static void setArmor(Player player, IDressMeSkin skin)
	{
		if (player == null)
			return;
		if (skin == null)
			PlayerAttachments.remove(player.getObjectId(), ARMOR);
		else
			PlayerAttachments.put(player.getObjectId(), ARMOR, skin);
	}
	
	public static IDressMeSkin weapon(Player player)
	{
		return player == null ? null : PlayerAttachments.get(player.getObjectId(), WEAPON);
	}
	
	public static void setWeapon(Player player, IDressMeSkin skin)
	{
		if (player == null)
			return;
		if (skin == null)
			PlayerAttachments.remove(player.getObjectId(), WEAPON);
		else
			PlayerAttachments.put(player.getObjectId(), WEAPON, skin);
	}
	
	public static long lastSummonTime(Player player)
	{
		if (player == null)
			return 0L;
		final Long v = PlayerAttachments.get(player.getObjectId(), LAST_SUMMON_TIME);
		return v == null ? 0L : v;
	}
	
	public static void setLastSummonTime(Player player, long time)
	{
		if (player == null)
			return;
		if (time <= 0L)
			PlayerAttachments.remove(player.getObjectId(), LAST_SUMMON_TIME);
		else
			PlayerAttachments.put(player.getObjectId(), LAST_SUMMON_TIME, time);
	}
}
