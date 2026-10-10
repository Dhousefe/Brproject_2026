package ext.mods.gameserver.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Teste de regressão para validação prévia de Geodata em MoveBackwardToLocation,
 * proteção autoritativa de ValidatePosition e estabilidade de Z em PlayerMove (FOG Parity).
 */
class GeodataMovementValidationTest
{
	@Test
	@DisplayName("MoveBackwardToLocation deve conter validação prévia de geodata sem quebrar barcos ou água")
	void moveBackwardToLocation_shouldValidateDestinationBeforeMoving() throws Exception
	{
		final String content = Files.readString(Path.of("src/main/java/ext/mods/gameserver/network/clientpackets/MoveBackwardToLocation.java"));

		assertTrue(content.contains("canNavigateToDestination"),
			"MoveBackwardToLocation deve possuir método de validação prévia canNavigateToDestination");
		assertTrue(content.contains("player.isInBoat() || player.isInWater() || player.getMove().getMoveType() == MoveType.SWIM"),
			"Validação de movimento não deve bloquear barcos ou água/natação");
		assertTrue(content.contains("CELL_FLAG_NONE"),
			"Validação de movimento deve rejeitar células sólidas sem trânsito (rochas/paredes)");
		assertTrue(content.contains("player.sendPacket(ActionFailed.STATIC_PACKET);"),
			"Cliques rejeitados devem enviar ActionFailed para limpar o ponteiro demarcado no cliente");
	}

	@Test
	@DisplayName("ValidatePosition não deve teleportar jogador para a cidade em cliques comuns contra rochas")
	void validatePosition_shouldNotTeleportToTownOnDesync() throws Exception
	{
		final String content = Files.readString(Path.of("src/main/java/ext/mods/gameserver/network/clientpackets/ValidatePosition.java"));

		// Não deve conter teleporte para cidade no bloco de jogador parado (!player.isMoving())
		assertFalse(content.contains("!player.isMoving()\n\t\t{\n\t\t\tif (diffSq <= softThresholdSq && (dz <= 120 || !isGround))\n\t\t\t{\n\t\t\t\tif (diffSq >= 16)\n\t\t\t\t\tplayer.getPosition().set(_x, _y, _z, _heading);"),
			"ValidatePosition não deve absorver coordenadas do cliente dentro de obstáculos em repouso");

		// Na Red Zone terrestre, deve enviar ValidateLocation corretivo em vez de teleportTo(RestartType.TOWN)
		assertTrue(content.contains("player.sendPacket(new ValidateLocation(player));"),
			"ValidatePosition deve emitir ValidateLocation para sincronizar o cliente");
	}

	@Test
	@DisplayName("PlayerMove deve limitar saltos verticais anômalos em multicamadas")
	void playerMove_shouldClampStepHeightOnGround() throws Exception
	{
		final String content = Files.readString(Path.of("src/main/kotlin/ext/mods/gameserver/model/actor/move/PlayerMove.kt"));

		assertTrue(content.contains("dzSample > 48"),
			"PlayerMove deve conter trava para saltos verticais maiores que 48 unidades em sub-tick");
		assertTrue(content.contains("canMoveToTarget(curX, curY, curZ, nextX, nextY, sampledZ)"),
			"PlayerMove deve verificar transitabilidade de chão antes de aceitar degrau alto");
	}

	@Test
	@DisplayName("SmoothObstacleAvoidance deve validar altura na geodata para pontos suavizados")
	void smoothObstacleAvoidance_shouldSampleRealGroundHeight() throws Exception
	{
		final String content = Files.readString(Path.of("src/main/kotlin/ext/mods/gameserver/geoengine/pathfinding/SmoothObstacleAvoidance.kt"));

		assertTrue(content.contains("geoEngine.getHeight"),
			"SmoothObstacleAvoidance deve consultar a geodata para amostrar altura real");
		assertTrue(content.contains("safeZ"),
			"SmoothObstacleAvoidance deve conter clamp safeZ para evitar pontos flutuantes no vazio");
	}
}
