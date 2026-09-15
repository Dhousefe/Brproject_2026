/*
* Copyleft © 2024-2026 L2Brproject
*/
package ext.mods.security.fail2ban;

import ext.mods.security.fail2ban.core.BanManager;
import ext.mods.security.fail2ban.core.Fail2BanConfig;
import ext.mods.security.fail2ban.core.Persistence;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fail2Ban initializer: singleton bootstrap that creates the BanManager
 * with Persistence (auto-creates the SQLite database and tables).
 * 
 * Safe to call multiple times: subsequent calls return the cached instance.
 * Must be called BEFORE the DashboardPanel shows the Fail2Ban button.
 */
public class Fail2BanInitializer {
	private static final Logger LOGGER = Logger.getLogger(Fail2BanInitializer.class.getName());
	
	private static volatile BanManager cachedBanManager;
	private static volatile Persistence cachedPersistence;
	
	/**
	 * Initialize Fail2Ban: loads config, creates SQLite database, starts BanManager.
	 * 
	 * @return BanManager instance (singleton)
	 */
	public static synchronized BanManager initialize() {
		if (cachedBanManager != null) {
			return cachedBanManager;
		}
		
		try {
			LOGGER.fine("=== Initializing Fail2Ban ===");
			
			// Load config from proxy.xml
			Fail2BanConfig config = new Fail2BanConfig();
			
			if (!config.isEnabled()) {
				LOGGER.fine("Fail2Ban is disabled in config");
				return null;
			}
			
			// Create persistence (auto-creates SQLite database)
			Persistence persistence = null;
			if (config.isPersistenceEnabled()) {
				String dbPath = config.getPersistencePath();
				persistence = new Persistence(dbPath);
				cachedPersistence = persistence;
				LOGGER.fine("Persistence initialized at: " + dbPath);
			}
			
			// Create BanManager with persistence
			BanManager banManager = new BanManager(config, persistence);
			cachedBanManager = banManager;
			
			LOGGER.fine("=== Fail2Ban initialized successfully ===");
			return banManager;
			
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Failed to initialize Fail2Ban", e);
			return null;
		}
	}
	
	/**
	 * Get the cached BanManager (calls initialize() if not yet created).
	 */
	public static BanManager getBanManager() {
		return initialize();
	}
	
	/**
	 * Get the cached Persistence instance.
	 */
	public static Persistence getPersistence() {
		initialize();
		return cachedPersistence;
	}
	
	/**
	 * Shutdown Fail2Ban (cleanup resources).
	 */
	public static synchronized void shutdown() {
		if (cachedBanManager != null) {
			cachedBanManager.shutdown();
			cachedBanManager = null;
		}
		if (cachedPersistence != null) {
			cachedPersistence.close();
			cachedPersistence = null;
		}
	}
}
