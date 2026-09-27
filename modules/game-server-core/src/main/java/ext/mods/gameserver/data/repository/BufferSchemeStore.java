package ext.mods.gameserver.data.repository;

import java.util.List;

/** Persistence port for player-owned NPC buffer schemes. */
public interface BufferSchemeStore
{
	List<SchemeRecord> loadSchemes();

	void replaceSchemes(List<SchemeRecord> schemes);

	record SchemeRecord(int playerId, String name, String skills, String levels)
	{
	}
}
