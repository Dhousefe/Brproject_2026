package ext.mods.benchmark.ai;

import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Microbenchmark JMH para avaliacao de desempenho da IA e Movimentacao de Monstros:
 * 1. Legado Melee Attack Check (Varredura O(N) de bloqueadores com margem inflada 125 e expansao de colisao)
 * 2. Otimizado Melee Attack Check (Alcance canonico direto O(1) com compensacao de alvo movel)
 * 3. Legado Slot Calculation (Varredura estatica de 12 angulos com gap excessivo de 60 unidades)
 * 4. Otimizado Dynamic Formation Ring Slot (Atribuicao radial hierarquica com prioridade de angulo e aneis de cerco)
 * 5. Geodata Z Sampling com Headroom e Clamp de Estabilidade
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(4)
@State(Scope.Benchmark)
public class MonsterAiMovementBenchmark
{
    private static final int WEAPON_RANGE = 40;
    private static final double ACTOR_COL = 16.0;
    private static final double TARGET_COL = 16.0;
    private static final int CELL_HEIGHT = 8;

    // Entidades simuladas para os testes
    public static final class MockCreature
    {
        public final int x;
        public final int y;
        public final int z;
        public final double col;
        public final boolean isMoving;

        public MockCreature(int x, int y, int z, double col, boolean isMoving)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.col = col;
            this.isMoving = isMoving;
        }

        public double distance2D(double ox, double oy)
        {
            double dx = this.x - ox;
            double dy = this.y - oy;
            return Math.sqrt(dx * dx + dy * dy);
        }

        public double distance3D(MockCreature other)
        {
            double dx = this.x - other.x;
            double dy = this.y - other.y;
            double dz = this.z - other.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    private MockCreature actor;
    private MockCreature target;
    private List<MockCreature> nearbyBlockers;

    @Setup(Level.Trial)
    public void setup()
    {
        // Alvo a 65 unidades de distancia (alcance legitimo de melee)
        target = new MockCreature(1000, 1000, -3500, TARGET_COL, true);
        actor = new MockCreature(1055, 1035, -3500, ACTOR_COL, false);

        // 8 monstros ao redor simulando densidade de combate em Catacumba / Raid
        nearbyBlockers = new ArrayList<>(8);
        for (int i = 0; i < 8; i++)
        {
            double angle = (2 * Math.PI * i) / 8;
            int bx = (int) (target.x + 60 * Math.cos(angle));
            int by = (int) (target.y + 60 * Math.sin(angle));
            nearbyBlockers.add(new MockCreature(bx, by, target.z, ACTOR_COL, false));
        }
    }

    // =========================================================================
    // 1. MELEE ATTACK CHECK (Throughput & Reaction Time)
    // =========================================================================

    @Benchmark
    public boolean benchmarkLegacyMeleeAttackCheck()
    {
        int totalAttackRange = (int) (WEAPON_RANGE + ACTOR_COL + TARGET_COL);
        double attackMargin = 125.0; // ConfigNpcs.MONSTER_MAX_RANGE legado
        double maxAttackRange = totalAttackRange + attackMargin;

        double dist = actor.distance3D(target);
        if (dist <= maxAttackRange)
            return true;

        // Loop legado de busca de bloqueadores
        for (MockCreature blocker : nearbyBlockers)
        {
            double distToBlocker = actor.distance3D(blocker);
            double blockerToTarget = blocker.distance3D(target);

            if (Math.abs((distToBlocker + blockerToTarget) - dist) < 35.0)
            {
                double adjustedRange = maxAttackRange + (blocker.col * 2.0);
                if (dist <= adjustedRange)
                    return true;
            }
        }
        return false;
    }

    @Benchmark
    public boolean benchmarkOptimizedMeleeAttackCheck()
    {
        int totalAttackRange = (int) (WEAPON_RANGE + ACTOR_COL + TARGET_COL);
        int movingTolerance = target.isMoving ? 50 : 15;
        double maxAttackRange = totalAttackRange + movingTolerance;

        double dist = actor.distance3D(target);
        return dist <= maxAttackRange;
    }

    // =========================================================================
    // 2. FORMATION SLOT ASSIGNMENT (Encirclement Ring)
    // =========================================================================

    @Benchmark
    public double[] benchmarkLegacySlotCalculation()
    {
        int angles = 12;
        double angleStep = 360.0 / angles;
        double startAngle = Math.toDegrees(Math.atan2(actor.y - target.y, actor.x - target.x));
        double collisionGap = 60.0;

        double[] bestLoc = null;
        double minScore = Double.MAX_VALUE;

        for (int i = 0; i < angles; i++)
        {
            double angleRadians = Math.toRadians(startAngle + (i * angleStep));
            double currentRange = WEAPON_RANGE + ((actor.x % 4) * 5);

            double testX = target.x + currentRange * Math.cos(angleRadians);
            double testY = target.y + currentRange * Math.sin(angleRadians);
            double testZ = target.z; // Sem headroom legado

            boolean isOccupied = false;
            for (MockCreature blocker : nearbyBlockers)
            {
                if (blocker.distance2D(testX, testY) < (blocker.col + actor.col + collisionGap))
                {
                    isOccupied = true;
                    break;
                }
            }

            if (!isOccupied)
            {
                double score = actor.distance2D(testX, testY);
                if (score < minScore)
                {
                    minScore = score;
                    bestLoc = new double[]{testX, testY, testZ};
                }
            }
        }
        return bestLoc;
    }

    @Benchmark
    public double[] benchmarkOptimizedDynamicFormationSlot()
    {
        double ring1Radius = Math.max(ACTOR_COL + TARGET_COL, WEAPON_RANGE + ACTOR_COL + TARGET_COL - 10.0);
        double ring2Radius = ring1Radius + ACTOR_COL * 2.0 + 15.0;

        double baseAngle = Math.toDegrees(Math.atan2(actor.y - target.y, actor.x - target.x));
        double[] angleOffsets = {0.0, 45.0, -45.0, 90.0, -90.0, 135.0, -135.0, 180.0};
        double[] rings = {ring1Radius, ring2Radius};

        for (double radius : rings)
        {
            for (double angleOffset : angleOffsets)
            {
                double angleRad = Math.toRadians(baseAngle + angleOffset);
                double testX = target.x + radius * Math.cos(angleRad);
                double testY = target.y + radius * Math.sin(angleRad);
                
                // Headroom sampling
                double testZ = target.z + 2 * CELL_HEIGHT;

                boolean isOccupied = false;
                for (MockCreature blocker : nearbyBlockers)
                {
                    if (blocker.distance2D(testX, testY) < (blocker.col + ACTOR_COL - 5.0))
                    {
                        isOccupied = true;
                        break;
                    }
                }

                if (!isOccupied)
                {
                    return new double[]{testX, testY, testZ};
                }
            }
        }
        return null;
    }

    // =========================================================================
    // 3. TERRAIN Z SAMPLING (Stability & Headroom Check)
    // =========================================================================

    @Benchmark
    public int benchmarkTerrainZSamplingWithHeadroom()
    {
        int curZ = actor.z;
        int nextX = actor.x + 10;
        int nextY = actor.y + 10;

        // Simulacao de altura de rampa/escada
        int sampledZ = curZ + 4; // Subindo escada
        int withHeadroom = (sampledZ <= curZ + 2 * CELL_HEIGHT) ? sampledZ : curZ;

        if (Math.abs(withHeadroom - curZ) > 120)
            return curZ;

        return withHeadroom;
    }
}
