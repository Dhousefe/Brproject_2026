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
package ext.mods.gameserver.network.clientpackets;

import ext.mods.commons.logging.CLogger;
import ext.mods.gameserver.model.World;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.network.serverpackets.AskJoinPledge;

public final class SiteClanInviteHandler
{
	private static final CLogger LOGGER = new CLogger(SiteClanInviteHandler.class.getName());
	
	/**
	 * Dispara o convite de clan via site.
	 * @return null se enviado com sucesso, ou uma String explicativa com a razão da falha.
	 */
	public static String sendInvite(int leaderObjId, int clanId, int targetSubPledgeId, Player targetPlayer, String clanName, String subPledgeName)
	{
		if (targetPlayer == null)
		{
			final String err = "O jogador alvo não foi encontrado no mundo do jogo (offline).";
			LOGGER.warn("[SITE-CLAN-INVITE] {}", err);
			return err;
		}
		
		if (targetPlayer.getClient() == null || targetPlayer.getClient().isDetached())
		{
			final String err = "O jogador " + targetPlayer.getName() + " está desconectado ou com cliente inativo no jogo.";
			LOGGER.warn("[SITE-CLAN-INVITE] {}", err);
			return err;
		}
		
		if (targetPlayer.getRequest().isProcessingRequest())
		{
			final String err = "O jogador " + targetPlayer.getName() + " está ocupado no jogo no momento (em negociação, conversa ou respondendo outro convite).";
			LOGGER.warn("[SITE-CLAN-INVITE] {}", err);
			return err;
		}
		
		final SiteJoinPledgeRequest siteReq = new SiteJoinPledgeRequest(clanId, targetSubPledgeId, leaderObjId);
		final Player leaderPlayer = World.getInstance().getPlayer(leaderObjId);
		
		boolean registered = false;
		int effectiveRequestorObjId = leaderObjId;

		if (leaderPlayer != null && leaderPlayer.isOnline())
		{
			registered = leaderPlayer.getRequest().setRequest(targetPlayer, siteReq);
			effectiveRequestorObjId = leaderPlayer.getObjectId();
		}
		else
		{
			registered = targetPlayer.getRequest().setSiteRequest(siteReq);
			effectiveRequestorObjId = targetPlayer.getObjectId();
		}
		
		if (!registered)
		{
			final String err = "Não foi possível registrar a requisição de convite no contêiner do jogador " + targetPlayer.getName() + ". Tente novamente em instantes.";
			LOGGER.warn("[SITE-CLAN-INVITE] {}", err);
			return err;
		}
		
		final String actualSubPledgeName = (subPledgeName != null && !subPledgeName.isEmpty()) ? subPledgeName : clanName;
		final AskJoinPledge askPacket = new AskJoinPledge(effectiveRequestorObjId, actualSubPledgeName, targetSubPledgeId, clanName);
		
		targetPlayer.sendPacket(askPacket);
		return null;
	}
}
