package br.project.cluster.hpc.disruptor;

/** State mutation kind persisted by the write-behind journal. */
public enum StateChangeType {
    PLAYER_POSITION,
    PLAYER_STATS,
    INVENTORY_ITEM,
    SKILL_SAVE,
    QUEST_VAR,
    ACCOUNT_TOUCH
}
