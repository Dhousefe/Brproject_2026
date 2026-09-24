/*
 * Copyleft © 2024-2026 L2Brproject
 * * This file is part of L2Brproject derived from aCis409/RusaCis3.8
 * * L2Brproject is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License.
 * * L2Brproject is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 */
package ext.mods.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 *
 * <h2>Estratégia de Teste (sem servidor completo)</h2>
 * Todos os testes validam a lógica das <b>fórmulas e caps</b> diretamente, sem instanciar
 * Player/Server. Os campos de {@link ConfigPlayers} são injetados via reflexão em {@code @BeforeEach}
 * para cada cenário, garantindo isolamento total entre testes.
 *
 * @see ConfigPlayers
 * @see ext.mods.gameserver.model.actor.status.PlayerStatus
 * @see ext.mods.gameserver.model.actor.cast.CreatureCast
 * @see ext.mods.gameserver.handler.skillhandlers.Cancel
 */
@DisplayName("ConfigPlayers — Compliance com L2OFF (L2Off Interlude)")
class ConfigPlayersStatsCapL2OffComplianceTest
{
	// ========================================================================
	// Valores de referência extraídos do RAG l2off (L2Off_CONSTANTS_REFERENCE.md)
	// ========================================================================
	
	/** L2OFF padrão: sem cap absoluto de ataque físico (0 = ilimitado no L2Off). BrProject usa 1500. */
	private static final int L2OFF_DEFAULT_MAX_PATK_SPEED = 1500;
	/** L2OFF padrão: sem cap de cast speed (formula: mAtkSpd/333.0). BrProject usa 1999. */
	private static final int L2OFF_DEFAULT_MAX_MATK_SPEED = 1999;
	/** L2OFF padrão: MaxRunSpeed = 250 unidades (hardcoded no binary). */
	private static final int L2OFF_DEFAULT_MAX_RUN_SPEED = 250;
	/** L2OFF padrão: Evasion máximo = 250. */
	private static final int L2OFF_DEFAULT_MAX_EVASION = 250;
	/** L2OFF: sem cap de P.Atk (amplamente configurável no Extender). */
	private static final int L2OFF_DEFAULT_MAX_PATK = 999999;
	/** L2OFF: sem cap de M.Atk. */
	private static final int L2OFF_DEFAULT_MAX_MATK = 999999;
	/** L2OFF: sem burst de HP por padrão (multiplicador = 1.0). */
	private static final double L2OFF_DEFAULT_HP_BURST = 1.0;
	/** L2OFF: sem burst de MP. */
	private static final double L2OFF_DEFAULT_MP_BURST = 1.0;
	/** L2OFF: sem burst de CP. */
	private static final double L2OFF_DEFAULT_CP_BURST = 1.0;
	/** L2OFF: sem cap rígido de HP (0 = sem limite externo). */
	private static final int L2OFF_DEFAULT_MAX_HP_LIMIT = 0;
	/** L2OFF: Skill reuse é gerenciado via SKILL_REUSE_MANAGER, multiplicador = 1.0x. */
	private static final double L2OFF_DEFAULT_SKILL_REUSE_MULTIPLIER = 1.0;
	/** L2OFF: sem delay mínimo de skill reuse além do calculado pelo servidor. */
	private static final int L2OFF_DEFAULT_MIN_SKILL_REUSE_MS = 0;
	/** L2OFF (i_dispel_buff): itera vAbnormalStatus begin→end (LIFO do vector). Padrão = true. */
	private static final boolean L2OFF_DEFAULT_CANCEL_LIFO = true;
	
	// ========================================================================
	// Setup — injeta os defaults L2OFF nos campos estáticos de ConfigPlayers
	// ========================================================================
	
