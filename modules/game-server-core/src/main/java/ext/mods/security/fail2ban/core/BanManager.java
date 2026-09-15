/*
* Copyleft © 2024-2026 L2Brproject
* * This file is part of L2Brproject derived from aCis409/RusaCis3.8
* * L2Brproject is free software: you can redistribute it and/or modify it
* under the terms of the GNU General Public License as published by the
* Free Software Foundation, either version 3 of the License.
* * L2Brproject is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* General Public License for more details.
* * You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
* Our main Developers, Dhousefe-L2JBR, Agazes33, Ban-L2jDev, Warman, SrEli.
*/
package ext.mods.security.fail2ban.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central ban manager: tracks active bans, failure counters, and auto-bans.
 * Thread-safe via ConcurrentHashMap. Emits events for UI listeners.
 */
public class BanManager {
	private static final Logger LOGGER = Logger.getLogger(BanManager.class.getName());
	private static BanManager instance;
	
	private final Fail2BanConfig config;
	private final Map<String, BanRecord> activeBans;           // IP => BanRecord
	private final Map<String, FailureWindow> failureCounters;    // IP:Jail => FailureWindow
	private final List<Consumer<Fail2BanEvent>> listeners;
	private final ScheduledExecutorService gcExecutor;
	private final String[] ignoreIps;
	private final Persistence persistence;
	private volatile boolean runtimeEnabled;
	
	/**
	 * Failure window: tracks failures within a sliding time window.
	 */
	private static class FailureWindow {
		final long findTimeMs;
		long firstFailureTime;
		AtomicInteger count;
		
		FailureWindow(long findTimeMs) {
			this.findTimeMs = findTimeMs;
			this.firstFailureTime = System.currentTimeMillis();
			this.count = new AtomicInteger(0);
		}
		
		boolean isExpired() {
			return System.currentTimeMillis() - firstFailureTime > findTimeMs;
		}
		
		int incrementAndGet() {
			if (isExpired()) {
				firstFailureTime = System.currentTimeMillis();
				count.set(1);
				return 1;
			}
			return count.incrementAndGet();
		}
	}
	
	/**
	 * Constructor: initialize with config and optional persistence.
	 */
	public BanManager(Fail2BanConfig config, Persistence persistence) {
		this.config = config;
		this.persistence = persistence;
		this.activeBans = new ConcurrentHashMap<>();
		this.failureCounters = new ConcurrentHashMap<>();
		this.listeners = new CopyOnWriteArrayList<>();
		this.ignoreIps = config.getIgnoreIps();
		this.runtimeEnabled = (config != null && config.isEnabled());
		
		// Load persisted bans if persistence available
		if (persistence != null) {
			try {
				List<BanRecord> loaded = persistence.loadActiveBans();
				for (BanRecord ban : loaded) {
					if (ban.isActive()) {
						activeBans.put(ban.ip(), ban);
					}
				}
				LOGGER.fine("Loaded " + activeBans.size() + " persisted bans");
			} catch (Exception e) {
				LOGGER.log(Level.WARNING, "Failed to load persisted bans", e);
			}
		}
		
		// Start GC thread
		this.gcExecutor = new ScheduledThreadPoolExecutor(1, r -> {
			Thread t = new Thread(r, "fail2ban-gc");
			t.setDaemon(true);
			return t;
		});
		
		scheduleGarbageCollection();
	}
	
	/**
	 * Singleton accessor.
	 */
	public static synchronized BanManager getInstance() {
		if (instance == null) {
			instance = new BanManager(new Fail2BanConfig(), null);
		}
		return instance;
	}
	
