package ext.mods.benchmark.gameapi;

import ext.mods.gameapi.clan.ClanChatRing;
import ext.mods.gameapi.security.RateLimiter;
import org.openjdk.jmh.annotations.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Microbenchmark JMH para avaliacao de desempenho dos componentes criticos da Game API:
 * 1. HMAC Verification (Legacy com String.format vs Optimized com HexFormat e ThreadLocal)
 * 2. Nonce Cache (Legacy com purgeExpired() O(N) por request vs Amortized/Threshold)
 * 3. Rate Limiter (Legacy com limiter.gc() O(N) por request vs Background/Amortized)
 * 4. ClanChat Ring Buffer (Legacy toList().takeLast() vs Direct slice)
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(8)
@State(Scope.Benchmark)
public class GameApiBenchmark
{
    private static final String ALGORITHM = "HmacSHA256";
    private static final byte[] SECRET = "brproject-secure-shared-secret-key-for-api-32bytes".getBytes(StandardCharsets.UTF_8);
    private static final SecretKeySpec PRECOMPUTED_KEY = new SecretKeySpec(SECRET, ALGORITHM);
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private static final ThreadLocal<Mac> MAC_THREAD_LOCAL = ThreadLocal.withInitial(() -> {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(PRECOMPUTED_KEY);
            return mac;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    });

