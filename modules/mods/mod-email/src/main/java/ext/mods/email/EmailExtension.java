package ext.mods.email;

import br.project.spi.Extension;
import br.project.spi.ExtensionContext;

/** Phase 3 SPI entry for email-items. */
public final class EmailExtension implements Extension {
	public static final String ID = "email-items";
	@Override public String id() { return ID; }
	@Override public String version() { return "3.0.0"; }
	@Override public void onEnable(ExtensionContext context) {
		try {
			ext.mods.email.task.EmailDeliveryTask.getInstance().loadAllPending();
			context.info("email-items enabled.");
		} catch (Throwable t) {
			context.warn("email-items failed: " + t.getMessage());
			t.printStackTrace();
		}
	}
	@Override public void onDisable(ExtensionContext context) {
		context.info("email-items disabled.");
	}
}