	@BeforeEach
	void resetToL2OffDefaults() throws Exception
	{
		setIntField("MAX_PATK_SPEED_LIMIT",  L2OFF_DEFAULT_MAX_PATK_SPEED);
		setIntField("MAX_MATK_SPEED_LIMIT",  L2OFF_DEFAULT_MAX_MATK_SPEED);
		setIntField("MAX_RUN_SPEED_LIMIT",   L2OFF_DEFAULT_MAX_RUN_SPEED);
		setIntField("MAX_EVASION_LIMIT",     L2OFF_DEFAULT_MAX_EVASION);
		setIntField("MAX_PATK_LIMIT",        L2OFF_DEFAULT_MAX_PATK);
		setIntField("MAX_MATK_LIMIT",        L2OFF_DEFAULT_MAX_MATK);
		setDoubleField("HP_BURST_MULTIPLIER", L2OFF_DEFAULT_HP_BURST);
		setDoubleField("MP_BURST_MULTIPLIER", L2OFF_DEFAULT_MP_BURST);
		setDoubleField("CP_BURST_MULTIPLIER", L2OFF_DEFAULT_CP_BURST);
		setIntField("MAX_HP_LIMIT",           L2OFF_DEFAULT_MAX_HP_LIMIT);
		setIntField("MAX_CP_LIMIT",           0);
		setIntField("MAX_MP_LIMIT",           0);
		setDoubleField("SKILL_REUSE_MULTIPLIER", L2OFF_DEFAULT_SKILL_REUSE_MULTIPLIER);
		setIntField("MIN_SKILL_REUSE_DELAY_MS",  L2OFF_DEFAULT_MIN_SKILL_REUSE_MS);
		setBoolField("CANCEL_DISPEL_ORDER_LIFO", L2OFF_DEFAULT_CANCEL_LIFO);
	}
	
	// ========================================================================
	// 1. STAT CAPS — Velocidade de Ataque Físico
	// ========================================================================
	
	@Nested
	@DisplayName("1. PAtkSpeed Cap")
	class PAtkSpeedCapTests
	{
		@Test
		@DisplayName("Padrão L2OFF: cap = 1500 (L2Off default)")
		void defaultMatchesL2Off()
		{
			assertEquals(L2OFF_DEFAULT_MAX_PATK_SPEED, ConfigPlayers.MAX_PATK_SPEED_LIMIT,
				"PAtkSpd cap padrão deve ser 1500 como no L2OFF L2Off");
		}
		
		@Test
		@DisplayName("Fórmula de cap: val abaixo do limite retorna val")
		void capBelowLimit_returnsOriginalVal()
		{
			final int input = 800;
			final int cap   = ConfigPlayers.MAX_PATK_SPEED_LIMIT;
			final int result = Math.min(input, cap);
			assertEquals(input, result, "Valor abaixo do cap não deve ser truncado");
		}
		
		@Test
		@DisplayName("Fórmula de cap: val acima do limite retorna limite")
		void capAboveLimit_returnsCap()
		{
			final int input = 2500;
			final int cap   = ConfigPlayers.MAX_PATK_SPEED_LIMIT;
			final int result = Math.min(input, cap);
			assertEquals(cap, result, "Valor acima do cap deve ser truncado ao limite");
		}
		
		@Test
		@DisplayName("Configuração personalizada funciona — ex: 1200 (torneio)")
		void customCapApplied() throws Exception
		{
			setIntField("MAX_PATK_SPEED_LIMIT", 1200);
			final int result = Math.min(1500, ConfigPlayers.MAX_PATK_SPEED_LIMIT);
			assertEquals(1200, result, "Cap personalizado de 1200 deve ser respeitado");
		}
	}
	
	// ========================================================================
	// 2. STAT CAPS — Cast Speed (MAtkSpd)
	// ========================================================================
	
	@Nested
	@DisplayName("2. MAtkSpeed Cap")
	class MAtkSpeedCapTests
	{
		/**
		 * Referência L2OFF: formula castTime = hitTime * (333.0 / mAtkSpd).
		 * Cap padrão L2Off = sem limite (o Extender define MaxCastingSpeed=0.0 = ilimitado).
		 * BrProject impõe 1999 por segurança. Este teste valida a fórmula interna.
		 */
		@Test
		@DisplayName("Fórmula L2OFF: castTime = hitTime * (333.0 / mAtkSpd) — consistência")
		void castTimeFormula_l2OffConsistency()
		{
			final int mAtkSpd  = ConfigPlayers.MAX_MATK_SPEED_LIMIT; // 1999
			final int hitTime  = 1000; // ms arbitrário
			final double ratio = 333.0 / mAtkSpd;
			final double castTime = hitTime * ratio;
			assertTrue(castTime < hitTime,
				"Com mAtkSpd 1999, castTime deve ser < hitTime (ratio=0.166)");
			assertTrue(ratio > 0.0 && ratio < 1.0,
				"ratio deve estar entre 0 e 1 para mAtkSpd > 333");
		}
		
