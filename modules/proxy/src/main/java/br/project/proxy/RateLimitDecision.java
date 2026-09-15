package br.project.proxy;

public record RateLimitDecision(boolean allowed, int limit, int currentCount, long retryAfterMillis) {
    public static RateLimitDecision allowed(int limit, int currentCount) {
        return new RateLimitDecision(true, limit, currentCount, 0L);
    }

    public static RateLimitDecision denied(int limit, int currentCount, long retryAfterMillis) {
        return new RateLimitDecision(false, limit, currentCount, retryAfterMillis);
    }
}
