package ext.mods.gameserver.model.entity.autofarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ext.mods.config.ConfigProject;
import ext.mods.gameserver.data.manager.ItemAutoFarmTimeManager.AutoFarmTimeConfig;

/**
 * Testes unitários para a restrição de tempo do AutoFarm,
 * ciclo histerético de 24 horas e acúmulo de tempo extra via itens.
 */
public class AutoFarmTimeRestrictionTest
{
	private AutoFarmProfile _profile;

	@BeforeEach
	public void setUp()
	{
		ConfigProject.AUTOFARM_DAILY_LIMIT_HOURS = 2;
		ConfigProject.AUTOFARM_RESET_CYCLE_HOURS = 24;
		ConfigProject.AUTOFARM_PREMIUM_UNLIMITED = true;

		_profile = new AutoFarmProfile(null);
	}

	@Test
	@DisplayName("1. Jogador dentro do limite de 2 horas possui tempo restante e pode farmar")
	public void testDailyLimitWithinTwoHoursAllowed()
	{
		long oneHourMs = 3600L * 1000L;
		_profile.setDailyTimeUsed(oneHourMs);
		_profile.setCycleStartTime(System.currentTimeMillis() - oneHourMs);

		long remaining = _profile.getRemainingTime();
		assertEquals(oneHourMs, remaining, "Deve restar exatamente 1 hora (3.600.000 ms)");
		assertTrue(_profile.canUseAutoFarm(), "Jogador com tempo restante deve poder usar o AutoFarm");
	}

	@Test
	@DisplayName("2. Jogador que atinge 2 horas usadas tem tempo esgotado e AutoFarm bloqueado")
	public void testDailyLimitExceededBlocksAutoFarm()
	{
		long twoHoursMs = 2 * 3600L * 1000L;
		_profile.setDailyTimeUsed(twoHoursMs);
		_profile.setCycleStartTime(System.currentTimeMillis() - twoHoursMs);

		long remaining = _profile.getRemainingTime();
		assertEquals(0L, remaining, "Tempo restante deve ser 0 ms");
		assertFalse(_profile.canUseAutoFarm(), "Jogador com tempo diário esgotado não pode iniciar AutoFarm");
	}

	@Test
	@DisplayName("3. Ciclo de 24 horas a partir do primeiro uso reseta o tempo usado e renova as 2 horas")
	public void testCycleResetAfter24HoursRestoresDailyTime()
	{
		long twoHoursMs = 2 * 3600L * 1000L;
		long twentyFiveHoursAgo = System.currentTimeMillis() - (25 * 3600L * 1000L);

		// Simula ciclo iniciado há 25 horas com 2 horas já gastas
		_profile.setDailyTimeUsed(twoHoursMs);
		_profile.setCycleStartTime(twentyFiveHoursAgo);

		// Executa a checagem de ciclo
		_profile.checkCycleReset();

		assertEquals(0L, _profile.getDailyTimeUsed(), "Tempo usado deve zerar após reset de 24 horas");
		assertEquals(0L, _profile.getCycleStartTime(), "Cycle start deve zerar até o próximo acionamento");
		assertEquals(twoHoursMs, _profile.getRemainingTime(), "Cota de 2 horas diárias deve estar 100% renovada");
		assertTrue(_profile.canUseAutoFarm(), "Jogador deve poder voltar a farmar");
	}

	@Test
	@DisplayName("4. Ordem de consumo: o tempo diário é gasto primeiro; tempo extra de itens só é debitado após esgotar o diário")
	public void testExtraTimeConsumedOnlyAfterDailyLimitExhausted()
	{
		long dailyLimitMs = 2 * 3600L * 1000L; // 2h = 7.200.000 ms
		long extraTimeMs = 1 * 3600L * 1000L;  // 1h extra = 3.600.000 ms
		_profile.setExtraTime(extraTimeMs);

		// Caso A: Usou 1h30m (5.400.000 ms). Ainda dentro do limite diário de 2h.
		_profile.setDailyTimeUsed((long) (1.5 * 3600L * 1000L));
		long remainingA = _profile.getRemainingTime();
		// Restam 30m do diário + 1h do extra = 1h30m = 5.400.000 ms
		assertEquals((long) (1.5 * 3600L * 1000L), remainingA, "Deve restar 30m diário + 1h extra = 1h30m");

		// Caso B: Usou 2h30m (9.000.000 ms). Esgotou as 2h diárias e consumiu 30m do extra.
		_profile.setDailyTimeUsed((long) (2.5 * 3600L * 1000L));
		long remainingB = _profile.getRemainingTime();
		// Restam apenas 30m do extra = 1.800.000 ms
		assertEquals((long) (0.5 * 3600L * 1000L), remainingB, "Deve restar apenas 30 minutos de tempo extra");
	}

