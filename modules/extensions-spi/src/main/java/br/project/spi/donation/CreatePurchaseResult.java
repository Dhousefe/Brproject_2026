package br.project.spi.donation;

import java.math.BigDecimal;

public record CreatePurchaseResult(
    boolean ok,
    String message,
    int purchaseId,
    String characterName,
    int productId,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal totalPrice,
    String currency,
    String qrCode,
    String link,
    String status,
    long expiresAt
) {
}
