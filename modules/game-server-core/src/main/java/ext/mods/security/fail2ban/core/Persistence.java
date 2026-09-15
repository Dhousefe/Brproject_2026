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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite persistence layer for Fail2Ban: bans and events.
 * Ring buffer for events (max 10000 rows), auto-cleanup for old entries.
 */
public class Persistence {
	private static final Logger LOGGER = Logger.getLogger(Persistence.class.getName());
	private static final int MAX_EVENTS = 10000;
	
	private final String dbPath;
	private Connection connection;
	
	public Persistence(String dbPath) {
		this.dbPath = dbPath;
		initialize();
	}
	
	/**
	 * Initialize database and create tables if needed.
	 */
	private void initialize() {
		try {
			Path dbFile = Paths.get(dbPath);
			Files.createDirectories(dbFile.getParent());
			
			// Explicitly load SQLite JDBC driver (avoids "No suitable driver" issues)
			Class.forName("org.sqlite.JDBC");
			
			String url = "jdbc:sqlite:" + dbPath;
			connection = DriverManager.getConnection(url);
			
			// Enable WAL mode for concurrency
			try (Statement stmt = connection.createStatement()) {
				stmt.execute("PRAGMA journal_mode = WAL");
			}
			
			createTables();
			LOGGER.fine("Persistence initialized: " + dbPath);
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Failed to initialize persistence at " + dbPath, e);
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Create tables if they don't exist.
	 */
	private void createTables() throws Exception {
		try (Statement stmt = connection.createStatement()) {
			// Table: active bans
			stmt.execute("CREATE TABLE IF NOT EXISTS fail2ban_bans (" +
				"id INTEGER PRIMARY KEY AUTOINCREMENT," +
				"ip TEXT NOT NULL," +
				"jail TEXT NOT NULL," +
				"reason TEXT," +
				"ban_time INTEGER NOT NULL," +
				"expire_time INTEGER NOT NULL," +
				"status TEXT NOT NULL DEFAULT 'ACTIVE'," +
				"source TEXT" +
				")");
			
			// Indexes for performance
			stmt.execute("CREATE INDEX IF NOT EXISTS idx_f2b_ip_status ON fail2ban_bans(ip, status)");
			stmt.execute("CREATE INDEX IF NOT EXISTS idx_f2b_expire ON fail2ban_bans(expire_time)");
			
			// Table: events (ring buffer)
			stmt.execute("CREATE TABLE IF NOT EXISTS fail2ban_events (" +
				"id INTEGER PRIMARY KEY AUTOINCREMENT," +
				"ts INTEGER NOT NULL," +
				"ip TEXT NOT NULL," +
				"jail TEXT NOT NULL," +
				"type TEXT NOT NULL," +
				"detail TEXT" +
				")");
			
			stmt.execute("CREATE INDEX IF NOT EXISTS idx_f2e_ts ON fail2ban_events(ts)");
		}
	}
	
	/**
	 * Save a ban record.
	 */
	public void saveBan(BanRecord ban) throws Exception {
		String sql = "INSERT INTO fail2ban_bans (ip, jail, reason, ban_time, expire_time, status, source) " +
			"VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'AUTO')";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setString(1, ban.ip());
			pstmt.setString(2, ban.jailName());
			pstmt.setString(3, ban.reason());
			pstmt.setLong(4, ban.banTime());
			pstmt.setLong(5, ban.expireTime());
			pstmt.executeUpdate();
		}
	}
	
	/**
	 * Mark ban as expired.
	 */
	public void markExpired(long banId) throws Exception {
		String sql = "UPDATE fail2ban_bans SET status = 'EXPIRED' WHERE id = ?";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setLong(1, banId);
			pstmt.executeUpdate();
		}
	}
	
	/**
	 * Mark ban as manually unbanned.
	 */
	public void markUnbanned(String ip) throws Exception {
		String sql = "UPDATE fail2ban_bans SET status = 'UNBANNED' WHERE ip = ? AND status = 'ACTIVE'";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setString(1, ip);
			pstmt.executeUpdate();
		}
	}
	
	/**
	 * Load all active bans.
	 */
	public List<BanRecord> loadActiveBans() throws Exception {
		List<BanRecord> bans = new ArrayList<>();
		String sql = "SELECT id, ip, jail, reason, ban_time, expire_time FROM fail2ban_bans " +
			"WHERE status = 'ACTIVE' AND expire_time > ? ORDER BY ban_time DESC";
		
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setLong(1, System.currentTimeMillis());
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					BanRecord ban = new BanRecord(
						rs.getLong("id"),
						rs.getString("ip"),
						rs.getString("jail"),
						rs.getString("reason"),
						rs.getLong("ban_time"),
						rs.getLong("expire_time"),
						1 // jailId (simplified, could load from config)
					);
					bans.add(ban);
				}
			}
		}
		
		return bans;
	}
	
	/**
	 * Append event to ring buffer.
	 */
	public void appendEvent(Fail2BanEvent event) throws Exception {
		// Check if we need to cleanup old events
		long count = getEventCount();
		if (count >= MAX_EVENTS) {
			cleanupOldEvents();
		}
		
		String sql = "INSERT INTO fail2ban_events (ts, ip, jail, type, detail) VALUES (?, ?, ?, ?, ?)";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setLong(1, event.timestamp());
			pstmt.setString(2, event.ip());
			pstmt.setString(3, event.jail());
			pstmt.setString(4, event.type());
			pstmt.setString(5, event.detail());
			pstmt.executeUpdate();
		}
	}
	
	/**
	 * Get count of events in table.
	 */
	private long getEventCount() throws Exception {
		try (Statement stmt = connection.createStatement();
		     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as cnt FROM fail2ban_events")) {
			if (rs.next()) {
				return rs.getLong("cnt");
			}
		}
		return 0;
	}
	
	/**
	 * Delete oldest events to maintain ring buffer.
	 */
	private void cleanupOldEvents() throws Exception {
		// Keep only the most recent MAX_EVENTS entries
		String sql = "DELETE FROM fail2ban_events WHERE id NOT IN (" +
			"SELECT id FROM fail2ban_events ORDER BY id DESC LIMIT ?" +
			")";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setInt(1, MAX_EVENTS - 1000); // Keep 1000 slots free
			pstmt.executeUpdate();
		}
	}
	
	/**
	 * Load recent events (for UI).
	 */
	public List<Fail2BanEvent> loadRecentEvents(int limit) throws Exception {
		List<Fail2BanEvent> events = new ArrayList<>();
		String sql = "SELECT ts, ip, jail, type, detail FROM fail2ban_events " +
			"ORDER BY ts DESC LIMIT ?";
		
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setInt(1, limit);
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					Fail2BanEvent event = new Fail2BanEvent(
						rs.getLong("ts"),
						rs.getString("ip"),
						rs.getString("jail"),
						rs.getString("type"),
						rs.getString("detail")
					);
					events.add(event);
				}
			}
		}
		
		return events;
	}
	
	/**
	 * Close database connection.
	 */
	public void close() {
		try {
			if (connection != null && !connection.isClosed()) {
				connection.close();
			}
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Failed to close persistence connection", e);
		}
	}
}
