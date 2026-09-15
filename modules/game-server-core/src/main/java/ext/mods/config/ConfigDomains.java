package ext.mods.config;

import java.util.Arrays;
import java.util.List;

/**
 * Phase 4 registry: documents and enumerates config domains for multi-dev ownership.
 */
public final class ConfigDomains
{
	private ConfigDomains()
	{
	}
	
	public static List<ConfigDomain> all()
	{
		return Arrays.asList(ConfigDomain.values());
	}
	
	/** Human-readable map for logs / admin UIs. */
	public static String describe()
	{
		final StringBuilder sb = new StringBuilder("Config domains:\n");
		for (ConfigDomain d : ConfigDomain.values())
			sb.append(" - ").append(d.name()).append(": ").append(d.files()).append(" (").append(d.owns()).append(")\n");
		return sb.toString();
	}
}
