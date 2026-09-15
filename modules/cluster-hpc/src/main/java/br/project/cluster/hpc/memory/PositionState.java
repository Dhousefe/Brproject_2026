package br.project.cluster.hpc.memory;

/** Packed XYZ+heading helper: no allocation, no object graph. */
public final class PositionState {
    private PositionState() { }
    public static long pack(int x, int y, int z, int heading) {
        long px = (long) (x & 0xFFFFF) << 44;
        long py = (long) (y & 0xFFFFF) << 24;
        long pz = (long) (z & 0xFFFFF) << 4;
        return px | py | pz | (heading & 0xF);
    }
    public static int x(long packed) { return (int) (packed >> 44); }
    public static int y(long packed) { return (int) ((packed >> 24) & 0xFFFFF); }
    public static int z(long packed) { return (int) ((packed >> 4) & 0xFFFFF); }
    public static int heading(long packed) { return (int) (packed & 0xF); }
}
