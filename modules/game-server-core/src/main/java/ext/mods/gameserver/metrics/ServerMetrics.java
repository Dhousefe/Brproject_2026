package ext.mods.gameserver.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Centralized metrics for BRProject game server.
 * Exposes Prometheus-compatible metrics via /metrics endpoint.
 */
public final class ServerMetrics
{
    private static final PrometheusMeterRegistry REGISTRY = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    private static final AtomicInteger ONLINE_PLAYERS = new AtomicInteger(0);
    private static final AtomicInteger ACTIVE_NPCS = new AtomicInteger(0);

    static
    {
        Gauge.builder("game.players.online", ONLINE_PLAYERS, AtomicInteger::get)
            .description("Number of players currently online")
            .register(REGISTRY);

        Gauge.builder("game.npcs.active", ACTIVE_NPCS, AtomicInteger::get)
            .description("Number of active NPCs")
            .register(REGISTRY);
    }

    private static final Counter LOGIN_COUNTER = Counter.builder("game.logins.total")
        .description("Total player logins")
        .register(REGISTRY);

    private static final Counter LOGOUT_COUNTER = Counter.builder("game.logouts.total")
        .description("Total player logouts")
        .register(REGISTRY);

    private static final Counter PACKETS_RECEIVED = Counter.builder("game.packets.received.total")
        .description("Total packets received from clients")
        .register(REGISTRY);

    private static final Timer PACKET_PROCESSING = Timer.builder("game.packets.processing.duration")
        .description("Packet processing duration")
        .register(REGISTRY);

    private ServerMetrics() {}

    public static PrometheusMeterRegistry getRegistry() { return REGISTRY; }

    public static void incrementOnlinePlayers() { ONLINE_PLAYERS.incrementAndGet(); }
    public static void decrementOnlinePlayers() { ONLINE_PLAYERS.decrementAndGet(); }
    public static void setOnlinePlayers(int count) { ONLINE_PLAYERS.set(count); }

    public static void setActiveNpcs(int count) { ACTIVE_NPCS.set(count); }

    public static void recordLogin() { LOGIN_COUNTER.increment(); }
    public static void recordLogout() { LOGOUT_COUNTER.increment(); }

    public static void recordPacketReceived() { PACKETS_RECEIVED.increment(); }
    public static Timer getPacketProcessingTimer() { return PACKET_PROCESSING; }

    /**
     * Returns the Prometheus scrape output (text/plain format).
     */
    public static String scrape() { return REGISTRY.scrape(); }
}
