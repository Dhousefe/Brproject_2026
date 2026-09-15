/*
* Copyleft © 2024-2026 L2Brproject
*/
package ext.mods.security.fail2ban.core;

import ext.mods.security.fail2ban.firewall.FirewallAdapter;
import ext.mods.security.fail2ban.firewall.FirewallAdapterFactory;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Panic Mode: graduated emergency response for volumetric attacks.
 * 
 * Levels:
 *   0 = NORMAL      — Standard Fail2Ban rules, no restrictions.
 *   1 = ALERT       — Double detection sensitivity, halve thresholds.
 *   2 = DEFENSIVE   — Auto-ban new IPs after 2 failures (instead of 5), block /24 ranges.
 *   3 = LOCKDOWN    — Only whitelisted IPs can connect. All new connections rejected.
 *   4 = BLACKHOLE   — Close ALL inbound connections. Server goes dark.
 * 
 * Loss control:
 *   - Each level has estimated player loss % and recovery time.
 *   - Dashboard shows real-time impact metrics.
 *   - Auto-escalation based on connection rate thresholds.
 *   - Auto-de-escalation when attack subsides (cooldown timer).
 */
public class PanicMode {
	private static final Logger LOGGER = Logger.getLogger(PanicMode.class.getName());
	
	// Panic levels
	public static final int NORMAL = 0;
	public static final int ALERT = 1;
	public static final int DEFENSIVE = 2;
	public static final int LOCKDOWN = 3;
	public static final int BLACKHOLE = 4;
	
	private static PanicMode instance;
	
	// State
	private volatile int currentLevel = NORMAL;
	private volatile long levelChangedAt = System.currentTimeMillis();
	private volatile String escalationReason = "";
	
	// Metrics (sliding 10s window)
	private final AtomicInteger connectionsPerSecond = new AtomicInteger(0);
	private final AtomicInteger rejectedPerSecond = new AtomicInteger(0);
	private final AtomicInteger uniqueIpsPerMinute = new AtomicInteger(0);
	private final AtomicLong totalRejected = new AtomicLong(0);
	private final AtomicLong totalAccepted = new AtomicLong(0);
	
	// Thresholds for auto-escalation
	private int alertThresholdCps = 50;       // >50 conn/s → ALERT
	private int defensiveThresholdCps = 150;  // >150 conn/s → DEFENSIVE
	private int lockdownThresholdCps = 500;   // >500 conn/s → LOCKDOWN
	private int blackholeThresholdCps = 2000; // >2000 conn/s → BLACKHOLE
	
	// De-escalation cooldown (ms)
	private long deescalationCooldownMs = 30000; // 30s below threshold → de-escalate
	private volatile long lastHighLoadTime = 0;
	
	// Whitelisted IPs (active during LOCKDOWN)
	private final ConcurrentHashMap<String, Boolean> whitelist = new ConcurrentHashMap<>();
	
	// Range bans (/24)
	private final ConcurrentHashMap<String, Long> bannedRanges = new ConcurrentHashMap<>();
	
	// Listeners for level changes
	private Consumer<PanicEvent> listener;
	
	// Monitoring thread
	private final ScheduledExecutorService monitor;
	
	public PanicMode() {
		this.monitor = new ScheduledThreadPoolExecutor(1, r -> {
			Thread t = new Thread(r, "panic-mode-monitor");
			t.setDaemon(true);
			return t;
		});
		
		// Start monitoring every 1 second
		monitor.scheduleAtFixedRate(this::evaluate, 1, 1, TimeUnit.SECONDS);
	}
	
	public static synchronized PanicMode getInstance() {
		if (instance == null) {
			instance = new PanicMode();
		}
		return instance;
	}
	
	/**
	 * Called by Netty handler on every new connection attempt.
	 * Returns true if connection should be ALLOWED, false to REJECT.
	 */
	public boolean allowConnection(String ip) {
		connectionsPerSecond.incrementAndGet();
		totalAccepted.incrementAndGet();
		
		switch (currentLevel) {
			case BLACKHOLE:
				reject(ip);
				return false;
				
			case LOCKDOWN:
				if (!isWhitelisted(ip)) {
					reject(ip);
					return false;
				}
				return true;
				
			case DEFENSIVE:
				// Block entire /24 ranges that have any banned IP
				if (isRangeBanned(ip)) {
					reject(ip);
					return false;
				}
				return true;
				
			case ALERT:
			case NORMAL:
			default:
				return true;
		}
	}
	
