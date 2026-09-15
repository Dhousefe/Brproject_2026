package ext.mods.extensions.hooks;

import java.util.Collections;
import java.util.List;

import ext.mods.extensions.api.IAgathionSpec;

public final class AgathionHooks
{
	public interface Api
	{
		List<? extends IAgathionSpec> getAgathionsByItemId(int itemId);
	}

	private static final Api NOOP = new Api()
	{
		@Override public List<? extends IAgathionSpec> getAgathionsByItemId(int itemId) { return Collections.emptyList(); }
	};

	private static volatile Api api = NOOP;

	private AgathionHooks() {}

	public static void register(Api implementation) { api = implementation != null ? implementation : NOOP; }
	public static void clear() { api = NOOP; }
	public static Api get() { return api; }
}
