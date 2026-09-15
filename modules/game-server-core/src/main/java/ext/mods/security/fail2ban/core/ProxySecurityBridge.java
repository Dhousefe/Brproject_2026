/*
* Copyleft © 2024-2026 L2Brproject
* * This file is part of L2Brproject derived from aCis409/RusaCis3.8
*/
package ext.mods.security.fail2ban.core;

import ext.mods.security.fail2ban.Fail2BanInitializer;
import ext.mods.security.fail2ban.simd.CleanRoomManager;
import ext.mods.security.fail2ban.simd.PacketVector128;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reactive security bridge between the standalone Netty Reverse Proxy (:proxy)
 * and Fail2Ban's BanManager. Parses structured security events from the proxy's
 * stdout stream and records violations in the 'dos_flood' jail.
 */
public final class ProxySecurityBridge {

    private static final Logger LOGGER = Logger.getLogger(ProxySecurityBridge.class.getName());

    // Matches: [PROXY-SECURITY-EVENT] type=RATE_LIMIT_DENIED route='l2-login' ip=1.2.3.4 (extra params)
    private static final Pattern SECURITY_EVENT_PATTERN = Pattern.compile(
        "\\[PROXY-SECURITY-EVENT\\]\\s+type=(\\w+)\\s+route=['\"]?([^'\"\\s]+)['\"]?\\s+ip=([^\\s]+)(?:\\s+(.*))?"
    );

    // Matches: [PROXY-TRAFFIC-EVENT] proto=HTTPS route='site' ip=1.2.3.4 (extra params)
    private static final Pattern TRAFFIC_EVENT_PATTERN = Pattern.compile(
        "\\[PROXY-TRAFFIC-EVENT\\]\\s+proto=(\\w+)\\s+route=['\"]?([^'\"\\s]+)['\"]?\\s+ip=([^\\s]+)(?:\\s+(.*))?"
    );

    // Matches: [KTOR-HTTP] POST /api/account/clan-services (IP: 2804:d57:592c:3e00:742:a222:e8b6:7a18)
    private static final Pattern KTOR_HTTP_PATTERN = Pattern.compile(
        "\\[KTOR-HTTP\\]\\s+(\\w+)\\s+([^\\s]+)\\s+\\(IP:\\s*([^\\)]+)\\)"
    );

    // Extracts optional sample='...' or uri='...'
    private static final Pattern SAMPLE_PATTERN = Pattern.compile("sample=['\"]?(.*?)['\"]?(?:\\s+\\w+=|$)");
    private static final Pattern URI_PATTERN = Pattern.compile("uri=['\"]?([^'\"\\s]+)['\"]?");
    private static final Pattern METHOD_PATTERN = Pattern.compile("method=(\\w+)");
    private static final Pattern STATUS_PATTERN = Pattern.compile("status=(\\d+)");
    private static final Pattern ACTION_PATTERN = Pattern.compile("action=(\\w+)");
    private static final Pattern LATENCY_PATTERN = Pattern.compile("latency=(\\d+ms)");

    private ProxySecurityBridge() {}

    /**
     * Process a raw stdout line from the Netty Proxy process or Site Ktor process.
     *
     * @param line log line
     * @return true if line was a recognized security or traffic event, false otherwise
     */
    public static boolean processLine(String line) {
        if (line == null) {
            return false;
        }

        if (line.contains("[PROXY-TRAFFIC-EVENT]")) {
            return processTrafficEvent(line);
        }

        if (line.contains("[PROXY-SECURITY-EVENT]")) {
            return processSecurityEvent(line);
        }

        if (line.contains("[KTOR-HTTP]")) {
            return processKtorHttpEvent(line);
        }

        return false;
    }

