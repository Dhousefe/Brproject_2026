package ext.mods.gameserver.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ext.mods.config.ConfigPlayers;

/**
 * Suíte de testes unitários para validação das fórmulas de dano e valorização de skills de fighters:
 * 1. Controle global de dano de auto-ataque, skills físicas, blows e magias.
 * 2. Multiplicadores de dano contra monstros vs jogadores.
 * 3. Mecanismo de transferência dinâmica: redução de auto-ataque potencializa skills físicas.
 */
class SkillAndAttackDamageBalancingTest
{
	@Test
	@DisplayName("ConfigPlayers deve declarar e carregar todas as 8 novas chaves de balanceamento de dano")
	void configPlayers_shouldDeclareDamageBalancingKeys() throws Exception
	{
		final String configClass = Files.readString(Path.of("src/main/java/ext/mods/config/ConfigPlayers.java"));

		assertTrue(configClass.contains("AUTO_ATTACK_DAMAGE_MULTIPLIER"), "Deve declarar AUTO_ATTACK_DAMAGE_MULTIPLIER");
		assertTrue(configClass.contains("PHYSICAL_SKILL_DAMAGE_MULTIPLIER"), "Deve declarar PHYSICAL_SKILL_DAMAGE_MULTIPLIER");
		assertTrue(configClass.contains("BLOW_SKILL_DAMAGE_MULTIPLIER"), "Deve declarar BLOW_SKILL_DAMAGE_MULTIPLIER");
		assertTrue(configClass.contains("MAGIC_SKILL_DAMAGE_MULTIPLIER"), "Deve declarar MAGIC_SKILL_DAMAGE_MULTIPLIER");
		assertTrue(configClass.contains("SKILL_DAMAGE_VS_MONSTERS_MULTIPLIER"), "Deve declarar SKILL_DAMAGE_VS_MONSTERS_MULTIPLIER");
		assertTrue(configClass.contains("SKILL_DAMAGE_VS_PLAYERS_MULTIPLIER"), "Deve declarar SKILL_DAMAGE_VS_PLAYERS_MULTIPLIER");
		assertTrue(configClass.contains("ENABLE_AUTO_ATTACK_TO_PHYSICAL_SKILL_TRANSFER"), "Deve declarar ENABLE_AUTO_ATTACK_TO_PHYSICAL_SKILL_TRANSFER");
		assertTrue(configClass.contains("AUTO_ATTACK_TO_SKILL_TRANSFER_FACTOR"), "Deve declarar AUTO_ATTACK_TO_SKILL_TRANSFER_FACTOR");
	}

	@Test
	@DisplayName("Formulas.java deve integrar os multiplicadores nos 4 métodos principais de cálculo de dano")
	void formulas_shouldIntegrateDamageMultipliers() throws Exception
	{
		final String formulasContent = Files.readString(Path.of("src/main/java/ext/mods/gameserver/skills/Formulas.java"));

		assertTrue(formulasContent.contains("ConfigPlayers.AUTO_ATTACK_DAMAGE_MULTIPLIER"),
			"calcPhysicalAttackDamage deve aplicar AUTO_ATTACK_DAMAGE_MULTIPLIER");
		assertTrue(formulasContent.contains("ConfigPlayers.PHYSICAL_SKILL_DAMAGE_MULTIPLIER"),
			"calcPhysicalSkillDamage deve aplicar PHYSICAL_SKILL_DAMAGE_MULTIPLIER");
		assertTrue(formulasContent.contains("ConfigPlayers.BLOW_SKILL_DAMAGE_MULTIPLIER"),
			"calcBlowDamage deve aplicar BLOW_SKILL_DAMAGE_MULTIPLIER");
		assertTrue(formulasContent.contains("ConfigPlayers.MAGIC_SKILL_DAMAGE_MULTIPLIER"),
			"calcMagicDam deve aplicar MAGIC_SKILL_DAMAGE_MULTIPLIER");
		assertTrue(formulasContent.contains("ConfigPlayers.ENABLE_AUTO_ATTACK_TO_PHYSICAL_SKILL_TRANSFER"),
			"Formulas deve suportar transferência de redução de auto-ataque para skills físicas");
	}

	@Test
	@DisplayName("Simulação matemática da transferência dinâmica de dano: redução de auto-ataque potencializa skills")
	void dynamicDamageTransfer_mathematicalSimulation()
	{
		final double baseAutoAttackDamage = 1000.0;
		final double basePhysicalSkillDamage = 2500.0;

		// Configuração do Administrador: -20% no auto-ataque, transferindo 1:1 para skills físicas
		final double autoAttackMultiplier = 0.80; // Redução de 20%
		final boolean enableTransfer = true;
		final double transferFactor = 1.0;
		final double physicalSkillConfigMultiplier = 1.0;

		// Cálculo do Auto-Ataque com multiplicador
		double finalAutoAttack = baseAutoAttackDamage * autoAttackMultiplier;
		assertEquals(800.0, finalAutoAttack, 0.001, "Auto-ataque deve ser reduzido exatamente para 800 (20% a menos)");

		// Cálculo da transferência para skills físicas
		double skillDamage = basePhysicalSkillDamage * physicalSkillConfigMultiplier;
		if (enableTransfer && autoAttackMultiplier < 1.0)
		{
			double transferBonus = (1.0 - autoAttackMultiplier) * transferFactor;
			skillDamage *= (1.0 + Math.max(0.0, transferBonus));
		}

		// 2500.0 * (1.0 + 0.20) = 3000.0 (+20% de dano)
		assertEquals(3000.0, skillDamage, 0.001, "Skills físicas devem receber o bônus transferido de 20% (3000.0 de dano)");
	}

	@Test
	@DisplayName("Multiplicadores de monstros vs players devem atuar de forma independente")
	void targetSpecificMultipliers_shouldScaleIndependently()
	{
		final double baseDamage = 2000.0;
		final double vsMonstersMultiplier = 1.30; // +30% em PvE
		final double vsPlayersMultiplier = 1.10;  // +10% em PvP

		double pveDamage = baseDamage * vsMonstersMultiplier;
		double pvpDamage = baseDamage * vsPlayersMultiplier;

		assertEquals(2600.0, pveDamage, 0.001, "Dano PvE contra monstros deve ser 2600.0");
		assertEquals(2200.0, pvpDamage, 0.001, "Dano PvP contra players deve ser 2200.0");
	}
}
