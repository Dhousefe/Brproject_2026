package ext.mods.global;

import br.project.spi.Extension;
import br.project.spi.ExtensionContext;
import ext.mods.Crypta.GlobalDropManager;
import ext.mods.extensions.hooks.GlobalDropHooks;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.instance.Monster;

/** Phase 3 SPI entry for global-drop. */
public final class GlobalDropExtension implements Extension {
	public static final String ID = "global-drop";

	@Override public String id() { return ID; }
	@Override public String version() { return "3.0.0"; }

	@Override
	public void onEnable(ExtensionContext context) {
		try {
			GlobalDropData.getInstance();
			GlobalDropManager.getInstance();
			GlobalDropHooks.register(new GlobalDropHooks.Api() {
				@Override
				public boolean shouldCancelOriginalDrop(Monster monster) {
					return GlobalDropManager.getInstance().shouldCancelOriginalDrop(monster);
				}

				@Override
				public void onKill(Player killer, Monster monster) {
					GlobalDropManager.getInstance().onKill(killer, monster);
				}

				@Override
				public void reload() {
					GlobalDropManager.getInstance().reload();
				}
			});
			context.info("global-drop hooks registered.");
		} catch (Throwable t) {
			context.warn("global-drop failed: " + t.getMessage());
			t.printStackTrace();
		}
	}

	@Override
	public void onDisable(ExtensionContext context) {
		GlobalDropHooks.clear();
	}
}
