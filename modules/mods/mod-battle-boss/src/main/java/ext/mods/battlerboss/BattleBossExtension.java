package ext.mods.battlerboss;

import br.project.spi.Extension;
import br.project.spi.ExtensionContext;
import ext.mods.Crypta.BattleBossData;
import ext.mods.battlerboss.register.BattleBossOpenRegister;
import ext.mods.battlerboss.tasks.BattleBossCountDownTask;
import ext.mods.extensions.hooks.BattleBossHooks;
import ext.mods.gameserver.model.actor.Attackable;
import ext.mods.gameserver.model.actor.Player;

/** Phase 3 SPI entry for battle-boss. */
public final class BattleBossExtension implements Extension {
	public static final String ID = "battle-boss";

	@Override public String id() { return ID; }
	@Override public String version() { return "3.0.0"; }

	@Override
	public void onEnable(ExtensionContext context) {
		try {
			BattleBossData.getInstance();
			BattleBossCountDownTask.getInstance().start();
			BattleBossHooks.register(new BattleBossHooks.Api() {
				@Override
				public void onPlayerDeath(Player player) {
					BattleBossOpenRegister.getInstance().onPlayerDeath(player);
				}

				@Override
				public void onPlayerDeathMonster(Attackable monster, Player killer) {
					BattleBossOpenRegister.getInstance().onPlayerDeathMonster(killer, monster);
				}
			});
			context.info("battle-boss hooks registered.");
		} catch (Throwable t) {
			context.warn("battle-boss failed: " + t.getMessage());
			t.printStackTrace();
		}
	}

	@Override
	public void onDisable(ExtensionContext context) {
		BattleBossHooks.clear();
	}
}
