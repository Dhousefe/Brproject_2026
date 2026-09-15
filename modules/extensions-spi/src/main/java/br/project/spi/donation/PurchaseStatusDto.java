package br.project.spi.donation;

import java.math.BigDecimal;

public record PurchaseStatusDto(
    boolean ok,
    int purchaseId,
    String status,
    String statusDescription,
    boolean delivered,
    String characterName,
    int quantity,
    BigDecimal totalPrice
) {
}
