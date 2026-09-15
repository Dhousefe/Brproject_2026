package ext.mods.PixMod.donationmanager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import ext.mods.PixMod.donationmanager.purchase.PaymentMethod;
import ext.mods.PixMod.donationmanager.purchase.PurchaseStatus;

/**
 * Pure donation validation helpers (Phase 6 + att-ver-3.0 hardening).
 * Unit-testable; no DB/Player.
 */
public final class DonationValidation
{
	public static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$");
	
	/** Inclusive quantity bounds for server-side purchase create. */
	public static final int MIN_QUANTITY = 1;
	public static final int MAX_QUANTITY = 999;
	
	private DonationValidation()
	{
	}
	
	public static boolean isValidEmailFormat(String email)
	{
		if (email == null || "0".equals(email))
			return false;
		if (email.length() < 6 || email.length() > 44)
			return false;
		return EMAIL_PATTERN.matcher(email).matches();
	}
	
	public static boolean isEmailDomainAllowed(String email, String[] allowedDomains)
	{
		if (!isValidEmailFormat(email) || allowedDomains == null || allowedDomains.length == 0)
			return false;
		final int at = email.indexOf('@');
		if (at < 0 || at >= email.length() - 1)
			return false;
		final String domain = email.substring(at + 1);
		return Arrays.asList(allowedDomains).contains(domain);
	}
	
	public static boolean isValidQuantity(int quantity)
	{
		return quantity >= MIN_QUANTITY && quantity <= MAX_QUANTITY;
	}
	
	public static BigDecimal totalPrice(BigDecimal unitPrice, int quantity, PaymentMethod method)
	{
		final BigDecimal price = unitPrice != null ? unitPrice : BigDecimal.ONE;
		final int scale = method == PaymentMethod.BINANCE ? 8 : 2;
		return price.multiply(BigDecimal.valueOf(Math.max(quantity, 1))).setScale(scale, RoundingMode.HALF_UP);
	}
	
	/**
	 * Expiration epoch millis from purchase date and method config minutes.
	 * PIX enforces Mercado Pago minimum of 30 minutes.
	 */
	public static long expirationMillis(long purchaseDateMillis, PaymentMethod method, int configuredMinutes)
	{
		int minutes = Math.max(configuredMinutes, 1);
		if (method == PaymentMethod.MP_PIX)
			minutes = Math.max(configuredMinutes, 30);
		return purchaseDateMillis + TimeUnit.MINUTES.toMillis(minutes);
	}
	
	public static boolean agreedTerms(boolean requireTerms, boolean termsAccepted)
	{
		return !requireTerms || termsAccepted;
	}
	
	/**
	 * True when gateway reported paid amount/currency covers the expected purchase total.
	 * Underpayment or currency mismatch must never fulfill.
	 */
	public static boolean paymentMatchesExpected(BigDecimal paidAmount, String paidCurrency, BigDecimal expectedTotal, String expectedCurrency)
	{
		if (paidAmount == null || expectedTotal == null)
			return false;
		if (paidCurrency == null || paidCurrency.isBlank() || expectedCurrency == null || expectedCurrency.isBlank())
			return false;
		if (!paidCurrency.equalsIgnoreCase(expectedCurrency.trim()))
			return false;
		// Normalize scale for fiat comparison noise (e.g. 10.00 vs 10.0)
		final int scale = Math.max(paidAmount.scale(), expectedTotal.scale());
		final BigDecimal paid = paidAmount.setScale(scale, RoundingMode.HALF_UP);
		final BigDecimal expected = expectedTotal.setScale(scale, RoundingMode.HALF_UP);
		return paid.compareTo(expected) >= 0;
	}
	
	/** Statuses from which a payment completion may claim fulfillment once. */
	public static boolean isFulfillableStatus(PurchaseStatus status)
	{
		if (status == null)
			return false;
		return status == PurchaseStatus.WAITING
			|| status == PurchaseStatus.FINISHING
			|| status == PurchaseStatus.CREATED
			|| status == PurchaseStatus.PENDING;
	}
	
	/** PayPal invoice statuses that must NOT grant items. */
	public static boolean isUnsafePaypalPaidStatus(String status)
	{
		return status != null && "MARKED_AS_PAID".equalsIgnoreCase(status);
	}
}
