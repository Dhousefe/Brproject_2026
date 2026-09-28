/*
* Copyleft © 2024-2026 L2Brproject
* * This file is part of L2Brproject derived from aCis409/RusaCis3.8
* * L2Brproject is free software: you can redistribute it and/or modify it
* under the terms of the GNU General Public License as published by the
* Free Software Foundation, either version 3 of the License.
* * L2Brproject is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* General Public License for more details.
* * You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
* Our main Developers, Dhousefe-L2JBR, Agazes33, Ban-L2jDev, Warman, SrEli.
* Our special thanks, Nattan Felipe, Diego Fonseca, Junin, ColdPlay, Denky, MecBew, Localhost, MundvayneHELLBOY, 
* SonecaL2, Eduardo.SilvaL2J, biLL, xpower, xTech, kakuzo, Tiagorosendo, Schuster, LucasStark, damedd
* as a contribution for the forum L2JBrasil.com
 */
package ext.mods.gameserver.network.serverpackets;

import java.util.ArrayList;
import java.util.List;

import ext.mods.gameserver.data.sql.ClanTable;
import ext.mods.gameserver.data.repository.CharacterSelection;
import ext.mods.gameserver.data.service.CharacterSelectionPersistenceService;
import ext.mods.gameserver.enums.Paperdoll;
import ext.mods.gameserver.model.CharSelectSlot;
import ext.mods.gameserver.model.pledge.Clan;
import ext.mods.gameserver.data.service.CharacterLifecycleService;

public class CharSelectInfo extends L2GameServerPacket
{
	private final CharSelectSlot[] _slots;
	private final String _loginName;
	private final int _sessionId;
	
	private int _activeId;
	
	public CharSelectInfo(String loginName, int sessionId)
	{
		_slots = loadCharSelectSlots(loginName);
		_sessionId = sessionId;
		_loginName = loginName;
		
		_activeId = -1;
	}
	
	public CharSelectInfo(String loginName, int sessionId, int activeId)
	{
		_slots = loadCharSelectSlots(loginName);
		_sessionId = sessionId;
		_loginName = loginName;
		
		_activeId = activeId;
	}
	
	@Override
	protected final void writeImpl()
	{
		final int size = _slots.length;
		
		writeC(0x13);
		writeD(size);
		
		long lastAccess = 0L;
		
		if (_activeId == -1)
		{
			for (int i = 0; i < size; i++)
				if (lastAccess < _slots[i].getLastAccess())
				{
					lastAccess = _slots[i].getLastAccess();
					_activeId = i;
				}
		}
		
		for (int i = 0; i < size; i++)
		{
			CharSelectSlot slot = _slots[i];
			
			writeS(slot.getName());
			writeD(slot.getCharId());
			writeS(_loginName);
			writeD(_sessionId);
			writeD(slot.getClanId());
			writeD(0x00);
			
			writeD(slot.getSex());
			writeD(slot.getRace());
			writeD(slot.getBaseClassId());
			
			writeD(0x01);
			
			writeD(slot.getX());
			writeD(slot.getY());
			writeD(slot.getZ());
			
			writeF(slot.getCurrentHp());
			writeF(slot.getCurrentMp());
			
			writeD(slot.getSp());
			writeQ(slot.getExp());
			writeD(slot.getLevel());
			
			writeD(slot.getKarma());
			writeD(slot.getPkKills());
			writeD(slot.getPvpKills());
			
			writeD(0x00);
			writeD(0x00);
			writeD(0x00);
			writeD(0x00);
			writeD(0x00);
			writeD(0x00);
			writeD(0x00);
			
			boolean isDressMe = slot.getArmorSkin() != null;
			boolean isWeaponSkin = slot.getWeaponSkin() != null;
			writeD(isDressMe ? slot.getArmorSkin().getHelmetId() : slot.getPaperdollObjectId(Paperdoll.HAIRALL));
			writeD(slot.getPaperdollObjectId(Paperdoll.REAR));
			writeD(slot.getPaperdollObjectId(Paperdoll.LEAR));
			writeD(slot.getPaperdollObjectId(Paperdoll.NECK));
			writeD(slot.getPaperdollObjectId(Paperdoll.RFINGER));
			writeD(slot.getPaperdollObjectId(Paperdoll.LFINGER));
			writeD(slot.getPaperdollObjectId(Paperdoll.HEAD));
			
			writeD(isWeaponSkin ? slot.getWeaponSkin().getRightHandId() : slot.getPaperdollObjectId(Paperdoll.RHAND));
			writeD(isWeaponSkin ? slot.getWeaponSkin().getLeftHandId() : slot.getPaperdollObjectId(Paperdoll.LHAND));
			
			writeD(isDressMe ? slot.getArmorSkin().getGlovesId() : slot.getPaperdollObjectId(Paperdoll.GLOVES));
			writeD(isDressMe ? slot.getArmorSkin().getChestId() : slot.getPaperdollObjectId(Paperdoll.CHEST));
			writeD(isDressMe ? slot.getArmorSkin().getLegsId() : slot.getPaperdollObjectId(Paperdoll.LEGS));
			writeD(isDressMe ? slot.getArmorSkin().getFeetId() : slot.getPaperdollObjectId(Paperdoll.FEET));
			
			writeD(slot.getPaperdollObjectId(Paperdoll.CLOAK));
			writeD(slot.getPaperdollObjectId(Paperdoll.RHAND));
			writeD(isDressMe ? slot.getArmorSkin().getHelmetId() : slot.getPaperdollObjectId(Paperdoll.HAIR));
			writeD(slot.getPaperdollObjectId(Paperdoll.FACE));
			
			writeD(isDressMe ? slot.getArmorSkin().getHelmetId() : slot.getPaperdollItemId(Paperdoll.HAIRALL));
			writeD(slot.getPaperdollItemId(Paperdoll.REAR));
			writeD(slot.getPaperdollItemId(Paperdoll.LEAR));
			writeD(slot.getPaperdollItemId(Paperdoll.NECK));
			writeD(slot.getPaperdollItemId(Paperdoll.RFINGER));
			writeD(slot.getPaperdollItemId(Paperdoll.LFINGER));
			writeD(slot.getPaperdollItemId(Paperdoll.HEAD));
			
			writeD(isWeaponSkin ? slot.getWeaponSkin().getRightHandId() : slot.getPaperdollItemId(Paperdoll.RHAND));
			writeD(isWeaponSkin ? slot.getWeaponSkin().getLeftHandId() : slot.getPaperdollItemId(Paperdoll.LHAND));
			
			writeD(isDressMe ? slot.getArmorSkin().getGlovesId() : slot.getPaperdollItemId(Paperdoll.GLOVES));
			writeD(isDressMe ? slot.getArmorSkin().getChestId() : slot.getPaperdollItemId(Paperdoll.CHEST));
			writeD(isDressMe ? slot.getArmorSkin().getLegsId() : slot.getPaperdollItemId(Paperdoll.LEGS));
			writeD(isDressMe ? slot.getArmorSkin().getFeetId() : slot.getPaperdollItemId(Paperdoll.FEET));
			writeD(slot.getPaperdollItemId(Paperdoll.CLOAK));
			writeD(isWeaponSkin ? slot.getWeaponSkin().getRightHandId() : slot.getPaperdollItemId(Paperdoll.RHAND));
			writeD(isDressMe ? slot.getArmorSkin().getHelmetId() : slot.getPaperdollItemId(Paperdoll.HAIR));
			writeD(isDressMe ? slot.getArmorSkin().getHelmetId() : slot.getPaperdollItemId(Paperdoll.FACE));
			
			writeD(slot.getHairStyle());
			writeD(slot.getHairColor());
			writeD(slot.getFace());
			
			writeF(slot.getMaxHp());
			writeF(slot.getMaxMp());
			
			writeD((slot.getAccessLevel() > -1) ? ((slot.getDeleteTimer() > 0) ? (int) ((slot.getDeleteTimer() - System.currentTimeMillis()) / 1000) : 0) : -1);
			writeD(slot.getClassId());
			writeD((i == _activeId) ? 0x01 : 0x00);
			writeC(Math.min(127, slot.getEnchantEffect()));
			writeD(slot.getAugmentationId());
		}
		getClient().setCharSelectSlot(_slots);
	}
	
