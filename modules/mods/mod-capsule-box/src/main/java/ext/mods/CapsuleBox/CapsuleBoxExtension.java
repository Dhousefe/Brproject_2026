package ext.mods.CapsuleBox;

import br.project.spi.Extension;
import br.project.spi.ExtensionContext;
import ext.mods.extensions.hooks.CapsuleBoxHooks;

/** Phase 3 SPI entry for capsule-box. */
public final class CapsuleBoxExtension implements Extension {
	public static final String ID = "capsule-box";

	@Override public String id() { return ID; }
	@Override public String version() { return "3.0.0"; }

	@Override
	public void onEnable(ExtensionContext context) {
		try {
			CapsuleBoxData.getInstance();
			CapsuleBoxHooks.register(new CapsuleBoxHooks.Api() {
				@Override
				public void reload() {
					CapsuleBoxData.getInstance().reload();
				}
			});
			context.info("capsule-box hooks registered.");
		} catch (Throwable t) {
			context.warn("capsule-box failed: " + t.getMessage());
			t.printStackTrace();
		}
	}

	@Override
	public void onDisable(ExtensionContext context) {
		CapsuleBoxHooks.clear();
	}
}
