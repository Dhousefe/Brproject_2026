package ext.mods.benchmark.proxy;

import br.project.proxy.FixedWindowRateLimiter;
import br.project.proxy.ProxyBanCache;
import br.project.proxy.RateLimitDecision;
import org.openjdk.jmh.annotations.*;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * JMH Microbenchmark for Netty Proxy Edge Security:
 * - FixedWindowRateLimiter atomic acquire throughput
 * - ProxyBanCache O(1) in-memory ban lookup latency under 10,000 active bans
 * - Integrated pre-flight security evaluation (Ban Check + Rate Limiter)
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(4)
public class ProxySecurityBenchmark {

    private FixedWindowRateLimiter rateLimiter;
    private ProxyBanCache banCache;
    private ext.mods.security.fail2ban.core.BanManager banManager;
    private ext.mods.security.fail2ban.core.PanicMode panicMode;
    private java.util.concurrent.atomic.AtomicInteger eventSink;
    private String[] testIps;
    private String[] bannedIps;
    private Random random;

    @Setup(Level.Trial)
    public void setup() {
        rateLimiter = new FixedWindowRateLimiter();
        banCache = ProxyBanCache.getInstance();
        banManager = new ext.mods.security.fail2ban.core.BanManager(new ext.mods.security.fail2ban.core.Fail2BanConfig(), null);
        eventSink = new java.util.concurrent.atomic.AtomicInteger(0);
        banManager.addListener(event -> eventSink.incrementAndGet());
        panicMode = ext.mods.security.fail2ban.core.PanicMode.getInstance();
        random = new Random(42);

        // Pre-populate 10,000 banned IPs
        bannedIps = new String[10000];
        for (int i = 0; i < bannedIps.length; i++) {
            bannedIps[i] = "10.200." + (i / 250) + "." + (i % 250);
            banCache.addBan(bannedIps[i], System.currentTimeMillis() + 3600000);
        }

        // Test clean IPs (legitimate players)
        testIps = new String[1000];
        for (int i = 0; i < testIps.length; i++) {
            testIps[i] = "192.168." + (i / 250) + "." + (i % 250);
        }
    }

    @Benchmark
    public RateLimitDecision benchmarkRateLimiterAllowed() {
        int idx = random.nextInt(testIps.length);
        return rateLimiter.tryAcquire("l2-login:connect:" + testIps[idx], 100000, 60);
    }

    @Benchmark
    public boolean benchmarkBanCacheNegativeLookup() {
        int idx = random.nextInt(testIps.length);
        return banCache.isBanned(testIps[idx]);
    }

    @Benchmark
    public boolean benchmarkBanCachePositiveHit() {
        int idx = random.nextInt(bannedIps.length);
        return banCache.isBanned(bannedIps[idx]);
    }

    @Benchmark
    public boolean benchmarkIntegratedPreFlightEdgeCheck() {
        int idx = random.nextInt(testIps.length);
        String ip = testIps[idx];
        if (banCache.isBanned(ip)) {
            return false;
        }
        RateLimitDecision dec = rateLimiter.tryAcquire("l2-game:connect:" + ip, 60, 60);
        return dec.allowed();
    }

    @Benchmark
    public boolean benchmarkFail2BanRecordFailure() {
        int idx = random.nextInt(testIps.length);
        return banManager.recordFailure(testIps[idx], "dos_flood", "Rate limit breach");
    }

    @Benchmark
    public boolean benchmarkFail2BanPanicModeConnectionCheck() {
        int idx = random.nextInt(testIps.length);
        return panicMode.allowConnection(testIps[idx]);
    }
}