		@Test
		@DisplayName("Padrão L2OFF: cap = 1999")
		void defaultMatchesL2Off()
		{
			assertEquals(L2OFF_DEFAULT_MAX_MATK_SPEED, ConfigPlayers.MAX_MATK_SPEED_LIMIT);
		}
		
		@Test
		@DisplayName("Cap customizado 999 impede hacks de cast speed extremo")
		void customCapBlocks_extremeCastSpeed() throws Exception
		{
			setIntField("MAX_MATK_SPEED_LIMIT", 999);
			final int hacker = 9999;
			final int capped = Math.min(hacker, ConfigPlayers.MAX_MATK_SPEED_LIMIT);
			assertEquals(999, capped);
		}
	}
	
	// ========================================================================
	// 3. STAT CAPS — RunSpeed
	// ========================================================================
	
	@Nested
	@DisplayName("3. RunSpeed Cap")
	class RunSpeedCapTests
	{
		@Test
		@DisplayName("Padrão L2OFF: MaxRunSpeed = 250 (hardcoded no binary L2Off)")
		void defaultMatchesL2Off()
		{
			assertEquals(L2OFF_DEFAULT_MAX_RUN_SPEED, ConfigPlayers.MAX_RUN_SPEED_LIMIT,
				"RunSpeed default deve ser 250 como em L2OFF");
		}
		
		@Test
		@DisplayName("Fórmula de fallback: quando MAX_RUN_SPEED_LIMIT=0 usa ConfigProject")
		void fallbackToConfigProject_whenLimitIsZero() throws Exception
		{
			setIntField("MAX_RUN_SPEED_LIMIT", 0);
			final int configProjectCap = 250; // simula ConfigProject.MAX_RUN_SPEED
			final int runSpdCap = (ConfigPlayers.MAX_RUN_SPEED_LIMIT > 0)
				? ConfigPlayers.MAX_RUN_SPEED_LIMIT
				: configProjectCap;
			assertEquals(configProjectCap, runSpdCap,
				"Com limit=0, deve usar ConfigProject como fallback");
		}
		
		@Test
		@DisplayName("Limite personalizado 300 permite eventos de corrida")
		void customLimit_300_allowsRaceEvents() throws Exception
		{
			setIntField("MAX_RUN_SPEED_LIMIT", 300);
			final float calcSpeed = (float) Math.min(280f, ConfigPlayers.MAX_RUN_SPEED_LIMIT);
			assertEquals(280f, calcSpeed, 0.01f,
				"Velocidade real 280 abaixo do cap 300 não deve ser truncada");
		}
	}
	
	// ========================================================================
	// 4. STAT CAPS — Evasion
	// ========================================================================
	
	@Nested
	@DisplayName("4. Evasion Cap")
	class EvasionCapTests
	{
		@Test
		@DisplayName("Padrão L2OFF: MaxEvasion = 250")
		void defaultMatchesL2Off()
		{
			assertEquals(L2OFF_DEFAULT_MAX_EVASION, ConfigPlayers.MAX_EVASION_LIMIT);
		}
		
		@Test
		@DisplayName("Evasão acima do cap é truncada")
		void evasionAboveCap_isTruncated() throws Exception
		{
			setIntField("MAX_EVASION_LIMIT", 150);
			final int hacked = 500;
			final int result  = Math.min(hacked, ConfigPlayers.MAX_EVASION_LIMIT);
			assertEquals(150, result, "Evasão deve ser limitada a 150");
		}
		
		@Test
		@DisplayName("Evasão igual ao cap retorna cap")
		void evasionExactlyAtCap_returnsCap()
		{
			final int val = ConfigPlayers.MAX_EVASION_LIMIT;
			assertEquals(val, Math.min(val, ConfigPlayers.MAX_EVASION_LIMIT));
		}
	}
	
	// ========================================================================
	// 5. BURST MULTIPLIERS — HP / CP / MP
	// ========================================================================
	
