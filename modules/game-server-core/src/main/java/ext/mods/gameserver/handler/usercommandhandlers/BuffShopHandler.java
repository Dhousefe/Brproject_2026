/*
* Copyleft © 2024-2026 L2Brproject
* * This file is part of L2Brproject derived from aCis409/RusaCis3.8
 */
package ext.mods.gameserver.handler.usercommandhandlers;

import ext.mods.extensions.hooks.BuffShopHooks;
import ext.mods.gameserver.handler.IUserCommandHandler;
import ext.mods.gameserver.model.actor.Player;

public class BuffShopHandler implements IUserCommandHandler
{
	private static final int[] COMMAND_IDS =
	{
		203
	};
	
	@Override
	public void useUserCommand(int id, Player player)
	{
		BuffShopHooks.get().showIndexWindow(player);
	}
	
	@Override
	public int[] getUserCommandList()
	{
		return COMMAND_IDS;
	}
}
