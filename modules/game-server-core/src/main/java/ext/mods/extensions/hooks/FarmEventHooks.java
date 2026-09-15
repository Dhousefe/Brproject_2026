package ext.mods.extensions.hooks;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import ext.mods.extensions.api.IFarmZoneInfo;
import ext.mods.gameserver.model.actor.Attackable;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.zone.type.RandomZone;

public final class FarmEventHooks
{
	public interface Api
	{
		void reload();

		void onPvPKill(Player killer, Player victim);

		void onKill(Player killer, Attackable target);

		boolean isEventRunning();

		IFarmZoneInfo getZoneDataForZone(RandomZone zone);

		Collection<?> getActiveZones();

		List<?> getActiveSpawns(RandomZone zone);

		void handleEventTeleportConfirmation(Player player, int answer);

		void handleFarmZoneTeleportConfirmation(Player player, boolean confirmed);

		String getTutorialAlertHtml(Player player);

		int tutorialQuestionMarkId();

		boolean teleportToFarmZone(Player player);
	}

	private static final Api NOOP = new Api()
	{
		@Override public void reload() {}
		@Override public void onPvPKill(Player killer, Player victim) {}
		@Override public void onKill(Player killer, Attackable target) {}
		@Override public boolean isEventRunning() { return false; }
		@Override public IFarmZoneInfo getZoneDataForZone(RandomZone zone) { return null; }
		@Override public Collection<?> getActiveZones() { return Collections.emptyList(); }
		@Override public List<?> getActiveSpawns(RandomZone zone) { return Collections.emptyList(); }
		@Override public void handleEventTeleportConfirmation(Player player, int answer) {}
		@Override public void handleFarmZoneTeleportConfirmation(Player player, boolean confirmed) {}
		@Override public String getTutorialAlertHtml(Player player) { return null; }
		@Override public int tutorialQuestionMarkId() { return -1; }
		@Override public boolean teleportToFarmZone(Player player) { return false; }
	};

	private static volatile Api api = NOOP;

	private FarmEventHooks() {}

	public static void register(Api implementation) { api = implementation != null ? implementation : NOOP; }
	public static void clear() { api = NOOP; }
	public static Api get() { return api; }
}
