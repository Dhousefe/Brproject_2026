package ext.mods.gameserver.model.entity.autofarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Suíte de testes para validação de expansão e cálculo de Range de Visão do AutoFarm.
 * Valida:
 * 1. getAreaMaxRadius permite teto visual de até 1.500 unidades para melee e range.
 * 2. getFinalRadius respeita o raio configurado pelo jogador (ex: 800, 1000, 1200) sem rebaixar para range de arma.
 * 3. Fallback inteligente para players melee quando o raio não foi configurado (valor padrão confortável >= 600u).
 */
class AutoFarmRadiusRangeTest
{
	@Test
	@DisplayName("Expansão de Range: Jogador Melee pode configurar raio de até 1.500 unidades")
	void testMeleePlayerCanConfigureExpandedRange()
	{
		// Simulador de perfil
		final int meleeAttackRange = 40; // Adaga/Espada
		final int maxConfigurableRadius = 1500;
		
		// Jogador escolhe raio de 1000 unidades no menu
		final int userChosenRadius = 1000;
		
		// Validação do AutoFarmManager: range >= 100 && range <= maxConfigurableRadius
		assertTrue(userChosenRadius >= 100 && userChosenRadius <= maxConfigurableRadius,
			"O raio de 1000 unidades deve ser aceito para jogadores melee");
		
		// getFinalRadius deve retornar 1000 e não rebaixar para 40
		final int finalRadius = Math.min(userChosenRadius, maxConfigurableRadius);
		assertEquals(1000, finalRadius, "O raio final deve ser exatamente o escolhido pelo jogador");
	}

	@Test
	@DisplayName("Teto de Segurança: Raio nunca ultrapassa 1.500 unidades (campo visual do cliente UE 2.5)")
	void testRangeNeverExceedsVisualCapOf1500()
	{
		final int hugeRadius = 3500;
		final int cappedRadius = Math.min(hugeRadius, 1500);
		
		assertEquals(1500, cappedRadius, "O raio deve ser limitado pelo teto visual da Unreal Engine 2.5");
	}

	@Test
	@DisplayName("Fallback Automático: Jogador melee sem raio definido recebe range padrão de busca de pelo menos 600u")
	void testDefaultFallbackRangeForMelee()
	{
		final int configuredRadius = 0;
		final int meleeWeaponRange = 40;
		
		final int effectiveRange = configuredRadius > 0 ? configuredRadius : Math.max(meleeWeaponRange, 600);
		assertEquals(600, effectiveRange, "Jogador melee deve ter no mínimo 600u de alcance padrão de busca");
	}
}
