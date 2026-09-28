package ext.mods.gameserver.data.repository;

/** Persisted shortcut entry for one character class index. */
public record ShortcutRecord(int slot, int page, String type, int id, int level, int classIndex)
{
}