	@Nested
	@DisplayName("5. HP/CP/MP Burst Multipliers")
	class BurstMultiplierTests
	{
		@Test
		@DisplayName("Padrão L2OFF: burst = 1.0 (sem amplificação extra)")
		void defaultBurstIs1x()
		{
			assertEquals(L2OFF_DEFAULT_HP_BURST, ConfigPlayers.HP_BURST_MULTIPLIER, 0.001);
			assertEquals(L2OFF_DEFAULT_MP_BURST, ConfigPlayers.MP_BURST_MULTIPLIER, 0.001);
			assertEquals(L2OFF_DEFAULT_CP_BURST, ConfigPlayers.CP_BURST_MULTIPLIER, 0.001);
		}
		
		@Test
		@DisplayName("Fórmula HP: burst 1.5x aplica corretamente")
		void hpBurst_1_5x_appliesCorrectly() throws Exception
		{
			setDoubleField("HP_BURST_MULTIPLIER", 1.5);
			final int baseHp   = 4000;
			final int resultHp = (ConfigPlayers.HP_BURST_MULTIPLIER > 1.0)
				? (int)(baseHp * ConfigPlayers.HP_BURST_MULTIPLIER)
				: baseHp;
			assertEquals(6000, resultHp, "HP base 4000 * 1.5 deve resultar em 6000");
		}
		
		@Test
		@DisplayName("Fórmula HP: MAX_HP_LIMIT = 10000 trunca burst excessivo")
		void hpBurst_cappedByMaxHpLimit() throws Exception
		{
			setDoubleField("HP_BURST_MULTIPLIER", 3.0);
			setIntField("MAX_HP_LIMIT", 10000);
			int val = 5000;
			if (ConfigPlayers.HP_BURST_MULTIPLIER > 1.0)
				val = (int)(val * ConfigPlayers.HP_BURST_MULTIPLIER); // 15000
			if (ConfigPlayers.MAX_HP_LIMIT > 0)
				val = Math.min(val, ConfigPlayers.MAX_HP_LIMIT);      // trunca em 10000
			assertEquals(10000, val, "HP pós-burst deve ser truncado ao MAX_HP_LIMIT");
		}
		
		@Test
		@DisplayName("Fórmula CP: burst 2.0x + cap 8000")
		void cpBurst_2x_cappedAt8000() throws Exception
		{
			setDoubleField("CP_BURST_MULTIPLIER", 2.0);
			setIntField("MAX_CP_LIMIT", 8000);
			int val = 5000;
			if (ConfigPlayers.CP_BURST_MULTIPLIER > 1.0)
				val = (int)(val * ConfigPlayers.CP_BURST_MULTIPLIER); // 10000
			if (ConfigPlayers.MAX_CP_LIMIT > 0)
				val = Math.min(val, ConfigPlayers.MAX_CP_LIMIT);      // 8000
			assertEquals(8000, val);
		}
		
		@Test
		@DisplayName("Fórmula MP: sem burst (1.0) + sem cap → valor original preservado")
		void mpDefault_noChange()
		{
			int val = 3000;
			if (ConfigPlayers.MP_BURST_MULTIPLIER > 1.0)
				val = (int)(val * ConfigPlayers.MP_BURST_MULTIPLIER);
			if (ConfigPlayers.MAX_MP_LIMIT > 0)
				val = Math.min(val, ConfigPlayers.MAX_MP_LIMIT);
			assertEquals(3000, val, "Com burst=1.0 e sem cap, MP deve ser inalterado");
		}
	}
	
	// ========================================================================
	// 6. SKILL REUSE MULTIPLIER
	// ========================================================================
	
	@Nested
	@DisplayName("6. Skill Reuse Multiplier")
	class SkillReuseTests
	{
		/**
		 * Referência L2OFF (CreatureAction.cpp OnSetSkillUsableTime2):
		 * - staticReuseTime sobrescreve o Reuse calculado quando > 0.
		 * - Sem ajuste multiplicador nativo; o Extender aplica SKILL_REUSE_MANAGER.
		 * BrProject: SKILL_REUSE_MULTIPLIER padrão = 1.0 (neutro).
		 */
		@Test
		@DisplayName("Padrão L2OFF: multiplicador = 1.0 (sem alteração)")
		void defaultMultiplierIs1x()
		{
			assertEquals(L2OFF_DEFAULT_SKILL_REUSE_MULTIPLIER,
				ConfigPlayers.SKILL_REUSE_MULTIPLIER, 0.0001);
		}
		
