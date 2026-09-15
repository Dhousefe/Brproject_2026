package ext.mods.gameserver.model.actor.player;

import ext.mods.gameserver.enums.actors.OperateType;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.multisell.PreparedListContainer;

/**
 * Operate type / craft / crystallize / multisell for a {@link Player}.
 */
public final class PlayerOperate
{
	private final Player _owner;
	
	private OperateType _operateType = OperateType.NONE;
	private PreparedListContainer _currentMultiSell;
	private boolean _isCrystallizing;
	private boolean _isCrafting;
	
	public PlayerOperate(Player owner)
	{
		_owner = owner;
	}
	
	public OperateType getOperateType()
	{
		return _operateType;
	}
	
	public void setOperateType(OperateType type)
	{
		_operateType = type;
		_owner.getPrivateStore().onOperateTypeChanged(type);
	}
	
	public boolean isOperating()
	{
		return _operateType != OperateType.NONE;
	}
	
	public boolean isCrafting()
	{
		return _isCrafting;
	}
	
	public void setCrafting(boolean state)
	{
		_isCrafting = state;
	}
	
	public boolean isCrystallizing()
	{
		return _isCrystallizing;
	}
	
	public void setCrystallizing(boolean mode)
	{
		_isCrystallizing = mode;
	}
	
	public PreparedListContainer getCurrentMultiSell()
	{
		return _currentMultiSell;
	}
	
	public void setCurrentMultiSell(PreparedListContainer list)
	{
		_currentMultiSell = list;
	}
}
