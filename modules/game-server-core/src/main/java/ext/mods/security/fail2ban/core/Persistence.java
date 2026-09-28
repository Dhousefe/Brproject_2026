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

import ext.mods.commons.pool.ConnectionPool;
import ext.mods.commons.jdbc.SqlDialect;
import ext.mods.commons.jdbc.SupportedDatabase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hybrid & Resilient persistence layer for Fail2Ban: bans and events.
 * Seamlessly operates on MariaDB, MySQL, PostgreSQL, or standalone SQLite WAL.
 * Auto-detects if the main server ConnectionPool is active, falling back gracefully to local SQLite.
 */
public class Persistence {
	private static final Logger LOGGER = Logger.getLogger(Persistence.class.getName());
	private static final int MAX_EVENTS = 10000;

	private final String dbPath;
	private Connection standaloneSqliteConn;
	private boolean useConnectionPool = false;

	public Persistence(String dbPath) {
		this.dbPath = dbPath;
		initialize();
	}

	/**
	 * Initialize database and create tables if needed.
	 */
	private synchronized void initialize() {
		try {
			if (ConnectionPool.isInitialized()) {
				this.useConnectionPool = true;
				LOGGER.info("[Fail2Ban/Persistence] Using shared server ConnectionPool (" + SqlDialect.getActiveDatabase() + ")");
				try (Connection conn = ConnectionPool.getConnection()) {
					createTables(conn);
				}
			} else {
				initStandaloneSqlite();
			}
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "[Fail2Ban/Persistence] Error initializing primary database, attempting SQLite fallback: " + e.getMessage(), e);
			initStandaloneSqlite();
		}
	}

	private void initStandaloneSqlite() {
		this.useConnectionPool = false;
		try {
			Path dbFile = Paths.get(dbPath);
			if (dbFile.getParent() != null) {
				Files.createDirectories(dbFile.getParent());
			}

			// Explicitly load SQLite JDBC driver
			try {
				Class.forName("org.sqlite.JDBC");
			} catch (ClassNotFoundException ignored) {}

			String url = "jdbc:sqlite:" + dbPath;
			standaloneSqliteConn = DriverManager.getConnection(url);

			try (Statement stmt = standaloneSqliteConn.createStatement()) {
				stmt.execute("PRAGMA journal_mode = WAL");
				stmt.execute("PRAGMA busy_timeout = 5000");
			}

			createTables(standaloneSqliteConn);
			LOGGER.info("[Fail2Ban/Persistence] Standalone SQLite initialized at: " + dbPath);
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "[Fail2Ban/Persistence] Fatal error initializing SQLite persistence at " + dbPath, e);
		}
	}

	private Connection acquireConnection() throws SQLException {
		if (useConnectionPool && ConnectionPool.isInitialized()) {
			return ConnectionPool.getConnection();
		}
		if (standaloneSqliteConn == null || standaloneSqliteConn.isClosed()) {
			initStandaloneSqlite();
		}
		return standaloneSqliteConn;
	}

	private void releaseConnection(Connection conn) {
		if (useConnectionPool && conn != null) {
			try {
				conn.close();
			} catch (Exception ignored) {}
		}
		// If standalone SQLite, we keep standaloneSqliteConn open until close()
	}

	/**
	 * Create tables if they don't exist, compatible with MariaDB, MySQL, PostgreSQL, and SQLite.
	 */
	private void createTables(Connection conn) throws Exception {
		boolean isSqlite = isCurrentConnSqlite(conn);
		boolean isPostgres = isCurrentConnPostgres(conn);

		try (Statement stmt = conn.createStatement()) {
			if (isSqlite) {
				stmt.execute("CREATE TABLE IF NOT EXISTS fail2ban_bans (" +
					"id INTEGER PRIMARY KEY AUTOINCREMENT," +
					"ip TEXT NOT NULL," +
					"jail TEXT NOT NULL," +
					"reason TEXT DEFAULT ''," +
					"ban_time INTEGER NOT NULL DEFAULT 0," +
					"expire_time INTEGER NOT NULL DEFAULT 0," +
					"status TEXT NOT NULL DEFAULT 'ACTIVE'," +
					"source TEXT DEFAULT 'AUTO'" +
					")");

				stmt.execute("CREATE INDEX IF NOT EXISTS idx_f2b_ip_status ON fail2ban_bans(ip, status)");
				stmt.execute("CREATE INDEX IF NOT EXISTS idx_f2b_expire ON fail2ban_bans(expire_time)");

				stmt.execute("CREATE TABLE IF NOT EXISTS fail2ban_events (" +
					"id INTEGER PRIMARY KEY AUTOINCREMENT," +
					"ts INTEGER NOT NULL," +
					"ip TEXT NOT NULL," +
					"jail TEXT NOT NULL," +
					"type TEXT NOT NULL," +
					"detail TEXT DEFAULT ''" +
					")");

				stmt.execute("CREATE INDEX IF NOT EXISTS idx_f2e_ts ON fail2ban_events(ts)");
				stmt.execute("CREATE INDEX IF NOT EXISTS idx_f2e_ip ON fail2ban_events(ip)");
			} else if (isPostgres) {
				stmt.execute("CREATE TABLE IF NOT EXISTS fail2ban_bans (" +
					"id BIGSERIAL PRIMARY KEY," +
					"ip VARCHAR(64) NOT NULL," +
					"jail VARCHAR(64) NOT NULL," +
					"reason VARCHAR(255) DEFAULT ''," +
					"ban_time BIGINT NOT NULL DEFAULT 0," +
					"expire_time BIGINT NOT NULL DEFAULT 0," +
					"status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'," +
					"source VARCHAR(32) DEFAULT 'AUTO'" +
					")");

				stmt.execute("CREATE INDEX IF NOT EXISTS idx_f2b_ip_status ON fail2ban_bans(ip, status)");
				stmt.execute("CREATE INDEX IF NOT EXISTS idx_f2b_expire ON fail2ban_bans(expire_time)");

				stmt.execute("CREATE TABLE IF NOT EXISTS fail2ban_events (" +
					"id BIGSERIAL PRIMARY KEY," +
					"ts BIGINT NOT NULL," +
					"ip VARCHAR(64) NOT NULL," +
					"jail VARCHAR(64) NOT NULL," +
					"type VARCHAR(32) NOT NULL," +
					"detail VARCHAR(512) DEFAULT ''" +
					")");

				stmt.execute("CREATE INDEX IF NOT EXISTS idx_f2e_ts ON fail2ban_events(ts)");
				stmt.execute("CREATE INDEX IF NOT EXISTS idx_f2e_ip ON fail2ban_events(ip)");
			} else {
				// MariaDB / MySQL / SQLServer
				stmt.execute("CREATE TABLE IF NOT EXISTS `fail2ban_bans` (" +
					"`id` BIGINT NOT NULL AUTO_INCREMENT," +
					"`ip` VARCHAR(64) NOT NULL," +
					"`jail` VARCHAR(64) NOT NULL," +
					"`reason` VARCHAR(255) DEFAULT ''," +
					"`ban_time` BIGINT NOT NULL DEFAULT 0," +
					"`expire_time` BIGINT NOT NULL DEFAULT 0," +
					"`status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'," +
					"`source` VARCHAR(32) DEFAULT 'AUTO'," +
					"PRIMARY KEY (`id`)," +
					"KEY `idx_f2b_ip_status` (`ip`, `status`)," +
					"KEY `idx_f2b_expire` (`expire_time`)" +
					")");

				stmt.execute("CREATE TABLE IF NOT EXISTS `fail2ban_events` (" +
					"`id` BIGINT NOT NULL AUTO_INCREMENT," +
					"`ts` BIGINT NOT NULL," +
					"`ip` VARCHAR(64) NOT NULL," +
					"`jail` VARCHAR(64) NOT NULL," +
					"`type` VARCHAR(32) NOT NULL," +
					"`detail` VARCHAR(512) DEFAULT ''," +
					"PRIMARY KEY (`id`)," +
					"KEY `idx_f2e_ts` (`ts`)," +
					"KEY `idx_f2e_ip` (`ip`)" +
					")");
			}
		}
	}

	private boolean isCurrentConnSqlite(Connection conn) {
		if (!useConnectionPool) return true;
		try {
			String metaUrl = conn.getMetaData().getURL();
			return metaUrl != null && metaUrl.contains("sqlite");
		} catch (Exception e) {
			return SqlDialect.isSqlite();
		}
	}

	private boolean isCurrentConnPostgres(Connection conn) {
		if (!useConnectionPool) return false;
		try {
			String metaUrl = conn.getMetaData().getURL();
			return metaUrl != null && metaUrl.contains("postgresql");
		} catch (Exception e) {
			return SqlDialect.getActiveDatabase() == SupportedDatabase.POSTGRESQL;
		}
	}

	/**
	 * Save a ban record.
	 */
	public void saveBan(BanRecord ban) throws Exception {
		String sql = "INSERT INTO fail2ban_bans (ip, jail, reason, ban_time, expire_time, status, source) " +
			"VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'AUTO')";
		Connection conn = acquireConnection();
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setString(1, ban.ip());
			pstmt.setString(2, ban.jailName());
			pstmt.setString(3, ban.reason() != null ? ban.reason() : "");
			pstmt.setLong(4, ban.banTime());
			pstmt.setLong(5, ban.expireTime());
			pstmt.executeUpdate();
		} finally {
			releaseConnection(conn);
		}
	}

	/**
	 * Mark ban as expired.
	 */
	public void markExpired(long banId) throws Exception {
		String sql = "UPDATE fail2ban_bans SET status = 'EXPIRED' WHERE id = ?";
		Connection conn = acquireConnection();
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setLong(1, banId);
			pstmt.executeUpdate();
		} finally {
			releaseConnection(conn);
		}
	}

	/**
	 * Mark ban as manually unbanned.
	 */
	public void markUnbanned(String ip) throws Exception {
		String sql = "UPDATE fail2ban_bans SET status = 'UNBANNED' WHERE ip = ? AND status = 'ACTIVE'";
		Connection conn = acquireConnection();
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setString(1, ip);
			pstmt.executeUpdate();
		} finally {
			releaseConnection(conn);
		}
	}

	/**
	 * Load all active bans.
	 */
	public List<BanRecord> loadActiveBans() throws Exception {
		List<BanRecord> bans = new ArrayList<>();
		String sql = "SELECT id, ip, jail, reason, ban_time, expire_time FROM fail2ban_bans " +
			"WHERE status = 'ACTIVE' AND expire_time > ? ORDER BY ban_time DESC";

		Connection conn = acquireConnection();
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
						1
					);
					bans.add(ban);
				}
			}
		} finally {
			releaseConnection(conn);
		}

		return bans;
	}

	/**
	 * Append event to ring buffer.
	 */
	public void appendEvent(Fail2BanEvent event) {
		try {
			long count = getEventCount();
			if (count >= MAX_EVENTS) {
				cleanupOldEvents();
			}

			String sql = "INSERT INTO fail2ban_events (ts, ip, jail, type, detail) VALUES (?, ?, ?, ?, ?)";
			Connection conn = acquireConnection();
			try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
				pstmt.setLong(1, event.timestamp());
				pstmt.setString(2, event.ip());
				pstmt.setString(3, event.jail());
				pstmt.setString(4, event.type());
				pstmt.setString(5, event.detail() != null ? event.detail() : "");
				pstmt.executeUpdate();
			} finally {
				releaseConnection(conn);
			}
		} catch (Exception e) {
			LOGGER.log(Level.FINE, "[Fail2Ban/Persistence] Could not append event: " + e.getMessage());
		}
	}

	/**
	 * Get count of events in table.
	 */
	private long getEventCount() throws Exception {
		Connection conn = acquireConnection();
		try (Statement stmt = conn.createStatement();
		     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as cnt FROM fail2ban_events")) {
			if (rs.next()) {
				return rs.getLong("cnt");
			}
		} finally {
			releaseConnection(conn);
		}
		return 0;
	}

	/**
	 * Delete oldest events to maintain ring buffer.
	 * 100% ANSI SQL compatible across MariaDB, MySQL, PostgreSQL, and SQLite.
	 */
	private void cleanupOldEvents() {
		try {
			Connection conn = acquireConnection();
			try {
				// Safe portable cutoff discovery: find the ID boundary for retention
				long cutoffId = -1;
				int offset = Math.max(0, MAX_EVENTS - 1000);
				String queryBoundary = "SELECT id FROM fail2ban_events ORDER BY id DESC LIMIT 1 OFFSET " + offset;
				try (Statement s = conn.createStatement();
				     ResultSet rs = s.executeQuery(queryBoundary)) {
					if (rs.next()) {
						cutoffId = rs.getLong("id");
					}
				}
				if (cutoffId > 0) {
					try (PreparedStatement del = conn.prepareStatement("DELETE FROM fail2ban_events WHERE id < ?")) {
						del.setLong(1, cutoffId);
						del.executeUpdate();
					}
				}
			} finally {
				releaseConnection(conn);
			}
		} catch (Exception e) {
			LOGGER.log(Level.FINE, "[Fail2Ban/Persistence] Old events cleanup skipped: " + e.getMessage());
		}
	}

	/**
	 * Load recent events (for UI).
	 */
	public List<Fail2BanEvent> loadRecentEvents(int limit) throws Exception {
		List<Fail2BanEvent> events = new ArrayList<>();
		String sql = "SELECT ts, ip, jail, type, detail FROM fail2ban_events ORDER BY ts DESC LIMIT ?";

		Connection conn = acquireConnection();
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
		} finally {
			releaseConnection(conn);
		}

		return events;
	}

	/**
	 * Close database connection.
	 */
	public synchronized void close() {
		try {
			if (standaloneSqliteConn != null && !standaloneSqliteConn.isClosed()) {
				standaloneSqliteConn.close();
				standaloneSqliteConn = null;
			}
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "[Fail2Ban/Persistence] Failed to close standalone connection", e);
		}
	}
}
