/*
* Copyleft © 2024-2026 L2Brproject
* * This file is part of L2Brproject derived from aCis409/RusaCis3.8
*/
package ext.mods.security.fail2ban.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rastreia conexões recentes do Proxy Nativo Netty para telemetria e análise de tráfego.
 */
public class ConnectionTracker {

	private static final int MAX_EVENTS = 1000;
	private static final List<String> RECENT_EVENTS = Collections.synchronizedList(new ArrayList<>());
	private static final Pattern IP_PATTERN = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");

	public static void record(String event) {
		if (event == null || event.isBlank()) return;
		if (RECENT_EVENTS.size() >= MAX_EVENTS) {
			RECENT_EVENTS.remove(0);
		}
		RECENT_EVENTS.add(event);
	}

	public static void parseAndRecordFromLog(String logLine, String source) {
		if (logLine == null || logLine.isBlank()) return;
		Matcher matcher = IP_PATTERN.matcher(logLine);
		if (matcher.find()) {
			String ip = matcher.group();
			if (!"127.0.0.1".equals(ip) && !"0.0.0.0".equals(ip)) {
				record("[" + source + "] " + ip + " - " + logLine);
			}
		}
	}

	public static List<String> getRecentEvents() {
		return new ArrayList<>(RECENT_EVENTS);
	}

	public static void clear() {
		RECENT_EVENTS.clear();
	}
}
