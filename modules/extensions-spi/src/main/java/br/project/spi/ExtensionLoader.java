package br.project.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Discovers and lifecycle-manages {@link Extension} implementations via {@link ServiceLoader}.
 */
public final class ExtensionLoader
{
	private final Map<String, Extension> extensions = new LinkedHashMap<>();
	private ExtensionContext context;
	private boolean enabled;
	
	/**
	 * Discover providers; skip missing/broken SPI entries (e.g. withoutMods fat jar
	 * listing a mod class that is not on the classpath).
	 */
	public List<Extension> discover(ClassLoader classLoader)
	{
		final List<Extension> found = new ArrayList<>();
		final ServiceLoader<Extension> loader = ServiceLoader.load(Extension.class, classLoader);
		final Iterator<Extension> it = loader.iterator();
		while (true)
		{
			try
			{
				if (!it.hasNext())
					break;
				final Extension extension = it.next();
				if (extension == null || extension.id() == null || extension.id().isBlank())
					continue;
				found.add(extension);
			}
			catch (ServiceConfigurationError e)
			{
				// att-ver-3.0: partial distros must still boot
				System.err.println("ExtensionLoader: skipped broken SPI provider: " + e.getMessage());
			}
		}
		return found;
	}
	
	/**
	 * Load + enable all discovered extensions on the given context.
	 *
	 * @return loaded extension ids
	 */
	public List<String> loadAndEnable(ExtensionContext context, ClassLoader classLoader)
	{
		Objects.requireNonNull(context, "context");
		Objects.requireNonNull(classLoader, "classLoader");
		this.context = context;
		
		final List<Extension> discovered = discover(classLoader);
		final List<String> ids = new ArrayList<>();
		for (Extension extension : discovered)
		{
			final String id = extension.id();
			if (extensions.containsKey(id))
			{
				context.warn("ExtensionLoader: duplicate id ignored: " + id);
				continue;
			}
			// Required in-tree features always load; optional extracted mods respect withoutMods flags
			final boolean required = "first-party-features".equals(id);
			if (context.modsDisabled() && !required)
			{
				context.info("ExtensionLoader: skipped optional extension '" + id + "' (mods disabled).");
				continue;
			}
			try
			{
				extension.onLoad(context);
				extension.onEnable(context);
				extensions.put(id, extension);
				ids.add(id);
				context.info("ExtensionLoader: enabled '" + id + "' v" + extension.version());
			}
			catch (Throwable t)
			{
				context.warn("ExtensionLoader: failed to enable '" + id + "': " + t.getMessage());
				t.printStackTrace();
			}
		}
		enabled = true;
		return ids;
	}
	
	public void disableAll()
	{
		if (!enabled || context == null)
			return;
		final List<Extension> reverse = new ArrayList<>(extensions.values());
		Collections.reverse(reverse);
		for (Extension extension : reverse)
		{
			try
			{
				extension.onDisable(context);
				context.info("ExtensionLoader: disabled '" + extension.id() + "'");
			}
			catch (Throwable t)
			{
				context.warn("ExtensionLoader: error disabling '" + extension.id() + "': " + t.getMessage());
			}
		}
		extensions.clear();
		enabled = false;
	}
	
	public boolean isLoaded(String id)
	{
		return extensions.containsKey(id);
	}
	
	public List<String> loadedIds()
	{
		return List.copyOf(extensions.keySet());
	}
}
