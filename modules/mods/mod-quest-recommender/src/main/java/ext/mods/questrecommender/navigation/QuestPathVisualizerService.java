package ext.mods.questrecommender.navigation;

import java.awt.Color;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import ext.mods.commons.logging.CLogger;
import ext.mods.commons.math.MathUtil;
import ext.mods.commons.pool.ThreadPool;
import ext.mods.gameserver.geoengine.GeoEngine;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.location.Location;
import ext.mods.gameserver.network.serverpackets.ExServerPrimitive;

/**
 * Serviço de visualização de caminho vetorial até o NPC utilizando ExServerPrimitive.
 *
 * Características:
 * - Calcula a rota via GeoEngine.findPath assincronamente (evita bloquear threads de rede).
 * - Renderiza linha de caminho contínua e setas direcionais (chevrons) orientadas pelo vetor de movimento.
 * - Limpeza automática ao expirar a duração configurada ou ao substituir a rota.
 * - Mechanical Sympathy: reaproveitamento de cálculos trigonométricos primitivos e decimação de nós colineares.
 */
public final class QuestPathVisualizerService
{
	private static final CLogger LOGGER = new CLogger(QuestPathVisualizerService.class.getName());

	private static final String PRIMITIVE_NAME = "QuestRecPath";
	private static final Color PATH_COLOR = new Color(0, 220, 255); // Ciano brilhante para linha
	private static final Color ARROW_COLOR = new Color(255, 215, 0); // Dourado para as setas
	private static final Color TARGET_COLOR = new Color(50, 255, 50); // Verde brilhante para o ponto do NPC

	private static final double ARROW_HEAD_LENGTH = 70.0;
	private static final double ARROW_ANGLE_RAD = Math.toRadians(30.0); // 30 graus de abertura

	private static final QuestPathVisualizerService INSTANCE = new QuestPathVisualizerService();

	private final Map<Integer, ScheduledFuture<?>> _activeTasks = new ConcurrentHashMap<>();

	private boolean _visualizerEnabled = true;
	private int _pathDurationSec = 60;
	private int _arrowSpacing = 450;

	private QuestPathVisualizerService()
	{
	}

	public static QuestPathVisualizerService getInstance()
	{
		return INSTANCE;
	}

	public void configure(boolean enabled, int durationSec, int arrowSpacing)
	{
		_visualizerEnabled = enabled;
		_pathDurationSec = Math.max(10, durationSec);
		_arrowSpacing = Math.max(200, arrowSpacing);
	}

