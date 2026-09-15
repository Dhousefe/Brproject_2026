package ext.mods.gameserver.model.actor.player;

import ext.mods.gameserver.model.actor.Player;

/**
 * Equipped cursed weapon id for a {@link Player}.
 */
public final class PlayerCursedWeapon
{
	private final Player _owner;
	private int _cursedWeaponEquippedId;
	
	public PlayerCursedWeapon(Player owner)
	{
		_owner = owner;
	}
	
	public int getCursedWeaponEquippedId()
	{
		return _cursedWeaponEquippedId;
	}
	
	public void setCursedWeaponEquippedId(int value)
	{
		_cursedWeaponEquippedId = value;
	}
	
	public boolean isEquipped()
	{
		return _cursedWeaponEquippedId > 0;
	}
}
