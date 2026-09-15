package ext.mods.security.fail2ban;

import ext.mods.security.fail2ban.core.BanManager;
import ext.mods.security.fail2ban.core.Fail2BanConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste unitário para validação de banimento manual de loopback (127.0.0.1 e ::1).
 * Garante que o administrador possui soberania para banir qualquer IP para testes/auditoria,
 * e que o auto-ban por jail continua protegendo loopback contra bloqueios involuntários.
 */
public class ManualBanLoopbackTest {

    private BanManager banManager;

    @BeforeEach
    public void setUp() {
        banManager = new BanManager(new Fail2BanConfig(), null);
    }

    @Test
    @DisplayName("Ban manual de 127.0.0.1 pelo administrador deve ser aceito e refletir em activeBans")
    public void testManualBanOfLoopbackIsApplied() {
        String loopback = "127.0.0.1";
        assertFalse(banManager.isBanned(loopback), "127.0.0.1 nao deve estar banido inicialmente");

        banManager.ban(loopback, "manual", "Manual administrator test ban", 3600_000L);

        assertTrue(banManager.isBanned(loopback), "127.0.0.1 deve estar banido apos ban manual");
        assertNotNull(banManager.getBan(loopback), "Registro de ban deve existir");
        assertTrue(banManager.getActiveBans().containsKey(loopback), "127.0.0.1 deve constar em getActiveBans()");

        // Teste de desbanimento imediato
        banManager.unban(loopback);
        assertFalse(banManager.isBanned(loopback), "127.0.0.1 nao deve estar mais banido apos unban");
        assertFalse(banManager.getActiveBans().containsKey(loopback), "127.0.0.1 deve ser removido de getActiveBans()");
    }

    @Test
    @DisplayName("Ban manual de IPv6 loopback (::1) pelo operador deve ser aceito")
    public void testManualBanOfIpv6Loopback() {
        String ipv6Loopback = "::1";
        banManager.ban(ipv6Loopback, "manual_operator", "Manual IPv6 ban", 1800_000L);

        assertTrue(banManager.isBanned(ipv6Loopback), "::1 deve estar banido");
        banManager.unban(ipv6Loopback);
        assertFalse(banManager.isBanned(ipv6Loopback), "::1 deve estar liberado apos unban");
    }

    @Test
    @DisplayName("Auto-ban automatico por jail ainda deve respeitar ignore list para 127.0.0.1")
    public void testAutoBanByJailStillProtectsLoopback() {
        String loopback = "127.0.0.1";
        String jailName = "bruteforce";

        // Registrar multiplas falhas automaticas no loopback
        for (int i = 0; i < 10; i++) {
            boolean triggered = banManager.recordFailure(loopback, jailName, "Auto failure attempt " + i);
            assertFalse(triggered, "Auto-ban de jail nao deve disparar para IP ignorado");
        }

        assertFalse(banManager.isBanned(loopback), "127.0.0.1 nao deve ser auto-banido por jail");
    }
}
