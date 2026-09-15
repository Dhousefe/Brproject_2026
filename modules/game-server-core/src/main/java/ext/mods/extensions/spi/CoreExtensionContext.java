package ext.mods.extensions.spi;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import br.project.spi.EventBus;
import br.project.spi.ExtensionContext;
import ext.mods.commons.logging.CLogger;

/**
 * Host-side {@link ExtensionContext} backed by the game process.
 */
public final class CoreExtensionContext implements ExtensionContext
{
	private static final CLogger LOGGER = new CLogger(CoreExtensionContext.class.getName());
	
	private final EventBus eventBus = new EventBus();
	private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();
	private final boolean modsDisabled;
	
	public CoreExtensionContext(boolean modsDisabled)
	{
		this.modsDisabled = modsDisabled;
	}
	
	@Override
	public void info(String message)
	{
		LOGGER.info(message);
	}
	
	@Override
	public void warn(String message)
	{
		LOGGER.warn(message);
	}
	
	@Override
	public EventBus eventBus()
	{
		return eventBus;
	}
	
	@Override
	public <T> void registerService(Class<T> type, T implementation)
	{
		if (type != null && implementation != null)
			services.put(type, implementation);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public <T> T getService(Class<T> type)
	{
		return (T) services.get(type);
	}
	
	@Override
	public boolean modsDisabled()
	{
		return modsDisabled;
	}
}
