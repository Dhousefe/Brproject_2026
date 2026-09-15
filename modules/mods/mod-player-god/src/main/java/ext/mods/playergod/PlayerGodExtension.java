package ext.mods.playergod;

import br.project.spi.Extension;
import br.project.spi.ExtensionContext;
import ext.mods.extensions.hooks.PlayerGodHooks;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.playergod.data.PlayerGodData;

/** Phase 3 SPI entry for player-god. */
public final class PlayerGodExtension implements Extension {
	public static final String ID = "player-god";

	@Override public String id() { return ID; }
	@Override public String version() { return "3.0.0"; }

	@Override
	public void onEnable(ExtensionContext context) {
		try {
			PlayerGodData.getInstance();
			PlayerGodHooks.register(new PlayerGodHooks.Api() {
				@Override
				public void onKill(Player killer) {
					PlayerGodManager.getInstance().onKill(killer);
				}

				@Override
				public void onEnterWorld(Player player) {
					PlayerGodManager.getInstance().onEnterWorld(player);
				}
			});
			context.info("player-god hooks registered.");
		} catch (Throwable t) {
			context.warn("player-god failed: " + t.getMessage());
			t.printStackTrace();
		}
	}

	@Override
	public void onDisable(ExtensionContext context) {
		PlayerGodHooks.clear();
	}
}
