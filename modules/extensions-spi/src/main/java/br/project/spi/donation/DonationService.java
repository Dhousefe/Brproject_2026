package br.project.spi.donation;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Pure SPI contract for server donation operations.
 * Decouples game-api from mod-pix and game-server-core.
 */
public interface DonationService {

    boolean isEnabled();

    DonationConfigDto getConfig();

    CreatePurchaseResult createPixPurchase(int playerId, String accountName, String email, int quantity, String clientIp);

    PurchaseStatusDto getPurchaseStatus(int purchaseId, boolean forceCheck);

    List<PurchaseSummaryDto> getPurchasesForAccount(String accountName, int playerId);

    default List<PurchaseSummaryDto> getPurchasesForAccount(String accountName) {
        return getPurchasesForAccount(accountName, 0);
    }

    DonationService NOOP = new DonationService() {
        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public DonationConfigDto getConfig() {
            return new DonationConfigDto(false, 0, "", "", BigDecimal.ZERO, "BRL", new int[0], 1, 999, 30, "Server");
        }

        @Override
        public CreatePurchaseResult createPixPurchase(int playerId, String accountName, String email, int quantity, String clientIp) {
            return new CreatePurchaseResult(false, "Sistema de doações temporariamente indisponível.", 0, "", 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, "BRL", null, null, "FAILED", 0L);
        }

        @Override
        public PurchaseStatusDto getPurchaseStatus(int purchaseId, boolean forceCheck) {
            return new PurchaseStatusDto(false, purchaseId, "NOT_FOUND", "Compra não encontrada", false, "", 0, BigDecimal.ZERO);
        }

        @Override
        public List<PurchaseSummaryDto> getPurchasesForAccount(String accountName, int playerId) {
            return Collections.emptyList();
        }
    };

    class Provider {
        private static volatile DonationService instance = NOOP;

        public static void register(DonationService service) {
            instance = service != null ? service : NOOP;
        }

        public static DonationService get() {
            return instance;
        }
    }
}
