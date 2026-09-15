package ext.mods.extensions.hooks;

public final class CapsuleBoxHooks
{
	public interface Api
	{
		void reload();
	}

	private static final Api NOOP = new Api()
	{
		@Override public void reload() {}
	};

	private static volatile Api api = NOOP;

	private CapsuleBoxHooks() {}

	public static void register(Api implementation) { api = implementation != null ? implementation : NOOP; }
	public static void clear() { api = NOOP; }
	public static Api get() { return api; }
}
