package ext.mods.benchmark.combat;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Microbenchmark JMH para avaliacao de desempenho do tempo de reacao, recuperacao
 * de ataque e balanceamento por DEX entre classes (Mago vs Guerreiro vs Dagger).
 * 
 * Modos avaliados:
 * 1. benchmarkDexFactorCalculation: Eficiencia matematica da formula de DEX (zero-alloc)
 * 2. benchmarkCombatReactionCycles: Tempo efetivo de travamento de acao por classe
 * 3. benchmarkTargetSwitchingThroughput: Vazao de troca de alvo e movimentacao responsiva
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(4)
@State(Scope.Benchmark)
public class PlayerReactionBenchmark
{
    // Simuladores de Entidades Leves para Benchmark
    public static final class MockPlayerStatus
    {
        public final int dex;
        public final int pAtkSpd;

        public MockPlayerStatus(int dex, int pAtkSpd)
        {
            this.dex = dex;
            this.pAtkSpd = pAtkSpd;
        }

        public int getDEX()
        {
            return dex;
        }

        public int getPAtkSpd()
        {
            return pAtkSpd;
        }
    }

    public static final class MockPlayer
    {
        public final String className;
        public final MockPlayerStatus status;
        public boolean isAttackingNow;
        public int pendingIntention; // 0=None, 1=Move, 2=AttackNewTarget

        public MockPlayer(String className, int dex, int pAtkSpd)
        {
            this.className = className;
            this.status = new MockPlayerStatus(dex, pAtkSpd);
            this.isAttackingNow = false;
            this.pendingIntention = 0;
        }
    }

    // Perfis de Classes
    private MockPlayer magePlayer;
    private MockPlayer tankPlayer;
    private MockPlayer fighterPlayer;
    private MockPlayer daggerPlayer;
    private MockPlayer daggerMaxPlayer;

    // Constantes do Sistema
    private static final int BASE_DEX = 20;
    private static final double DEX_DIVISOR = 45.0;
    private static final double MAX_BONUS = 0.45;
    private static final double MIN_FACTOR = 0.50;
    private static final double MAX_FACTOR = 1.15;

    @Setup(Level.Trial)
    public void setup()
    {
        // Mago: Baixo DEX, P.Atk.Spd baixa (ex: Mystic / Necromancer com dyes -4 DEX)
        magePlayer = new MockPlayer("Mage", 18, 300);

        // Tanque: DEX moderado, infantaria pesada (ex: Paladin / Dark Avenger)
        tankPlayer = new MockPlayer("Tank", 28, 480);

        // Guerreiro: DEX equilibrado (ex: Gladiator / Destroyer)
        fighterPlayer = new MockPlayer("Fighter", 32, 550);

        // Dagger: DEX alto, vocacao de agilidade (ex: Treasure Hunter / Abyss Walker)
        daggerPlayer = new MockPlayer("Dagger", 43, 750);

        // Dagger Max: DEX extremo com Dyes +5, buffs de Wind Walk / Haste / Agility (Plains Walker)
        daggerMaxPlayer = new MockPlayer("DaggerMax", 52, 950);
    }

    /**
     * Calculo da formula de DEX (zero alocacao, puramente aritmético em registradores).
     */
    public static double calcDexFactor(int dex)
    {
        final double scaled = 1.0 - (((double) (dex - BASE_DEX) / DEX_DIVISOR) * MAX_BONUS);
        return Math.clamp(scaled, MIN_FACTOR, MAX_FACTOR);
    }

    /**
     * Tempo base entre ataques (Formulas.calculateTimeBetweenAttacks).
     */
    public static int calculateTimeBetweenAttacks(int pAtkSpd)
    {
        return Math.max(100, 350000 / pAtkSpd);
    }

    // =========================================================================
    // 1. Throughput do Calculo da Formula de DEX (Mechanical Sympathy)
    // =========================================================================

    @Benchmark
    public double benchmarkDexFactorCalculationMage()
    {
        return calcDexFactor(magePlayer.status.dex);
    }

    @Benchmark
    public double benchmarkDexFactorCalculationDagger()
    {
        return calcDexFactor(daggerPlayer.status.dex);
    }

    // =========================================================================
    // 2. Simulacao do Ciclo de Travamento de Animacao (Legacy vs Canônico vs Otimizado)
    // =========================================================================

    /**
     * Modelo Legado: timeAtk completo para o hit + timeAtk completo no delay final (2x timeAtk).
     * Sem consideracao do atributo DEX.
     */
    @Benchmark
    public int benchmarkLegacyAttackCycle()
    {
        final int timeAtk = calculateTimeBetweenAttacks(daggerPlayer.status.pAtkSpd);
        final int safeAtkTime = Math.max(200, timeAtk);
        final int afterAttackDelay = safeAtkTime;
        final int totalLockDuration = safeAtkTime + Math.max(150, afterAttackDelay);
        return totalLockDuration;
    }

    /**
     * Modelo Canônico aCis 409: timeAtk / 2 para o hit + timeAtk / 2 no delay final.
     * Sem modulacao por DEX.
     */
    @Benchmark
    public int benchmarkCanonicalAttackCycle()
    {
        final int timeAtk = calculateTimeBetweenAttacks(daggerPlayer.status.pAtkSpd);
        final int safeAtkTime = Math.max(100, timeAtk / 2);
        final int afterAttackDelay = safeAtkTime;
        final int totalLockDuration = safeAtkTime + Math.max(100, afterAttackDelay);
        return totalLockDuration;
    }

