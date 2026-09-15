/*
 * Copyleft © 2024-2026 L2Brproject
 */
package ext.mods.security.fail2ban.simd;

import ext.mods.security.fail2ban.core.BanManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Clean Room (Area Limpa) & Golden Profile Manager.
 * Collects legitimate packet patterns from designated verified IPs,
 * calculates statistical centroids (mean & std), and evaluates traffic in real-time
 * using AVX2 SIMD vector comparisons.
 */
public final class CleanRoomManager {
    private static final Logger LOGGER = Logger.getLogger(CleanRoomManager.class.getName());

    public enum ProfileState {
        NOT_TRAINED("Nao Treinado"),
        COLLECTING("Coletando Amostras"),
        CALIBRATED("Calibrado (Ativo)"),
        FROZEN("Congelado (Protegido)");

        private final String label;
        ProfileState(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public record CleanIpEntry(String ip, long addedAt, String notes, AtomicLong packetCount) {}

    public record AnomalyDecision(boolean isAnomaly, float zScore, float cosineDist, String reason) {}

    public record CapturedPacketSample(
        long id,
        long timestamp,
        String ip,
        String proto,
        String summary,
        PacketVector128 vector,
        AtomicBoolean selectedForModel
    ) {
        public String formattedTime() {
            return new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date(timestamp));
        }
    }

    private static final CleanRoomManager INSTANCE = new CleanRoomManager();

    private final ConcurrentHashMap<String, CleanIpEntry> cleanIps = new ConcurrentHashMap<>();
    private final AtomicLong totalSamplesCollected = new AtomicLong(0);
    private final AtomicLong totalAnomaliesDetected = new AtomicLong(0);
    private final AtomicBoolean frozen = new AtomicBoolean(false);

    // Dynamic calibration margin (default 100, user-adjustable)
    private final AtomicInteger calibrationMargin = new AtomicInteger(100);

    // Recent captured packet samples buffer for visual inspection, filtering & selective baseline training
    private final ConcurrentLinkedDeque<CapturedPacketSample> capturedPackets = new ConcurrentLinkedDeque<>();
    private static final int MAX_CAPTURED_PACKETS = 2000;
    private final AtomicLong packetSequence = new AtomicLong(0);

    // Golden Profile Centroids (128 dimensions)
    private final float[] goldenMean = new float[PacketVector128.DIMENSIONS];
    private final float[] goldenStd = new float[PacketVector128.DIMENSIONS];
    private final double[] runningM2 = new double[PacketVector128.DIMENSIONS]; // For Welford's algorithm

    private static final float ANOMALY_Z_THRESHOLD = 3.5f;

    private CleanRoomManager() {
        // Inicializa com dispersao padrao unitaria para evitar divisao por zero
        for (int i = 0; i < PacketVector128.DIMENSIONS; i++) {
            goldenMean[i] = 1.0f;
            goldenStd[i] = 1.0f;
        }
    }

    public static CleanRoomManager getInstance() {
        return INSTANCE;
    }

    /**
     * Add an IP to the Clean Room.
     */
    public boolean addCleanIp(String ip, String notes) {
        if (ip == null || ip.isBlank()) return false;
        String clean = ip.trim();
        CleanIpEntry entry = new CleanIpEntry(clean, System.currentTimeMillis(), 
            (notes != null && !notes.isBlank()) ? notes : "Promovido via Live Events", 
            new AtomicLong(0));
        cleanIps.put(clean, entry);
        LOGGER.info("[CLEAN-ROOM] IP added to clean room: " + clean);
        return true;
    }

    /**
     * Remove an IP from the Clean Room.
     */
    public boolean removeCleanIp(String ip) {
        if (ip == null) return false;
        boolean removed = cleanIps.remove(ip.trim()) != null;
        if (removed) {
            LOGGER.info("[CLEAN-ROOM] IP removed from clean room: " + ip);
        }
        return removed;
    }

    public boolean isCleanIp(String ip) {
        if (ip == null) return false;
        return cleanIps.containsKey(ip.trim());
    }

