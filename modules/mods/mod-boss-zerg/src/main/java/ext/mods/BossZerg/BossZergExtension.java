package ext.mods.BossZerg;

import br.project.spi.Extension;
import br.project.spi.ExtensionContext;
import ext.mods.extensions.hooks.HealHooks;
import ext.mods.config.ConfigBossZerg;

/**
 * SPI entry for the BossZerg anti-zerg raid feature.
 * Discovered via {@code META-INF/services/br.project.spi.Extension}.
 */
public final class BossZergExtension implements Extension
{
	public static final String ID = "boss-zerg";
	
	@Override
	public String id()
	{
		return ID;
	}
	
	@Override
	public String version()
	{
		return "1.2.0";
	}
	
	@Override
	public void onLoad(ExtensionContext context)
	{
		context.info("BossZerg extension loaded (SPI).");
	}
	
	@Override
	public void onEnable(ExtensionContext context)
	{
		// Register heal pipeline before manager ticks
		HealHooks.setAdjuster((effector, effected, amount) -> BossZergManager.getInstance().applyHealPenalty(effector, effected, amount));
		// Ensures singleton starts scheduled ticks (respects ConfigBossZerg.BOSS_ZERG_ENABLED)
		BossZergManager.getInstance();
		context.info("BossZerg extension enabled.");
	}
	
	@Override
	public void onDisable(ExtensionContext context)
	{
		HealHooks.clear();
		context.info("BossZerg extension disabled (heal hooks cleared).");
	}
}
