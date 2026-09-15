/*
* Copyleft © 2024-2026 L2Brproject
* * This file is part of L2Brproject derived from aCis409/RusaCis3.8
 */
package ext.mods.gameserver.handler.voicedcommandhandlers;

import ext.mods.extensions.hooks.BuffShopHooks;
import ext.mods.gameserver.handler.IVoicedCommandHandler;
import ext.mods.gameserver.model.actor.Player;

public class BuffManagerVCmd implements IVoicedCommandHandler
{
	private static final String[] VOICED_COMMANDS =
	{
		"rembuff"
	};
	
	@Override
	public boolean useVoicedCommand(String command, Player activeChar, String target)
	{
		if (command.equalsIgnoreCase("rembuff"))
		{
			BuffShopHooks.get().showBuffRemovalWindow(activeChar);
		}
		return true;
	}
	
	@Override
	public String[] getVoicedCommandList()
	{
		return VOICED_COMMANDS;
	}
}
