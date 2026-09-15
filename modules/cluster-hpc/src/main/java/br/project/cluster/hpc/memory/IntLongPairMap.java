package br.project.cluster.hpc.memory;

import java.util.Arrays;

/** Primitive open-addressing int->long map for player state; no boxing, no per-update allocation. */
public final class IntLongPairMap {
    private static final int EMPTY = 0;
    private int[] keys;
    private long[] values;
    private int mask;

    public IntLongPairMap(int capacityPowerOfTwo) {
        int cap = 1;
        while (cap < capacityPowerOfTwo) cap <<= 1;
        keys = new int[cap]; values = new long[cap]; mask = cap - 1;
    }

    public long get(int key, long missing) {
        int idx = mix(key) & mask;
        for (;;) {
            int k = keys[idx];
            if (k == EMPTY) return missing;
            if (k == key) return values[idx];
            idx = (idx + 1) & mask;
        }
    }

    public void put(int key, long value) {
        if (key == EMPTY) throw new IllegalArgumentException("key 0 reserved");
        int idx = mix(key) & mask;
        for (;;) {
            int k = keys[idx];
            if (k == EMPTY || k == key) { keys[idx] = key; values[idx] = value; return; }
            idx = (idx + 1) & mask;
        }
    }

    public void clear() { Arrays.fill(keys, EMPTY); }
    private static int mix(int x) { x ^= x >>> 16; x *= 0x7feb352d; x ^= x >>> 15; x *= 0x846ca68b; return x ^ (x >>> 16); }
}
