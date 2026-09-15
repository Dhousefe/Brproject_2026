package ext.mods.gameapi.donation

import br.project.spi.donation.CreatePurchaseResult
import br.project.spi.donation.DonationConfigDto
import br.project.spi.donation.DonationService
import br.project.spi.donation.PurchaseStatusDto
import br.project.spi.donation.PurchaseSummaryDto
import ext.mods.gameapi.GameApiConfig
import ext.mods.gameapi.security.HmacVerifier
import ext.mods.gameapi.security.NonceCache
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Field
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.util.HexFormat
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class GameApiDonationTest {

    private val testSecret = "test-secret-key-that-is-at-least-32-bytes-long".toByteArray(StandardCharsets.UTF_8)

    @BeforeEach
    fun setUp() {
        NonceCache.clear()
        setPrivateField(GameApiConfig, "secret", testSecret)
        setPrivateField(GameApiConfig, "nonceWindowMs", 300_000L)
        DonationService.Provider.register(null) // Reset to NOOP
    }

    @AfterEach
    fun tearDown() {
        DonationService.Provider.register(null)
    }

    private fun setPrivateField(target: Any, fieldName: String, value: Any) {
        val field: Field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun sign(method: String, path: String, timestamp: Long, nonce: String, bodyBytes: ByteArray): String {
        val bodyHash = HmacVerifier.sha256Hex(bodyBytes)
        val canonical = "$method|$path|$timestamp|$nonce|$bodyHash"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(testSecret, "HmacSHA256"))
        return HexFormat.of().formatHex(mac.doFinal(canonical.toByteArray(StandardCharsets.UTF_8)))
    }

    @Test
    fun testDefaultProviderIsNoopAndSafe() {
        val service = DonationService.Provider.get()
        assertNotNull(service)
        assertFalse(service.isEnabled)

        val config = service.config
        assertNotNull(config)
        assertFalse(config.enabled())

        val result = service.createPixPurchase(1001, "player_test", "test@domain.com", 10, "127.0.0.1")
        assertNotNull(result)
        assertFalse(result.ok())

        val status = service.getPurchaseStatus(1, true)
        assertNotNull(status)
        assertFalse(status.ok())
        assertEquals("NOT_FOUND", status.status())

        val history = service.getPurchasesForAccount("player_test")
        assertNotNull(history)
        assertTrue(history.isEmpty())
    }

    @Test
    fun testProviderRegistrationAndInvocation() {
        val customService = object : DonationService {
            override fun isEnabled(): Boolean = true

            override fun getConfig(): DonationConfigDto = DonationConfigDto(
                true,
                4037,
                "Coin of Luck",
                "icon.etc_coins_gold_i00",
                BigDecimal.ONE,
                "BRL",
                intArrayOf(10, 20, 50, 100),
                1,
                50000,
                1800,
                "BrProject L2"
            )

            override fun createPixPurchase(
                playerId: Int,
                accountName: String,
                email: String,
                quantity: Int,
                clientIp: String
            ): CreatePurchaseResult = CreatePurchaseResult(
                true,
                "PIX gerado com sucesso.",
                999,
                "HeroTester",
                4037,
                quantity,
                BigDecimal.ONE,
                BigDecimal(quantity),
                "BRL",
                "base64-mock-qrcode",
                "https://mercadopago.com/pix/checkout/999",
                "WAITING",
                System.currentTimeMillis() + 1800_000L
            )

            override fun getPurchaseStatus(purchaseId: Int, forceCheck: Boolean): PurchaseStatusDto =
                if (purchaseId == 999) {
                    PurchaseStatusDto(true, 999, "WAITING", "Aguardando pagamento", false, "HeroTester", 10, BigDecimal("10.00"))
                } else {
                    PurchaseStatusDto(false, purchaseId, "NOT_FOUND", "Não encontrado", false, "", 0, BigDecimal.ZERO)
                }

            override fun getPurchasesForAccount(accountName: String, playerId: Int): List<PurchaseSummaryDto> =
                listOf(
                    PurchaseSummaryDto(999, System.currentTimeMillis(), "HeroTester", 10, BigDecimal("10.00"), "BRL", "WAITING", "MP_PIX")
                )
        }

        DonationService.Provider.register(customService)
        val active = DonationService.Provider.get()
        assertTrue(active.isEnabled)

        val cfg = active.config
        assertEquals(4037, cfg.itemId())
        assertEquals("Coin of Luck", cfg.itemName())

        val created = active.createPixPurchase(1001, "hero_acc", "hero@mail.com", 25, "127.0.0.1")
        assertTrue(created.ok())
        assertEquals(999, created.purchaseId())
        assertEquals("base64-mock-qrcode", created.qrCode())
        assertEquals("https://mercadopago.com/pix/checkout/999", created.link())

        val status = active.getPurchaseStatus(999, true)
        assertTrue(status.ok())
        assertEquals("WAITING", status.status())

        val history = active.getPurchasesForAccount("hero_acc")
        assertEquals(1, history.size)
        assertEquals(999, history[0].id())
    }

    @Test
    fun testDonationRoutesHmacVerification() {
        val timestamp = System.currentTimeMillis()
        val nonce = UUID.randomUUID().toString()

        // 1. GET /internal/site/donation/config
        val getPath = "/internal/site/donation/config"
        val getSig = sign("GET", getPath, timestamp, nonce, ByteArray(0))
        assertTrue(HmacVerifier.verify("GET", getPath, timestamp, nonce, ByteArray(0), getSig))

        // 2. POST /internal/site/donation/create
        val createNonce = UUID.randomUUID().toString()
        val createPath = "/internal/site/donation/create"
        val createBody = "{\"login\":\"test_acc\",\"characterId\":123,\"email\":\"user@site.com\",\"count\":50}".toByteArray(StandardCharsets.UTF_8)
        val createSig = sign("POST", createPath, timestamp, createNonce, createBody)
        assertTrue(HmacVerifier.verify("POST", createPath, timestamp, createNonce, createBody, createSig))

        // 3. POST /internal/site/donation/status
        val statusNonce = UUID.randomUUID().toString()
        val statusPath = "/internal/site/donation/status"
        val statusBody = "{\"login\":\"test_acc\",\"purchaseId\":999}".toByteArray(StandardCharsets.UTF_8)
        val statusSig = sign("POST", statusPath, timestamp, statusNonce, statusBody)
        assertTrue(HmacVerifier.verify("POST", statusPath, timestamp, statusNonce, statusBody, statusSig))

        // 4. POST /internal/site/donation/history
        val histNonce = UUID.randomUUID().toString()
        val histPath = "/internal/site/donation/history"
        val histBody = "{\"login\":\"test_acc\"}".toByteArray(StandardCharsets.UTF_8)
        val histSig = sign("POST", histPath, timestamp, histNonce, histBody)
        assertTrue(HmacVerifier.verify("POST", histPath, timestamp, histNonce, histBody, histSig))

        // Tampered payload fails
        val tamperedBody = "{\"login\":\"attacker\",\"purchaseId\":999}".toByteArray(StandardCharsets.UTF_8)
        assertFalse(HmacVerifier.verify("POST", statusPath, timestamp, statusNonce, tamperedBody, statusSig))
    }
}
