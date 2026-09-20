package ext.mods.questrecommender.navigation;

import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.location.Location;

/**
 * Serviço de navegação de quests.<br>
 * <br>
 * <b>Nota Arquitetural:</b><br>
 * A emissão do pacote de debug {@code ExServerPrimitive} foi descontinuada para evitar
 * sobrecarga de pacotes e estouro de buffer (STATUS_STACK_BUFFER_OVERRUN) no cliente Fermata
 * e outros clientes modernos. A orientação ao jogador é realizada nativamente através da
 * bússola/radar do Lineage 2 (RadarControl).
 */
public final class QuestPathVisualizerService
{
	private static final QuestPathVisualizerService INSTANCE = new QuestPathVisualizerService();

	private QuestPathVisualizerService()
	{
	}

	public static QuestPathVisualizerService getInstance()
	{
		return INSTANCE;
	}

	public void configure(boolean enabled, int durationSec, int arrowSpacing)
	{
		// No-op: visualizador vetorial de debug ExServerPrimitive descontinuado
	}

	/**
	 * Guia o jogador até o NPC (a marcação no radar/bússola é feita pelo QuestNavigationService).
	 */
	public void renderPathToNpc(Player player, Location targetLoc)
	{
		// No-op: navegação orientada nativamente via radar do cliente oficial/Fermata
	}

	/**
	 * Limpeza de rota visual.
	 */
	public void clearVisualPath(Player player)
	{
		// No-op
	}
}
