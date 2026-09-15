/*
 * Copyright (c) Dev A.L.N. Todos os direitos reservados.
 */

package ext.mods.PixMod.donationmanager.tasks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import ext.mods.commons.logging.CLogger;
import ext.mods.commons.pool.ThreadPool;

import ext.mods.PixMod.donationmanager.CurrencyManager;
import ext.mods.PixMod.donationmanager.DonationManager;
import ext.mods.PixMod.donationmanager.purchase.PaymentMethod;
import ext.mods.Config;
import ext.mods.config.ConfigDonation;

public class CryptoCurrencyTask implements Runnable
{
	private static final CLogger LOGGER = new CLogger(CryptoCurrencyTask.class.getName());
	
	protected CryptoCurrencyTask()
	{
		// Só precisa da API pública da Binance quando pagamento Binance está ativo.
		if (!ConfigDonation.DONATION_BINANCE_PAY)
			return;
		
		final long delay = Math.max(TimeUnit.MINUTES.toMillis(1), Math.min(TimeUnit.MINUTES.toMillis(ConfigDonation.DONATION_BINANCE_CURRENCY_TASK_INTERVAL), TimeUnit.MINUTES.toMillis(60)));
		ThreadPool.scheduleAtFixedRate(this, 5000, delay);
	}
	
	@Override
	public void run()
	{
		if (!ConfigDonation.DONATION_BINANCE_PAY)
			return;
		
		update(PaymentMethod.valueOf("BINANCE").getAllCurrencies(), null, 0);
	}
	
	public void search(int playerId, String search)
	{
		update(null, search, playerId);
	}
	
	private static void update(List<String> pmCurrencies, String playerSearch, int playerId)
	{
		if (!ConfigDonation.DONATION_BINANCE_PAY)
			return;
		
		// api/v3/ticker/price -> última variação
		// api/v3/avgPrice -> valor médio (não suporta múltiplas requisições)

		try
		{
			// https://api.binance.com/api/v3/ticker/price?symbols=["BTCBRL","ETHBRL"]
			final List<String> currencies = playerSearch == null ? pmCurrencies : List.of(playerSearch);
			final String conversions = currencies.stream().map(c -> "\"" + c + ConfigDonation.DONATION_BINANCE_FIAT_CURRENCY + "\"").collect(Collectors.joining(",", "[", "]"));
			final HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("https://api.binance.com/api/v3/ticker/price?symbols=" + URLEncoder.encode(conversions, StandardCharsets.UTF_8)))
				.build();

			final HttpResponse<String> response = CurrencyManager.getInstance().getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
			final String body = response.body();
			if (body.contains("\"code\"") && body.contains("\"msg\""))
			{
				LOGGER.warn("Binance API recusou a consulta (ex.: região restrita). Corpo: " + body);
				return;
			}
			if (!body.contains("Invalid symbol"))
			{
				final JsonElement root = JsonParser.parseString(body);
				if (!root.isJsonArray())
				{
					LOGGER.warn("Resposta Binance inesperada (não é array JSON): " + body);
					return;
				}
				final JsonArray jsonArray = root.getAsJsonArray();
				for (JsonElement element : jsonArray)
				{
					final JsonObject jsonObject = element.getAsJsonObject();
					CurrencyManager.getInstance().setExchangeRate(jsonObject.get("symbol").getAsString(), jsonObject.get("price").getAsBigDecimal(), true);
				}
			}
		}
		catch (Exception e)
		{
			if (playerSearch == null)
				LOGGER.warn("Falha ao consultar a cotação de moedas através da Binance. Por favor, verique se as moedas utilizadas são compatíveis. Os últimos valores serão utilizados (caso existam).", e);
		}
		finally
		{
			// Essa busca foi solicitada por um player, ele deve ser notificado
			if (playerId != 0 && playerSearch != null)
				DonationManager.getInstance().onSearchCrypto(playerId, playerSearch);
		}
	}
	
	public static CryptoCurrencyTask getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder
	{
		protected static final CryptoCurrencyTask INSTANCE = new CryptoCurrencyTask();
	}
}
