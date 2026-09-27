package ext.mods.gameserver.data.repository;

import java.util.List;

/** Persistence port for player quest state and variables. */
public interface QuestStore
{
	List<VariableRecord> loadVariables(int characterId);

	void saveVariable(int characterId, String questName, String variable, String value);

	void deleteVariable(int characterId, String questName, String variable);

	void deleteQuest(int characterId, String questName);

	void completeQuest(int characterId, String questName);

	record VariableRecord(String questName, String variable, String value)
	{
	}
}
