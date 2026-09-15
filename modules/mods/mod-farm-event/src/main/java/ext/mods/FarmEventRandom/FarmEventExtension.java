package ext.mods.FarmEventRandom;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import br.project.spi.Extension;
import br.project.spi.ExtensionContext;
import ext.mods.Crypta.RandomManager;
import ext.mods.extensions.api.IFarmZoneInfo;
import ext.mods.extensions.hooks.FarmEventHooks;
import ext.mods.gameserver.handler.BypassHandler;
import ext.mods.gameserver.handler.VoicedCommandHandler;
import ext.mods.gameserver.handler.bypasshandlers.FarmZoneTeleportBypass;
import ext.mods.gameserver.handler.voicedcommandhandlers.FarmZoneTeleport;
import ext.mods.gameserver.model.actor.Attackable;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.spawn.Spawn;
import ext.mods.gameserver.model.zone.type.RandomZone;

/**
 * Phase 3 SPI entry for farm-event.
 * Loads zone XML data and registers FarmEventHooks against Crypta.RandomManager.
 */
public final class FarmEventExtension implements Extension {
	public static final String ID = "farm-event";

	private FarmZoneTeleportBypass _bypassHandler;
	private FarmZoneTeleport _voicedHandler;

	@Override public String id() { return ID; }
	@Override public String version() { return "3.0.0"; }

	@Override
	public void onEnable(ExtensionContext context) {
		try {
			RandomData.getInstance();
			RandomManager.getInstance().start();
			_bypassHandler = new FarmZoneTeleportBypass();
			BypassHandler.getInstance().registerHandler(_bypassHandler);
			_voicedHandler = new FarmZoneTeleport();
			VoicedCommandHandler.getInstance().registerHandler(_voicedHandler);
			FarmEventHooks.register(new FarmEventHooks.Api() {
				@Override
				public void reload() {
					RandomManager.getInstance().reload();
				}

				@Override
				public void onPvPKill(Player killer, Player victim) {
					RandomManager.getInstance().onPvPKill(killer, victim);
				}

				@Override
				public void onKill(Player killer, Attackable target) {
					RandomManager.getInstance().onKill(killer, target);
				}

				@Override
				public boolean isEventRunning() {
					return RandomManager.getInstance().isEventRunning();
				}

				@Override
				public IFarmZoneInfo getZoneDataForZone(RandomZone zone) {
					return RandomManager.getInstance().getZoneDataForZone(zone);
				}

				@Override
				public Collection<?> getActiveZones() {
					return RandomManager.getInstance().getActiveZones();
				}

				@Override
				public List<?> getActiveSpawns(RandomZone zone) {
					List<Spawn> spawns = RandomManager.getInstance().getActiveSpawns();
					if (zone == null)
						return spawns;
					return spawns.stream()
						.filter(s -> s != null && s.getNpc() != null && zone.isInsideZone(s.getNpc()))
						.collect(Collectors.toList());
				}

				@Override
				public void handleEventTeleportConfirmation(Player player, int answer) {
					RandomManager.getInstance().handleEventTeleportConfirmation(player, answer == 1);
				}

				@Override
				public void handleFarmZoneTeleportConfirmation(Player player, boolean confirmed) {
					new FarmZoneTeleport().handleConfirmation(player, confirmed);
				}

				@Override
				public String getTutorialAlertHtml(Player player) {
					return RandomManager.getInstance().getTutorialAlertHtml(player);
				}

				@Override
				public int tutorialQuestionMarkId() {
					return RandomManager.TUTORIAL_QUESTION_MARK_ID;
				}

				@Override
				public boolean teleportToFarmZone(Player player) {
					return _bypassHandler != null && _bypassHandler.useBypass("farmzone", player, null);
				}
			});
			context.info("farm-event hooks registered.");
		} catch (Throwable t) {
			context.warn("farm-event failed: " + t.getMessage());
			t.printStackTrace();
		}
	}

	@Override
	public void onDisable(ExtensionContext context) {
		if (_bypassHandler != null) {
			BypassHandler.getInstance().unregisterHandler(_bypassHandler);
			_bypassHandler = null;
		}
		if (_voicedHandler != null) {
			VoicedCommandHandler.getInstance().unregisterHandler(_voicedHandler);
			_voicedHandler = null;
		}
		FarmEventHooks.clear();
	}
}
