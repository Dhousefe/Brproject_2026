package ext.mods.extensions.hooks;

import ext.mods.extensions.api.IDressMeSkin;
import ext.mods.gameserver.model.actor.Player;

/**
 * DressMe host hooks. Behavior + apply/remove/restore live in {@code mod-dressme}.
 * Player methods are thin delegates to this API.
 */
public final class DressMeHooks
{
	public interface Api
	{
		IDressMeSkin getBySkillId(int skillId);
		
		void startEffect(Player player, IDressMeSkin skin);
		
		void stopEffect(Player player);
		
		void reload();
		
		/** Apply armor or weapon skin (paperdoll visuals + state + effect). */
		void apply(Player player, IDressMeSkin skin, boolean test);
		
		void removeArmor(Player player);
		
		void removeWeapon(Player player);
		
		/** Restore skins from player memos on EnterWorld. */
		void restore(Player player);
	}
	
	private static final Api NOOP = new Api()
	{
		@Override
		public IDressMeSkin getBySkillId(int skillId)
		{
			return null;
		}
		
		@Override
		public void startEffect(Player player, IDressMeSkin skin)
		{
		}
		
		@Override
		public void stopEffect(Player player)
		{
		}
		
		@Override
		public void reload()
		{
		}
		
		@Override
		public void apply(Player player, IDressMeSkin skin, boolean test)
		{
		}
		
		@Override
		public void removeArmor(Player player)
		{
		}
		
		@Override
		public void removeWeapon(Player player)
		{
		}
		
		@Override
		public void restore(Player player)
		{
		}
	};
	
	private static volatile Api api = NOOP;
	
	private DressMeHooks()
	{
	}
	
	public static void register(Api implementation)
	{
		api = implementation != null ? implementation : NOOP;
	}
	
	public static void clear()
	{
		api = NOOP;
	}
	
	public static Api get()
	{
		return api;
	}
}