    /**
     * Modelo Otimizado com Escalonamento DEX: Mago (DEX 18).
     * Recuperacao deliberadamente penalizada / lenta.
     */
    @Benchmark
    public int benchmarkDexScaledMageCycle()
    {
        final int timeAtk = calculateTimeBetweenAttacks(magePlayer.status.pAtkSpd);
        final int safeAtkTime = Math.max(100, timeAtk / 2);
        final double factor = calcDexFactor(magePlayer.status.dex);
        final int recoveryDelay = Math.max(100, (int) (safeAtkTime * factor));
        return safeAtkTime + recoveryDelay;
    }

    /**
     * Modelo Otimizado com Escalonamento DEX: Tanque (DEX 28).
     */
    @Benchmark
    public int benchmarkDexScaledTankCycle()
    {
        final int timeAtk = calculateTimeBetweenAttacks(tankPlayer.status.pAtkSpd);
        final int safeAtkTime = Math.max(100, timeAtk / 2);
        final double factor = calcDexFactor(tankPlayer.status.dex);
        final int recoveryDelay = Math.max(100, (int) (safeAtkTime * factor));
        return safeAtkTime + recoveryDelay;
    }

    /**
     * Modelo Otimizado com Escalonamento DEX: Dagger (DEX 43).
     * Recuperacao rapida (-23% de delay pós-hit).
     */
    @Benchmark
    public int benchmarkDexScaledDaggerCycle()
    {
        final int timeAtk = calculateTimeBetweenAttacks(daggerPlayer.status.pAtkSpd);
        final int safeAtkTime = Math.max(100, timeAtk / 2);
        final double factor = calcDexFactor(daggerPlayer.status.dex);
        final int recoveryDelay = Math.max(100, (int) (safeAtkTime * factor));
        return safeAtkTime + recoveryDelay;
    }

    /**
     * Modelo Otimizado com Escalonamento DEX: Dagger Max (DEX 52).
     * Recuperacao maxima (-32% de delay pós-hit, fluidez total).
     */
    @Benchmark
    public int benchmarkDexScaledDaggerMaxCycle()
    {
        final int timeAtk = calculateTimeBetweenAttacks(daggerMaxPlayer.status.pAtkSpd);
        final int safeAtkTime = Math.max(100, timeAtk / 2);
        final double factor = calcDexFactor(daggerMaxPlayer.status.dex);
        final int recoveryDelay = Math.max(100, (int) (safeAtkTime * factor));
        return safeAtkTime + recoveryDelay;
    }

    // =========================================================================
    // 3. Throughput de Tentativa de Troca de Alvo / Movimentacao (PlayableAI)
    // =========================================================================

    /**
     * Simula o jogador tentando trocar de alvo ou andar repetidamente durante
     * a janela de ataque (PlayableAI tryToAttack / tryToMoveTo).
     */
    @Benchmark
    public boolean benchmarkTargetSwitchDispatchDagger()
    {
        final int timeAtk = calculateTimeBetweenAttacks(daggerPlayer.status.pAtkSpd);
        final int safeAtkTime = Math.max(100, timeAtk / 2);
        final double factor = calcDexFactor(daggerPlayer.status.dex);
        final int recoveryDelay = Math.max(100, (int) (safeAtkTime * factor));

        boolean canActImmediately = (recoveryDelay <= 180);
        return canActImmediately;
    }

    // =========================================================================
    // 4. Concorrencia Multi-Thread: Atomic Swap vs Synchronized Monitor Lock
    // =========================================================================

    public static final class MockIntentionHolder
    {
        public int type;
        public int targetId;
        public int x;
        public int y;
        public int z;

        public MockIntentionHolder(int type, int targetId, int x, int y, int z)
        {
            this.type = type;
            this.targetId = targetId;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private final Object legacyLock = new Object();
    private MockIntentionHolder legacyNextIntention = new MockIntentionHolder(0, 0, 0, 0, 0);
    private final java.util.concurrent.atomic.AtomicReference<MockIntentionHolder> atomicNextIntention = new java.util.concurrent.atomic.AtomicReference<>(null);

    /**
     * Modelo Legado: Contencao de lock sincronizado com mutacao in-place de campos.
     */
    @Benchmark
    public MockIntentionHolder benchmarkLegacySynchronizedIntentionSwap()
    {
        synchronized (legacyLock)
        {
            // Thread A enfileira
            legacyNextIntention.type = 1;
            legacyNextIntention.targetId = 12345;
            legacyNextIntention.x = 83400;
            legacyNextIntention.y = 148200;
            legacyNextIntention.z = -3400;

            // Thread B consome
            final MockIntentionHolder consumed = new MockIntentionHolder(
                legacyNextIntention.type,
                legacyNextIntention.targetId,
                legacyNextIntention.x,
                legacyNextIntention.y,
                legacyNextIntention.z
            );
            legacyNextIntention.type = 0;
            return consumed;
        }
    }

    /**
     * Modelo Otimizado: Swap atomico lock-free via AtomicReference (Zero Contention).
     */
    @Benchmark
    public MockIntentionHolder benchmarkAtomicIntentionSwap()
    {
        // Thread A enfileira com atomic set
        final MockIntentionHolder next = new MockIntentionHolder(1, 12345, 83400, 148200, -3400);
        atomicNextIntention.set(next);

        // Thread B consome com atomic getAndSet(null)
        final MockIntentionHolder consumed = atomicNextIntention.getAndSet(null);
        return consumed != null ? consumed : next;
    }
}
