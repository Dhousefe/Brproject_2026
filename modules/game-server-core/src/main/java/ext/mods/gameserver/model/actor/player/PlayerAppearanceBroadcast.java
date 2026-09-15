/*
* Copyleft © 2024-2026 L2Brproject
* This file is part of L2Brproject derived from aCis409/RusaCis3.8
* L2Brproject is free software: you can redistribute it and/or modify it
* under the terms of the GNU General Public License as published by the
* Free Software Foundation, either version 3 of the License.
*/
package ext.mods.gameserver.model.actor.player;

import ext.mods.commons.pool.ThreadPool;
import ext.mods.gameserver.data.xml.NpcData;
import ext.mods.gameserver.enums.Paperdoll;
import ext.mods.gameserver.model.WorldObject;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.item.instance.ItemInstance;
import ext.mods.gameserver.model.item.kind.Item;
import ext.mods.gameserver.network.serverpackets.AbstractNpcInfo;
import ext.mods.gameserver.network.serverpackets.CharInfo;
import ext.mods.gameserver.network.serverpackets.L2GameServerPacket;
import ext.mods.gameserver.network.serverpackets.RelationChanged;
import ext.mods.gameserver.network.serverpackets.TitleUpdate;
import ext.mods.gameserver.network.serverpackets.UserInfo;

/**
 * Visibility, broadcasting, collision, polymorph, Varka/Ketra alliance and
 * appearance-related queries for a {@link Player}.
 *
 * <p>Owns the bodies of {@link #broadcastUserInfo()}, {@link #broadcastCharInfo()},
 * {@link #broadcastTitleInfo()}, {@link #broadcastPacket(L2GameServerPacket, boolean)},
 * {@link #broadcastPacketInRadius(L2GameServerPacket, int)},
 * {@link #setTemporarilyVisible(int)}, {@link #isVisibleTo(WorldObject)},
 * {@link #setAllianceWithVarkaKetra(int)}, {@link #getAllianceWithVarkaKetra()},
 * {@link #isAlliedWithVarka()}, {@link #isAlliedWithKetra()},
 * {@link #getCollisionRadius()}, {@link #getCollisionHeight()},
 * {@link #polymorph(int)}, {@link #unpolymorph()} and {@link #isWearingFormalWear()}.</p>
 */
public final class PlayerAppearanceBroadcast
{
	private final Player _owner;

	public PlayerAppearanceBroadcast(Player owner)
	{
		_owner = owner;
	}

	/**
	 * @return The {@link Player} owning this component.
	 */
	public Player getOwner()
	{
		return _owner;
	}

	/**
	 * Revela temporariamente um GM invisível (ex.: ao atacar um NPC), evitando que a IA entre em loop
	 * e trave o servidor. Após o tempo indicado, a invisibilidade é reaplicada.
	 * @param seconds Tempo em segundos até voltar a ficar invisível (ex.: 10).
	 */
	public void setTemporarilyVisible(int seconds)
	{
		if (!_owner.isGM() || _owner.getAppearance().isVisible() || seconds <= 0)
			return;
		_owner.getProtect().cancelGmRehideTask();
		_owner.getAppearance().setVisible(true);
		_owner.broadcastUserInfo();
		_owner.getProtect().setGmRehideTask(ThreadPool.schedule(() ->
		{
			_owner.getProtect().setGmRehideTask(null);
			if (_owner.isGM() && !_owner.isInObserverMode())
			{
				_owner.getAppearance().setVisible(false);
				_owner.decayMe();
				_owner.broadcastUserInfo();
				_owner.spawnMe();
			}
		}, seconds * 1000L));
	}

	public boolean isVisibleTo(WorldObject wo)
	{
		boolean notVisible = _owner.isGM() && !_owner.getAppearance().isVisible();
		return !notVisible || (wo instanceof Player pc && pc.isGM());
	}

