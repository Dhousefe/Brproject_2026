package ext.mods.tour;

import ext.mods.InstanceMap.InstanceManager;
import ext.mods.extensions.hooks.TournamentHooks;
import ext.mods.gameserver.data.SkillTable;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.player.PlayerAttachments;
import ext.mods.gameserver.network.serverpackets.SkillCoolTime;
import ext.mods.gameserver.skills.L2Skill;
import ext.mods.gameserver.model.actor.player.TournamentState;

/**
 * Tournament save/restore of player position and arena flags (was on {@link Player}).
 */
public final class TournamentPlayerState
{
	private static final PlayerAttachments.Key<int[]> ATT_TOUR_SAVE = PlayerAttachments.Key.of("tournament.save");
	private static final int NOBLESSE_BLESSING_SKILL_ID = 1323;
	
	private TournamentPlayerState()
	{
	}
	
	public static void save(Player player)
	{
		if (player == null)
			return;
		final int[] snap = new int[]
		{
			player.getX(),
			player.getY(),
			player.getZ(),
			(int) player.getStatus().getCp(),
			(int) player.getStatus().getHp(),
			(int) player.getStatus().getMp(),
			player.getInstanceMap() != null ? player.getInstanceMap().getId() : 0
		};
		PlayerAttachments.put(player.getObjectId(), ATT_TOUR_SAVE, snap);
	}
	
	public static void restore(Player player)
	{
		if (player == null)
			return;
		
		if (player.isDead() || player.isAlikeDead())
			player.doRevive();
		
		player.getStatus().startHpMpRegeneration();
		TournamentState.setIn(player, false);
		TournamentState.setBattle(player, null);
		TournamentState.setOpponents(player, null);
		player.setInvul(false);
		
		if (player.isFakeDeath())
			player.stopFakeDeath(true);
		
		player.setIsImmobilized(false);
		
		final int[] snap = PlayerAttachments.get(player.getObjectId(), ATT_TOUR_SAVE);
		final int saveX = snap != null ? snap[0] : player.getX();
		final int saveY = snap != null ? snap[1] : player.getY();
		final int saveZ = snap != null ? snap[2] : player.getZ();
		final int saveInstanceId = snap != null ? snap[6] : 0;
		PlayerAttachments.remove(player.getObjectId(), ATT_TOUR_SAVE);
		
		player.setInstanceMap(InstanceManager.getInstance().getInstance(saveInstanceId), true);
		player.teleportTo(saveX, saveY, saveZ, 75);
		
		player.getStatus().setCp(player.getStatus().getMaxCp());
		player.getStatus().setHp(player.getStatus().getMaxHp());
		player.getStatus().setMp(player.getStatus().getMaxMp());
		
		if (player.isNoble())
		{
			final L2Skill noblessSkill = SkillTable.getInstance().getInfo(NOBLESSE_BLESSING_SKILL_ID, 1);
			if (noblessSkill != null && player.getSkill(NOBLESSE_BLESSING_SKILL_ID) != null)
				noblessSkill.getEffects(player, player);
		}
		
		if (player.isDead() || player.isAlikeDead())
		{
			player.doRevive();
			player.getStatus().setCp(player.getStatus().getMaxCp());
			player.getStatus().setHp(player.getStatus().getMaxHp());
			player.getStatus().setMp(player.getStatus().getMaxMp());
		}
		
		player.updateEffectIcons();
		player.sendPacket(new SkillCoolTime(player));
		TournamentHooks.get().showRanking(player);
	}
}
