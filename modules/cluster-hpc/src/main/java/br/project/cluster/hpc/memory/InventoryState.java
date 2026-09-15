package br.project.cluster.hpc.memory;

/** Fixed inventory slot arrays — no Item object allocation in hot path. */
public final class InventoryState {
    private final int[] objectIds;
    private final int[] itemIds;
    private final long[] counts;

    public InventoryState(int maxItems) { objectIds = new int[maxItems]; itemIds = new int[maxItems]; counts = new long[maxItems]; }
    public void set(int slot, int objectId, int itemId, long count) { objectIds[slot]=objectId; itemIds[slot]=itemId; counts[slot]=count; }
    public int objectId(int slot) { return objectIds[slot]; }
    public int itemId(int slot) { return itemIds[slot]; }
    public long count(int slot) { return counts[slot]; }
}