	public void broadcastPacket(L2GameServerPacket packet, boolean selfToo)
	{
		if (selfToo)
			_owner.sendPacket(packet);

		_owner.superBroadcastPacket(packet, selfToo);
	}

	public void broadcastPacketInRadius(L2GameServerPacket packet, int radius)
	{
		_owner.sendPacket(packet);

		_owner.superBroadcastPacketInRadius(packet, radius);
	}

	/**
	 * Broadcast informations from a user to himself and his knownlist.<BR>
	 * If player is morphed, it sends informations from the template the player is using.
	 * <ul>
	 * <li>Send a UserInfo packet (public and private data) to this Player.</li>
	 * <li>Send a CharInfo packet (public data only) to Player's knownlist.</li>
	 * </ul>
	 */
	public final void broadcastUserInfo()
	{
		_owner.sendPacket(new UserInfo(_owner));

		if (_owner.getPolymorphTemplate() != null)
			broadcastPacket(new AbstractNpcInfo.PcMorphInfo(_owner, _owner.getPolymorphTemplate()), false);
		else
			broadcastCharInfo();
	}

	public final void broadcastCharInfo()
	{
		_owner.forEachKnownType(Player.class, player ->
		{
			if (!isVisibleTo(player))
				return;

			player.sendPacket(new CharInfo(_owner));

			final int relation = _owner.getRelation(player);
			final boolean isAutoAttackable = _owner.isAttackableWithoutForceBy(player);

			player.sendPacket(new RelationChanged(_owner, relation, isAutoAttackable));
			if (_owner.getSummon() != null)
				player.sendPacket(new RelationChanged(_owner.getSummon(), relation, isAutoAttackable));
		});
	}

	/**
	 * Broadcast player title information.
	 */
	public final void broadcastTitleInfo()
	{
		_owner.sendPacket(new UserInfo(_owner));
		broadcastPacket(new TitleUpdate(_owner), true);
	}

	public void setAllianceWithVarkaKetra(int sideAndLvlOfAlliance)
	{
		_owner.getPvP().setAllianceWithVarkaKetra(sideAndLvlOfAlliance);
	}

	/**
	 * [-5,-1] varka, 0 neutral, [1,5] ketra
	 * @return the side faction.
	 */
	public int getAllianceWithVarkaKetra()
	{
		return _owner.getPvP().getAllianceWithVarkaKetra();
	}

	public boolean isAlliedWithVarka()
	{
		return _owner.getPvP().getAllianceWithVarkaKetra() < 0;
	}

	public boolean isAlliedWithKetra()
	{
		return _owner.getPvP().getAllianceWithVarkaKetra() > 0;
	}

	public double getCollisionRadius()
	{
		return (_owner.isMounted()) ? NpcData.getInstance().getTemplate(_owner.getMountNpcId()).getCollisionRadius() : _owner.getBaseTemplate().getCollisionRadiusBySex(_owner.getAppearance().getSex());
	}

	public double getCollisionHeight()
	{
		return (_owner.isMounted()) ? NpcData.getInstance().getTemplate(_owner.getMountNpcId()).getCollisionHeight() : _owner.getBaseTemplate().getCollisionHeightBySex(_owner.getAppearance().getSex());
	}

	public boolean polymorph(int npcId)
	{
		if (_owner.superPolymorph(npcId))
		{
			_owner.sendPacket(new UserInfo(_owner));
			return true;
		}
		return false;
	}

	public void unpolymorph()
	{
		_owner.superUnpolymorph();
		_owner.sendPacket(new UserInfo(_owner));
	}

	/**
	 * @return true if this {@link Player} is currently wearing a Formal Wear.
	 */
	public boolean isWearingFormalWear()
	{
		final ItemInstance formal = _owner.getInventory().getItemFrom(Paperdoll.CHEST);
		return formal != null && formal.getItem().getBodyPart() == Item.SLOT_ALLDRESS;
	}
}
