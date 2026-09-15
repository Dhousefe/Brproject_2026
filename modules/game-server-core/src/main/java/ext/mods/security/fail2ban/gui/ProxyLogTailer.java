/*
* Copyleft © 2024-2026 L2Brproject
*/
package ext.mods.security.fail2ban.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tails the proxy log file (logs/proxy/proxy.log + proxy-error.log + proxy-access.log).
 * Reads last N lines, then polls for new content.
 */
public class ProxyLogTailer {
	private static final Logger LOGGER = Logger.getLogger(ProxyLogTailer.class.getName());
	private static final int DEFAULT_MAX_LINES = 500;
	private static final long POLL_INTERVAL_MS = 500;
	
	private final Path logDir;
	private final int maxLines;
	private StringBuilder buffer;
	private long lastSize;
	private boolean paused;
	
	private static final StringBuilder GLOBAL_LOG_BUFFER = new StringBuilder();

	public static synchronized void appendLog(String line) {
		if (line == null) return;
		GLOBAL_LOG_BUFFER.append(line).append("\n");
		if (GLOBAL_LOG_BUFFER.length() > 50000) {
			GLOBAL_LOG_BUFFER.delete(0, 20000);
		}
	}
	
	public ProxyLogTailer() {
		this("logs/proxy", DEFAULT_MAX_LINES);
	}
	
	public ProxyLogTailer(String logDirPath, int maxLines) {
		this.logDir = Paths.get(logDirPath);
		this.maxLines = maxLines;
		this.buffer = new StringBuilder();
		this.lastSize = 0;
		this.paused = false;
		
		initialize();
	}
	
	/**
	 * Load initial content (last N lines from all log files).
	 */
	private void initialize() {
		buffer.setLength(0);
		appendFile("proxy.log");
		appendFile("proxy-error.log");
		appendFile("proxy-access.log");
	}
	
	private void appendFile(String fileName) {
		Path file = logDir.resolve(fileName);
		if (!Files.exists(file)) return;
		
		try {
			long size = Files.size(file);
			if (size > lastSize) {
				// Read new content
				try (var lines = Files.lines(file)) {
					lines.skip(Math.max(0, countLines(file) - maxLines / 3))
						.forEach(line -> {
							buffer.append("[").append(fileName).append("] ").append(line).append("\n");
						});
				}
				lastSize = size;
			}
		} catch (IOException e) {
			LOGGER.log(Level.FINE, "Failed to read " + fileName, e);
		}
	}
	
	/**
	 * Count lines in file (approximate).
	 */
	private long countLines(Path file) throws IOException {
		try (var stream = Files.lines(file)) {
			return stream.count();
		}
	}
	
	/**
	 * Read current content (from in-memory stream and log files).
	 */
	public synchronized String read() {
		if (paused) return buffer.toString();
		
		// 1. Incorporate live in-memory streaming logs if present
		String globalLogs;
		synchronized (GLOBAL_LOG_BUFFER) {
			globalLogs = GLOBAL_LOG_BUFFER.toString();
		}
		if (!globalLogs.isEmpty()) {
			buffer.setLength(0);
			buffer.append(globalLogs);
		}
		
		// 2. Poll for file content if files exist
		appendFile("proxy.log");
		appendFile("proxy-error.log");
		appendFile("proxy-access.log");
		
		// Trim to maxLines
		trim();
		
		return buffer.toString();
	}
	
	/**
	 * Trim buffer to last maxLines.
	 */
	private void trim() {
		String content = buffer.toString();
		String[] lines = content.split("\n");
		if (lines.length > maxLines) {
			StringBuilder trimmed = new StringBuilder();
			for (int i = lines.length - maxLines; i < lines.length; i++) {
				trimmed.append(lines[i]).append("\n");
			}
			buffer = trimmed;
		}
	}
	
	public void pause() { this.paused = true; }
	public void resume() { this.paused = false; }
	public void clear() {
		buffer.setLength(0);
		synchronized (GLOBAL_LOG_BUFFER) {
			GLOBAL_LOG_BUFFER.setLength(0);
		}
	}
	public void reload() { initialize(); }
	
	public boolean isPaused() { return paused; }
}
