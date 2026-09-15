package ext.mods.questrecommender.navigation;

import ext.mods.commons.logging.CLogger;
import ext.mods.commons.pool.ThreadPool;
import ext.mods.gameserver.data.manager.SpawnManager;
import ext.mods.gameserver.data.manager.ZoneManager;
import ext.mods.gameserver.data.xml.RestartPointData;
import ext.mods.gameserver.enums.GaugeColor;
import ext.mods.gameserver.enums.ZoneId;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.player.DungeonState;
import ext.mods.gameserver.model.actor.player.TournamentState;
import ext.mods.gameserver.model.entity.events.capturetheflag.CTFEvent;
import ext.mods.gameserver.model.entity.events.deathmatch.DMEvent;
import ext.mods.gameserver.model.entity.events.lastman.LMEvent;
import ext.mods.gameserver.model.entity.events.teamvsteam.TvTEvent;
import ext.mods.gameserver.model.location.Location;
import ext.mods.gameserver.model.restart.RestartArea;
import ext.mods.gameserver.model.restart.RestartPoint;
import ext.mods.gameserver.model.zone.type.TownZone;
import ext.mods.gameserver.network.serverpackets.ActionFailed;
import ext.mods.gameserver.network.serverpackets.MagicSkillUse;
import ext.mods.gameserver.network.serverpackets.SetupGauge;
import ext.mods.gameserver.network.serverpackets.SystemMessage;
import ext.mods.tour.TournamentEvent;
import ext.mods.Crypta.RandomManager;
import ext.mods.config.ConfigEvents;

/**
 * Serviço de Navegação e Teleporte Inteligente para o motor de quests.
 *
 * Casos de Uso atendidos:
 * 1. Se o player estiver na mesma cidade do NPC inicial OU a <= 5000 de range:
 *    -> Ativa imediatamente o Radar (bússola) para o NPC.
 * 2. Se estiver fora da cidade e > 5000 de range:
 *    -> Valida restrições rigorosas (canTeleport) espelhadas no LevelUpMaker.
 *    -> Identifica a vila/cidade mais próxima do NPC alvo usando a malha do RestartPointData e TownZone.
 *    -> Conjura o teleporte com animação visual, gauge azul e tempo de cast configurado.
 *    -> Ao chegar, marca o NPC no radar para guiar os passos finais.
 */
public final class QuestNavigationService
{
	private static final CLogger LOGGER = new CLogger(QuestNavigationService.class.getName());

	private static final QuestNavigationService INSTANCE = new QuestNavigationService();

	public static QuestNavigationService getInstance()
	{
		return INSTANCE;
	}

	private QuestNavigationService()
	{
	}

	/**
	 * Verifica se o jogador pode utilizar o teleporte com as mesmas regras de segurança do LevelUpMaker.
	 */
	public boolean canTeleport(Player player)
	{
		if (player == null || player.isDead())
			return false;
		if (player.getKarma() > 0)
			return false;
		if (player.isInCombat())
			return false;
		if (player.getCast().isCastingNow() || player.isTeleporting())
			return false;
		if (DungeonState.get(player) != null)
			return false;
		if (TournamentState.isIn(player) || TournamentEvent.isRunning())
			return false;
		if (player.isInOlympiadMode() || player.isInObserverMode() || player.isFestivalParticipant())
			return false;
		if (player.isInJail())
			return false;
		if (player.isInsideZone(ZoneId.BOSS))
			return false;
		if (ConfigEvents.CTF_EVENT_ENABLED && (CTFEvent.getInstance().isParticipating() || CTFEvent.getInstance().isStarted()))
			return false;
		if (ConfigEvents.DM_EVENT_ENABLED && (DMEvent.getInstance().isParticipating() || DMEvent.getInstance().isStarted()))
			return false;
		if (ConfigEvents.LM_EVENT_ENABLED && (LMEvent.getInstance().isParticipating() || LMEvent.getInstance().isStarted()))
			return false;
		if (ConfigEvents.TVT_EVENT_ENABLED && (TvTEvent.getInstance().isParticipating() || TvTEvent.getInstance().isStarted()))
			return false;

		// Verifica zonas aleatórias de farm ativas
		for (ext.mods.gameserver.model.zone.type.RandomZone zone : RandomManager.getInstance().getActiveZones())
		{
			ext.mods.FarmEventRandom.holder.RandomZoneData zoneData = (ext.mods.FarmEventRandom.holder.RandomZoneData) RandomManager.getInstance().getZoneDataForZone(zone);
			if (zoneData != null && zoneData.isActive())
				return false;
		}

		return true;
	}

