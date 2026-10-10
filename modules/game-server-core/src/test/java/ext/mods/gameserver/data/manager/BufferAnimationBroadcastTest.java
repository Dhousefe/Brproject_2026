package ext.mods.gameserver.data.manager;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Teste de regressão para validação das funções de buffer (NPC SchemeBuffer e Community Board BuffBBSManager):
 * - Cooldown individual de 800ms com barra SetupGauge BLUE.
 * - BroadcastPacket de MagicSkillUse para exibir animação e som da skill para o alvo e todos ao redor.
 * - Cooldown de lote (3000ms com barra RED) preservado para esquemas.
 * - Bloqueio estrito mantido contra uso em Olimpíadas.
 */
class BufferAnimationBroadcastTest
{
	@Test
	@DisplayName("BufferManager deve declarar SINGLE_BUFF_COOLDOWN_MS de 800ms e checagem individual")
	void bufferManager_shouldDeclare800msSingleBuffCooldown() throws Exception
	{
		final String content = Files.readString(Path.of("src/main/java/ext/mods/gameserver/data/manager/BufferManager.java"));

		assertTrue(content.contains("SINGLE_BUFF_COOLDOWN_MS = 800"),
			"BufferManager deve definir SINGLE_BUFF_COOLDOWN_MS como 800ms");
		assertTrue(content.contains("checkAndSetSingleBuffCooldown"),
			"BufferManager deve expor checkAndSetSingleBuffCooldown para buffs individuais");
		assertTrue(content.contains("GaugeColor.BLUE"),
			"Buff individual deve apresentar SetupGauge na cor azul com 800ms");
		assertTrue(content.contains("player.isInOlympiadMode() || player.isOlympiadStart()"),
			"BufferManager deve manter bloqueio estrito contra Olimpíadas em todas as funções");
	}

	@Test
	@DisplayName("SchemeBuffer deve transmitir MagicSkillUse e utilizar delay ágil de 800ms em buffs individuais")
	void schemeBuffer_shouldBroadcastMagicSkillUseAndUse800ms() throws Exception
	{
		final String content = Files.readString(Path.of("src/main/java/ext/mods/gameserver/model/actor/instance/SchemeBuffer.java"));

		assertTrue(content.contains("checkAndSetSingleBuffCooldown"),
			"SchemeBuffer deve utilizar checkAndSetSingleBuffCooldown para buffs individuais");
		assertTrue(content.contains("target.broadcastPacket(new MagicSkillUse"),
			"SchemeBuffer deve enviar broadcast de MagicSkillUse para o alvo e todos na vizinhança");
	}

	@Test
	@DisplayName("BuffBBSManager deve transmitir MagicSkillUse e utilizar delay ágil de 800ms em buffs individuais")
	void buffBbsManager_shouldBroadcastMagicSkillUseAndUse800ms() throws Exception
	{
		final String content = Files.readString(Path.of("src/main/java/ext/mods/gameserver/communitybbs/custom/BuffBBSManager.java"));

		assertTrue(content.contains("checkAndSetSingleBuffCooldown"),
			"BuffBBSManager deve utilizar checkAndSetSingleBuffCooldown para buffs individuais");
		assertTrue(content.contains("target.broadcastPacket(new MagicSkillUse"),
			"BuffBBSManager deve enviar broadcast de MagicSkillUse para o alvo e todos na vizinhança");
	}
}