    public List<CleanIpEntry> getCleanIps() {
        return new ArrayList<>(cleanIps.values());
    }

    /**
     * Ingest a packet vector into the model or test against the Golden Profile.
     */
    public AnomalyDecision processPacket(PacketVector128 vector) {
        return processPacket(vector, vector != null ? vector.protocol() : "TCP", "Traffic sample");
    }

    /**
     * Ingest a packet vector with semantic protocol and summary description.
     */
    public AnomalyDecision processPacket(PacketVector128 vector, String proto, String summary) {
        if (vector == null) {
            return new AnomalyDecision(false, 0.0f, 0.0f, "Vetor nulo");
        }

        String ip = vector.ip();
        CleanIpEntry cleanEntry = cleanIps.get(ip);
        boolean isClean = (cleanEntry != null);

        // 1. Se pertence a Area Limpa: Treina o modelo (Welford online algorithm)
        if (isClean) {
            cleanEntry.packetCount().incrementAndGet();
            if (!frozen.get()) {
                updateGoldenProfileOnline(vector.features());
            }
        }

        // Armazena no buffer recente para visualizacao, auditoria e selecao do usuario
        recordCapturedPacket(vector, proto != null ? proto : vector.protocol(), summary, isClean);

        if (isClean) {
            return new AnomalyDecision(false, 0.0f, 0.0f, "IP Verificado na Area Limpa");
        }

        // 2. Se for IP externo: Compara contra o Golden Profile usando SIMD
        if (getState() == ProfileState.NOT_TRAINED) {
            return new AnomalyDecision(false, 0.0f, 0.0f, "Modelo ainda em calibracao");
        }

        float cosineDist = SimdVectorEngine.cosineDistance(vector.features(), goldenMean);
        float zScore = SimdVectorEngine.anomalyZScore(vector.features(), goldenMean, goldenStd);

        boolean isAnomaly = (zScore >= ANOMALY_Z_THRESHOLD) || (cosineDist > 0.70f);
        if (isAnomaly) {
            totalAnomaliesDetected.incrementAndGet();
            LOGGER.fine(() -> String.format("[ANOMALY-DETECTED] IP %s - Z=%.2f, CosDist=%.3f", ip, zScore, cosineDist));
        }

        return new AnomalyDecision(isAnomaly, zScore, cosineDist, 
            isAnomaly ? String.format("Desvio vetorial acentuado (Z=%.1f, Dist=%.2f)", zScore, cosineDist) : "Padrao normal");
    }

    private void recordCapturedPacket(PacketVector128 vector, String proto, String summary, boolean selectedByDefault) {
        long id = packetSequence.incrementAndGet();
        CapturedPacketSample sample = new CapturedPacketSample(
            id, System.currentTimeMillis(), vector.ip(), proto, summary, vector, new AtomicBoolean(selectedByDefault)
        );
        capturedPackets.addFirst(sample);
        while (capturedPackets.size() > MAX_CAPTURED_PACKETS) {
            capturedPackets.pollLast();
        }
    }

    public List<CapturedPacketSample> getCapturedPackets() {
        return new ArrayList<>(capturedPackets);
    }

    public boolean removeCapturedPacket(long id) {
        return capturedPackets.removeIf(s -> s.id() == id);
    }

    public int removeCapturedPackets(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        int before = capturedPackets.size();
        capturedPackets.removeIf(s -> ids.contains(s.id()));
        return before - capturedPackets.size();
    }

    public void clearCapturedPackets() {
        capturedPackets.clear();
    }

    public void setPacketSelected(long id, boolean selected) {
        for (CapturedPacketSample s : capturedPackets) {
            if (s.id() == id) {
                s.selectedForModel().set(selected);
                break;
            }
        }
    }

    public void selectAllPackets(boolean selected) {
        for (CapturedPacketSample s : capturedPackets) {
            s.selectedForModel().set(selected);
        }
    }