	public CharSelectSlot[] getCharacterSlots()
	{
		return _slots;
	}
	
	private static CharSelectSlot[] loadCharSelectSlots(String loginName)
	{
		final List<CharSelectSlot> list = new ArrayList<>();
		
		try
		{
			for (CharacterSelection character : CharacterSelectionPersistenceService.findByAccount(loginName))
			{
				final int objectId = character.objectId();
				final long deleteTime = character.deleteTime();
				if (deleteTime > 0 && System.currentTimeMillis() > deleteTime)
				{
					final Clan clan = ClanTable.getInstance().getClan(character.clanId());
					if (clan != null)
						clan.removeClanMember(objectId, 0);
					CharacterLifecycleService.deleteCharacter(objectId);
					continue;
				}

				final CharSelectSlot slot = new CharSelectSlot(objectId, character.name(), character.paperdoll());
				slot.setAccessLevel(character.accessLevel());
				slot.setLevel(character.level());
				slot.setMaxHp(character.maxHp());
				slot.setCurrentHp(character.currentHp());
				slot.setMaxMp(character.maxMp());
				slot.setCurrentMp(character.currentMp());
				slot.setKarma(character.karma());
				slot.setPkKills(character.pkKills());
				slot.setPvpKills(character.pvpKills());
				slot.setFace(character.face());
				slot.setHairStyle(character.hairStyle());
				slot.setHairColor(character.hairColor());
				slot.setSex(character.sex());
				slot.setExp(character.exp());
				slot.setSp(character.sp());
				slot.setClanId(character.clanId());
				slot.setRace(character.race());
				slot.setX(character.x());
				slot.setY(character.y());
				slot.setZ(character.z());
				slot.setClassId(character.classId());
				slot.setAugmentationId(character.augmentationId());
				slot.setBaseClassId(character.baseClassId());
				slot.setDeleteTimer(deleteTime);
				slot.setLastAccess(character.lastAccess());
				list.add(slot);
			}
			return list.toArray(new CharSelectSlot[0]);
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't restore player slots for account {}.", loginName, e);
			System.err.println("[CharSelectInfo] Failed to restore player slots for account " + loginName);
			e.printStackTrace(System.err);
		}
		
		return new CharSelectSlot[0];
	}
}
