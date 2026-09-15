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
import java.util.List;

/**
 * Configuration of a single "jail" — a rule set that triggers bans.
 * Example: login brute force = jail with findTime=60s, maxRetry=5, banTime=1h.
 * 
 * Immutable after construction (built via Builder pattern).
 */
public final class Jail {
	private final int id;
	private final String name;
	private final long findTimeMs;      // Sliding window (e.g., 60000ms = 60s)
	private final int maxRetry;         // Max failures allowed in window before ban
	private final long banTimeMs;       // Duration of ban (0 = permanent)
	private final boolean enabled;
	private final List<String> rules;  // Detection rule names
	
	private Jail(Builder b) {
		this.id = b.id;
		this.name = b.name;
		this.findTimeMs = b.findTimeMs;
		this.maxRetry = b.maxRetry;
		this.banTimeMs = b.banTimeMs;
		this.enabled = b.enabled;
		this.rules = new ArrayList<>(b.rules);
	}
	
	public static Builder builder(String name) {
		return new Builder(name);
	}
	
	// Getters
	public int getId() { return id; }
	public String getName() { return name; }
	public long getFindTimeMs() { return findTimeMs; }
	public int getMaxRetry() { return maxRetry; }
	public long getBanTimeMs() { return banTimeMs; }
	public boolean isEnabled() { return enabled; }
	public List<String> getRules() { return new ArrayList<>(rules); }
	
	@Override
	public String toString() {
		return String.format("Jail{name='%s', findTime=%dms, maxRetry=%d, banTime=%dms, enabled=%b}",
			name, findTimeMs, maxRetry, banTimeMs, enabled);
	}
	
	// Builder
	public static class Builder {
		private int id = 0;
		private final String name;
		private long findTimeMs = 60000;        // Default: 60s
		private int maxRetry = 5;               // Default: 5 failures
		private long banTimeMs = 3600000;       // Default: 1h
		private boolean enabled = true;
		private List<String> rules = new ArrayList<>();
		
		public Builder(String name) {
			this.name = name;
		}
		
		public Builder id(int id) { this.id = id; return this; }
		public Builder findTimeMs(long ms) { this.findTimeMs = ms; return this; }
		public Builder maxRetry(int count) { this.maxRetry = count; return this; }
		public Builder banTimeMs(long ms) { this.banTimeMs = ms; return this; }
		public Builder banTimeSecs(long secs) { this.banTimeMs = secs * 1000; return this; }
		public Builder permanent() { this.banTimeMs = 0; return this; }
		public Builder enabled(boolean b) { this.enabled = b; return this; }
		public Builder addRule(String ruleName) { this.rules.add(ruleName); return this; }
		
		public Jail build() {
			if (name == null || name.isBlank()) throw new IllegalArgumentException("Jail name cannot be empty");
			if (findTimeMs <= 0) throw new IllegalArgumentException("findTimeMs must be positive");
			if (maxRetry < 1) throw new IllegalArgumentException("maxRetry must be at least 1");
			return new Jail(this);
		}
	}
}
