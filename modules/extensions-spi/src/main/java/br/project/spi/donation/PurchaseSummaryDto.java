package br.project.spi.donation;

import java.math.BigDecimal;

public record PurchaseSummaryDto(
    int id,
    long date,
    String characterName,
    int quantity,
    BigDecimal totalPrice,
    String currency,
    String status,
    String paymentMethod
) {
}
