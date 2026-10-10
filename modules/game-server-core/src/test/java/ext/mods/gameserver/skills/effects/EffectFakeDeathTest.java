package ext.mods.gameserver.skills.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Constructor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ext.mods.gameserver.enums.skills.EffectType;
import ext.mods.gameserver.model.actor.Creature;
import ext.mods.gameserver.skills.L2Skill;

/**
 * Testes unitários para validar a integridade de {@link EffectFakeDeath} e sua compatibilidade
 * com o carregador de efeitos por reflexão ({@link EffectTemplate}).
 */
class EffectFakeDeathTest
{
	@Test
	@DisplayName("EffectTemplate deve ser capaz de instanciar EffectFakeDeath via reflexão sem ClassNotFoundException")
	void reflectionLookup_shouldSucceed()
	{
		// Act
		Class<?> effectClass = assertDoesNotThrow(() ->
			Class.forName("ext.mods.gameserver.skills.effects.EffectFakeDeath")
		);

		// Assert
		assertNotNull(effectClass);
		sameSuperclass(effectClass);
	}

	@Test
	@DisplayName("EffectFakeDeath deve possuir o construtor padrão esperado por EffectTemplate")
	void constructor_shouldMatchEffectTemplateSignature() throws NoSuchMethodException
	{
		// Act
		Constructor<EffectFakeDeath> constructor = EffectFakeDeath.class.getConstructor(
			EffectTemplate.class,
			L2Skill.class,
			Creature.class,
			Creature.class
		);

		// Assert
		assertNotNull(constructor);
		assertEquals(4, constructor.getParameterCount());
	}

	@Test
	@DisplayName("EffectType do EffectFakeDeath deve ser FAKE_DEATH")
	void effectType_shouldBeFakeDeath()
	{
		assertEquals("FAKE_DEATH", EffectType.FAKE_DEATH.name());
	}

	@Test
	@DisplayName("Disablers deve conter suporte a toggle para desativar Fake Death quando já ativa")
	void disablers_shouldSupportFakeDeathToggle()
	{
		final String disablersContent = assertDoesNotThrow(() ->
			java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/ext/mods/gameserver/handler/skillhandlers/Disablers.java"))
		);
		org.junit.jupiter.api.Assertions.assertTrue(disablersContent.contains("case FAKE_DEATH:"), "Disablers deve possuir case FAKE_DEATH");
		org.junit.jupiter.api.Assertions.assertTrue(disablersContent.contains("effect.exit();"), "Disablers deve chamar effect.exit() para desativar toggle de Fake Death");
	}

	@Test
	@DisplayName("Player.stopFakeDeath deve transmitir Revive, ChangeWaitType e UserInfo para levantar o personagem")
	void player_stopFakeDeathShouldBroadcastReviveAndChangeWaitType()
	{
		final String playerContent = assertDoesNotThrow(() ->
			java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/ext/mods/gameserver/model/actor/Player.java"))
		);
		org.junit.jupiter.api.Assertions.assertTrue(playerContent.contains("broadcastPacket(new Revive(this));"), "Player deve transmitir Revive");
		org.junit.jupiter.api.Assertions.assertTrue(playerContent.contains("ChangeWaitType.WT_STOP_FAKEDEATH"), "Player deve transmitir WT_STOP_FAKEDEATH");
		org.junit.jupiter.api.Assertions.assertTrue(playerContent.contains("broadcastUserInfo();"), "Player deve transmitir UserInfo");
	}

	@Test
	@DisplayName("MoveBackwardToLocation e AttackRequest devem quebrar Fake Death ao se movimentar ou atacar")
	void moveAndAttack_shouldBreakFakeDeath()
	{
		final String moveContent = assertDoesNotThrow(() ->
			java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/ext/mods/gameserver/network/clientpackets/MoveBackwardToLocation.java"))
		);
		org.junit.jupiter.api.Assertions.assertTrue(moveContent.contains("if (player.isFakeDeath())"), "MoveBackwardToLocation deve verificar isFakeDeath");
		org.junit.jupiter.api.Assertions.assertTrue(moveContent.contains("player.stopFakeDeath(true);"), "MoveBackwardToLocation deve quebrar Fake Death");

		final String attackContent = assertDoesNotThrow(() ->
			java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/ext/mods/gameserver/network/clientpackets/AttackRequest.java"))
		);
		org.junit.jupiter.api.Assertions.assertTrue(attackContent.contains("if (player.isFakeDeath())"), "AttackRequest deve verificar isFakeDeath");
		org.junit.jupiter.api.Assertions.assertTrue(attackContent.contains("player.stopFakeDeath(true);"), "AttackRequest deve quebrar Fake Death");
	}

	private static void sameSuperclass(Class<?> effectClass)
	{
		assertSame(ext.mods.gameserver.skills.AbstractEffect.class, effectClass.getSuperclass());
	}

	private static <T> T assertDoesNotThrow(ThrowingSupplier<T> supplier)
	{
		try
		{
			return supplier.get();
		}
		catch (Throwable t)
		{
			throw new AssertionError("Esperado que não lançasse exceção, mas lançou: " + t.getMessage(), t);
		}
	}

	@FunctionalInterface
	private interface ThrowingSupplier<T>
	{
		T get() throws Throwable;
	}
}
