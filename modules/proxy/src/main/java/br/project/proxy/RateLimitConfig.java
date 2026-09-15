package br.project.proxy;

public record RateLimitConfig(
    boolean enabled,
    int windowSeconds,
    int maxConnections,
    int maxRequests
) {
    public static RateLimitConfig disabled() {
        return new RateLimitConfig(false, 60, 0, 0);
    }

    public static RateLimitConfig defaultsFor(ProxyRoute.RouteType type) {
        if (type == ProxyRoute.RouteType.HTTP) {
            return new RateLimitConfig(true, 60, 120, 120);
        }
        return new RateLimitConfig(true, 60, 30, 0);
    }

    public int effectiveConnectionLimit() {
        return maxConnections > 0 ? maxConnections : Integer.MAX_VALUE;
    }

    public int effectiveRequestLimit() {
        return maxRequests > 0 ? maxRequests : effectiveConnectionLimit();
    }
}
