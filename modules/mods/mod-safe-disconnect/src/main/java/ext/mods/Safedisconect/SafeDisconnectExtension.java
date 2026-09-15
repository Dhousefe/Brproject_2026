package ext.mods.Safedisconect;

import br.project.spi.Extension;
import br.project.spi.ExtensionContext;
import ext.mods.extensions.hooks.SafeDisconnectHooks;

public final class SafeDisconnectExtension implements Extension {
	public static final String ID = "safe-disconnect";
	@Override public String id() { return ID; }
	@Override public String version() { return "3.0.0"; }
	@Override public void onEnable(ExtensionContext context) {
		SafeDisconnectHooks.register(new SafeDisconnectHooks.Api() {
			@Override public void markExpectedLogout(ext.mods.gameserver.model.actor.Player player) {
				SafeDisconnectManager.getInstance().markExpectedLogout(player);
			}
			@Override public boolean handleDisconnect(ext.mods.gameserver.network.GameClient client) {
				return SafeDisconnectManager.getInstance().handleDisconnect(client);
			}
			@Override public void onEnterWorld(ext.mods.gameserver.model.actor.Player player) {
				SafeDisconnectManager.getInstance().onEnterWorld(player);
			}
			@Override public boolean prepareReconnect(ext.mods.gameserver.model.actor.Player player, ext.mods.gameserver.network.GameClient newClient) {
				return SafeDisconnectManager.getInstance().prepareReconnect(player, newClient);
			}
			@Override public boolean isSafeDisconnectActive(ext.mods.gameserver.model.actor.Player player) {
				return SafeDisconnectManager.getInstance().isSafeDisconnectActive(player);
			}
		});
		context.info("SafeDisconnect hooks registered.");
	}
	@Override public void onDisable(ExtensionContext context) {
		SafeDisconnectHooks.clear();
	}
}