    private static final ThreadLocal<MessageDigest> SHA256_THREAD_LOCAL = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    });

    private String method;
    private String path;
    private long timestamp;
    private String nonce;
    private byte[] bodyBytes;
    private String validSignature;

    // Nonce test states
    private final ConcurrentHashMap<String, Long> legacyNonceMap = new ConcurrentHashMap<>(256);
    private final ConcurrentHashMap<String, Long> optimizedNonceMap = new ConcurrentHashMap<>(256);
    private final AtomicLong nonceCounter = new AtomicLong();

    // Rate limiter states
    private RateLimiter legacyLimiter;
    private RateLimiter optimizedLimiter;
    private final String[] testIps = new String[64];

    // Clan chat ring states
    private final ArrayDeque<ClanChatRing.Entry> testRing = new ArrayDeque<>(300);

    @Setup(Level.Trial)
    public void setup() throws Exception
    {
        method = "POST";
        path = "/internal/site/account/characters";
        timestamp = System.currentTimeMillis();
        nonce = UUID.randomUUID().toString();
        bodyBytes = "{\"login\":\"player_test_user_01\"}".getBytes(StandardCharsets.UTF_8);

        // Precompute valid signature
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        String bodyHash = HEX_FORMAT.formatHex(md.digest(bodyBytes));
        String payload = method + "|" + path + "|" + timestamp + "|" + nonce + "|" + bodyHash;
        Mac mac = Mac.getInstance(ALGORITHM);
        mac.init(PRECOMPUTED_KEY);
        validSignature = HEX_FORMAT.formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));

        // Pre-populate nonces
        for (int i = 0; i < 200; i++) {
            legacyNonceMap.put("old-nonce-" + i, timestamp - 1000 - i);
            optimizedNonceMap.put("old-nonce-" + i, timestamp - 1000 - i);
        }

        // Rate limiter setup
        legacyLimiter = new RateLimiter(500_000);
        optimizedLimiter = new RateLimiter(500_000);
        for (int i = 0; i < testIps.length; i++) {
            testIps[i] = "192.168.1." + i;
        }

        // Clan chat ring setup
        for (int i = 0; i < 300; i++) {
            testRing.addLast(new ClanChatRing.Entry(
                i,
                System.currentTimeMillis() - (300 - i) * 1000L,
                1,
                1000 + i,
                "Player" + i,
                "Message test content number " + i,
                "MEMBER",
                "CLAN"
            ));
        }
    }

    // =========================================================================
    // 1. HMAC Verification Benchmark
    // =========================================================================

    @Benchmark
    public boolean benchmarkLegacyHmacVerify() throws Exception
    {
        // Replicates current HmacVerifier.verify() logic
        long now = System.currentTimeMillis();
        if (Math.abs(now - timestamp) > 300_000L) return false;

        // Legacy sha256Hex with String.format
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bodyBytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        String bodyHash = sb.toString();

        String payload = method.toUpperCase() + "|" + path + "|" + timestamp + "|" + nonce + "|" + bodyHash;

        Mac mac = Mac.getInstance(ALGORITHM);
        mac.init(new SecretKeySpec(SECRET, ALGORITHM));
        byte[] macBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

        StringBuilder sbMac = new StringBuilder();
        for (byte b : macBytes) {
            sbMac.append(String.format("%02x", b));
        }
        String expected = sbMac.toString();

        return MessageDigest.isEqual(expected.getBytes(), validSignature.toLowerCase().getBytes());
    }

    @Benchmark
    public boolean benchmarkOptimizedHmacVerify()
    {
        long now = System.currentTimeMillis();
        if (Math.abs(now - timestamp) > 300_000L) return false;

        // Optimized: ThreadLocal MessageDigest + Java 25 HexFormat
        MessageDigest digest = SHA256_THREAD_LOCAL.get();
        digest.reset();
        String bodyHash = HEX_FORMAT.formatHex(digest.digest(bodyBytes));

        String payload = method.toUpperCase() + "|" + path + "|" + timestamp + "|" + nonce + "|" + bodyHash;

        Mac mac = MAC_THREAD_LOCAL.get();
        mac.reset();
        byte[] macBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        String expected = HEX_FORMAT.formatHex(macBytes);

        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), validSignature.getBytes(StandardCharsets.US_ASCII));
    }

    // =========================================================================
    // 2. Nonce Cache Benchmark
    // =========================================================================

    @Benchmark
    public boolean benchmarkLegacyNonceCache()
    {
        // Legacy: iterates entire map on every single consume call
        long cutoff = System.currentTimeMillis() - 300_000L;
        var iter = legacyNonceMap.entrySet().iterator();
        while (iter.hasNext()) {
            if (iter.next().getValue() < cutoff) {
                iter.remove();
            }
        }
        String n = "nonce-" + (nonceCounter.incrementAndGet() & 0x3FF);
        return legacyNonceMap.putIfAbsent(n, System.currentTimeMillis()) == null;
    }

    @Benchmark
    public boolean benchmarkOptimizedNonceCache()
    {
        // Optimized: amortized purge (only once every 1024 inserts)
        long count = nonceCounter.incrementAndGet();
        if ((count & 0x3FF) == 0) {
            long cutoff = System.currentTimeMillis() - 300_000L;
            optimizedNonceMap.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        }
        String n = "nonce-" + (count & 0x3FF);
        return optimizedNonceMap.putIfAbsent(n, System.currentTimeMillis()) == null;
    }

    // =========================================================================
    // 3. Rate Limiter Benchmark
    // =========================================================================

    @Benchmark
    public boolean benchmarkLegacyRateLimiter()
    {
        // Legacy: calls limiter.gc() in finally block on EVERY request
        String ip = testIps[(int) (System.nanoTime() & (testIps.length - 1))];
        boolean acquired = legacyLimiter.tryAcquire(ip);
        legacyLimiter.gc();
        return acquired;
    }

    @Benchmark
    public boolean benchmarkOptimizedRateLimiter()
    {
        // Optimized: tryAcquire only; gc is executed by periodic daemon thread
        String ip = testIps[(int) (System.nanoTime() & (testIps.length - 1))];
        return optimizedLimiter.tryAcquire(ip);
    }

    // =========================================================================
    // 4. ClanChatRing Buffer Fetch Benchmark
    // =========================================================================

    @Benchmark
    public List<ClanChatRing.Entry> benchmarkLegacyClanChatRingFetch()
    {
        // Legacy: ring.toList().takeLast(50)
        synchronized (testRing) {
            return testRing.stream().toList().subList(Math.max(0, testRing.size() - 50), testRing.size());
        }
    }

    @Benchmark
    public List<ClanChatRing.Entry> benchmarkOptimizedClanChatRingFetch()
    {
        // Optimized: pre-sized ArrayList reading directly the last 50 elements
        synchronized (testRing) {
            int count = Math.min(50, testRing.size());
            List<ClanChatRing.Entry> result = new ArrayList<>(count);
            int skip = testRing.size() - count;
            int idx = 0;
            for (ClanChatRing.Entry entry : testRing) {
                if (idx++ >= skip) {
                    result.add(entry);
                }
            }
            return result;
        }
    }
}
