/*
* Copyleft © 2024-2026 L2Brproject
* * This file is part of L2Brproject derived from aCis409/RusaCis3.8
 */
package ext.mods.gameserver.handler.voicedcommandhandlers;

import ext.mods.extensions.hooks.TranslatorHooks;
import ext.mods.gameserver.handler.IVoicedCommandHandler;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.player.TranslatorState;

/**
 * Handler para o comando de voz .translation/.trans que abre o menu de linguagens.
 * Feature implementation is provided by TranslatorHooks (mod-crypta).
 */
public class LanguageCommand implements IVoicedCommandHandler
{
	private static final String[] COMMANDS = { "translation", "trans", "toggletrans", "toggletranslation" };
	
	@Override
	public boolean useVoicedCommand(String command, Player player, String target)
	{
		if (command.equals("translation") || command.equals("trans"))
		{
			if (TranslatorHooks.get().isTranslatorAvailable())
			{
				TranslatorHooks.get().showLanguageMenu(player);
				return true;
			}
			player.sendMessage("Sistema de tradução não disponível no momento.");
			return false;
		}
		else if (command.equals("toggletrans") || command.equals("toggletranslation"))
		{
			TranslatorState.switchTranslatePreference(player);
			boolean enabled = TranslatorState.isHtmlTranslationEnabled(player);
			player.sendMessage("Tradução HTML " + (enabled ? "habilitada" : "desabilitada") + ".");
			return true;
		}
		return false;
	}
	
	@Override
	public String[] getVoicedCommandList()
	{
		return COMMANDS;
	}
}