	/**
	 * Record a failure and decide on action.
	 * Returns true if threshold reached and ban was triggered.
	 */
	public boolean recordFailure(String ip, String jailName, String reason) {
		if (!runtimeEnabled) {
			return false;
		}

		if (isIgnored(ip)) {
			return false;
		}
		
		if (isBanned(ip)) {
			return false; // Already banned
		}
		
		Jail jail = config.getJail(jailName);
		if (jail == null || !jail.isEnabled()) {
			return false;
		}
		
		String key = ip + ":" + jailName;
		FailureWindow window = failureCounters.computeIfAbsent(key, 
			k -> new FailureWindow(jail.getFindTimeMs()));
		
		int failCount = window.incrementAndGet();
		emitEvent(Fail2BanEvent.failure(ip, jailName, reason + " (" + failCount + "/" + jail.getMaxRetry() + ")"));
		
		if (failCount >= jail.getMaxRetry()) {
			ban(ip, jailName, "Auto-ban: " + reason, jail.getBanTimeMs());
			failureCounters.remove(key);
			return true;
		}
		
		return false;
	}
	
	/**
	 * Manually or automatically ban an IP.
	 * Administrator manual bans bypass the ignore list to allow audit/testing.
	 */
	public void ban(String ip, String jailName, String reason, long durationMs) {
		if (ip == null || ip.isBlank()) {
			return;
		}
		final String cleanIp = ip.trim();

		boolean isManual = "manual".equalsIgnoreCase(jailName) || "manual_operator".equalsIgnoreCase(jailName);
		if (!isManual && isIgnored(cleanIp)) {
			LOGGER.warning("Attempt to auto-ban ignored IP: " + cleanIp);
			return;
		}
		
		Jail jail = config.getJail(jailName);
		int jailId = (jail != null) ? jail.getId() : 0;
		BanRecord ban = BanRecord.temporary(cleanIp, jailName, reason, durationMs, jailId);
		
		activeBans.put(cleanIp, ban);
		
		// Persist if available
		if (persistence != null) {
			try {
				persistence.saveBan(ban);
			} catch (Exception e) {
				LOGGER.log(Level.WARNING, "Failed to persist ban", e);
			}
		}
		
		emitEvent(Fail2BanEvent.ban(cleanIp, jailName, durationMs));
		ext.mods.security.fail2ban.ipc.IpcBanNotifier.notifyBan(cleanIp, ban.expireTime());
		
		// Se for loopback, sincroniza tambem os equivalentes IPv4/IPv6 no proxy para garantir bloqueio total
		if (isLoopback(cleanIp)) {
			for (String loopVariant : new String[]{"127.0.0.1", "0:0:0:0:0:0:0:1", "::1"}) {
				if (!loopVariant.equalsIgnoreCase(cleanIp)) {
					ext.mods.security.fail2ban.ipc.IpcBanNotifier.notifyBan(loopVariant, ban.expireTime());
				}
			}
		}
		
		LOGGER.info("[BAN] " + cleanIp + " by " + jailName + ": " + reason);
	}
	
	/**
	 * Unban an IP.
	 */
	public void unban(String ip) {
		if (ip == null || ip.isBlank()) {
			return;
		}
		final String cleanIp = ip.trim();
		BanRecord ban = activeBans.remove(cleanIp);
		if (ban != null) {
			emitEvent(Fail2BanEvent.unban(cleanIp, ban.jailName()));
			ext.mods.security.fail2ban.ipc.IpcBanNotifier.notifyUnban(cleanIp);
			if (isLoopback(cleanIp)) {
				for (String loopVariant : new String[]{"127.0.0.1", "0:0:0:0:0:0:0:1", "::1"}) {
					if (!loopVariant.equalsIgnoreCase(cleanIp)) {
						ext.mods.security.fail2ban.ipc.IpcBanNotifier.notifyUnban(loopVariant);
					}
				}
			}
			LOGGER.info("[UNBAN] " + cleanIp);
		}
		failureCounters.entrySet().removeIf(e -> e.getKey().startsWith(cleanIp + ":"));
	}

	private static boolean isLoopback(String ip) {
		return "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip) || "localhost".equalsIgnoreCase(ip);
	}
	
	/**
	 * Check if IP is currently banned.
	 */
	public boolean isBanned(String ip) {
		BanRecord ban = activeBans.get(ip);
		if (ban == null) return false;
		if (ban.isActive()) return true;
		
		// Expired ban
		activeBans.remove(ip);
		emitEvent(Fail2BanEvent.expire(ip, ban.jailName()));
		return false;
	}
	
