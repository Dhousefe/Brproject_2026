package ext.mods.PixMod;

import br.project.spi.Extension;
import br.project.spi.ExtensionContext;
import ext.mods.Config;
import ext.mods.PixMod.donation.DonationBypassAdapter;
import ext.mods.PixMod.donationmanager.DonationManager;
import ext.mods.extensions.hooks.DonationHooks;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.config.ConfigDonation;

/** Phase 3 SPI entry for pix-donation. */
public final class PixModExtension implements Extension {
	public static final String ID = "pix-donation";

	@Override public String id() { return ID; }
	@Override public String version() { return "3.0.0"; }

	@Override
	public void onEnable(ExtensionContext context) {
		try {
			if (ConfigDonation.ENABLE_PIX_MOD && ConfigDonation.DONATION_ENABLED)
			{
				final DonationManager manager = DonationManager.getInstance();
				br.project.spi.donation.DonationService.Provider.register(manager);
			}

			DonationHooks.register(new DonationHooks.Api() {
				@Override
				public void offlinePlayer(Player player) {
					if (ConfigDonation.ENABLE_PIX_MOD && ConfigDonation.DONATION_ENABLED)
						DonationManager.getInstance().offlinePlayer(player);
				}

				@Override
				public boolean handleBypass(Player player, String command) {
					return DonationBypassAdapter.tryHandle(player, command);
				}
			});
			context.info("pix-donation hooks and DonationService registered.");
		} catch (Throwable t) {
			context.warn("pix-donation failed: " + t.getMessage());
			t.printStackTrace();
		}
	}

	@Override
	public void onDisable(ExtensionContext context) {
		DonationHooks.clear();
		br.project.spi.donation.DonationService.Provider.register(br.project.spi.donation.DonationService.NOOP);
	}
}