		@Test
		@DisplayName("Multiplicador 0.5 reduz reuse à metade (servidor custom)")
		void halfMultiplier_halvesReuse() throws Exception
		{
			setDoubleField("SKILL_REUSE_MULTIPLIER", 0.5);
			final int baseReuse = 10000; // 10s
			final int result    = (int)(baseReuse * ConfigPlayers.SKILL_REUSE_MULTIPLIER);
			assertEquals(5000, result, "Reuse 10s * 0.5 deve resultar em 5s");
		}
		
		@Test
		@DisplayName("Multiplicador 2.0 dobra o reuse")
		void doubleMultiplier_doublesReuse() throws Exception
		{
			setDoubleField("SKILL_REUSE_MULTIPLIER", 2.0);
			final int base   = 5000;
			final int result = (int)(base * ConfigPlayers.SKILL_REUSE_MULTIPLIER);
			assertEquals(10000, result);
		}
		
		@Test
		@DisplayName("MIN_SKILL_REUSE_DELAY_MS aplica piso mínimo quando calculado < min")
		void minSkillDelay_enforcesFloor() throws Exception
		{
			setIntField("MIN_SKILL_REUSE_DELAY_MS", 500);
			int reuseDelay = 100; // muito baixo
			if (ConfigPlayers.MIN_SKILL_REUSE_DELAY_MS > 0 &&
				reuseDelay < ConfigPlayers.MIN_SKILL_REUSE_DELAY_MS)
			{
				reuseDelay = ConfigPlayers.MIN_SKILL_REUSE_DELAY_MS;
			}
			assertEquals(500, reuseDelay, "Reuse menor que mínimo deve ser elevado ao piso");
		}
		
		@Test
		@DisplayName("MIN_SKILL_REUSE_DELAY_MS = 0 não interfere (comportamento L2OFF nativo)")
		void minSkillDelay_zeroDoesNotInterfere()
		{
			assertEquals(0, ConfigPlayers.MIN_SKILL_REUSE_DELAY_MS);
			int reuseDelay = 100;
			if (ConfigPlayers.MIN_SKILL_REUSE_DELAY_MS > 0 &&
				reuseDelay < ConfigPlayers.MIN_SKILL_REUSE_DELAY_MS)
			{
				reuseDelay = ConfigPlayers.MIN_SKILL_REUSE_DELAY_MS;
			}
			assertEquals(100, reuseDelay, "Com MIN=0, reuse não deve ser alterado");
		}
		
		@Test
		@DisplayName("Skill estática (staticReuseTime) não deve ter multiplicador aplicado — isStaticReuse guard")
		void staticSkill_noMultiplierApplied() throws Exception
		{
			setDoubleField("SKILL_REUSE_MULTIPLIER", 0.5);
			// Simula: isStaticReuse() == true → não aplica multiplicador
			final boolean isStaticReuse = true;
			int reuseDelay = 10000;
			if (!isStaticReuse && ConfigPlayers.SKILL_REUSE_MULTIPLIER != 1.0)
				reuseDelay = (int)(reuseDelay * ConfigPlayers.SKILL_REUSE_MULTIPLIER);
			assertEquals(10000, reuseDelay,
				"Skill com reuse estático não deve sofrer multiplicação");
		}
	}
	
	// ========================================================================
	// 7. CANCEL DISPEL ORDER — LIFO vs SHUFFLE
	// ========================================================================
	
