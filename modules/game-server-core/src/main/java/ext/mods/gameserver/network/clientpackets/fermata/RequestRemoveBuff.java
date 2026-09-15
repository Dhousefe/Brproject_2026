/*
 * Copyleft © 2024-2026 L2Brproject
 * * This file is part of L2Brproject derived from aCis409/RusaCis3.8
 * * L2Brproject is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License.
 * * L2Brproject is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package ext.mods.gameserver.network.clientpackets.fermata;

import ext.mods.gameserver.enums.skills.EffectType;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.network.GameClient.GameClientState;
import ext.mods.gameserver.network.clientpackets.L2GameClientPacket;
import ext.mods.gameserver.skills.AbstractEffect;
import ext.mods.gameserver.skills.L2Skill;

/**
 * Fermata {@code 0xFD:0x0001}: remove um buff visivel e cancelavel do jogador (Alt+Click).
 * Valida rigorosamente politica de remocao prevenindo exclusao de debuffs, toggles, passivas e signets.
 */
public final class RequestRemoveBuff extends L2GameClientPacket
{
	public static final int PAYLOAD_LENGTH = Integer.BYTES + Short.BYTES;
	
	private int _skillId;
	private int _skillLevel;
	private boolean _validPayload;
	
	@Override
	protected void readImpl()
	{
		if (_buf.remaining() != PAYLOAD_LENGTH)
		{
			getClient().closeNow();
			return;
		}
		
		_skillId = readD();
		_skillLevel = readH();
		_validPayload = true;
	}
	
	@Override
	protected void runImpl()
	{
		if (getClient().getState() != GameClientState.IN_GAME)
		{
			getClient().closeNow();
			return;
		}
		
		if (!_validPayload || _skillId <= 0 || _skillLevel <= 0)
			return;
		
		final Player player = getClient().getPlayer();
		if (player == null || player.isDead())
			return;
		
		final AbstractEffect[] effects = player.getAllEffects();
		if (!containsRemovableVisibleBuff(effects, _skillId, _skillLevel))
			return;
		
		for (AbstractEffect effect : effects)
		{
			if (effect == null)
				continue;
			
			final L2Skill skill = effect.getSkill();
			if (skill.getId() == _skillId && skill.getLevel() == _skillLevel)
				effect.exit();
		}
	}
	
	public static boolean containsRemovableVisibleBuff(AbstractEffect[] effects, int skillId, int skillLevel)
	{
		if (effects == null || skillId <= 0 || skillLevel <= 0)
			return false;
		
		for (AbstractEffect effect : effects)
		{
			if (effect == null)
				continue;
			
			final L2Skill skill = effect.getSkill();
			if (matchesRemovalPolicy(
				skillId,
				skillLevel,
				skill.getId(),
				skill.getLevel(),
				skill.isDebuff(),
				skill.isToggle(),
				skill.canBeDispeled(),
				effect.getTemplate().showIcon(),
				effect.getInUse(),
				effect.getEffectType() == EffectType.SIGNET_GROUND))
				return true;
		}
		return false;
	}
	
	public static boolean matchesRemovalPolicy(
		int requestedSkillId,
		int requestedSkillLevel,
		int activeSkillId,
		int activeSkillLevel,
		boolean debuff,
		boolean toggle,
		boolean dispellable,
		boolean showIcon,
		boolean inUse,
		boolean groundSignet)
	{
		return requestedSkillId > 0
			&& requestedSkillLevel > 0
			&& requestedSkillId == activeSkillId
			&& requestedSkillLevel == activeSkillLevel
			&& !debuff
			&& !toggle
			&& dispellable
			&& showIcon
			&& inUse
			&& !groundSignet;
	}
}
