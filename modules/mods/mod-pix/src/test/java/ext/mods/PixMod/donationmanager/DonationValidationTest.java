package ext.mods.PixMod.donationmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import ext.mods.PixMod.donationmanager.purchase.PaymentMethod;
import ext.mods.PixMod.donationmanager.purchase.PurchaseStatus;

class DonationValidationTest
{
	@Test
	void emailFormat_validAndInvalid()
	{
		assertTrue(DonationValidation.isValidEmailFormat("user@gmail.com"));
		assertFalse(DonationValidation.isValidEmailFormat(null));
		assertFalse(DonationValidation.isValidEmailFormat("0"));
		assertFalse(DonationValidation.isValidEmailFormat("a@b")); // too short
		assertFalse(DonationValidation.isValidEmailFormat("not-an-email"));
		assertFalse(DonationValidation.isValidEmailFormat("user@.")); // domain incomplete
	}
	
	@Test
	void emailDomain_allowlist()
	{
		final String[] allowed = { "gmail.com", "outlook.com" };
		assertTrue(DonationValidation.isEmailDomainAllowed("a@gmail.com", allowed));
		assertFalse(DonationValidation.isEmailDomainAllowed("a@evil.com", allowed));
		assertFalse(DonationValidation.isEmailDomainAllowed("bad", allowed));
	}
	
	@Test
	void totalPrice_scalesByMethod()
	{
		final BigDecimal unit = new BigDecimal("10.00");
		assertEquals(new BigDecimal("30.00"), DonationValidation.totalPrice(unit, 3, PaymentMethod.MP_PIX));
		assertEquals(8, DonationValidation.totalPrice(unit, 3, PaymentMethod.BINANCE).scale());
		assertEquals(new BigDecimal("30.00000000"), DonationValidation.totalPrice(unit, 3, PaymentMethod.BINANCE));
	}
	
	@Test
	void expiration_pixMinimum30Minutes()
	{
		final long date = 1_000_000L;
		final long exp = DonationValidation.expirationMillis(date, PaymentMethod.MP_PIX, 5);
		assertEquals(date + 30L * 60_000L, exp);
		final long exp2 = DonationValidation.expirationMillis(date, PaymentMethod.PAYPAL, 10);
		assertEquals(date + 10L * 60_000L, exp2);
	}
	
	@Test
	void agreedTerms()
	{
		assertTrue(DonationValidation.agreedTerms(false, false));
		assertFalse(DonationValidation.agreedTerms(true, false));
		assertTrue(DonationValidation.agreedTerms(true, true));
	}
	
	@Test
	void quantity_bounds()
	{
		assertFalse(DonationValidation.isValidQuantity(0));
		assertFalse(DonationValidation.isValidQuantity(-1));
		assertFalse(DonationValidation.isValidQuantity(1000));
		assertTrue(DonationValidation.isValidQuantity(1));
		assertTrue(DonationValidation.isValidQuantity(999));
	}
	
	@Test
	void paymentMatchesExpected_amountAndCurrency()
	{
		assertTrue(DonationValidation.paymentMatchesExpected(
			new BigDecimal("30.00"), "BRL", new BigDecimal("30.00"), "BRL"));
		assertTrue(DonationValidation.paymentMatchesExpected(
			new BigDecimal("30.50"), "BRL", new BigDecimal("30.00"), "brl")); // overpay OK
		assertFalse(DonationValidation.paymentMatchesExpected(
			new BigDecimal("29.99"), "BRL", new BigDecimal("30.00"), "BRL")); // underpay
		assertFalse(DonationValidation.paymentMatchesExpected(
			new BigDecimal("30.00"), "USD", new BigDecimal("30.00"), "BRL"));
		assertFalse(DonationValidation.paymentMatchesExpected(null, "BRL", new BigDecimal("30"), "BRL"));
	}
	
	@Test
	void fulfillableStatus_andUnsafePaypal()
	{
		assertTrue(DonationValidation.isFulfillableStatus(PurchaseStatus.WAITING));
		assertFalse(DonationValidation.isFulfillableStatus(PurchaseStatus.COMPLETED));
		assertFalse(DonationValidation.isFulfillableStatus(PurchaseStatus.OFFLINE));
		assertTrue(DonationValidation.isUnsafePaypalPaidStatus("MARKED_AS_PAID"));
		assertFalse(DonationValidation.isUnsafePaypalPaidStatus("PAID"));
	}
}