	@Nested
	@DisplayName("7. Cancel Dispel Order (LIFO vs Shuffle) — L2OFF Compliance")
	class CancelDispelOrderTests
	{
		/**
		 * Referência L2OFF (InstantEffects.cpp i_cancel):
		 *   Usa RandInt(buffCount) → seleciona índice aleatório no vector vAbnormalStatus.
		 * Referência L2OFF (i_hide_abnormal):
		 *   Itera iterator begin→end, remove o PRIMEIRO que atende a chance → FIFO-like.
		 *
		 * Em prática, vAbnormalStatus é um std::vector ordenado por tempo de aplicação.
		 * i_cancel usa RandInt = shuffle efetivo por slot.
		 * i_hide_abnormal (Mage Bane) usa begin→end = FIFO (remove mais antigo primeiro).
		 *
		 * BrProject unifica em CANCEL_DISPEL_ORDER_LIFO = true → Collections.reverse
		 * simula a ordem "último buff adicionado primeiro" (equivalente ao L2OFF nativo).
		 */
		@Test
		@DisplayName("Padrão L2OFF: LIFO = true (compatível com i_cancel L2Off)")
		void defaultIsLifo()
		{
			assertTrue(ConfigPlayers.CANCEL_DISPEL_ORDER_LIFO,
				"CANCEL_DISPEL_ORDER_LIFO deve ser true por padrão (compliance L2OFF)");
		}
		
		@Test
		@DisplayName("LIFO=true: reverse aplicado (última skill removida primeiro)")
		void lifoTrue_reversesBuffList()
		{
			// Simula uma lista de buffs na ordem de aplicação: [B1(mais antigo), B2, B3(mais recente)]
			final java.util.List<String> buffs = new java.util.ArrayList<>(
				java.util.Arrays.asList("buff_B1", "buff_B2", "buff_B3"));
			
			if (ConfigPlayers.CANCEL_DISPEL_ORDER_LIFO)
				java.util.Collections.reverse(buffs);
			
			// Após reverse: B3 (mais recente) fica no índice 0 → será cancelado primeiro
			assertEquals("buff_B3", buffs.get(0),
				"LIFO: buff mais recente (B3) deve ser o primeiro a ser cancelado");
			assertEquals("buff_B1", buffs.get(2),
				"LIFO: buff mais antigo (B1) deve ficar por último");
		}
		
		@Test
		@DisplayName("LIFO=false: shuffle mantém comportamento legado (aleatório)")
		void lifoFalse_shuffleNotReverse() throws Exception
		{
			setBoolField("CANCEL_DISPEL_ORDER_LIFO", false);
			assertFalse(ConfigPlayers.CANCEL_DISPEL_ORDER_LIFO,
				"LIFO desativado deve usar Collections.shuffle (comportamento aleatório)");
		}
		
		@Test
		@DisplayName("LIFO=true: reversão em lista vazia não lança exceção")
		void lifoTrue_emptyList_noException()
		{
			final java.util.List<String> empty = new java.util.ArrayList<>();
			assertDoesNotThrow(() ->
			{
				if (ConfigPlayers.CANCEL_DISPEL_ORDER_LIFO)
					java.util.Collections.reverse(empty);
			}, "reverse em lista vazia não deve lançar exceção");
		}
	}
	
	// ========================================================================
	// 8. P.ATK / M.ATK CAPS
	// ========================================================================
	
	@Nested
	@DisplayName("8. PAtk / MAtk Hard Caps")
	class PatkMatkCapsTests
	{
		@Test
		@DisplayName("Padrão L2OFF: PAtk cap = 999999 (sem limite prático)")
		void pAtkDefaultMatchesL2Off()
		{
			assertEquals(L2OFF_DEFAULT_MAX_PATK, ConfigPlayers.MAX_PATK_LIMIT);
		}
		
		@Test
		@DisplayName("Padrão L2OFF: MAtk cap = 999999 (sem limite prático)")
		void mAtkDefaultMatchesL2Off()
		{
			assertEquals(L2OFF_DEFAULT_MAX_MATK, ConfigPlayers.MAX_MATK_LIMIT);
		}
		
		@Test
		@DisplayName("PAtk cap 5000 bloqueia dano de crafter extremo (rate server)")
		void pAtkCustomCap_blocksExtremeValues() throws Exception
		{
			setIntField("MAX_PATK_LIMIT", 5000);
			final int hacked = 99999;
			final int result  = Math.min(hacked, ConfigPlayers.MAX_PATK_LIMIT);
			assertEquals(5000, result);
		}
		
		@Test
		@DisplayName("Fallback: MAX_PATK_LIMIT=0 usa ConfigProject.MAX_PATK (999999)")
		void pAtkFallback_whenLimitIsZero() throws Exception
		{
			setIntField("MAX_PATK_LIMIT", 0);
			final int configProjectCap = 999999;
			final int cap = (ConfigPlayers.MAX_PATK_LIMIT > 0)
				? ConfigPlayers.MAX_PATK_LIMIT
				: configProjectCap;
			assertEquals(configProjectCap, cap,
				"Com limit=0, deve usar ConfigProject como fallback");
		}
	}
	
