package br.project.cluster.hpc.memory;

/** In-memory player state as primitive arrays; SQLite is durability log, not runtime truth. */
public final class PlayerState {
    private final IntLongPairMap positions = new IntLongPairMap(8192);
    private final IntLongPairMap hpMpCp = new IntLongPairMap(8192);

    public void position(int objectId, int x, int y, int z, int heading) { positions.put(objectId, PositionState.pack(x, y, z, heading)); }
    public long position(int objectId) { return positions.get(objectId, 0L); }

    public void vitals(int objectId, int hp, int mp, int cp) {
        long packed = ((long) hp << 42) | ((long) mp << 21) | cp;
        hpMpCp.put(objectId, packed);
    }
    public long vitals(int objectId) { return hpMpCp.get(objectId, 0L); }
}
