package ext.mods.gameserver.model.actor.player;

import ext.mods.extensions.hooks.TranslatorHooks;
import ext.mods.gameserver.model.actor.Player;

/** HTML translation preference + DeepL hooks (no fields on Player). */
public final class TranslatorState
{
	private TranslatorState()
	{
	}
	
	public static boolean isHtmlTranslationEnabled(Player player)
	{
		return player != null && player.getMemos().getBool("html_translation", true);
	}
	
	public static void switchTranslatePreference(Player player)
	{
		if (player != null)
			player.getMemos().set("html_translation", !isHtmlTranslationEnabled(player));
	}
	
	public static boolean isApiActive()
	{
		try
		{
			return TranslatorHooks.get().isTranslatorAvailable();
		}
		catch (Exception e)
		{
			return false;
		}
	}
	
	public static void setLanguage(Player player, String langCode)
	{
		if (player != null)
			TranslatorHooks.get().setPlayerLanguage(player, langCode);
	}
	
	public static Object getLanguage(Player player)
	{
		return player == null ? null : TranslatorHooks.get().getPlayerLanguage(player);
	}
	
	public static void showLanguageMenu(Player player)
	{
		if (player != null)
			TranslatorHooks.get().showLanguageMenu(player);
	}
	
	public static Object translatorApi()
	{
		return TranslatorHooks.get();
	}
}
