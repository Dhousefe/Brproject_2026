/*
 * Copyleft © 2024-2026 L2Brproject
 */
package ext.mods.security.fail2ban;

import ext.mods.security.fail2ban.simd.CleanRoomManager;
import ext.mods.security.fail2ban.simd.PacketVector128;
import ext.mods.security.fail2ban.simd.SimdVectorEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CleanRoomSimdTest {

    private CleanRoomManager cleanRoom;

    @BeforeEach
    public void setup() {
        cleanRoom = CleanRoomManager.getInstance();
        cleanRoom.resetProfile();
        for (CleanRoomManager.CleanIpEntry entry : cleanRoom.getCleanIps()) {
            cleanRoom.removeCleanIp(entry.ip());
        }
    }

    @Test
    public void testCleanRoomIpLifecycle() {
        String testIp = "192.168.100.50";
        assertFalse(cleanRoom.isCleanIp(testIp));

        assertTrue(cleanRoom.addCleanIp(testIp, "Verified player"));
        assertTrue(cleanRoom.isCleanIp(testIp));

        assertEquals(1, cleanRoom.getCleanIps().size());

        assertTrue(cleanRoom.removeCleanIp(testIp));
        assertFalse(cleanRoom.isCleanIp(testIp));
    }

    @Test
    public void testSimdCosineDistanceCalculations() {
        float[] v1 = new float[PacketVector128.DIMENSIONS];
        float[] v2 = new float[PacketVector128.DIMENSIONS];

        for (int i = 0; i < PacketVector128.DIMENSIONS; i++) {
            v1[i] = 1.0f;
            v2[i] = 1.0f;
        }

        // Vetores identicos devem ter distancia de cosseno proxima de 0.0
        float distIdentical = SimdVectorEngine.cosineDistance(v1, v2);
        assertEquals(0.0f, distIdentical, 0.001f, "Identical vectors must have 0.0 cosine distance");

        // Vetor ortogonal
        float[] vOrthogonal = new float[PacketVector128.DIMENSIONS];
        for (int i = 0; i < 64; i++) v1[i] = 1.0f;
        for (int i = 64; i < 128; i++) v1[i] = 0.0f;

        for (int i = 0; i < 64; i++) vOrthogonal[i] = 0.0f;
        for (int i = 64; i < 128; i++) vOrthogonal[i] = 1.0f;

        float distOrthogonal = SimdVectorEngine.cosineDistance(v1, vOrthogonal);
        assertEquals(1.0f, distOrthogonal, 0.001f, "Orthogonal vectors must have 1.0 cosine distance");
    }

    @Test
    public void testGoldenProfileCalibrationAndAnomalyDetection() {
        String cleanIp = "10.0.0.15";
        cleanRoom.addCleanIp(cleanIp, "Benchmark Player");

        // Treina o modelo com 120 pacotes legitimos
        for (int i = 0; i < 120; i++) {
            PacketVector128 normalPacket = PacketVector128.synthesizeTcp(cleanIp, 64, 0x01, 20L, 64240);
            CleanRoomManager.AnomalyDecision dec = cleanRoom.processPacket(normalPacket);
            assertFalse(dec.isAnomaly(), "Clean IP packets should not trigger anomaly");
        }

        assertEquals(CleanRoomManager.ProfileState.CALIBRATED, cleanRoom.getState());

        // Testa pacote de IP desconhecido com padrao compativel
        PacketVector128 similarUnknown = PacketVector128.synthesizeTcp("198.51.100.8", 64, 0x01, 20L, 64240);
        CleanRoomManager.AnomalyDecision decSimilar = cleanRoom.processPacket(similarUnknown);
        assertFalse(decSimilar.isAnomaly(), "Normal traffic from unknown IP should be accepted");

        // Testa pacote anomalo (tamanho extremo 65535, delay bizarro e opcode distorcido)
        float[] anomalyFeatures = new float[PacketVector128.DIMENSIONS];
        for (int i = 0; i < PacketVector128.DIMENSIONS; i++) {
            anomalyFeatures[i] = 5000.0f;
        }
        PacketVector128 anomalyPacket = new PacketVector128("203.0.113.99", "TCP", anomalyFeatures);
        CleanRoomManager.AnomalyDecision decAnomaly = cleanRoom.processPacket(anomalyPacket);

        assertTrue(decAnomaly.isAnomaly(), "Extreme deviation should be flagged as anomaly");
        assertTrue(decAnomaly.zScore() > 3.0f, "Z-score should exceed threshold");
        assertTrue(cleanRoom.getTotalAnomaliesDetected() > 0);
    }

    @Test
    public void testModelFreezeProtection() {
        cleanRoom.freezeProfile();
        assertTrue(cleanRoom.isFrozen());
        assertEquals(CleanRoomManager.ProfileState.FROZEN, cleanRoom.getState());

        cleanRoom.unfreezeProfile();
        assertFalse(cleanRoom.isFrozen());
    }

    @Test
    public void testProxyTrafficIngestionIntoCleanRoom() {
        String testIp = "192.168.1.120";
        cleanRoom.addCleanIp(testIp, "Verified Mobile");

        // Simula linha de stdout gerada pelo Proxy Reverso Netty
        String proxyLogLine = "[PROXY-TRAFFIC-EVENT] proto=TCP route='l2-game' ip=" + testIp + " action=CONNECT latency=12ms";
        boolean handled = ext.mods.security.fail2ban.core.ProxySecurityBridge.processLine(proxyLogLine);
        assertTrue(handled, "ProxySecurityBridge should handle the traffic event");

        // Confirma que o contador de pacotes do IP foi incrementado pela ingestao do proxy
        CleanRoomManager.CleanIpEntry entry = cleanRoom.getCleanIps().stream()
            .filter(e -> e.ip().equals(testIp))
            .findFirst()
            .orElse(null);
        assertNotNull(entry, "Entry for test IP must exist");
        assertEquals(1L, entry.packetCount().get(), "Packet count should be incremented via proxy traffic event");
        assertEquals(1L, cleanRoom.getTotalSamplesCollected(), "Total samples collected should be 1");
        assertEquals(CleanRoomManager.ProfileState.NOT_TRAINED, cleanRoom.getState(), "State should be NOT_TRAINED until 50 samples");
    }

    @Test
    public void testDynamicCalibrationMargin() {
        // Testa margem default
        assertEquals(100, cleanRoom.getCalibrationMargin());

        // Altera margem para 20
        cleanRoom.setCalibrationMargin(20);
        assertEquals(20, cleanRoom.getCalibrationMargin());

        // Valida limites (minimo 10)
        cleanRoom.setCalibrationMargin(5);
        assertEquals(10, cleanRoom.getCalibrationMargin());

        String cleanIp = "10.10.10.10";
        cleanRoom.addCleanIp(cleanIp, "Fast calibration test");

        // Com 15 pacotes e margem 10, o perfil ja deve estar calibrado (> margem)
        for (int i = 0; i < 15; i++) {
            cleanRoom.processPacket(PacketVector128.synthesizeTcp(cleanIp, 50, 0x01, 10L, 64240));
        }
        assertEquals(CleanRoomManager.ProfileState.CALIBRATED, cleanRoom.getState());
    }

    @Test
    public void testCapturedPacketsBufferAndSelectiveRecalculation() {
        cleanRoom.clearCapturedPackets();
        assertEquals(0, cleanRoom.getCapturedPackets().size());

        String cleanIp = "10.20.30.40";
        cleanRoom.addCleanIp(cleanIp, "Buffer test");

        // Ingestao de 5 pacotes normais e 1 pacote de ruido
        for (int i = 0; i < 5; i++) {
            cleanRoom.processPacket(PacketVector128.synthesizeTcp(cleanIp, 64, 0x01, 20L, 64240), "TCP", "L2 normal");
        }

        java.util.List<CleanRoomManager.CapturedPacketSample> samples = cleanRoom.getCapturedPackets();
        assertEquals(5, samples.size());

        // Pega o ID da primeira amostra
        long firstId = samples.get(0).id();
        assertTrue(samples.get(0).selectedForModel().get(), "Clean samples should be selected by default");

        // Desmarca a primeira amostra
        cleanRoom.setPacketSelected(firstId, false);
        assertFalse(cleanRoom.getCapturedPackets().stream().filter(s -> s.id() == firstId).findFirst().get().selectedForModel().get());

        // Recalcula o modelo a partir das amostras selecionadas
        cleanRoom.recalculateProfileFromSelectedSamples();
        assertEquals(4L, cleanRoom.getTotalSamplesCollected(), "Should train only the 4 remaining selected samples");

        // Testa remocao individual
        assertTrue(cleanRoom.removeCapturedPacket(firstId));
        assertEquals(4, cleanRoom.getCapturedPackets().size());

        // Testa remocao em lote
        java.util.Set<Long> toRemove = new java.util.HashSet<>();
        toRemove.add(samples.get(1).id());
        toRemove.add(samples.get(2).id());
        int removed = cleanRoom.removeCapturedPackets(toRemove);
        assertEquals(2, removed);
        assertEquals(2, cleanRoom.getCapturedPackets().size());
    }
}