	/**
	 * Get effective maxRetry for current level.
	 * Lower levels = more aggressive banning.
	 */
	public int getEffectiveMaxRetry(int configMaxRetry) {
		switch (currentLevel) {
			case ALERT:     return Math.max(1, configMaxRetry / 2);  // 5 → 2
			case DEFENSIVE: return 1;                                  // 1 failure = ban
			case LOCKDOWN:  return 1;
			case BLACKHOLE: return 1;
			default:        return configMaxRetry;                     // Normal
		}
	}
	
	/**
	 * Get effective ban duration multiplier.
	 */
	public long getEffectiveBanTimeMs(long configBanTimeMs) {
		switch (currentLevel) {
			case ALERT:     return configBanTimeMs * 2;   // 1h → 2h
			case DEFENSIVE: return configBanTimeMs * 4;   // 1h → 4h
			case LOCKDOWN:  return configBanTimeMs * 12;  // 1h → 12h
			case BLACKHOLE: return -1;                     // Permanent
			default:        return configBanTimeMs;
		}
	}
	
	/**
	 * Manually set panic level.
	 */
	public void setLevel(int level, String reason) {
		if (level < NORMAL || level > BLACKHOLE) return;
		int oldLevel = currentLevel;
		currentLevel = level;
		levelChangedAt = System.currentTimeMillis();
		escalationReason = reason;
		
		LOGGER.warning(String.format("[PANIC] Level %d → %d (%s → %s). Reason: %s",
			oldLevel, level, levelName(oldLevel), levelName(level), reason));
		
		emitEvent(new PanicEvent(oldLevel, level, reason));
	}
	
	/**
	 * Ban an entire /24 range (DEFENSIVE+ mode).
	 */
	public void banRange(String ip, long durationMs) {
		String range = getSubnetRange(ip);
		bannedRanges.put(range, System.currentTimeMillis() + durationMs);
		String mask = ip != null && ip.contains(":") ? "/64" : "/24";
		LOGGER.info("[PANIC] Subnet range banned: " + range + " (" + mask + ")");
	}
	
	/**
	 * Add IP to whitelist (protected during LOCKDOWN).
	 */
	public void addWhitelist(String ip) {
		whitelist.put(ip, Boolean.TRUE);
	}
	
	/**
	 * Remove IP from whitelist.
	 */
	public void removeWhitelist(String ip) {
		whitelist.remove(ip);
	}
	
	/**
	 * Periodic evaluation: auto-escalate or de-escalate based on traffic.
	 */
	private void evaluate() {
		try {
			int cps = connectionsPerSecond.getAndSet(0);
			
			// Auto-escalation
			if (cps >= blackholeThresholdCps && currentLevel < BLACKHOLE) {
				setLevel(BLACKHOLE, "Auto: " + cps + " conn/s exceeds blackhole threshold");
				lastHighLoadTime = System.currentTimeMillis();
			} else if (cps >= lockdownThresholdCps && currentLevel < LOCKDOWN) {
				setLevel(LOCKDOWN, "Auto: " + cps + " conn/s exceeds lockdown threshold");
				lastHighLoadTime = System.currentTimeMillis();
			} else if (cps >= defensiveThresholdCps && currentLevel < DEFENSIVE) {
				setLevel(DEFENSIVE, "Auto: " + cps + " conn/s exceeds defensive threshold");
				lastHighLoadTime = System.currentTimeMillis();
			} else if (cps >= alertThresholdCps && currentLevel < ALERT) {
				setLevel(ALERT, "Auto: " + cps + " conn/s exceeds alert threshold");
				lastHighLoadTime = System.currentTimeMillis();
			}
			
			// Auto-de-escalation
			if (currentLevel > NORMAL && lastHighLoadTime > 0) {
				long timeSinceHighLoad = System.currentTimeMillis() - lastHighLoadTime;
				if (timeSinceHighLoad > deescalationCooldownMs) {
					int newLevel = Math.max(NORMAL, currentLevel - 1);
					setLevel(newLevel, "Auto-de-escalation: " + timeSinceHighLoad/1000 + "s below threshold");
					lastHighLoadTime = System.currentTimeMillis(); // Reset for next de-escalation
				}
			}
			
			// GC expired range bans
			long now = System.currentTimeMillis();
			bannedRanges.entrySet().removeIf(e -> e.getValue() < now);
			
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Panic mode evaluation error", e);
		}
	}
	
