package br.project.spi.donation;

import java.math.BigDecimal;

public record DonationConfigDto(
    boolean enabled,
    int itemId,
    String itemName,
    String itemIcon,
    BigDecimal pixPrice,
    String currency,
    int[] dropdown,
    int minQuantity,
    int maxQuantity,
    int expirationMinutes,
    String serverName
) {
}
