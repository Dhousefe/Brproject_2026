package ext.mods.summonmobitem;

import br.project.spi.Extension;
import br.project.spi.ExtensionContext;
import ext.mods.extensions.hooks.SummonMobHooks;

/** Phase 3 SPI entry for summon-mob. */
public final class SummonMobExtension implements Extension {
	public static final String ID = "summon-mob";

	@Override public String id() { return ID; }
	@Override public String version() { return "3.0.0"; }

	@Override
	public void onEnable(ExtensionContext context) {
		try {
			SummonMobItemData.getInstance();
			SummonMobHooks.register(new SummonMobHooks.Api() {
				@Override
				public void ensureLoaded() {
					SummonMobItemData.getInstance();
				}

				@Override
				public void resetDailyCounters() {
					SummonMobItemData.getInstance().resetDailyCounters();
				}

				@Override
				public void reload() {
					SummonMobItemData.getInstance().load();
				}

				@Override
				public int getLoadedCount() {
					return SummonMobItemData.getInstance().getLoadedCount();
				}
			});
			context.info("summon-mob hooks registered.");
		} catch (Throwable t) {
			context.warn("summon-mob failed: " + t.getMessage());
			t.printStackTrace();
		}
	}

	@Override
	public void onDisable(ExtensionContext context) {
		SummonMobHooks.clear();
	}
}
