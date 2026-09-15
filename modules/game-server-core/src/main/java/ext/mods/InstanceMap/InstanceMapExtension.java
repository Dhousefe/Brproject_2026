package ext.mods.InstanceMap;

import br.project.spi.Extension;
import br.project.spi.ExtensionContext;

/** Phase 3 SPI entry for instance-map. */
public final class InstanceMapExtension implements Extension {
	public static final String ID = "instance-map";
	@Override public String id() { return ID; }
	@Override public String version() { return "3.0.0"; }
	@Override public void onEnable(ExtensionContext context) {
		try {
			// InstanceMap used as infrastructure by dungeon/tour
			context.info("instance-map enabled.");
		} catch (Throwable t) {
			context.warn("instance-map failed: " + t.getMessage());
			t.printStackTrace();
		}
	}
}
