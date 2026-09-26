package ext.mods.gameserver.data.repository;

/** Persistence port for the enabled state of custom events. */
public interface CustomEventStateStore
{
	boolean isEnabled(String eventName);

	void setEnabled(String eventName, boolean enabled);
}
