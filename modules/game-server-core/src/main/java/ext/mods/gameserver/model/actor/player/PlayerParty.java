package ext.mods.gameserver.model.actor.player;

import ext.mods.gameserver.data.manager.PartyMatchRoomManager;
import ext.mods.gameserver.enums.LootRule;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.group.Party;
import ext.mods.gameserver.model.group.PartyMatchRoom;

/**
 * Party membership + party match room surface for a {@link Player} (att-ver-3.0 onda 6).
 */
public final class PlayerParty
{
	private final Player _owner;
	
	private Party _party;
	private int _partyRoom;
	private LootRule _lootRule;
	
	public PlayerParty(Player owner)
	{
		_owner = owner;
	}
	
	public boolean isInParty()
	{
		return _party != null;
	}
	
	public Party getParty()
	{
		return _party;
	}
	
	public void setParty(Party party)
	{
		_party = party;
	}
	
	public int getPartyRoom()
	{
		return _partyRoom;
	}
	
	public void setPartyRoom(int id)
	{
		_partyRoom = id;
	}
	
	public boolean isInPartyMatchRoom()
	{
		return _partyRoom > 0;
	}
	
	public void removeMeFromPartyMatch()
	{
		PartyMatchRoomManager.getInstance().removeWaitingPlayer(_owner);
		
		if (_partyRoom > 0)
		{
			final PartyMatchRoom room = PartyMatchRoomManager.getInstance().getRoom(_partyRoom);
			if (room != null)
				room.removeMember(_owner);
		}
	}
	
	public LootRule getLootRule()
	{
		return _lootRule;
	}
	
	public void setLootRule(LootRule lootRule)
	{
		_lootRule = lootRule;
	}
}