	/**
	 * Calcula e renderiza a rota do jogador até o NPC de forma assíncrona.
	 */
	public void renderPathToNpc(Player player, Location targetLoc)
	{
		if (!_visualizerEnabled || player == null || !player.isOnline() || targetLoc == null)
			return;

		final int playerObjId = player.getObjectId();

		// Cancela eventual agendamento de limpeza anterior
		cancelScheduledCleanup(playerObjId);

		ThreadPool.schedule(() ->
		{
			if (player == null || !player.isOnline() || player.isDead())
				return;

			final int ox = player.getX();
			final int oy = player.getY();
			final int oz = player.getZ();

			final int tx = targetLoc.getX();
			final int ty = targetLoc.getY();
			final int tz = targetLoc.getZ();

			final GeoEngine geo = GeoEngine.getInstance();
			List<Location> rawPath;
			try
			{
				rawPath = geo.findPathBidirectional(ox, oy, oz, tx, ty, tz, null);
				if (rawPath == null || rawPath.isEmpty())
				{
					rawPath = geo.findPath(ox, oy, oz, tx, ty, tz, true, null);
				}
			}
			catch (Throwable t)
			{
				LOGGER.warn("Falha ao calcular caminho para Quest NPC via GeoEngine: {}", t.getMessage());
				rawPath = Collections.emptyList();
			}

			// Se geodata não retornar caminho completo, busca o ponto válido mais distante em direção ao NPC sem atravessar paredes
			if (rawPath == null || rawPath.isEmpty())
			{
				final Location validExit = geo.getValidLocation(ox, oy, oz, tx, ty, tz, null);
				if (validExit != null && (validExit.getX() != ox || validExit.getY() != oy))
				{
					rawPath = List.of(new Location(ox, oy, oz), validExit);
				}
				else
				{
					rawPath = List.of(new Location(ox, oy, oz));
				}
			}

			// Otimização: simplificar nós colineares para obter trajetos retos limpos
			final List<Location> path = simplifyPath(rawPath);

			// Obtém o ExServerPrimitive oficial do jogador vinculado à âncora de entrada de mundo (_enterWorld)
			final ExServerPrimitive packet = player.getDebugPacket(PRIMITIVE_NAME);
			packet.reset();

			// Ponto final de destaque sobre o NPC com círculo visual
			packet.addPoint("NPC da Quest", TARGET_COLOR, true, tx, ty, tz + 45);
			drawTargetMarker(packet, tx, ty, tz + 25);

			// Renderiza uma trilha elegante de SETAS DIRECIONAIS ao invés de linhas contínuas
			// Passo constante ao longo dos segmentos para gerar chevrons orientados no chão
			final double stepDistance = 90.0;
			int prevX = ox;
			int prevY = oy;
			int prevZ = oz;

			int totalArrows = 0;
			final int maxArrows = 120; // Limite de segurança de primitivos para o cliente Interlude

			for (Location node : path)
			{
				final int currX = node.getX();
				final int currY = node.getY();
				final int currZ = node.getZ();

				final double segDx = (double) currX - prevX;
				final double segDy = (double) currY - prevY;
				final double segLen = Math.sqrt(segDx * segDx + segDy * segDy);

				if (segLen > 20.0)
				{
					final double headingRad = Math.atan2(segDy, segDx);
					final int steps = (int) Math.max(1, Math.round(segLen / stepDistance));
					final double dxStep = segDx / steps;
					final double dyStep = segDy / steps;

					for (int s = 1; s <= steps && totalArrows < maxArrows; s++)
					{
						final int ax = (int) Math.round(prevX + dxStep * s);
						final int ay = (int) Math.round(prevY + dyStep * s);

						// Projeta o Z da seta exatamente na altura do solo pelo geodata
						int groundZ;
						try
						{
							groundZ = geo.getHeight(ax, ay, prevZ);
						}
						catch (Throwable e)
						{
							groundZ = prevZ;
						}

						// Valida se o ponto intermediário é navegável (sem colisão contra paredes/balcões)
						final int gx = GeoEngine.getGeoX(ax);
						final int gy = GeoEngine.getGeoY(ay);
						if (geo.hasGeoPos(gx, gy))
						{
							final byte nswe = geo.getNsweNearest(gx, gy, groundZ);
							if (nswe == 0) // GeoStructure.CELL_FLAG_NONE = 0
								continue;
						}

						final int az = groundZ + 24; // Leve elevação para visualização nítida acima do piso
						drawDirectionalArrow(packet, ax, ay, az, headingRad);
						totalArrows++;
					}
				}

				prevX = currX;
				prevY = currY;
				prevZ = currZ;

				if (totalArrows >= maxArrows)
					break;
			}

			// Seta final orientada ao NPC se estiver em linha de visão navegável
			final double finalDx = (double) tx - prevX;
			final double finalDy = (double) ty - prevY;
			final double finalLen = Math.sqrt(finalDx * finalDx + finalDy * finalDy);
			if (finalLen > 15.0 && geo.canMove(prevX, prevY, prevZ, tx, ty, tz, null))
			{
				final double finalHeading = Math.atan2(finalDy, finalDx);
				int groundTz;
				try
				{
					groundTz = geo.getHeight(tx, ty, tz);
				}
				catch (Throwable e)
				{
					groundTz = tz;
				}
				drawDirectionalArrow(packet, tx, ty, groundTz + 24, finalHeading);
			}

			// Envio atômico com suporte a pacotes encadeados nativo de ExServerPrimitive
			packet.sendTo(player);

			// Agenda a limpeza automática da rota
			final ScheduledFuture<?> task = ThreadPool.schedule(() ->
			{
				clearVisualPath(player);
				_activeTasks.remove(playerObjId);
			}, _pathDurationSec * 1000L);

			_activeTasks.put(playerObjId, task);

		}, 0L);
	}

	/**
	 * Remove a linha e pontos visuais de rota do cliente enviando um pacote com mesmo nome vazio.
	 */
	public void clearVisualPath(Player player)
	{
		if (player == null || !player.isOnline())
			return;

		cancelScheduledCleanup(player.getObjectId());

		try
		{
			final ExServerPrimitive clearPacket = player.getDebugPacket(PRIMITIVE_NAME);
			clearPacket.reset();
			clearPacket.sendTo(player);
		}
		catch (Throwable t)
		{
			// Ignora falhas se jogador estiver desconectando
		}
	}

