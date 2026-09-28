package ext.mods.gameserver.data.repository;

/** Persisted macro data before it is converted into the game model. */
public record MacroRecord(int id, int icon, String name, String description, String acronym, String commands)
{
}
