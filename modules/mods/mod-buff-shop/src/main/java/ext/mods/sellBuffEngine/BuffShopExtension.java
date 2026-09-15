package ext.mods.sellBuffEngine;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

import br.project.spi.Extension;
import br.project.spi.ExtensionContext;
import ext.mods.Config;
import ext.mods.extensions.hooks.BuffShopHooks;
import ext.mods.gameserver.model.World;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.config.ConfigProject;

/** Phase 3 SPI entry for buff-shop. */
public final class BuffShopExtension implements Extension {
	public static final String ID = "buff-shop";

	@Override public String id() { return ID; }
	@Override public String version() { return "3.0.0"; }

	@Override
	public void onEnable(ExtensionContext context) {
		try {
			if (ConfigProject.SELLBUFF_ENABLED) {
				try {
					Class.forName("ext.mods.gameserver.data.manager.SellBuffsManager");
					BuffShopConfigs.getInstance().loadConfigs();
					BuffShopManager.getInstance().restoreOfflineTraders();
				} catch (Exception e) {
					context.warn(e.getMessage());
				}
			}

			BuffShopHooks.register(new BuffShopHooks.Api() {
				@Override
				public boolean handleBypass(Player player, String command) {
					if (player == null || command == null)
						return false;
					BuffShopBypassHandler.getInstance().handleBypass(player, command);
					return true;
				}

				@Override
				public Collection<? extends Player> getSellers() {
					return BuffShopManager.getInstance().getSellers().keySet().stream()
						.map(id -> World.getInstance().getPlayer(id))
						.filter(Objects::nonNull)
						.collect(Collectors.toList());
				}

				@Override
				public boolean isSeller(int objectId) {
					return BuffShopManager.getInstance().getSellers().containsKey(objectId);
				}

				@Override
				public Integer getSellerOwnerId(int objectId) {
					return BuffShopManager.getInstance().getSellers().get(objectId);
				}

				@Override
				public void showIndexWindow(Player player) {
					BuffShopUIManager.getInstance().showIndexWindow(player, null);
				}

				@Override
				public void showBuffRemovalWindow(Player player) {
					BuffShopUIManager.getInstance().showBuffRemovalWindow(player, "player");
				}
			});
			context.info("buff-shop hooks registered.");
		} catch (Throwable t) {
			context.warn("buff-shop failed: " + t.getMessage());
			t.printStackTrace();
		}
	}

	@Override
	public void onDisable(ExtensionContext context) {
		BuffShopHooks.clear();
	}
}
