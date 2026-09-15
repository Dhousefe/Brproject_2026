package ext.mods.extensions.hooks;

import ext.mods.gameserver.model.actor.Player;
import ext.mods.util.Tokenizer;

public final class DungeonHooks
{
	public interface Api
	{
		void handleEnterDungeonId(Player player, Tokenizer tokenizer);
	}

	private static final Api NOOP = new Api()
	{
		@Override public void handleEnterDungeonId(Player player, Tokenizer tokenizer) {}
	};

	private static volatile Api api = NOOP;

	private DungeonHooks() {}

	public static void register(Api implementation) { api = implementation != null ? implementation : NOOP; }
	public static void clear() { api = NOOP; }
	public static Api get() { return api; }
}