	/**
	 * Resolve a localização do NPC alvo e processa a navegação ou teleporte.
	 *
	 * @param player o jogador
	 * @param npcId o ID do NPC inicial
	 * @param fallbackLoc localização estática caso o spawn dinâmico não esteja carregado
	 * @param scrollSkillId ID da skill do pergaminho de teleporte
	 * @param scrollSkillLevel nível da skill de teleporte
	 * @param castTimeMs tempo de conjuração do teleporte em milissegundos
	 * @param rangeLimit limite de distância em unidades Lineage 2 (padrão 5000)
	 * @param cannotTeleportMsg mensagem de bloqueio de teleporte
	 */
	public void navigateOrTeleportToNpc(Player player, int npcId, Location fallbackLoc, int scrollSkillId, int scrollSkillLevel, int castTimeMs, int rangeLimit, String cannotTeleportMsg)
	{
		if (player == null)
			return;

		Location targetNpcLoc = null;
		try
		{
			targetNpcLoc = SpawnManager.getInstance().getClosestSpawnLocation(player, npcId);
		}
		catch (Throwable t)
		{
			LOGGER.warn("Erro ao buscar localizacao de spawn para NPC {}: {}", npcId, t.getMessage());
		}

		if (targetNpcLoc == null)
			targetNpcLoc = fallbackLoc;

		if (targetNpcLoc == null)
		{
			player.sendMessage("Não foi possível localizar o NPC de início desta quest.");
			return;
		}

		final double distance = calculateDistance2D(player.getX(), player.getY(), targetNpcLoc.getX(), targetNpcLoc.getY());
		final boolean inSameTown = isInSameTown(player, targetNpcLoc);

		// Caso 1: Na mesma cidade ou dentro do range limite (<= 5000)
		if (inSameTown || distance <= rangeLimit)
		{
			markNpcOnRadar(player, targetNpcLoc);
			QuestPathVisualizerService.getInstance().renderPathToNpc(player, targetNpcLoc);
			player.sendMessage("O NPC de início da quest está próximo! Siga a trilha luminosa no chão e a bússola.");
			return;
		}

		// Caso 2: Fora da cidade e > 5000 de range -> Teleportar para cidade mais próxima do NPC
		if (!canTeleport(player))
		{
			player.sendMessage(cannotTeleportMsg != null ? cannotTeleportMsg : "Você não pode teleportar no momento (verifique combate, karma ou evento).");
			return;
		}

		final Location nearestTownLocation = findNearestTownRestartLocation(player, targetNpcLoc);
		if (nearestTownLocation == null)
		{
			// Fallback: se não achar restart point, apenas marca radar
			markNpcOnRadar(player, targetNpcLoc);
			player.sendMessage("Siga a indicação da bússola no minimapa até o NPC.");
			return;
		}

		executeTeleportToTown(player, nearestTownLocation, targetNpcLoc, scrollSkillId, scrollSkillLevel, castTimeMs);
	}

	/**
	 * Marca o NPC no Radar (bússola e minimapa) do jogador.
	 */
	public void markNpcOnRadar(Player player, Location targetLoc)
	{
		player.getRadarList().removeAllMarkers();
		player.getRadarList().addMarker(targetLoc.getX(), targetLoc.getY(), targetLoc.getZ());
	}

	/**
	 * Verifica se o jogador e a posição alvo estão na mesma TownZone.
	 */
	private boolean isInSameTown(Player player, Location targetLoc)
	{
		final TownZone playerTown = ZoneManager.getInstance().getZone(player.getX(), player.getY(), player.getZ(), TownZone.class);
		final TownZone targetTown = ZoneManager.getInstance().getZone(targetLoc.getX(), targetLoc.getY(), targetLoc.getZ(), TownZone.class);
		return playerTown != null && targetTown != null && playerTown.getTownId() == targetTown.getTownId();
	}

	/**
	 * Encontra a localização de restart point (cidade/vila) mais próxima da posição do NPC.
	 */
	private Location findNearestTownRestartLocation(Player player, Location targetLoc)
	{
		// 1. Tenta obter o RestartPoint correspondente à coordenada geográfica do NPC
		RestartPoint rp = RestartPointData.getInstance().getRestartPoint(targetLoc);
		if (rp != null)
		{
			if (rp.getBannedRace() == player.getRace() && rp.getBannedPoint() != null)
			{
				RestartPoint alternate = RestartPointData.getInstance().getRestartPointByName(rp.getBannedPoint());
				if (alternate != null)
					rp = alternate;
			}
			return player.getKarma() > 0 ? rp.getRandomChaoPoint() : rp.getRandomPoint();
		}

		// 2. Busca entre todos os RestartPoints carregados o que tiver menor distância euclidiana até o NPC
		Location bestPoint = null;
		long bestDistSq = Long.MAX_VALUE;

		for (RestartPoint point : RestartPointData.getInstance().getRestartPoints())
		{
			Location loc = point.getRandomPoint();
			if (loc == null)
				continue;

			final long dx = loc.getX() - targetLoc.getX();
			final long dy = loc.getY() - targetLoc.getY();
			final long distSq = dx * dx + dy * dy;

			if (distSq < bestDistSq)
			{
				bestDistSq = distSq;
				bestPoint = loc;
			}
		}

		return bestPoint;
	}

	/**
	 * Executa o teleporte com barra de gauge, skill animada e agendamento assíncrono.
	 */
	private void executeTeleportToTown(Player player, Location townLoc, Location targetNpcLoc, int scrollSkillId, int scrollSkillLevel, int castTimeMs)
	{
		player.broadcastPacket(new MagicSkillUse(player, player, scrollSkillId, scrollSkillLevel, castTimeMs, 0));
		player.sendPacket(new SetupGauge(GaugeColor.BLUE, castTimeMs));
		player.sendMessage("Teleportando para a cidade mais próxima do NPC da quest...");

		ThreadPool.schedule(() ->
		{
			if (player == null || !player.isOnline() || player.isDead() || player.isInCombat() || player.isTeleporting())
				return;

			player.teleToLocation(townLoc);

			// Ao chegar na cidade, ativa o radar indicando a direção do NPC e desenha o trajeto luminoso
			ThreadPool.schedule(() ->
			{
				if (player.isOnline() && !player.isDead())
				{
					markNpcOnRadar(player, targetNpcLoc);
					QuestPathVisualizerService.getInstance().renderPathToNpc(player, targetNpcLoc);
					player.sendMessage("Você chegou à cidade! Siga a trilha luminosa no chão e a bússola até o NPC.");
				}
			}, 1000L);

		}, castTimeMs);
	}

	private static double calculateDistance2D(int x1, int y1, int x2, int y2)
	{
		final double dx = (double) x1 - x2;
		final double dy = (double) y1 - y2;
		return Math.sqrt(dx * dx + dy * dy);
	}
}
