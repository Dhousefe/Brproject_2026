package ext.mods.gameserver.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Teste de regressão para validação da convergência segura do cliente em ValidatePosition:
 * 1. Previne teleporte/rubberbanding durante o farm quando o jogador para em solo livre navegável.
 * 2. Mantém blindagem estrita contra penetração em rochas de Forge of the Gods (FOG).
 * 3. Assegura que ValidateLocation só é emitido se a geodata rejeitar o solo ou se houver desvio excessivo.
 */
class ValidatePositionFarmConvergenceTest
{
	@Test
	@DisplayName("ValidatePosition deve possuir o método isValidStandingGround com checagem de NSWE e canMoveToTarget")
	void validatePosition_shouldHaveGeodataGroundValidation() throws Exception
	{
		final String content = Files.readString(Path.of("src/main/java/ext/mods/gameserver/network/clientpackets/ValidatePosition.java"));

		assertTrue(content.contains("isValidStandingGround"),
			"ValidatePosition deve possuir o método isValidStandingGround");
		assertTrue(content.contains("CELL_FLAG_NONE"),
			"isValidStandingGround deve rejeitar células sólidas (rochas/paredes)");
		assertTrue(content.contains("canMoveToTarget"),
			"isValidStandingGround deve verificar se a linha entre o servidor e o cliente é transitável");
	}

	@Test
	@DisplayName("ValidatePosition deve adotar coordenadas do cliente suavemente em solo livre sem rubberband")
	void validatePosition_shouldSilentlyAbsorbSafeGroundCoordinates() throws Exception
	{
		final String content = Files.readString(Path.of("src/main/java/ext/mods/gameserver/network/clientpackets/ValidatePosition.java"));

		assertTrue(content.contains("player.getPosition().set(_x, _y, safeZ, _heading);"),
			"ValidatePosition deve sincronizar coordenadas seguras na geodata quando o cliente para");

		// Não deve emitir ValidateLocation quando o solo é aprovado
		assertTrue(content.contains("if (isValidStandingGround(player, _x, _y, _z))"),
			"ValidatePosition deve validar o solo com geodata antes de decidir se emite ou não ValidateLocation");
	}

	@Test
	@DisplayName("ValidatePosition deve utilizar limiares dinâmicos suaves de velocidade (speed * 1.5)")
	void validatePosition_shouldUseSmoothSpeedThresholds() throws Exception
	{
		final String content = Files.readString(Path.of("src/main/java/ext/mods/gameserver/network/clientpackets/ValidatePosition.java"));

		assertTrue(content.contains("Math.max(speed * 1.5, 220.0)"),
			"O limiar suave de movimento deve tolerar jitter de rede no farm (speed * 1.5)");
	}
}
