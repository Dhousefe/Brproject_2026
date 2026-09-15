package ext.mods.aghation;

import java.util.List;

import br.project.spi.Extension;
import br.project.spi.ExtensionContext;
import ext.mods.Crypta.AgathionData;
import ext.mods.extensions.api.IAgathionSpec;
import ext.mods.extensions.hooks.AgathionHooks;

/** Phase 3 SPI entry for agathion. */
public final class AgathionExtension implements Extension {
	public static final String ID = "agathion";

	@Override public String id() { return ID; }
	@Override public String version() { return "3.0.0"; }

	@Override
	public void onEnable(ExtensionContext context) {
		try {
			AgathionData.getInstance();
			AgathionHooks.register(new AgathionHooks.Api() {
				@Override
				public List<? extends IAgathionSpec> getAgathionsByItemId(int itemId) {
					return AgathionData.getInstance().getAgathionsByItemId(itemId);
				}
			});
			context.info("agathion hooks registered.");
		} catch (Throwable t) {
			context.warn("agathion failed: " + t.getMessage());
			t.printStackTrace();
		}
	}

	@Override
	public void onDisable(ExtensionContext context) {
		AgathionHooks.clear();
	}
}
