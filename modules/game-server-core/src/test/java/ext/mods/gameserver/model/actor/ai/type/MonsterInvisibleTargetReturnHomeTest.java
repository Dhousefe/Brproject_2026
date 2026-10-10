package ext.mods.gameserver.model.actor.ai.type;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Teste de regressão para validação da perda de alvo de monstros quando um jogador/GM fica invisível,
 * eliminação de loops infinitos de 100ms no ThreadPool e retorno garantido ao ponto de spawn (returnHome).
 */
class MonsterInvisibleTargetReturnHomeTest
{
	@Test
	@DisplayName("NpcAI.thinkAttack deve detectar invisibilidade de jogador e abortar loop de perseguição")
	void npcAi_shouldDetectInvisiblePlayerAndReturnHome() throws Exception
	{
		final String content = Files.readString(Path.of("src/main/java/ext/mods/gameserver/model/actor/ai/type/NpcAI.java"));

		assertTrue(content.contains("target instanceof Player p && !p.getAppearance().isVisible()"),
			"NpcAI deve testar explicitamente se a aparência do jogador é invisível");
		assertTrue(content.contains("attackable.returnHome();"),
			"NpcAI deve acionar returnHome() ao detectar perda de alvo/alvo invisível");
		assertTrue(content.contains("attackable.getAI().getHateList().clear();"),
			"NpcAI deve limpar a HateList para liberar o monstro a retornar para casa");
	}

	@Test
	@DisplayName("NpcAI não deve re-agendar loop de 100ms se o alvo for invisível fora da knownlist")
	void npcAi_shouldNotScheduleLoopIfTargetIsInvisible() throws Exception
	{
		final String content = Files.readString(Path.of("src/main/java/ext/mods/gameserver/model/actor/ai/type/NpcAI.java"));

		assertTrue(content.contains("if (target instanceof Player p && !p.getAppearance().isVisible())"),
			"Ao constatar !knows(target), NpcAI deve checar se o alvo é jogador invisível antes de agendar o loop");
	}

	@Test
	@DisplayName("AiTaskManager deve processar monstros engajados em combate mesmo sem outros players visíveis")
	void aiTaskManager_shouldProcessEngagedMonstersEvenIfTargetVanished() throws Exception
	{
		final String content = Files.readString(Path.of("src/main/java/ext/mods/gameserver/taskmanager/AiTaskManager.java"));

		assertTrue(content.contains("final boolean isEngaged = currentIntention == IntentionType.ATTACK || currentIntention == IntentionType.CAST;"),
			"AiTaskManager deve identificar se o NPC está engajado em combate");
		assertTrue(content.contains("if (!isEngaged && !hasVisiblePlayersNearNpc(npc))"),
			"A otimização de CPU só pode pausar a IA se o NPC NÃO estiver engajado em combate");
	}

	@Test
	@DisplayName("AdminEffects.admin_hide deve limpar o aggro e comandar returnHome nos monstros vizinhos")
	void adminEffects_shouldClearAggroAndReturnHomeOnHide() throws Exception
	{
		final String content = Files.readString(Path.of("src/main/java/ext/mods/gameserver/handler/admincommandhandlers/AdminEffects.java"));

		assertTrue(content.contains("player.forEachKnownTypeInRadius(Attackable.class, 2500, attackable ->"),
			"admin_hide deve iterar sobre monstros conhecidos na vizinhança");
		assertTrue(content.contains("attackable.getAI().setBackToPeace();"),
			"admin_hide deve colocar os monstros em postura pacífica (setBackToPeace)");
		assertTrue(content.contains("attackable.returnHome();"),
			"admin_hide deve comandar retorno ao spawn para os monstros");
	}

	@Test
	@DisplayName("CreatureMove deve cancelar perseguição e retornar ao spawn se o alvo ficar invisível")
	void creatureMove_shouldCancelFollowIfTargetBecomesInvisible() throws Exception
	{
		final String content = Files.readString(Path.of("src/main/kotlin/ext/mods/gameserver/model/actor/move/CreatureMove.kt"));

		assertTrue(content.contains("target is Player && !target.appearance.isVisible"),
			"CreatureMove deve verificar se o alvo de perseguição é jogador invisível");
		assertTrue(content.contains("(_actor as Attackable).returnHome()"),
			"CreatureMove deve acionar returnHome() se o alvo de perseguição for perdido");
	}
}