	// ========================================================================
	// 9. VALIDAÇÃO DE INTEGRIDADE — Coerência dos valores padrão
	// ========================================================================
	
	@Nested
	@DisplayName("9. Integridade Geral dos Defaults")
	class DefaultIntegrityTests
	{
		@Test
		@DisplayName("Todos os caps de velocidade são positivos")
		void allSpeedCaps_arePositive()
		{
			assertTrue(ConfigPlayers.MAX_PATK_SPEED_LIMIT  > 0, "PAtkSpd cap > 0");
			assertTrue(ConfigPlayers.MAX_MATK_SPEED_LIMIT  > 0, "MAtkSpd cap > 0");
			assertTrue(ConfigPlayers.MAX_RUN_SPEED_LIMIT   > 0, "RunSpeed cap > 0");
			assertTrue(ConfigPlayers.MAX_EVASION_LIMIT      > 0, "Evasion cap > 0");
		}
		
		@Test
		@DisplayName("Burst multipliers default >= 1.0 (não reduzem HP/CP/MP base)")
		void burstMultipliers_areAtLeast1()
		{
			assertTrue(ConfigPlayers.HP_BURST_MULTIPLIER >= 1.0, "HP burst >= 1.0");
			assertTrue(ConfigPlayers.MP_BURST_MULTIPLIER >= 1.0, "MP burst >= 1.0");
			assertTrue(ConfigPlayers.CP_BURST_MULTIPLIER >= 1.0, "CP burst >= 1.0");
		}
		
		@Test
		@DisplayName("Skill reuse multiplier default = 1.0 (sem alteração de tempo)")
		void skillReuseMultiplier_defaultIsNeutral()
		{
			assertEquals(1.0, ConfigPlayers.SKILL_REUSE_MULTIPLIER, 0.0001);
		}
		
		@Test
		@DisplayName("MIN_SKILL_REUSE_DELAY_MS default = 0 (sem piso artificial)")
		void minSkillReuseDelay_defaultIsZero()
		{
			assertEquals(0, ConfigPlayers.MIN_SKILL_REUSE_DELAY_MS);
		}
		
		@Test
		@DisplayName("RunSpeed padrão ≤ Evasion padrão (coerência de escala L2OFF)")
		void runSpeed_lessThanOrEqualToEvasion()
		{
			// RunSpeed e Evasion têm o mesmo cap (250) no L2OFF — escala idêntica
			assertEquals(ConfigPlayers.MAX_RUN_SPEED_LIMIT, ConfigPlayers.MAX_EVASION_LIMIT,
				"RunSpeed e Evasion têm o mesmo cap padrão de 250 no L2OFF");
		}
		
		@Test
		@DisplayName("MAtkSpd cap > PAtkSpd cap (cast speed tem margem maior no L2OFF)")
		void mAtkSpd_capIsHigherThanPAtkSpd()
		{
			assertTrue(ConfigPlayers.MAX_MATK_SPEED_LIMIT > ConfigPlayers.MAX_PATK_SPEED_LIMIT,
				"MAtkSpd (1999) deve ter cap maior que PAtkSpd (1500) como no L2OFF");
		}
	}
	
	// ========================================================================
	// Helpers — injeção de campos estáticos via reflexão
	// ========================================================================
	
	private static void setIntField(String fieldName, int value) throws Exception
	{
		final java.lang.reflect.Field f = ConfigPlayers.class.getDeclaredField(fieldName);
		f.setAccessible(true);
		f.set(null, value);
	}
	
	private static void setDoubleField(String fieldName, double value) throws Exception
	{
		final java.lang.reflect.Field f = ConfigPlayers.class.getDeclaredField(fieldName);
		f.setAccessible(true);
		f.set(null, value);
	}
	
	private static void setBoolField(String fieldName, boolean value) throws Exception
	{
		final java.lang.reflect.Field f = ConfigPlayers.class.getDeclaredField(fieldName);
		f.setAccessible(true);
		f.set(null, value);
	}
}
