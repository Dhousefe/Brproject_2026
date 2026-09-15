package ext.mods.tour;

import br.project.spi.Extension;
import br.project.spi.ExtensionContext;
import ext.mods.extensions.hooks.TournamentHooks;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.tour.battle.TournamentManager;

/** Phase 3 SPI entry for tour. */
public final class TourExtension implements Extension {
	public static final String ID = "tour";

	@Override public String id() { return ID; }
	@Override public String version() { return "3.0.0"; }

	@Override
	public void onEnable(ExtensionContext context) {
		try {
			TourData.getInstance();
			TournamentHooks.register(new TournamentHooks.Api() {
				@Override
				public void onPlayerDeath(Player player) {
					TournamentManager.getInstance().onPlayerDeath(player);
				}

				@Override
				public boolean isRunning() {
					return TournamentEvent.isRunning();
				}

				@Override
				public boolean isRunningRegister() {
					return TournamentEvent.isRunningRegister();
				}

				@Override
				public String lastEvent() {
					return TournamentEvent.lastEvent();
				}

				@Override
				public int tutorialQuestionMarkId() {
					return TournamentEvent.TUTORIAL_QUESTION_MARK_ID;
				}

				@Override
				public String getTutorialAlertHtml(Player player) {
					return TournamentEvent.getTutorialAlertHtml(player);
				}

				@Override
				public int getDurationMinutes() {
					return TourData.getInstance().getConfig() != null
						? TourData.getInstance().getConfig().getDuration()
						: 0;
				}

				@Override
				public void showRanking(Player player) {
					new ext.mods.gameserver.handler.voicedcommandhandlers.VoicedTournamentRank()
						.useVoicedCommand("tournamentrank", player, null);
				}

				@Override
				public void savePlayerState(Player player) {
					TournamentPlayerState.save(player);
				}

				@Override
				public void restorePlayerState(Player player) {
					TournamentPlayerState.restore(player);
				}
			});
			context.info("tour hooks + player state service registered.");
		} catch (Throwable t) {
			context.warn("tour failed: " + t.getMessage());
			t.printStackTrace();
		}
	}

	@Override
	public void onDisable(ExtensionContext context) {
		TournamentHooks.clear();
	}
}
