/*
 * Copyright (c) Dev A.L.N. Todos os direitos reservados.
 */

package ext.mods.PixMod.donationmanager.tasks;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import ext.mods.commons.logging.CLogger;
import ext.mods.commons.pool.ThreadPool;

import ext.mods.PixMod.donationmanager.CurrencyManager;
import ext.mods.PixMod.donationmanager.purchase.PaymentMethod;
import ext.mods.Config;
import ext.mods.config.ConfigDonation;

public class FiatCurrencyTask implements Runnable
{
	private static final CLogger LOGGER = new CLogger(FiatCurrencyTask.class.getName());

	/** User-Agent identificável: alguns hosts fecham conexão com o UA padrão do Java HTTP Client. */
	private static final String JSON_USER_AGENT = "Mozilla/5.0 (compatible; DonationManager/1.0; +https://economia.awesomeapi.com.br/)";

	protected FiatCurrencyTask()
	{
		// A cada 20 minutos, 2 requisições (1 mp, 1 paypal) = 6 por hora
		// 6*24*31 = 4464
		final long delay = Math.max(TimeUnit.MINUTES.toMillis(20), Math.min(TimeUnit.MINUTES.toMillis(ConfigDonation.DONATION_CURRENCY_TASK_INTERVAL), TimeUnit.HOURS.toMillis(2)));
		ThreadPool.scheduleAtFixedRate(this, 5000, delay);
	}

	@Override
	public void run()
	{
		for (PaymentMethod pm : PaymentMethod.values())
		{
			if (!ConfigDonation.DONATION_ENABLED)
				break;

			if (pm == PaymentMethod.BINANCE || pm.getAltCurrencies() == null)
				continue;

			// Caso uma API falhe, tentamos a outra
			final boolean beacon = !ConfigDonation.DONATION_CURRENCY_CB_API_KEY.isEmpty();

			try
			{
				if (beacon)
					updateCurrencyBeacon(pm);
				else if (ConfigDonation.DONATION_CURRENCY_AWESOMEAPI)
					updateAwesomeApi(pm);
				else
					LOGGER.warn("Nenhum serviço de câmbio de moedas fiat foi ativado. Por favor, verifique seu arquivo de configuração.");
			}
			catch (Exception e)
			{
				LOGGER.warn("Falha ao consultar a cotação de moedas através do " + (beacon ? "CurrencyBeacon" : "AwesomeAPI") + ".", e);

				try
				{
					if (beacon)
					{
						LOGGER.warn("Tentando o AwesomeAPI como alternativa.");
						updateAwesomeApi(pm);
					}
				}
				catch (Exception e2)
				{
					LOGGER.warn("Nenhuma API de currency exchange está funcionando. Os últimos valores serão utilizados (caso existam).", e2);
				}
			}
		}
	}

	private static void updateCurrencyBeacon(PaymentMethod pm) throws IOException, InterruptedException
	{
		final String url = String.format("https://api.currencybeacon.com/v1/latest?api_key=%s&base=%s&symbols=%s", ConfigDonation.DONATION_CURRENCY_CB_API_KEY, pm.getMainCurrency(), String.join(",", pm.getAltCurrencies()));
		final HttpResponse<String> response = sendFiatHttpGet(CurrencyManager.getInstance().getHttpClient(), url);
		final Gson gson = new Gson();
		final JsonObject jsonObject = gson.fromJson(response.body(), JsonObject.class);
		final JsonObject rates = jsonObject.getAsJsonObject("response").getAsJsonObject("rates");

		for (String currency : pm.getAltCurrencies())
		{
			final BigDecimal rate = rates.get(currency).getAsBigDecimal();
			CurrencyManager.getInstance().setExchangeRate(pm.getMainCurrency() + currency, rate, false);
		}
	}

	private static void updateAwesomeApi(PaymentMethod pm) throws IOException, InterruptedException
	{
		// https://economia.awesomeapi.com.br/last/USD-BRL,EUR-BRL
		final String conversions = Arrays.stream(pm.getAltCurrencies()).map(c -> pm.getMainCurrency() + "-" + c).collect(Collectors.joining(","));
		final String url = "https://economia.awesomeapi.com.br/last/" + conversions;
		final HttpResponse<String> response = sendFiatHttpGet(CurrencyManager.getInstance().getHttpClient(), url);
		final Gson gson = new Gson();
		final JsonObject jsonObject = gson.fromJson(response.body(), JsonObject.class);

		for (String currency : conversions.replace("-", "").split(","))
		{
			final BigDecimal rate = jsonObject.getAsJsonObject(currency).get("high").getAsBigDecimal();
			CurrencyManager.getInstance().setExchangeRate(pm.getMainCurrency() + currency, rate, false);
		}
	}

	/**
	 * GET com cabeçalhos compatíveis com APIs públicas e até 3 tentativas (backoff) em caso de reset/IO.
	 */
	private static HttpResponse<String> sendFiatHttpGet(HttpClient client, String urlString) throws IOException, InterruptedException
	{
		final URI uri = URI.create(urlString);
		IOException lastIo = null;
		for (int attempt = 1; attempt <= 3; attempt++)
		{
			final HttpRequest request = HttpRequest.newBuilder()
				.uri(uri)
				.timeout(Duration.ofSeconds(30))
				.header("User-Agent", JSON_USER_AGENT)
				.header("Accept", "application/json")
				.GET()
				.build();
			try
			{
				final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
				final int code = response.statusCode();
				if (code >= 200 && code < 300)
					return response;
				final String body = response.body();
				final String snippet = body == null || body.length() <= 160 ? body : body.substring(0, 160) + "...";
				lastIo = new IOException("HTTP " + code + ": " + snippet);
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				throw e;
			}
			catch (IOException e)
			{
				lastIo = e;
			}
			if (attempt < 3)
			{
				try
				{
					Thread.sleep(500L * attempt);
				}
				catch (InterruptedException ie)
				{
					Thread.currentThread().interrupt();
					throw ie;
				}
			}
		}
		throw lastIo != null ? lastIo : new IOException("Falha após 3 tentativas");
	}

	public static FiatCurrencyTask getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder
	{
		protected static final FiatCurrencyTask INSTANCE = new FiatCurrencyTask();
	}
}
