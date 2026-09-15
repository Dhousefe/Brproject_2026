/*
 * Copyleft © 2024-2026 L2Brproject
 */
package ext.mods.security.fail2ban.core;

import io.netty.handler.codec.http.HttpHeaders;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Robust Client IP Resolver for Fail2Ban.
 * Extracts the real client IP address when HTTP requests arrive through
 * Cloudflare Tunnel (cloudflared), Cloudflare CDN, or local reverse proxies.
 *
 * Enforces strict anti-spoofing: headers like CF-Connecting-IP, True-Client-IP,
 * X-Real-IP, and X-Forwarded-For are ONLY trusted if the immediate remote TCP socket
 * is a verified proxy (Loopback, Link-Local, or official Cloudflare CIDR block).
 */
public final class ClientIpResolver {

    private static final Set<String> LOCAL_TRUSTED_IPS = Set.of(
        "127.0.0.1",
        "0:0:0:0:0:0:0:1",
        "::1",
        "localhost"
    );

    // Official Cloudflare IPv4 CIDR blocks
    private static final String[] CLOUDFLARE_IPV4_CIDRS = {
        "173.245.48.0/20",
        "103.21.244.0/22",
        "103.22.200.0/22",
        "103.31.4.0/22",
        "141.101.64.0/18",
        "108.162.192.0/18",
        "190.93.240.0/20",
        "188.114.96.0/20",
        "197.234.240.0/22",
        "198.41.128.0/17",
        "162.158.0.0/15",
        "104.16.0.0/13",
        "104.24.0.0/14",
        "172.64.0.0/13",
        "131.0.72.0/22"
    };

    // Official Cloudflare IPv6 CIDR prefixes
    private static final String[] CLOUDFLARE_IPV6_PREFIXES = {
        "2400:cb00:",
        "2606:4700:",
        "2803:f800:",
        "2405:b500:",
        "2405:8100:",
        "2a06:98c0:",
        "2c0f:f248:"
    };

    private static final Pattern IPV4_PATTERN = Pattern.compile(
        "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    private static final List<Ipv4Cidr> PARSED_CF_CIDRS = new ArrayList<>();

    static {
        for (String cidr : CLOUDFLARE_IPV4_CIDRS) {
            try {
                PARSED_CF_CIDRS.add(new Ipv4Cidr(cidr));
            } catch (Exception ignored) {}
        }
    }

    private ClientIpResolver() {}

    /**
     * Resolves the real client IP from incoming HTTP headers and direct remote peer IP.
     *
     * @param remoteSocketIp direct peer IP of the incoming socket
     * @param headers HTTP headers of the request
     * @return real client IP, or remoteSocketIp if not from a trusted proxy or invalid
     */
    public static String resolveRealIp(String remoteSocketIp, HttpHeaders headers) {
        if (remoteSocketIp == null || remoteSocketIp.isBlank()) {
            return "unknown";
        }

        String remoteTrimmed = remoteSocketIp.trim();

        // Anti-spoofing: only trust proxy headers if direct socket is a trusted proxy
        if (!isTrustedProxy(remoteTrimmed)) {
            return remoteTrimmed;
        }

        if (headers == null || headers.isEmpty()) {
            return remoteTrimmed;
        }

        // 1. Cloudflare Tunnel / Cloudflare CDN primary header
        String cfIp = headers.get("CF-Connecting-IP");
        if (cfIp != null) {
            cfIp = cfIp.trim();
            if (isValidIp(cfIp)) {
                return cfIp;
            }
        }

        // 1b. True-Client-IP (Cloudflare Enterprise / Akamai)
        String trueClientIp = headers.get("True-Client-IP");
        if (trueClientIp != null) {
            trueClientIp = trueClientIp.trim();
            if (isValidIp(trueClientIp)) {
                return trueClientIp;
            }
        }

        // 2. X-Real-IP fallback (Nginx / HAProxy / ingress)
        String xRealIp = headers.get("X-Real-IP");
        if (xRealIp != null) {
            xRealIp = xRealIp.trim();
            if (isValidIp(xRealIp)) {
                return xRealIp;
            }
        }

        // 3. X-Forwarded-For chain: closest untrusted hop
        String xff = headers.get("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] hops = xff.split(",");
            for (int i = hops.length - 1; i >= 0; i--) {
                String hop = hops[i].trim();
                if (isValidIp(hop) && !isTrustedProxy(hop)) {
                    return hop;
                }
            }
            String firstHop = hops[0].trim();
            if (isValidIp(firstHop)) {
                return firstHop;
            }
        }

        return remoteTrimmed;
    }

    /**
     * Alias for {@link #resolveRealIp(String, HttpHeaders)} for API symmetry with Proxy module.
     */
    public static String extractRealIp(String remoteSocketIp, HttpHeaders headers) {
        return resolveRealIp(remoteSocketIp, headers);
    }

    /**
     * Checks whether an IP belongs to a trusted proxy (Loopback, Link-Local, or Cloudflare).
     */
    public static boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }

        String trimmed = ip.trim();

        if (LOCAL_TRUSTED_IPS.contains(trimmed)) {
            return true;
        }

        // IPv6 Cloudflare check
        if (trimmed.contains(":")) {
            for (String prefix : CLOUDFLARE_IPV6_PREFIXES) {
                if (trimmed.toLowerCase().startsWith(prefix)) {
                    return true;
                }
            }
            try {
                InetAddress addr = InetAddress.getByName(trimmed);
                return addr.isLoopbackAddress() || addr.isLinkLocalAddress();
            } catch (UnknownHostException ignored) {
                return false;
            }
        }

        // IPv4 Loopback / Link-local check
        try {
            InetAddress addr = InetAddress.getByName(trimmed);
            if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()) {
                return true;
            }
        } catch (UnknownHostException ignored) {
            return false;
        }

        // IPv4 Cloudflare CIDR range check
        if (IPV4_PATTERN.matcher(trimmed).matches()) {
            try {
                int ipInt = ipv4ToInt(trimmed);
                for (Ipv4Cidr cidr : PARSED_CF_CIDRS) {
                    if (cidr.contains(ipInt)) {
                        return true;
                    }
                }
            } catch (Exception ignored) {}
        }

        return false;
    }

    /**
     * Validates whether string is a well-formed IPv4 or IPv6 address.
     */
    public static boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String trimmed = ip.trim();
        if (IPV4_PATTERN.matcher(trimmed).matches()) {
            return true;
        }
        if (trimmed.contains(":")) {
            try {
                InetAddress addr = InetAddress.getByName(trimmed);
                return addr.getAddress().length == 16;
            } catch (UnknownHostException ignored) {
                return false;
            }
        }
        return false;
    }

    private static int ipv4ToInt(String ip) {
        String[] parts = ip.split("\\.");
        return (Integer.parseInt(parts[0]) << 24) |
               (Integer.parseInt(parts[1]) << 16) |
               (Integer.parseInt(parts[2]) << 8)  |
                Integer.parseInt(parts[3]);
    }

    private static final class Ipv4Cidr {
        private final int network;
        private final int mask;

        Ipv4Cidr(String cidr) {
            String[] parts = cidr.split("/");
            this.network = ipv4ToInt(parts[0]);
            int prefix = Integer.parseInt(parts[1]);
            this.mask = prefix == 0 ? 0 : (-1 << (32 - prefix));
        }

        boolean contains(int ip) {
            return (ip & mask) == (network & mask);
        }
    }
}
