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
*/
package ext.mods.commons.gui.services;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Thread-safe log manager for capturing all console logs, startup sequence, HTTP requests
 * and error messages of the Ktor Web Server process into memory for Swing GUI display.
 */
public class SiteKtorLogManager {

    private static final SiteKtorLogManager INSTANCE = new SiteKtorLogManager();
    private static final int MAX_ENTRIES = 1000;

    private final List<LogEntry> logEntries = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private long idCounter = 0;

    public static class LogEntry {
        private final long id;
        private final String timestamp;
        private final String level;
        private final String source;
        private final String message;

        public LogEntry(long id, String timestamp, String level, String source, String message) {
            this.id = id;
            this.timestamp = timestamp;
            this.level = level;
            this.source = source;
            this.message = message;
        }

        public long getId() { return id; }
        public String getTimestamp() { return timestamp; }
        public String getLevel() { return level; }
        public String getSource() { return source; }
        public String getMessage() { return message; }
    }

    private SiteKtorLogManager() {}

    public static SiteKtorLogManager getInstance() {
        return INSTANCE;
    }

    public synchronized void addLog(String level, String source, String message) {
        if (message == null || message.trim().isEmpty()) return;
        
        String timeStr = dateFormat.format(new Date());
        logEntries.add(new LogEntry(++idCounter, timeStr, level.toUpperCase(), source, message.trim()));

        while (logEntries.size() > MAX_ENTRIES) {
            logEntries.remove(0);
        }
    }

    public synchronized void addLogLine(String line) {
        if (line == null || line.trim().isEmpty()) return;

        String clean = line.replaceAll("\\u001B\\[[;\\d]*m", "").trim();
        if (clean.isEmpty()) return;

        String level = "INFO";
        String source = "SITE-KTOR";

        String lower = clean.toLowerCase();
        if (lower.contains("error") || lower.contains("exception") || lower.contains("failed") || lower.contains("fatal")) {
            level = "ERROR";
        } else if (lower.contains("warn") || lower.contains("aviso") || lower.contains("deprecated")) {
            level = "WARN";
        }

        if (clean.startsWith("> Task")) {
            source = "GRADLE";
        } else if (clean.contains("listening on") || clean.contains("Engine")) {
            source = "NETTY";
        } else if (clean.contains("[SITE-KTOR]")) {
            clean = clean.replace("[SITE-KTOR]", "").trim();
        }

        addLog(level, source, clean);
    }

    public synchronized List<LogEntry> getLogs() {
        return new ArrayList<>(logEntries);
    }

    public synchronized void clear() {
        logEntries.clear();
    }
}
