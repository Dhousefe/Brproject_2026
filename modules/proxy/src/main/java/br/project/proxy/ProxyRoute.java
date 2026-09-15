package br.project.proxy;

import java.util.Locale;

public record ProxyRoute(
    String name,
    RouteType type,
    boolean enabled,
    String bindHost,
    int bindPort,
    String targetHost,
    int targetPort,
    boolean preserveHost,
    boolean addForwardedHeaders,
    RateLimitConfig rateLimit,
    String tlsCertPath,
    String tlsKeyPath,
    boolean autoTls,
    String tlsDomain,
    int maxContentLength
) {
    public static final int DEFAULT_MAX_CONTENT_LENGTH = 64 * 1024 * 1024; // 64 MB

    public ProxyRoute(
        String name,
        RouteType type,
        boolean enabled,
        String bindHost,
        int bindPort,
        String targetHost,
        int targetPort,
        boolean preserveHost,
        boolean addForwardedHeaders,
        RateLimitConfig rateLimit,
        String tlsCertPath,
        String tlsKeyPath
    ) {
        this(name, type, enabled, bindHost, bindPort, targetHost, targetPort,
             preserveHost, addForwardedHeaders, rateLimit, tlsCertPath, tlsKeyPath, false, null, DEFAULT_MAX_CONTENT_LENGTH);
    }

    public ProxyRoute(
        String name,
        RouteType type,
        boolean enabled,
        String bindHost,
        int bindPort,
        String targetHost,
        int targetPort,
        boolean preserveHost,
        boolean addForwardedHeaders,
        RateLimitConfig rateLimit,
        String tlsCertPath,
        String tlsKeyPath,
        boolean autoTls
    ) {
        this(name, type, enabled, bindHost, bindPort, targetHost, targetPort,
             preserveHost, addForwardedHeaders, rateLimit, tlsCertPath, tlsKeyPath, autoTls, null, DEFAULT_MAX_CONTENT_LENGTH);
    }

    public boolean tlsEnabled() {
        return isAutoTls() || (tlsCertPath != null && !tlsCertPath.isBlank()
            && tlsKeyPath != null && !tlsKeyPath.isBlank());
    }

    public boolean isAutoTls() {
        return autoTls || "auto".equalsIgnoreCase(tlsCertPath);
    }

    public enum RouteType {
        TCP,
        HTTP,
        HTTP_REDIRECT;

        public static RouteType fromXml(String value) {
            if (value == null || value.isBlank()) {
                return TCP;
            }
            return RouteType.valueOf(value.trim().toUpperCase(Locale.ROOT).replace("-", "_"));
        }
    }
}
