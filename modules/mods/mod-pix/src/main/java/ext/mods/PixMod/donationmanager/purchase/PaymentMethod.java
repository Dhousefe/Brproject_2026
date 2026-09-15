/*
 * Copyright (c) Dev A.L.N. Todos os direitos reservados.
 */

package ext.mods.PixMod.donationmanager.purchase;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import ext.mods.PixMod.donationmanager.CurrencyManager;
import ext.mods.Config;
import ext.mods.config.ConfigDonation;

public enum PaymentMethod
{
	MP_PIX("MP", "MercadoPago", "PIX"), // qrcode, email
	MP_LINK("MP", "MercadoPago", ""), // link, qrcode
	PAYPAL("PAYPAL", "PayPal", ""), // link, qrcode
	BINANCE("BINANCE", "Binance", ""); // qrcode, link

	private final String _id;
	private final String _name;
	private final String _type;

	private PaymentMethod(String id, String name, String type)
	{
		_id = id;
		_name = name;
		_type = type;
	}

	public String getId()
	{
		return _id;
	}

	public final String getName()
	{
		return _name;
	}

	public final String getTypeName()
	{
		return _type.isEmpty() ? "" : "- " + _type;
	}

	/**
	* @return : Pagamento é feito preferencialmente via QRCODE
	*/
	public final boolean isQrCode()
	{
		return equals(MP_PIX) || equals(BINANCE);
	}

	/**
	* @return : Moeda principal desse método de pagamento
	*/
	public final String getMainCurrency()
	{
		switch (this)
		{
			case MP_PIX:
				return "BRL";
			case MP_LINK:
				return ConfigDonation.DONATION_MP_CURRENCY;
			case PAYPAL:
				return ConfigDonation.DONATION_PAYPAL_CURRENCY;
			case BINANCE:
				return ConfigDonation.DONATION_BINANCE_FIAT_CURRENCY;
			default:
				return null;
		}
	}

	/**
	* @return : Lista de moedas adicionais desse método de pagamento
	*/
	public final String[] getAltCurrencies()
	{
		switch (this)
		{
			case MP_LINK:
				return ConfigDonation.DONATION_MP_CURRENCIES[0].isEmpty() ? null : ConfigDonation.DONATION_MP_CURRENCIES;
			case PAYPAL:
				return ConfigDonation.DONATION_PAYPAL_CURRENCIES[0].isEmpty() ? null : ConfigDonation.DONATION_PAYPAL_CURRENCIES;
			case BINANCE:
				return ConfigDonation.DONATION_BINANCE_PAY_CURRENCY[0].isEmpty() ? null : ConfigDonation.DONATION_BINANCE_PAY_CURRENCY;
			default:
				return null;
		}
	}

	/**
	* @return : Lista com a moeda principal e as alternativas
	*/
	public final List<String> getAllCurrencies()
	{
		final List<String> resultList = new ArrayList<>();
		if (getAltCurrencies() != null)
		{
			for (String alt : getAltCurrencies())
				resultList.add(alt);
		}

		// A moeda principal da binance (fiat) é utilizada somente para conversões, não para pagamento
		if (this != BINANCE)
			resultList.add(getMainCurrency());
		else if (getAltCurrencies() == null)
		{
			// Todas as moedas que já acompanhamos continuarão serão atualizadas
			resultList.addAll(CurrencyManager.getInstance().getCryptoCurrencies());
			
			// Será aceita qualquer moeda no checkout, mas vamos exibir algumas por padrão
			if (resultList.isEmpty())
				resultList.addAll(CurrencyManager.DEFAULT_CRYPTO_CURRENCIES);
		}

		return resultList;
	}

	public final String getDisplayName()
	{
		return this == MP_PIX ? _type : _name;
	}

	public final boolean isEnabled()
	{
		switch (this)
		{
			case MP_PIX:
				return ConfigDonation.DONATION_MP_PIX;
			case MP_LINK:
				return ConfigDonation.DONATION_MP_LINK;
			case PAYPAL:
				return ConfigDonation.DONATION_PAYPAL_LINK;
			case BINANCE:
				return ConfigDonation.DONATION_BINANCE_PAY;
			default:
				return false;
		}
	}

	public final BigDecimal getPrice(String currency)
	{
		return getPrice(currency, 1);
	}

	public final BigDecimal getPrice(String currency, int qnt)
	{
		return CurrencyManager.getInstance().convert(this, currency, qnt);
	}

	public final int[] getDropdown()
	{
		switch (this)
		{
			case MP_PIX:
				return ConfigDonation.DONATION_MP_PIX_DROPDOWN;
			case MP_LINK:
				return ConfigDonation.DONATION_MP_LINK_DROPDOWN;
			case PAYPAL:
				return ConfigDonation.DONATION_PAYPAL_DROPDOWN;
			case BINANCE:
				return ConfigDonation.DONATION_BINANCE_DROPDOWN;
			default:
				return null;
		}
	}
	
	public final int getHtmlColumnWidth()
	{
		switch (this)
		{
			case MP_PIX:
				return 25;
			case MP_LINK:
				return 70;
			case PAYPAL:
				return 38;
			case BINANCE:
				return 40;
			default:
				return 0;
		}
	}
	
	public final boolean sendMail()
	{
		switch (this)
		{
			case MP_PIX:
				return ConfigDonation.DONATION_MP_PIX_MAIL;
			case MP_LINK:
				return ConfigDonation.DONATION_MP_LINK_MAIL;
			case PAYPAL:
				return ConfigDonation.DONATION_PAYPAL_MAIL;
			case BINANCE:
				return ConfigDonation.DONATION_BINANCE_MAIL;
			default:
				return false;
		}
	}
}
