package ext.mods.levelupmaker;

import br.project.spi.Extension;
import br.project.spi.ExtensionContext;
import ext.mods.extensions.hooks.LevelUpMakerHooks;
import ext.mods.gameserver.model.actor.Player;

/** Phase 3 SPI entry for levelup-maker. */
public final class LevelUpMakerExtension implements Extension {
	public static final String ID = "levelup-maker";

	@Override public String id() { return ID; }
	@Override public String version() { return "3.0.0"; }

	@Override
	public void onEnable(ExtensionContext context) {
		try {
			LevelUpMakerManager.getInstance().init();
			LevelUpMakerHooks.register(new LevelUpMakerHooks.Api() {
				@Override
				public boolean isEnabled() {
					return LevelUpMakerManager.getInstance().isEnabled();
				}

				@Override
				public int getQuestionMarkId() {
					return LevelUpMakerManager.getInstance().getQuestionMarkId();
				}

				@Override
				public int getScrollSkillId() {
					return LevelUpMakerManager.getInstance().getScrollSkillId();
				}

				@Override
				public int getScrollSkillLevel() {
					return LevelUpMakerManager.getInstance().getScrollSkillLevel();
				}

				@Override
				public int getCastTimeMs() {
					return LevelUpMakerManager.getInstance().getCastTimeMs();
				}

				@Override
				public String getTutorialAlertHtml(Player player) {
					return LevelUpMakerManager.getInstance().getTutorialAlertHtml(player);
				}

				@Override
				public void sendQuestionMark(Player player) {
					LevelUpMakerManager.getInstance().sendQuestionMark(player);
				}
			});
			context.info("levelup-maker hooks registered.");
		} catch (Throwable t) {
			context.warn("levelup-maker failed: " + t.getMessage());
			t.printStackTrace();
		}
	}

	@Override
	public void onDisable(ExtensionContext context) {
		LevelUpMakerHooks.clear();
		try {
			LevelUpMakerManager.getInstance().shutdown();
		} catch (Throwable ignored) {
		}
	}
}
