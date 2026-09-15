package ext.mods.dressme;

import ext.mods.dressme.holder.DressMeHolder;
import ext.mods.dressme.task.DressMeEffectManager;
import ext.mods.extensions.api.IDressMeSkin;
import ext.mods.extensions.hooks.DressMeHooks;
import ext.mods.gameserver.enums.Paperdoll;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.itemcontainer.Inventory;
import ext.mods.gameserver.model.actor.player.DressMeState;

/**
 * DressMe apply/remove/restore logic (was on {@link Player}).
 * Registered via {@link DressMeHooks}.
 */
public final class DressMeService implements DressMeHooks.Api
{
	public static final DressMeService INSTANCE = new DressMeService();
	
	private DressMeService()
	{
	}
	
	@Override
	public IDressMeSkin getBySkillId(int skillId)
	{
		return DressMeData.getInstance().getBySkillId(skillId);
	}
	
	@Override
	public void startEffect(Player player, IDressMeSkin skin)
	{
		if (skin instanceof DressMeHolder holder)
			DressMeEffectManager.getInstance().startEffect(player, holder);
	}
	
	@Override
	public void stopEffect(Player player)
	{
		DressMeEffectManager.getInstance().stopEffect(player);
	}
	
	@Override
	public void reload()
	{
		DressMeData.getInstance().reload();
	}
	
	@Override
	public void apply(Player player, IDressMeSkin skin, boolean test)
	{
		if (player == null || skin == null)
			return;
		
		final String visualType = skin.getVisualTypeName();
		if (visualType != null && visualType.equalsIgnoreCase("WEAPON"))
		{
			DressMeState.setWeapon(player, skin);
			setWeaponVisual(player, skin.getRightHandId(), skin.getLeftHandId(), skin.getTwoHandId());
		}
		else
		{
			DressMeState.setArmor(player, skin);
			setDressVisual(player, skin.getChestId(), skin.getLegsId(), skin.getGlovesId(), skin.getFeetId(), skin.getHelmetId());
		}
		
		DressMeState.setActive(player, true);
		startEffect(player, skin);
		
		if (test && visualType != null)
			player.getMemos().set("dressme_" + visualType.toLowerCase(), skin.getSkillId());
	}
	
	@Override
	public void removeArmor(Player player)
	{
		if (player == null)
			return;
		stopEffect(player);
		DressMeState.setArmor(player, null);
		final Inventory inv = player.getInventory();
		inv.setPaperdollItemVisual(Paperdoll.CHEST);
		inv.setPaperdollItemVisual(Paperdoll.LEGS);
		inv.setPaperdollItemVisual(Paperdoll.GLOVES);
		inv.setPaperdollItemVisual(Paperdoll.FEET);
		inv.setPaperdollItemVisual(Paperdoll.HAIRALL);
		player.broadcastUserInfo();
		checkIfNoDressMe(player);
		player.getMemos().unset("dressme_armor");
	}
	
	@Override
	public void removeWeapon(Player player)
	{
		if (player == null)
			return;
		stopEffect(player);
		DressMeState.setWeapon(player, null);
		final Inventory inv = player.getInventory();
		inv.setPaperdollItemVisual(Paperdoll.RHAND);
		inv.setPaperdollItemVisual(Paperdoll.LHAND);
		inv.setPaperdollItemVisual(Paperdoll.LRHAND);
		player.broadcastUserInfo();
		checkIfNoDressMe(player);
		player.getMemos().unset("dressme_weapon");
	}
	
	@Override
	public void restore(Player player)
	{
		if (player == null)
			return;
		final String armorId = player.getMemos().get("dressme_armor");
		if (armorId != null && !armorId.isEmpty())
		{
			final IDressMeSkin armor = getBySkillId(Integer.parseInt(armorId));
			apply(player, armor, false);
		}
		final String weaponId = player.getMemos().get("dressme_weapon");
		if (weaponId != null && !weaponId.isEmpty())
		{
			final IDressMeSkin weapon = getBySkillId(Integer.parseInt(weaponId));
			apply(player, weapon, false);
		}
	}
	
	private static void checkIfNoDressMe(Player player)
	{
		if (DressMeState.armor(player) == null && DressMeState.weapon(player) == null)
			DressMeState.setActive(player, false);
	}
	
	private static void setDressVisual(Player player, int chest, int legs, int gloves, int feet, int helmet)
	{
		final Inventory inv = player.getInventory();
		if (chest > 0)
			inv.setPaperdollItemVisual(Paperdoll.CHEST);
		if (legs > 0)
			inv.setPaperdollItemVisual(Paperdoll.LEGS);
		if (gloves > 0)
			inv.setPaperdollItemVisual(Paperdoll.GLOVES);
		if (feet > 0)
			inv.setPaperdollItemVisual(Paperdoll.FEET);
		if (helmet > 0)
			inv.setPaperdollItemVisual(Paperdoll.HEAD);
		player.broadcastUserInfo();
	}
	
	private static void setWeaponVisual(Player player, int rhand, int lhand, int lrhand)
	{
		final Inventory inv = player.getInventory();
		if (rhand > 0)
			inv.setPaperdollItemVisual(Paperdoll.RHAND);
		if (lhand > 0)
			inv.setPaperdollItemVisual(Paperdoll.LHAND);
		if (lrhand > 0)
			inv.setPaperdollItemVisual(Paperdoll.LRHAND);
		player.broadcastUserInfo();
	}
}