    private static boolean processTrafficEvent(String line) {
        Matcher m = TRAFFIC_EVENT_PATTERN.matcher(line);
        if (!m.find()) {
            return false;
        }

        String proto = m.group(1).toUpperCase();
        String route = m.group(2);
        String ip = m.group(3);
        String extra = m.group(4) != null ? m.group(4) : "";

        String method = null;
        Matcher mm = METHOD_PATTERN.matcher(extra);
        if (mm.find()) method = mm.group(1);

        String uri = null;
        Matcher um = URI_PATTERN.matcher(extra);
        if (um.find()) uri = um.group(1);

        String statusStr = null;
        Matcher stm = STATUS_PATTERN.matcher(extra);
        if (stm.find()) statusStr = stm.group(1);

        String action = null;
        Matcher am = ACTION_PATTERN.matcher(extra);
        if (am.find()) action = am.group(1);

        String latency = null;
        Matcher lm = LATENCY_PATTERN.matcher(extra);
        if (lm.find()) latency = lm.group(1);

        String detail;
        String sample;
        String statusDisplay;

        if (method != null || uri != null) {
            detail = (method != null ? method : "REQ") + " " + (uri != null ? uri : "/")
                + (latency != null ? " (" + latency + ")" : "");
            sample = uri != null ? uri : (method != null ? method : proto);
            int code = 200;
            if (statusStr != null) {
                try {
                    code = Integer.parseInt(statusStr);
                } catch (NumberFormatException ignored) {}
            }
            if (code == 429) {
                statusDisplay = "RATE_LIMIT";
            } else if (code >= 200 && code < 400) {
                statusDisplay = "PERMITIDO";
            } else if (code >= 400 && code < 500) {
                statusDisplay = "CLIENT_ERR " + code;
            } else {
                statusDisplay = "SERVER_ERR " + code;
            }
        } else if (action != null) {
            detail = action + ("CONNECT".equalsIgnoreCase(action) ? " ESTABLISHED" : " CLOSED");
            sample = "TCP " + action;
            statusDisplay = "CONNECT".equalsIgnoreCase(action) ? "ATIVO" : "ENCERRADO";
        } else {
            detail = extra;
            sample = proto + " " + route;
            statusDisplay = "PERMITIDO";
        }

        // Feed proxy traffic event into CleanRoomManager (SIMD Online Training & Anomaly Detection)
        PacketVector128 vector;
        if ("HTTP".equalsIgnoreCase(proto) || "HTTPS".equalsIgnoreCase(proto)) {
            vector = PacketVector128.synthesizeHttp(ip, method != null ? method : "GET", uri != null ? uri.length() : 1, 8, 0);
        } else {
            vector = PacketVector128.synthesizeTcp(ip, 128, 0x0E, 20L, 64240);
        }
        CleanRoomManager.getInstance().processPacket(vector);

        BanManager banManager = Fail2BanInitializer.getBanManager();
        if (banManager != null) {
            banManager.emitTrafficEvent(Fail2BanEvent.trafficEvent(ip, route, proto, detail, sample, statusDisplay));
        }
        return true;
    }

    private static boolean processKtorHttpEvent(String line) {
        Matcher m = KTOR_HTTP_PATTERN.matcher(line);
        if (!m.find()) {
            return false;
        }

        String method = m.group(1).toUpperCase();
        String uri = m.group(2);
        String ip = m.group(3).trim();

        boolean isWs = uri.startsWith("/ws");
        String proto = isWs ? "WSS" : "HTTPS";
        String route = "site-ktor";
        String detail = method + " " + uri;
        String sample = uri;

        BanManager banManager = Fail2BanInitializer.getBanManager();
        boolean isBanned = (banManager != null && banManager.isBanned(ip));
        String statusDisplay = isBanned ? "BLOQUEADO" : "PERMITIDO";

        // Feed into CleanRoomManager (SIMD online training / evaluation)
        PacketVector128 vector = PacketVector128.synthesizeHttp(ip, method, uri.length(), 8, 0);
        CleanRoomManager.getInstance().processPacket(vector);

        if (banManager != null) {
            banManager.emitTrafficEvent(Fail2BanEvent.trafficEvent(ip, route, proto, detail, sample, statusDisplay));
            if (isBanned) {
                banManager.emitEvent(Fail2BanEvent.proxyEvent(ip, route, "BAN_DROP", "Bloqueado acesso ao site Ktor: " + uri, sample));
                LOGGER.warning("[FAIL2BAN-KTOR] Banned IP attempt intercepted: " + ip + " -> " + detail);
            }
        }

        return true;
    }

    private static boolean processSecurityEvent(String line) {
        Matcher m = SECURITY_EVENT_PATTERN.matcher(line);
        if (!m.find()) {
            return false;
        }

        String type = m.group(1);
        String route = m.group(2);
        String ip = m.group(3);
        String extra = m.group(4) != null ? m.group(4) : "";

        if ("RATE_LIMIT_DENIED".equalsIgnoreCase(type) && ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip))) {
            return false;
        }

        String sample = null;
        Matcher sm = SAMPLE_PATTERN.matcher(extra);
        if (sm.find()) {
            sample = sm.group(1).trim();
        } else {
            Matcher um = URI_PATTERN.matcher(extra);
            if (um.find()) {
                sample = um.group(1).trim();
            }
        }

        BanManager banManager = Fail2BanInitializer.getBanManager();

        if ("RATE_LIMIT_DENIED".equalsIgnoreCase(type)) {
            if (banManager != null) {
                String reason = "Proxy rate limit exceeded on route '" + route + "' (" + extra + ")";
                banManager.recordFailure(ip, "dos_flood", reason);
                banManager.emitEvent(Fail2BanEvent.proxyEvent(ip, route, "RATE_LIMIT_DENIED", extra, sample));
                LOGGER.fine("[ProxySecurityBridge] Recorded dos_flood failure for IP: " + ip + " on route: " + route);
            }
            return true;
        } else if ("BAN_DROP".equalsIgnoreCase(type)) {
            if (banManager != null) {
                banManager.emitEvent(Fail2BanEvent.proxyEvent(ip, route, "BAN_DROP", "Blocked by proxy edge (" + extra + ")", sample));
            }
            LOGGER.fine("[ProxySecurityBridge] Proxy dropped banned IP: " + ip);
            return true;
        } else if ("REQ_PASS".equalsIgnoreCase(type) || "ALLOW".equalsIgnoreCase(type)) {
            if (banManager != null) {
                banManager.emitEvent(Fail2BanEvent.proxyEvent(ip, route, "REQ_PASS", extra, sample));
            }
            return true;
        }

        return false;
    }
}
