package ext.mods.dungeon;

import br.project.spi.Extension;
import br.project.spi.ExtensionContext;
import ext.mods.dungeon.data.DungeonData;
import ext.mods.extensions.hooks.DungeonHooks;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.util.Tokenizer;

/** Phase 3 SPI entry for dungeon. */
public final class DungeonExtension implements Extension {
	public static final String ID = "dungeon";

	@Override public String id() { return ID; }
	@Override public String version() { return "3.0.0"; }

	@Override
	public void onEnable(ExtensionContext context) {
		try {
			DungeonData.getInstance();
			DungeonHooks.register(new DungeonHooks.Api() {
				@Override
				public void handleEnterDungeonId(Player player, Tokenizer tokenizer) {
					DungeonManager.getInstance().handleEnterDungeonId(player, tokenizer);
				}
			});
			context.info("dungeon hooks registered.");
		} catch (Throwable t) {
			context.warn("dungeon failed: " + t.getMessage());
			t.printStackTrace();
		}
	}

	@Override
	public void onDisable(ExtensionContext context) {
		DungeonHooks.clear();
	}
}