	private void cancelScheduledCleanup(int playerObjId)
	{
		final ScheduledFuture<?> previous = _activeTasks.remove(playerObjId);
		if (previous != null)
		{
			previous.cancel(false);
		}
	}

	/**
	 * Desenha uma seta direcional (chevron com espinha central e duas hastes) orientada pelo vetor de movimento.
	 */
	private static void drawDirectionalArrow(ExServerPrimitive packet, int tipX, int tipY, int tipZ, double angleRad)
	{
		final double leftAngle = angleRad + Math.PI - ARROW_ANGLE_RAD;
		final double rightAngle = angleRad + Math.PI + ARROW_ANGLE_RAD;

		final int leftX = (int) Math.round(tipX + Math.cos(leftAngle) * ARROW_HEAD_LENGTH);
		final int leftY = (int) Math.round(tipY + Math.sin(leftAngle) * ARROW_HEAD_LENGTH);

		final int rightX = (int) Math.round(tipX + Math.cos(rightAngle) * ARROW_HEAD_LENGTH);
		final int rightY = (int) Math.round(tipY + Math.sin(rightAngle) * ARROW_HEAD_LENGTH);

		// Espinha central curta da seta para corpo nítido
		final double backAngle = angleRad + Math.PI;
		final int tailX = (int) Math.round(tipX + Math.cos(backAngle) * (ARROW_HEAD_LENGTH * 0.7));
		final int tailY = (int) Math.round(tipY + Math.sin(backAngle) * (ARROW_HEAD_LENGTH * 0.7));

		packet.addLine(ARROW_COLOR, tailX, tailY, tipZ, tipX, tipY, tipZ);
		// Asas do chevron
		packet.addLine(ARROW_COLOR, tipX, tipY, tipZ, leftX, leftY, tipZ);
		packet.addLine(ARROW_COLOR, tipX, tipY, tipZ, rightX, rightY, tipZ);
	}

	/**
	 * Marcador no chão ao redor do NPC para sinalização elegante de chegada.
	 */
	private static void drawTargetMarker(ExServerPrimitive packet, int cx, int cy, int cz)
	{
		final int radius = 40;
		final int segments = 8;
		for (int i = 0; i < segments; i++)
		{
			final double a1 = (2 * Math.PI * i) / segments;
			final double a2 = (2 * Math.PI * (i + 1)) / segments;
			final int x1 = (int) Math.round(cx + Math.cos(a1) * radius);
			final int y1 = (int) Math.round(cy + Math.sin(a1) * radius);
			final int x2 = (int) Math.round(cx + Math.cos(a2) * radius);
			final int y2 = (int) Math.round(cy + Math.sin(a2) * radius);
			packet.addLine(TARGET_COLOR, x1, y1, cz, x2, y2, cz);
		}
	}

	/**
	 * Simplifica caminhos colineares somente quando houver linha de visão/caminhada direta desobstruída (canMove).
	 * Isso previne atalhos que cortem quinas de paredes ou balcões.
	 */
	private static List<Location> simplifyPath(List<Location> fullPath)
	{
		if (fullPath == null || fullPath.size() <= 2)
			return fullPath;

		final java.util.ArrayList<Location> simplified = new java.util.ArrayList<>(fullPath.size());
		simplified.add(fullPath.get(0));

		final GeoEngine geo = GeoEngine.getInstance();
		int anchorIdx = 0;

		for (int i = 1; i < fullPath.size() - 1; i++)
		{
			final Location anchor = fullPath.get(anchorIdx);
			final Location next = fullPath.get(i + 1);

			// Testa se é possível mover em linha reta do anchor até o next sem colidir em obstáculos
			final boolean canWalkDirect = geo.canMove(
				anchor.getX(), anchor.getY(), anchor.getZ(),
				next.getX(), next.getY(), next.getZ(),
				null
			);

			// Se houver obstáculo entre o anchor e next, o nó atual (i) é um waypoint indispensável de contorno!
			if (!canWalkDirect)
			{
				simplified.add(fullPath.get(i));
				anchorIdx = i;
			}
		}

		simplified.add(fullPath.get(fullPath.size() - 1));
		return simplified;
	}
}
