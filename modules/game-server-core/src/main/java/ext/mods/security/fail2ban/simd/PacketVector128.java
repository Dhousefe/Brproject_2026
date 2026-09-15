/*
 * Copyleft © 2024-2026 L2Brproject
 */
package ext.mods.security.fail2ban.simd;

import java.util.Arrays;

/**
 * 128-dimensional dense feature vector for network packet anomaly detection.
 * Designed with Mechanical Sympathy:
 * - 128 float elements = exactly 512 bytes (8 contiguous 64-byte cache lines).
 * - Fixed stride enables 256-bit AVX2 (8 floats per lane) and 512-bit AVX-512 vectorization.
 */
public final class PacketVector128 {
    public static final int DIMENSIONS = 128;

    private final float[] features;
    private final long timestamp;
    private final String ip;
    private final String protocol;

    public PacketVector128(String ip, String protocol, float[] rawFeatures) {
        this.ip = (ip != null) ? ip.trim() : "0.0.0.0";
        this.protocol = (protocol != null) ? protocol.toUpperCase() : "TCP";
        this.timestamp = System.currentTimeMillis();
        this.features = new float[DIMENSIONS];
        if (rawFeatures != null) {
            int len = Math.min(rawFeatures.length, DIMENSIONS);
            System.arraycopy(rawFeatures, 0, this.features, 0, len);
        }
    }

    public static PacketVector128 synthesizeTcp(String ip, int payloadLen, int opcode, long interArrivalMs, int windowSize) {
        float[] v = new float[DIMENSIONS];
        // Bloco 1: Temporal & Frequencia (0..15)
        v[0] = (float) Math.min(interArrivalMs, 5000L);
        v[1] = (interArrivalMs > 0) ? (1000.0f / interArrivalMs) : 100.0f;
        
        // Bloco 2: Volumetria & Janela TCP (16..31)
        v[16] = (float) Math.min(payloadLen, 65535);
        v[17] = (float) Math.min(windowSize, 65535);
        
        // Bloco 3: Opcodes L2 (32..63)
        int opcodeIdx = 32 + (Math.abs(opcode) % 32);
        v[opcodeIdx] = 1.0f;
        
        // Bloco 4: Entropia (64..79)
        v[64] = (payloadLen > 0) ? (float) (Math.log(payloadLen + 1) / Math.log(2)) : 0.0f;
        
        return new PacketVector128(ip, "TCP", v);
    }

    public static PacketVector128 synthesizeHttp(String ip, String method, int uriLen, int headerCount, int bodyLen) {
        float[] v = new float[DIMENSIONS];
        v[0] = 10.0f;
        
        // Bloco 5: HTTP Headers & Method (80..111)
        if ("GET".equalsIgnoreCase(method)) v[80] = 1.0f;
        else if ("POST".equalsIgnoreCase(method)) v[81] = 1.0f;
        else if ("PUT".equalsIgnoreCase(method)) v[82] = 1.0f;
        else if ("DELETE".equalsIgnoreCase(method)) v[83] = 1.0f;
        else v[84] = 1.0f;
        
        v[85] = (float) Math.min(headerCount, 64);
        v[86] = (float) Math.min(bodyLen, 10_000_000);
        
        // Bloco 6: URI & Query Params (112..127)
        v[112] = (float) Math.min(uriLen, 2048);
        v[113] = (float) (uriLen > 30 ? 1.0f : 0.0f);
        
        return new PacketVector128(ip, "HTTP", v);
    }

    public float[] features() {
        return features;
    }

    public float get(int index) {
        return features[index];
    }

    public String ip() {
        return ip;
    }

    public String protocol() {
        return protocol;
    }

    public long timestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "PacketVector128{" +
                "ip='" + ip + '\'' +
                ", protocol='" + protocol + '\'' +
                ", dims=" + DIMENSIONS +
                '}';
    }
}
