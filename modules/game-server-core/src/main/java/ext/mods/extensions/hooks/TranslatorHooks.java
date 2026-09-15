package ext.mods.extensions.hooks;

import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.network.serverpackets.NpcHtmlMessage;

public final class TranslatorHooks
{
	public interface Api
	{
		boolean isTranslatorAvailable();

		boolean accountLimitReached(Player player);

		String translateText(Player player, String text);

		String translateFile(Player player, String path);

		boolean handlePacket(Player player, String html);

		default boolean handleHtmlPacket(Player player, NpcHtmlMessage message)
		{
			return handlePacket(player, message != null ? message.getFileName() : null);
		}

		void setPlayerLanguage(Player player, String langCode);

		String getPlayerLanguage(Player player);

		void showLanguageMenu(Player player);
	}

	private static final Api NOOP = new Api()
	{
		@Override public boolean isTranslatorAvailable() { return false; }
		@Override public boolean accountLimitReached(Player player) { return false; }
		@Override public String translateText(Player player, String text) { return text; }
		@Override public String translateFile(Player player, String path) { return null; }
		@Override public boolean handlePacket(Player player, String html) { return false; }
		@Override public boolean handleHtmlPacket(Player player, NpcHtmlMessage message) { return true; }
		@Override public void setPlayerLanguage(Player player, String langCode) {}
		@Override public String getPlayerLanguage(Player player) { return "en"; }
		@Override public void showLanguageMenu(Player player) {}
	};

	private static volatile Api api = NOOP;

	private TranslatorHooks() {}

	public static void register(Api implementation) { api = implementation != null ? implementation : NOOP; }
	public static void clear() { api = NOOP; }
	public static Api get() { return api; }
}
