package ext.mods.benchmark.ai;

import org.openjdk.jmh.annotations.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Microbenchmark JMH para avaliacao de desempenho dos filtros de agressividade de monstros:
 * 1. benchmarkLegacyAggroScan vs benchmarkOptimizedAggroScan (Varredura de 150 jogadores sob alta densidade)
 * 2. benchmarkLegacyGetMostHated vs benchmarkOptimizedGetMostHated (Throughput & Zero-Allocation de odio/aggro)
 * 3. benchmarkLegacyPlayerNearNpc vs benchmarkOptimizedPlayerNearNpc (Varredura com ArrayList vs Short-Circuit)
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(4)
@State(Scope.Benchmark)
public class MonsterAggressionBenchmark
{
    // Simulacao de Entidades para o Benchmark
    public static final class MockPlayable
    {
        public final int id;
        public final int x;
        public final int y;
        public final int z;
        public final boolean isPeace;
        public final boolean isSpawnProtected;
        public final boolean isFlying;
        public final boolean isVisible;

        public MockPlayable(int id, int x, int y, int z, boolean isPeace, boolean isSpawnProtected, boolean isFlying, boolean isVisible)
        {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.isPeace = isPeace;
            this.isSpawnProtected = isSpawnProtected;
            this.isFlying = isFlying;
            this.isVisible = isVisible;
        }

