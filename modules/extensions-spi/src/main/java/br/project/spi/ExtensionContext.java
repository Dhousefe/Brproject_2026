package br.project.spi;

/**
 * Services exposed by the host (game core) to extensions.
 * Keep this surface small and stable.
 */
public interface ExtensionContext
{
	/** Info-level log line from the host logger. */
	void info(String message);
	
	/** Warning-level log line. */
	void warn(String message);
	
	/** Shared event bus for optional cross-mod communication. */
	EventBus eventBus();
	
	/**
	 * Register a host-side service implementation (e.g. heal adjuster bridge).
	 * Implementations are host-defined; extensions cast carefully.
	 */
	<T> void registerService(Class<T> type, T implementation);
	
	/** Lookup a previously registered service, or null. */
	<T> T getService(Class<T> type);
	
	/**
	 * True when the operator asked to skip optional mods
	 * (Gradle {@code -PwithoutMods=true} or system property).
	 */
	boolean modsDisabled();
}
