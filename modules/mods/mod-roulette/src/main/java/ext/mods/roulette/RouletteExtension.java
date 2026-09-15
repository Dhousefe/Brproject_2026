package ext.mods.roulette;

import br.project.spi.Extension;
import br.project.spi.ExtensionContext;

/** Phase 3 SPI entry for roulette. */
public final class RouletteExtension implements Extension {
	public static final String ID = "roulette";
	@Override public String id() { return ID; }
	@Override public String version() { return "3.0.0"; }
	@Override public void onEnable(ExtensionContext context) {
		try {
			ext.mods.roulette.RouletteData.getInstance();
			context.info("roulette enabled.");
		} catch (Throwable t) {
			context.warn("roulette failed: " + t.getMessage());
			t.printStackTrace();
		}
	}
	@Override public void onDisable(ExtensionContext context) {
		context.info("roulette disabled.");
	}
}
