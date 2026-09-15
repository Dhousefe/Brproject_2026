package ext.mods.PixMod.donationmanager.purchase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import ext.mods.config.ConfigDonation;

class PaymentMethodTest
{
	@AfterEach
	void reset()
	{
		ConfigDonation.DONATION_MP_PIX = false;
		ConfigDonation.DONATION_MP_LINK = false;
		ConfigDonation.DONATION_PAYPAL_LINK = false;
		ConfigDonation.DONATION_BINANCE_PAY = false;
		ConfigDonation.DONATION_MP_CURRENCY = "BRL";
		ConfigDonation.DONATION_PAYPAL_CURRENCY = "BRL";
		ConfigDonation.DONATION_BINANCE_FIAT_CURRENCY = "BRL";
	}
	
	@Test
	void isQrCode()
	{
		assertTrue(PaymentMethod.MP_PIX.isQrCode());
		assertTrue(PaymentMethod.BINANCE.isQrCode());
		assertFalse(PaymentMethod.PAYPAL.isQrCode());
		assertFalse(PaymentMethod.MP_LINK.isQrCode());
	}
	
	@Test
	void mainCurrency_pixAlwaysBrl()
	{
		assertEquals("BRL", PaymentMethod.MP_PIX.getMainCurrency());
	}
	
	@Test
	void isEnabled_respectsConfigFlags()
	{
		assertFalse(PaymentMethod.MP_PIX.isEnabled());
		ConfigDonation.DONATION_MP_PIX = true;
		assertTrue(PaymentMethod.MP_PIX.isEnabled());
	}
	
	@Test
	void displayNames_nonEmpty()
	{
		for (PaymentMethod m : PaymentMethod.values())
		{
			assertTrue(m.getName() != null && !m.getName().isEmpty());
			assertTrue(m.getHtmlColumnWidth() > 0);
		}
	}
}