        public double distance3D(int ox, int oy, int oz)
        {
            double dx = this.x - ox;
            double dy = this.y - oy;
            double dz = this.z - oz;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    public static final class MockAggroInfo
    {
        public final int attackerId;
        public double hate;
        public double damage;

        public MockAggroInfo(int attackerId, double hate, double damage)
        {
            this.attackerId = attackerId;
            this.hate = hate;
            this.damage = damage;
        }

        public double getHate()
        {
            return hate;
        }
    }

    private static final int SEE_RANGE = 450;
    private static final int SEE_RANGE_SQ = SEE_RANGE * SEE_RANGE;
    private static final int ACTOR_X = 20000;
    private static final int ACTOR_Y = 20000;
    private static final int ACTOR_Z = -3500;

    private List<MockPlayable> surroundingPlayers;
    private Set<Integer> legacySeenSet;
    private Set<Integer> optimizedSeenSet;

    private Map<Integer, MockAggroInfo> aggroMap;

    @Setup(Level.Trial)
    public void setup()
    {
        // 150 jogadores simulando alta densidade (Catacumbas / Raids / Sieges)
        surroundingPlayers = new ArrayList<>(150);
        final java.util.Random rnd = new java.util.Random(42);

        for (int i = 0; i < 150; i++)
        {
            // 15% proximos dentro de 450 unidades, 85% espalhados ate 3.500 unidades nas 9 regioes
            int dist = (i < 22) ? rnd.nextInt(SEE_RANGE) : (SEE_RANGE + rnd.nextInt(3000));
            double angle = rnd.nextDouble() * 2 * Math.PI;

            int px = (int) (ACTOR_X + dist * Math.cos(angle));
            int py = (int) (ACTOR_Y + dist * Math.sin(angle));
            int pz = ACTOR_Z + rnd.nextInt(300) - 150;

            boolean isPeace = (i % 20 == 0);
            boolean isProtected = (i % 30 == 0);
            boolean isFlying = false;
            boolean isVisible = (i % 25 != 0);

            surroundingPlayers.add(new MockPlayable(i + 1, px, py, pz, isPeace, isProtected, isFlying, isVisible));
        }

        legacySeenSet = ConcurrentHashMap.newKeySet();
        optimizedSeenSet = ConcurrentHashMap.newKeySet();

        // 20 agressores atacando simultaneamente um Raid Boss / Elite Pack
        aggroMap = new ConcurrentHashMap<>();
        for (int i = 0; i < 20; i++)
        {
            aggroMap.put(i + 1, new MockAggroInfo(i + 1, rnd.nextDouble() * 5000 + 10, rnd.nextDouble() * 20000));
        }
    }

    // =========================================================================
    // 1. PERCEPTION LOOP: FOR-EACH AGGRO SCAN (High Density 150 Players)
    // =========================================================================

    @Benchmark
    public int benchmarkLegacyAggroScan()
    {
        int triggered = 0;

        for (MockPlayable pl : surroundingPlayers)
        {
            // Verificacoes pesadas incondicionais antes de saber se esta no alcance
            if (pl.isPeace)
                continue;

            if (pl.isSpawnProtected || pl.isFlying || !pl.isVisible)
                continue;

            // Math.sqrt floating point em cada um dos 150 jogadores
            boolean isInRange = pl.distance3D(ACTOR_X, ACTOR_Y, ACTOR_Z) <= SEE_RANGE;

            if (legacySeenSet.contains(pl.id))
            {
                if (!isInRange)
                    legacySeenSet.remove(pl.id);
            }
            else if (isInRange)
            {
                legacySeenSet.add(pl.id);
                triggered++;
            }
        }
        return triggered;
    }

    @Benchmark
    public int benchmarkOptimizedAggroScan()
    {
        int triggered = 0;

        for (MockPlayable pl : surroundingPlayers)
        {
            // UC2: Fast AABB Reject (< 1 ns)
            final int dx = pl.x - ACTOR_X;
            final int dy = pl.y - ACTOR_Y;
            final int dz = pl.z - ACTOR_Z;

            if (Math.abs(dx) > SEE_RANGE || Math.abs(dy) > SEE_RANGE || Math.abs(dz) > 500)
            {
                if (!optimizedSeenSet.isEmpty())
                    optimizedSeenSet.remove(pl.id);
                continue;
            }

            // Distancia Euclidiana Quadratica Sem Raiz
            final boolean isInRange = (dx * dx + dy * dy + dz * dz) <= SEE_RANGE_SQ;

            if (!isInRange)
            {
                if (!optimizedSeenSet.isEmpty())
                    optimizedSeenSet.remove(pl.id);
                continue;
            }

            // Apenas entidades no raio legitimo realizam checagens de status e zona
            if (pl.isPeace)
                continue;

            if (pl.isSpawnProtected || pl.isFlying || !pl.isVisible)
                continue;

            if (!optimizedSeenSet.contains(pl.id))
            {
                optimizedSeenSet.add(pl.id);
                triggered++;
            }
        }
        return triggered;
    }

    @Benchmark
    public int benchmarkOptimizedAggroScanEngagedBypass()
    {
        // UC1: Monstro ja engajado em combate ativo contra alvo valido
        boolean isEngagedInAttack = true;
        if (isEngagedInAttack)
        {
            return 0; // Bypass imediato zero CPU
        }
        return benchmarkOptimizedAggroScan();
    }

    // =========================================================================
    // 2. AGGRO LIST RETRIEVAL (getMostHated)
    // =========================================================================

    @Benchmark
    public MockAggroInfo benchmarkLegacyGetMostHated()
    {
        // Java Streams com alocacao de Stream, lambda e Comparator
        return aggroMap.values().stream()
            .filter(ai -> ai.getHate() > 0)
            .max(Comparator.comparing(MockAggroInfo::getHate))
            .orElse(null);
    }

    @Benchmark
    public MockAggroInfo benchmarkOptimizedGetMostHated()
    {
        // UC3: Loop procedural direto com zero alocacoes na heap
        MockAggroInfo mostHated = null;
        double maxHate = 0;
        for (MockAggroInfo ai : aggroMap.values())
        {
            final double hate = ai.hate;
            if (hate > maxHate)
            {
                maxHate = hate;
                mostHated = ai;
            }
        }
        return mostHated;
    }

    // =========================================================================
    // 3. AI TASK MANAGER: PLAYER NEAR NPC CHECK (2000 units range)
    // =========================================================================

    @Benchmark
    public boolean benchmarkLegacyPlayerNearNpc()
    {
        // Aloca new ArrayList, calcula Math.sqrt em todos os 150 players, preenche e itera
        final List<MockPlayable> inRadius = new ArrayList<>();
        for (MockPlayable pl : surroundingPlayers)
        {
            if (pl.distance3D(ACTOR_X, ACTOR_Y, ACTOR_Z) <= 2000)
            {
                inRadius.add(pl);
            }
        }

        for (MockPlayable pl : inRadius)
        {
            if (pl.isVisible)
                return true;
        }
        return false;
    }

    @Benchmark
    public boolean benchmarkOptimizedPlayerNearNpc()
    {
        // UC4: Short-Circuit zero-allocation: encerra imediatamente no primeiro jogador visivel
        final int rangeSq = 2000 * 2000;
        for (MockPlayable pl : surroundingPlayers)
        {
            final int dx = pl.x - ACTOR_X;
            final int dy = pl.y - ACTOR_Y;
            final int dz = pl.z - ACTOR_Z;

            if (Math.abs(dx) <= 2000 && Math.abs(dy) <= 2000 && Math.abs(dz) <= 2000)
            {
                if ((dx * dx + dy * dy + dz * dz) <= rangeSq)
                {
                    if (pl.isVisible)
                        return true; // Aborta imediatamente sem alocacoes
                }
            }
        }
        return false;
    }
}
