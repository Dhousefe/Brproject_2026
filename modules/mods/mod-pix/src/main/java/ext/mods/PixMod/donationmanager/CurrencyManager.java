/*
 * Copyright (c) Dev A.L.N. Todos os direitos reservados.
 */

package ext.mods.PixMod.donationmanager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import ext.mods.PixMod.donationmanager.purchase.PaymentMethod;
import ext.mods.PixMod.donationmanager.tasks.CryptoCurrencyTask;
import ext.mods.PixMod.donationmanager.tasks.FiatCurrencyTask;
import ext.mods.Config;
import ext.mods.config.ConfigDonation;

public class CurrencyManager
{
	public static final List<String> DEFAULT_CRYPTO_CURRENCIES = List.of("BTC", "ETH", "BNB", "USDT");
	
	/** HTTP/1.1 evita resets do host com alguns CDNs; timeout evita sockets presos. */
	private static final HttpClient _httpClient = HttpClient.newBuilder()
		.version(Version.HTTP_1_1)
		.connectTimeout(Duration.ofSeconds(20))
		.followRedirects(Redirect.NORMAL)
		.build();
	
	// A binance retorna o valor no formato ALT -> MAIN
	private static final Map<String, BigDecimal> _exchangeCrypto = new ConcurrentHashMap<>();

	// As outras MAIN -> ALT
	private static final Map<String, BigDecimal> _exchangeFiat = new ConcurrentHashMap<>();
	
	protected CurrencyManager()
	{
		FiatCurrencyTask.getInstance();
		CryptoCurrencyTask.getInstance();
	}
	
	public void reload()
	{
		_exchangeCrypto.clear();
		_exchangeFiat.clear();
		
		FiatCurrencyTask.getInstance().run();
		if (ConfigDonation.DONATION_BINANCE_PAY)
			CryptoCurrencyTask.getInstance().run();
	}
	
	public boolean isExchangeAvailable(PaymentMethod pm, String currency)
	{
		if (pm.getMainCurrency().equals(currency))
			return true;

		if (pm == PaymentMethod.BINANCE)
			return _exchangeCrypto.containsKey(currency + pm.getMainCurrency());

		return _exchangeFiat.containsKey(pm.getMainCurrency() + currency);
	}

	public boolean isCryptoAvailable(String currency)
	{
		return _exchangeCrypto.containsKey(currency + ConfigDonation.DONATION_BINANCE_FIAT_CURRENCY);
	}

	public void setExchangeRate(String currencies, BigDecimal rate, boolean crypto)
	{
		if (crypto)
			_exchangeCrypto.put(currencies, rate);
		else
			_exchangeFiat.put(currencies, rate);
	}
	
	public Map<String, BigDecimal> getCryptoExchangeRates()
	{
		final Map<String, BigDecimal> result = new HashMap<>();
		final PaymentMethod pm = PaymentMethod.valueOf("BINANCE");
		for (String currency : getCryptoCurrencies())
			result.put(currency, convert(pm, currency, 1));
		
		return result;
	}
	
	public List<String> getCryptoCurrencies()
	{
		return _exchangeCrypto.keySet().stream().map(c -> c.substring(0, c.length() - ConfigDonation.DONATION_BINANCE_FIAT_CURRENCY.length())).collect(Collectors.toList());
	}
	
	public BigDecimal convert(PaymentMethod pm, String toCurrency, int qnt)
	{
		switch (pm)
		{
			case MP_PIX:
			{
				final BigDecimal amount = new BigDecimal(ConfigDonation.DONATION_MP_PIX_PRICE);
				return amount.multiply(new BigDecimal(qnt)).setScale(2, RoundingMode.HALF_UP);
			}
			case MP_LINK:
				return convertFiat(pm.getMainCurrency(), toCurrency, new BigDecimal(ConfigDonation.DONATION_MP_LINK_PRICE), new BigDecimal(qnt));
			case PAYPAL:
				return convertFiat(pm.getMainCurrency(), toCurrency, new BigDecimal(ConfigDonation.DONATION_PAYPAL_PRICE), new BigDecimal(qnt));
			case BINANCE:
				return convertCripto(pm.getMainCurrency(), toCurrency, new BigDecimal(ConfigDonation.DONATION_BINANCE_PRICE), new BigDecimal(qnt));
			default:
				return null;
		}
	}

	private static BigDecimal convertFiat(String fromCurrency, String toCurrency, BigDecimal amount, BigDecimal qnt)
	{
		if (fromCurrency.equals(toCurrency))
			return amount.multiply(qnt).setScale(2, RoundingMode.HALF_UP);

		final String key = fromCurrency + toCurrency;
		return amount.multiply(qnt).multiply(_exchangeFiat.get(key)).setScale(2, RoundingMode.HALF_UP);
	}

	private static BigDecimal convertCripto(String fromCurrency, String toCurrency, BigDecimal amount, BigDecimal qnt)
	{
		final String key = toCurrency + fromCurrency;
		return amount.multiply(qnt).divide(_exchangeCrypto.get(key), 8, RoundingMode.HALF_UP);
	}
	
	public HttpClient getHttpClient()
	{
		return _httpClient;
	}

	public static CurrencyManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder
	{
		protected static final CurrencyManager INSTANCE = new CurrencyManager();
	}
}