    /**
     * Recalculates the Golden Profile baseline exclusively using the user-selected samples.
     * Expunges noise/outliers and guarantees mathematical consistency via Welford's algorithm.
     */
    public synchronized void recalculateProfileFromSelectedSamples() {
        totalSamplesCollected.set(0);
        totalAnomaliesDetected.set(0);
        for (int i = 0; i < PacketVector128.DIMENSIONS; i++) {
            goldenMean[i] = 1.0f;
            goldenStd[i] = 1.0f;
            runningM2[i] = 0.0;
        }
        for (CleanIpEntry entry : cleanIps.values()) {
            entry.packetCount().set(0);
        }

        List<CapturedPacketSample> list = new ArrayList<>(capturedPackets);
        Collections.reverse(list); // Chronological order
        for (CapturedPacketSample s : list) {
            if (s.selectedForModel().get() && s.vector() != null) {
                CleanIpEntry entry = cleanIps.get(s.ip());
                if (entry != null) {
                    entry.packetCount().incrementAndGet();
                }
                updateGoldenProfileOnline(s.vector().features());
            }
        }
        LOGGER.info("[CLEAN-ROOM] Recalculated Golden Profile from " + totalSamplesCollected.get() + " selected samples.");
    }

    public int getCalibrationMargin() {
        return calibrationMargin.get();
    }

    public void setCalibrationMargin(int margin) {
        int valid = Math.max(10, Math.min(margin, 10000));
        this.calibrationMargin.set(valid);
        LOGGER.info("[CLEAN-ROOM] Calibration margin set to: " + valid);
    }

    /**
     * Welford's algorithm for computing running mean and variance in O(1) time without keeping all vectors in memory.
     */
    private synchronized void updateGoldenProfileOnline(float[] features) {
        long count = totalSamplesCollected.incrementAndGet();
        for (int i = 0; i < PacketVector128.DIMENSIONS; i++) {
            float x = features[i];
            float oldMean = goldenMean[i];
            float newMean = oldMean + (x - oldMean) / count;
            goldenMean[i] = newMean;
            runningM2[i] += (x - oldMean) * (x - newMean);
            if (count > 1) {
                goldenStd[i] = (float) Math.max(0.001, Math.sqrt(runningM2[i] / (count - 1)));
            }
        }
    }

    public ProfileState getState() {
        if (frozen.get()) return ProfileState.FROZEN;
        long samples = totalSamplesCollected.get();
        int margin = calibrationMargin.get();
        if (samples < (margin / 2)) return ProfileState.NOT_TRAINED;
        if (samples < margin) return ProfileState.COLLECTING;
        return ProfileState.CALIBRATED;
    }

    public boolean isCalibrated() {
        ProfileState s = getState();
        return s == ProfileState.CALIBRATED || s == ProfileState.FROZEN;
    }

    public void freezeProfile() {
        frozen.set(true);
        LOGGER.info("[CLEAN-ROOM] Golden Profile frozen against data poisoning.");
    }

    public void unfreezeProfile() {
        frozen.set(false);
        LOGGER.info("[CLEAN-ROOM] Golden Profile unfreezed.");
    }

    public boolean isFrozen() {
        return frozen.get();
    }

    public synchronized void resetProfile() {
        totalSamplesCollected.set(0);
        totalAnomaliesDetected.set(0);
        frozen.set(false);
        for (int i = 0; i < PacketVector128.DIMENSIONS; i++) {
            goldenMean[i] = 1.0f;
            goldenStd[i] = 1.0f;
            runningM2[i] = 0.0;
        }
        for (CleanIpEntry entry : cleanIps.values()) {
            entry.packetCount().set(0);
        }
        LOGGER.info("[CLEAN-ROOM] Golden Profile reset to initial state.");
    }

    public long getTotalSamplesCollected() {
        return totalSamplesCollected.get();
    }

    public long getTotalAnomaliesDetected() {
        return totalAnomaliesDetected.get();
    }

    public float[] getGoldenMean() {
        return goldenMean.clone();
    }
}