	/**
	 * Get ban details or null.
	 */
	public BanRecord getBan(String ip) {
		BanRecord ban = activeBans.get(ip);
		if (ban != null && ban.isActive()) {
			return ban;
		}
		return null;
	}
	
	/**
	 * Get all active bans.
	 */
	public Map<String, BanRecord> getActiveBans() {
		return new ConcurrentHashMap<>(activeBans);
	}
	
	/**
	 * Add event listener.
	 */
	public void addListener(Consumer<Fail2BanEvent> listener) {
		listeners.add(listener);
	}
	
	/**
	 * Schedule periodic garbage collection of expired bans.
	 */
	private void scheduleGarbageCollection() {
		gcExecutor.scheduleAtFixedRate(
			this::gc,
			config.getGcIntervalMs(),
			config.getGcIntervalMs(),
			TimeUnit.MILLISECONDS
		);
	}
	
	/**
	 * Garbage collection: remove expired bans and counters.
	 */
	private void gc() {
		try {
			int removed = 0;
			for (Map.Entry<String, BanRecord> e : activeBans.entrySet()) {
				if (!e.getValue().isActive()) {
					if (activeBans.remove(e.getKey()) != null) {
						removed++;
					}
				}
			}
			if (removed > 0) {
				LOGGER.fine("GC: removed " + removed + " expired bans");
			}
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "GC error", e);
		}
	}
	
	/**
	 * Check if IP is in ignore list.
	 */
	private boolean isIgnored(String ip) {
		return Arrays.binarySearch(ignoreIps, ip) >= 0 ||
		       Arrays.stream(ignoreIps).anyMatch(ip::equals);
	}
	
	/**
	 * Emit event to all listeners.
	 */
	public void emitEvent(Fail2BanEvent event) {
		// Persist if available
		if (persistence != null) {
			try {
				persistence.appendEvent(event);
			} catch (Exception e) {
				LOGGER.log(Level.WARNING, "Failed to persist event", e);
			}
		}
		
		for (Consumer<Fail2BanEvent> listener : listeners) {
			try {
				listener.accept(event);
			} catch (Exception e) {
				LOGGER.log(Level.WARNING, "Listener error", e);
			}
		}
	}

	/**
	 * Emits high-throughput real-time proxy traffic event in-memory to UI listeners
	 * WITHOUT persisting to SQLite to guarantee zero disk I/O latency and maximum Netty throughput.
	 */
	public void emitTrafficEvent(Fail2BanEvent event) {
		for (Consumer<Fail2BanEvent> listener : listeners) {
			try {
				listener.accept(event);
			} catch (Exception e) {
				LOGGER.log(Level.WARNING, "Listener error", e);
			}
		}
	}

	/**
	 * Unregister an event listener.
	 */
	public void removeListener(Consumer<Fail2BanEvent> listener) {
		if (listener != null) {
			listeners.remove(listener);
		}
	}

	/**
	 * Load recent events from persistence for audit and UI display.
	 */
	public List<Fail2BanEvent> getRecentEvents(int limit) {
		if (persistence != null) {
			try {
				return persistence.loadRecentEvents(limit);
			} catch (Exception e) {
				LOGGER.log(Level.WARNING, "Failed to load recent events from persistence", e);
			}
		}
		return new ArrayList<>();
	}

	
	/**
	 * Check if Fail2Ban is currently enabled at runtime.
	 */
	public boolean isEnabled() {
		return runtimeEnabled;
	}

	/**
	 * Dynamically enable or disable Fail2Ban at runtime.
	 */
	public void setEnabled(boolean enabled) {
		this.runtimeEnabled = enabled;
		LOGGER.info("[Fail2Ban] Runtime defense state set to: " + (enabled ? "ENABLED" : "DISABLED"));
	}

	/**
	 * Shutdown (cleanup resources).
	 */
	public void shutdown() {
		gcExecutor.shutdown();
		try {
			if (!gcExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
				gcExecutor.shutdownNow();
			}
		} catch (InterruptedException e) {
			gcExecutor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}
}
