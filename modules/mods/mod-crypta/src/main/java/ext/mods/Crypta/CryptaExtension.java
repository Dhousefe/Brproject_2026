package ext.mods.Crypta;

import java.util.concurrent.TimeUnit;

import br.project.spi.Extension;
import br.project.spi.ExtensionContext;
import ext.mods.extensions.hooks.TranslatorHooks;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.network.serverpackets.NpcHtmlMessage;

/** Phase 3 SPI entry for crypta-data (TranslatorHooks + shared Crypta services). */
public final class CryptaExtension implements Extension {
	public static final String ID = "crypta-data";

	@Override public String id() { return ID; }
	@Override public String version() { return "3.0.0"; }

	@Override
	public void onEnable(ExtensionContext context) {
		try {
			// Eager-init translator singleton (async API key load).
			DeeplTranslator.getInstance();

			TranslatorHooks.register(new TranslatorHooks.Api() {
				@Override
				public boolean isTranslatorAvailable() {
					return DeeplTranslator.getInstance().isTranslatorAvailable();
				}

				@Override
				public boolean accountLimitReached(Player player) {
					return DeeplTranslator.getInstance().accountLimitReached();
				}

				@Override
				public String translateText(Player player, String text) {
					try {
						DeeplTranslator.Language lang = DeeplTranslator.getInstance().getPlayerLanguage(player);
						return DeeplTranslator.getInstance().translateText(lang, text).get(20, TimeUnit.SECONDS);
					} catch (Exception e) {
						return text;
					}
				}

				@Override
				public String translateFile(Player player, String path) {
					try {
						DeeplTranslator.Language lang = DeeplTranslator.getInstance().getPlayerLanguage(player);
						Boolean ok = DeeplTranslator.getInstance().translateFile(path, lang).get(60, TimeUnit.SECONDS);
						return Boolean.TRUE.equals(ok) ? path : null;
					} catch (Exception e) {
						return null;
					}
				}

				@Override
				public boolean handlePacket(Player player, String html) {
					// Bridge String path/html → NpcHtmlMessage used by DeeplTranslator.
					NpcHtmlMessage msg = new NpcHtmlMessage(0);
					if (html != null && (html.endsWith(".htm") || html.endsWith(".html") || html.contains("/")))
						msg.setFileName(html);
					else if (html != null)
						msg.setHtml(html);
					return DeeplTranslator.getInstance().handlePacket(player, msg);
				}

				@Override
				public boolean handleHtmlPacket(Player player, NpcHtmlMessage msg) {
					return DeeplTranslator.getInstance().handlePacket(player, msg);
				}

				@Override
				public void setPlayerLanguage(Player player, String langCode) {
					DeeplTranslator.getInstance().setPlayerLanguage(player, langCode);
				}

				@Override
				public String getPlayerLanguage(Player player) {
					DeeplTranslator.Language lang = DeeplTranslator.getInstance().getPlayerLanguage(player);
					return lang != null ? lang.getCode() : "en";
				}

				@Override
				public void showLanguageMenu(Player player) {
					DeeplTranslator.getInstance().showLanguageMenu(player);
				}
			});
			context.info("crypta-data TranslatorHooks registered.");
		} catch (Throwable t) {
			context.warn("crypta-data failed: " + t.getMessage());
			t.printStackTrace();
		}
	}

	@Override
	public void onDisable(ExtensionContext context) {
		TranslatorHooks.clear();
	}
}