	// Helpers
	private void reject(String ip) {
		rejectedPerSecond.incrementAndGet();
		totalRejected.incrementAndGet();
	}
	
	private boolean isWhitelisted(String ip) {
		return whitelist.containsKey(ip);
	}
	
	private boolean isRangeBanned(String ip) {
		String range = getSubnetRange(ip);
		Long expireTime = bannedRanges.get(range);
		if (expireTime == null) return false;
		if (System.currentTimeMillis() > expireTime) {
			bannedRanges.remove(range);
			return false;
		}
		return true;
	}
	
	public String getSubnetRange(String ip) {
		if (ip == null || ip.isBlank()) {
			return "";
		}
		// IPv6: Extract /64 prefix (first 4 hextets)
		if (ip.contains(":")) {
			String[] parts = ip.split(":");
			if (parts.length >= 4) {
				return parts[0] + ":" + parts[1] + ":" + parts[2] + ":" + parts[3];
			}
			return ip;
		}
		// IPv4: Extract /24 prefix (first 3 octets)
		int lastDot = ip.lastIndexOf('.');
		return lastDot > 0 ? ip.substring(0, lastDot) : ip;
	}
	
	private void emitEvent(PanicEvent event) {
		if (listener != null) {
			try {
				listener.accept(event);
			} catch (Exception e) {
				LOGGER.log(Level.WARNING, "Listener error", e);
			}
		}
	}
	
	// Getters
	public int getCurrentLevel() { return currentLevel; }
	public String getLevelName() { return levelName(currentLevel); }
	public long getLevelChangedAt() { return levelChangedAt; }
	public String getEscalationReason() { return escalationReason; }
	public long getTotalRejected() { return totalRejected.get(); }
	public long getTotalAccepted() { return totalAccepted.get(); }
	public ConcurrentHashMap<String, Long> getBannedRanges() { return bannedRanges; }
	public ConcurrentHashMap<String, Boolean> getWhitelist() { return whitelist; }
	
	public void setListener(Consumer<PanicEvent> listener) { this.listener = listener; }
	
	/**
	 * Get loss estimation for current level.
	 */
	public LossEstimate getLossEstimate() {
		switch (currentLevel) {
			case ALERT:     return new LossEstimate(0, 5, "0-5%", "Legitimate slow users may see delays");
			case DEFENSIVE: return new LossEstimate(5, 15, "5-15%", "New players from attacked ranges blocked");
			case LOCKDOWN:  return new LossEstimate(30, 60, "30-60%", "Only whitelisted IPs can connect");
			case BLACKHOLE: return new LossEstimate(100, 100, "100%", "ALL connections rejected");
			default:        return new LossEstimate(0, 0, "0%", "Normal operation");
		}
	}
	
	public static String levelName(int level) {
		switch (level) {
			case NORMAL:    return "NORMAL";
			case ALERT:     return "ALERT";
			case DEFENSIVE: return "DEFENSIVE";
			case LOCKDOWN:  return "LOCKDOWN";
			case BLACKHOLE: return "BLACKHOLE";
			default:        return "UNKNOWN";
		}
	}
	
	/**
	 * Loss estimation record for dashboard display.
	 */
	public record LossEstimate(int minLossPercent, int maxLossPercent, String display, String description) {}
	
	/**
	 * Panic level change event.
	 */
	public record PanicEvent(int oldLevel, int newLevel, String reason) {
		public String display() {
			return String.format("%s → %s: %s", levelName(oldLevel), levelName(newLevel), reason);
		}
	}
}
