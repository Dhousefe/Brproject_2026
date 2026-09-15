package br.project.spi;

/**
 * Contract for BrProject first-party and third-party mods (Phase 2+).
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader}
 * ({@code META-INF/services/br.project.spi.Extension}).
 * The game core must not hard-depend on concrete mod classes.
 */
public interface Extension
{
	/** Stable unique id, e.g. {@code boss-zerg}. */
	String id();
	
	/** Semantic version of this extension artifact. */
	String version();
	
	/**
	 * Called once after construction, before {@link #onEnable(ExtensionContext)}.
	 * Prefer cheap init; do not start game loops here.
	 */
	default void onLoad(ExtensionContext context)
	{
	}
	
	/**
	 * Called when the server is ready for feature activation
	 * (world/managers available). Start schedulers and register hooks here.
	 */
	default void onEnable(ExtensionContext context)
	{
	}
	
	/**
	 * Called on shutdown or explicit disable. Must unregister hooks.
	 */
	default void onDisable(ExtensionContext context)
	{
	}
}