	@Test
	@DisplayName("5. Tempo extra adquirido por itens NÃO é perdido quando o ciclo de 24 horas reseta")
	public void testExtraTimeNotLostOnCycleReset()
	{
		long twoHoursMs = 2 * 3600L * 1000L;
		long extraTwoHoursMs = 2 * 3600L * 1000L;
		long twentySixHoursAgo = System.currentTimeMillis() - (26 * 3600L * 1000L);

		_profile.setDailyTimeUsed(twoHoursMs);
		_profile.setExtraTime(extraTwoHoursMs);
		_profile.setCycleStartTime(twentySixHoursAgo);

		_profile.checkCycleReset();

		assertEquals(0L, _profile.getDailyTimeUsed(), "Tempo diário usado deve ter sido resetado a zero");
		assertEquals(extraTwoHoursMs, _profile.getExtraTime(), "Saldo de tempo extra deve continuar intacto");
		// Total disponível agora: 2h (diário renovado) + 2h (extra) = 4h (14.400.000 ms)
		assertEquals(twoHoursMs + extraTwoHoursMs, _profile.getRemainingTime(), "Total disponível deve ser 4 horas");
	}

	@Test
	@DisplayName("6. Configuração de itens de tempo (AutoFarmTimeConfig) converte horas e minutos com precisão")
	public void testItemAutoFarmTimeConfigCalculations()
	{
		AutoFarmTimeConfig config1h = new AutoFarmTimeConfig(0, 1);
		assertEquals(3600000L, config1h.toMilliseconds(), "1 hora deve ser exatamente 3.600.000 ms");

		AutoFarmTimeConfig config2h = new AutoFarmTimeConfig(0, 2);
		assertEquals(7200000L, config2h.toMilliseconds(), "2 horas devem ser exatamente 7.200.000 ms");

		AutoFarmTimeConfig configMixed = new AutoFarmTimeConfig(30, 1);
		assertEquals(5400000L, configMixed.toMilliseconds(), "1h30m deve ser exatamente 5.400.000 ms");
	}

	@Test
	@DisplayName("7. Limite diário desabilitado (AutoFarmDailyLimitHours = 0) concede tempo irrestrito")
	public void testDisabledDailyLimitAllowsUnrestrictedUse()
	{
		ConfigProject.AUTOFARM_DAILY_LIMIT_HOURS = 0;
		_profile.setDailyTimeUsed(100 * 3600L * 1000L); // 100 horas usadas

		assertEquals(Long.MAX_VALUE, _profile.getRemainingTime(), "Com limite 0, o tempo restante deve ser Long.MAX_VALUE");
		assertTrue(_profile.canUseAutoFarm(), "Com limite 0, canUseAutoFarm deve retornar true");
	}

	@Test
	@DisplayName("8. Formatação de contagem regressiva de tela em Dia: 00, Hora: 00, Minuto: 00, Segundos: 00")
	public void testScreenCountdownFormat()
	{
		// Cota limpa: 2 horas restantes
		_profile.setDailyTimeUsed(0L);
		String formatted2h = AutoFarmManager.formatScreenCountdown(_profile);
		assertEquals("Dia: 00, Hora: 02, Minuto: 00, Segundos: 00", formatted2h);

		// Tempo composto: 1 dia, 3 horas, 45 minutos e 20 segundos
		long customTimeMs = (24 * 3600L + 3 * 3600L + 45 * 60L + 20L) * 1000L;
		// Configuramos o limite diário e tempo extra para bater esse valor
		ConfigProject.AUTOFARM_DAILY_LIMIT_HOURS = 27; // 1 dia + 3h
		_profile.setExtraTime((45 * 60L + 20L) * 1000L);
		String formattedCustom = AutoFarmManager.formatScreenCountdown(_profile);
		assertEquals("Dia: 01, Hora: 03, Minuto: 45, Segundos: 20", formattedCustom);

		// Tempo zerado
		_profile.setDailyTimeUsed(27 * 3600L * 1000L);
		_profile.setExtraTime(0L);
		String formattedZero = AutoFarmManager.formatScreenCountdown(_profile);
		assertEquals("Dia: 00, Hora: 00, Minuto: 00, Segundos: 00", formattedZero);
	}

	@Test
	@DisplayName("9. Comando .away bloqueia quando o tempo de AutoFarm está esgotado")
	public void testAwayCommandRespectsAutoFarmTimeLimit()
	{
		_profile.setDailyTimeUsed(2 * 3600L * 1000L); // 2 horas usadas (esgotado)
		assertFalse(_profile.canUseAutoFarm(), "Perfil sem tempo restante não pode usar AutoFarm nem .away");

		_profile.addExtraTime(3600L * 1000L); // Adiciona 1 hora extra
		assertTrue(_profile.canUseAutoFarm(), "Com tempo extra adicionado, AutoFarm e .away voltam a ser permitidos");
	}
}
