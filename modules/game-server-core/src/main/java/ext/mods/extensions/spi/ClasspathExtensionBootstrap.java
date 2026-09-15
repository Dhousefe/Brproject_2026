package ext.mods.extensions.spi;

import java.util.List;

import br.project.spi.ExtensionLoader;
import ext.mods.commons.logging.CLogger;
import ext.mods.commons.lang.StringUtil;

/**
 * Boots {@link br.project.spi.Extension} implementations discovered on the classpath
 * (Gradle multi-module mods via ServiceLoader). Complements legacy
 * {@link ext.mods.extensions.ExtensionLoader} which only loads {@code *.ext.jar}.
 */
public final class ClasspathExtensionBootstrap
{
	private static final CLogger LOGGER = new CLogger(ClasspathExtensionBootstrap.class.getName());
	
	private static final ExtensionLoader LOADER = new ExtensionLoader();
	private static CoreExtensionContext context;
	
	private ClasspathExtensionBootstrap()
	{
	}
	
	public static void load()
	{
		final boolean modsDisabled = isModsDisabled();
		context = new CoreExtensionContext(modsDisabled);
		StringUtil.printSection("Classpath Extensions (SPI)");
		final List<String> ids = LOADER.loadAndEnable(context, ClasspathExtensionBootstrap.class.getClassLoader());
		if (ids.isEmpty())
			LOGGER.info("No classpath SPI extensions loaded.");
		else
			LOGGER.info("SPI extensions loaded: {}", String.join(", ", ids));
	}
	
	public static void shutdown()
	{
		LOADER.disableAll();
		if (context != null)
			context.eventBus().clear();
	}
	
	public static boolean isLoaded(String id)
	{
		return LOADER.isLoaded(id);
	}
	
	public static List<String> loadedIds()
	{
		return LOADER.loadedIds();
	}
	
	private static boolean isModsDisabled()
	{
		if (Boolean.getBoolean("brproject.withoutMods"))
			return true;
		if (Boolean.getBoolean("brproject.withoutBossZerg"))
			return true;
		final String prop = System.getProperty("brproject.mods", "all");
		return "none".equalsIgnoreCase(prop) || "false".equalsIgnoreCase(prop);
	}
}
