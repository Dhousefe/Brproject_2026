package ext.mods.dressme;

import br.project.spi.Extension;
import br.project.spi.ExtensionContext;
import ext.mods.extensions.hooks.DressMeHooks;

/** Phase 3 SPI entry for dressme. */
public final class DressMeExtension implements Extension {
	public static final String ID = "dressme";

	@Override public String id() { return ID; }
	@Override public String version() { return "3.0.0"; }

	@Override
	public void onEnable(ExtensionContext context) {
		try {
			DressMeData.getInstance();
			DressMeHooks.register(DressMeService.INSTANCE);
			context.info("dressme hooks + service registered.");
		} catch (Throwable t) {
			context.warn("dressme failed: " + t.getMessage());
			t.printStackTrace();
		}
	}

	@Override
	public void onDisable(ExtensionContext context) {
		DressMeHooks.clear();
	}
}
