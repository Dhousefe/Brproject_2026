/*
 * Copyright (c) Dev A.L.N. Todos os direitos reservados.
 */

package ext.mods.PixMod.donationmanager.paymenthandlers.binance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import ext.mods.commons.pool.ThreadPool;

import java.math.BigDecimal;

import ext.mods.PixMod.donationmanager.DonationManager;
import ext.mods.PixMod.donationmanager.DonationValidation;
import ext.mods.PixMod.donationmanager.IPaymentHandler;
import ext.mods.PixMod.donationmanager.Mailersend;
import ext.mods.PixMod.donationmanager.paymenthandlers.binance.Order.Env;
import ext.mods.PixMod.donationmanager.paymenthandlers.binance.Order.GoodsDetail;
import ext.mods.PixMod.donationmanager.paymenthandlers.binance.Order.GoodsUnitAmount;
import ext.mods.PixMod.donationmanager.purchase.Purchase;
import ext.mods.PixMod.donationmanager.purchase.PurchaseStatus;
import ext.mods.Config;
import ext.mods.gameserver.model.World;
import ext.mods.gameserver.model.actor.Player;

import org.apache.commons.codec.binary.Hex;
import ext.mods.config.ConfigDonation;
import ext.mods.config.ConfigServer;

public class Binance implements IPaymentHandler
{
	private static final String BINANCE_PAY_URL = "https://bpay.binanceapi.com/binancepay/openapi";

	private final HttpClient _httpClient = HttpClient.newHttpClient();

	@Override
	public void reload()
	{
	}

	@Override
	public void checkPurchase(Purchase purchase)
	{
		ThreadPool.execute(() -> queryOrder(purchase));
	}

	@Override
	public void sendPurchase(Purchase purchase)
	{
		ThreadPool.execute(() -> createOrder(purchase));
	}

	@Override
	public void closePurchase(Purchase purchase)
	{
		ThreadPool.execute(() -> closeOrder(purchase));
	}
	
	@Override
	public void refund(Purchase purchase)
	{
	}

	@Override
	public void resendEmail(Purchase purchase)
	{
		purchase.logEmailSending();
		Mailersend.sendPurchaseMail(purchase);
	}

	/*
	* https://merchant.binance.com/en/docs/getting-started
	* https://developers.binance.com/docs/binance-pay/api-common
	* https://developers.binance.com/docs/derivatives/option/error-code
	*/
	private static void handleCheckResponse(Purchase p, JsonObject response)
	{
		// https://developers.binance.com/docs/binance-pay/api-order-query-v2#queryorderresult
		final String status = response.get("status").getAsString();
		switch (status)
		{
			case "EXPIRED":
			case "CANCELED":
			case "REFUNDING":
			case "REFUNDED":
			case "FULL_REFUNDED":
			case "ERROR":
				DonationManager.getInstance().onPaymentFailed(p);
				break;

			case "PENDING":
			case "INITIAL":
				if (p.getStatus() == PurchaseStatus.FINISHING)
				{
					// Não é necessário cancelar o pagamento porque já estamos na data de vencimento
					DonationManager.getInstance().inactivePurchase(p);
					return;
				}

				DonationManager.getInstance().onPaymentNotFound(p);
				break;

			case "PAID":
				if (p.getStatus() == PurchaseStatus.COMPLETED)
					return;
				
				// att-ver-3.0: verify order amount/currency when present in query response
				if (response.has("orderAmount") && response.has("currency"))
				{
					try
					{
						final BigDecimal paid = new BigDecimal(response.get("orderAmount").getAsString());
						final String currency = response.get("currency").getAsString();
						if (!DonationValidation.paymentMatchesExpected(paid, currency, p.getTotalPrice(), p.getCurrency()))
						{
							LOGGER.warn("Binance PAID but amount/currency mismatch for purchase #{} paid={} {} expected={} {}",
								p.getId(), paid, currency, p.getTotalPrice(), p.getCurrency());
							return;
						}
					}
					catch (Exception e)
					{
						LOGGER.warn("Binance PAID amount parse failed for purchase #{}", e, p.getId());
						return;
					}
				}

				DonationManager.getInstance().onCompletedPayment(p);
				break;
		}
	}

	private void handleCreateResponse(Purchase p, JsonObject response)
	{
		final Player player = World.getInstance().getPlayer(p.getPlayerId());
		p.setPaymentId(response.get("prepayId").getAsString());
		p.setQrCode(response.get("qrContent").getAsString());
		p.setLink(response.get("checkoutUrl").getAsString());
		p.changeStatus(PurchaseStatus.WAITING);
		DonationManager.getInstance().showCheckoutWindow(player, p);
		
		if (p.wantQrCode())
			DonationManager.getInstance().showQrCodeWindow(player, p);			
		else
			resendEmail(p);
	}

	/*
	* https://github.com/binance/binance-connector-java
	* src/main/java/com/binance/connector/client/utils/signaturegenerator/HmacSignatureGenerator.java
	*/
	private static String hashing(String data, String nonce, String timestamp) throws NoSuchAlgorithmException, InvalidKeyException
	{
		byte[] hmacSha256;
		final SecretKeySpec secretKeySpec = new SecretKeySpec(ConfigDonation.DONATION_BINANCE_SECRET_KEY.getBytes(), "HmacSHA512");
		final Mac mac = Mac.getInstance("HmacSHA512");
		final String payload = timestamp + "\n" + nonce + "\n" + data + "\n";
		mac.init(secretKeySpec);
		hmacSha256 = mac.doFinal(payload.getBytes());
		return Hex.encodeHexString(hmacSha256).toUpperCase();
	}

	private void createOrder(Purchase purchase)
	{
		try
		{
			// Se mandarmos a moeda fiat, a binance faz a conversão para outras crypto
			// Isso não é o ideal porque o câmbio pode mudar e não ser mais o valor que foi exibido no jogo
			final Order order = new Order();
			order.env = new Env();
			order.env.terminalType = "APP";
			order.merchantTradeNo = String.valueOf(purchase.getId());
            order.orderAmount = purchase.getTotalPrice().toString();
			order.currency = purchase.getCurrency();
//			order.fiatCurrency = "BRL";
//			order.fiatAmount = "10";
			order.description = "Compra no " + ConfigDonation.DONATION_SERVER_NAME;
			order.orderExpireTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(ConfigDonation.DONATION_BINANCE_EXPIRATION_TIME);
			order.supportPayCurrency = purchase.getCurrency();

			final GoodsDetail goods = new GoodsDetail();
			goods.goodsType = "02";
			goods.goodsCategory = "6000";
			goods.referenceGoodsId = String.valueOf(purchase.getProductId());
			goods.goodsName = purchase.getProductName();
			final GoodsUnitAmount goodsUnit = new GoodsUnitAmount();
			goodsUnit.amount = purchase.getUnitPrice().doubleValue();
			goodsUnit.currency = purchase.getCurrency();
			goods.goodsUnitAmount = goodsUnit;
			order.goodsDetails = new GoodsDetail[]{goods};

			final Gson gson = new GsonBuilder().create();
			final String payload = gson.toJson(order);
			System.out.println(payload);

			final String nonce = randomString(32);
			final String timestamp = String.valueOf(System.currentTimeMillis());
			final String signature = hashing(payload, nonce, timestamp);

			final HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(BINANCE_PAY_URL + "/v3/order"))
				.header("Content-Type", "application/json")
				.header("BinancePay-Timestamp", timestamp)
				.header("BinancePay-Nonce", nonce)
				.header("BinancePay-Certificate-SN", ConfigDonation.DONATION_BINANCE_API_KEY)
				.header("BinancePay-Signature", signature)
				.POST(HttpRequest.BodyPublishers.ofString(payload))
				.build();

			final HttpResponse<String> response = _httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			final JsonObject jsonObject = gson.fromJson(response.body(), JsonObject.class);
			final String queryResponse = jsonObject.get("status").getAsString();
			if (queryResponse.equals("SUCCESS"))
				handleCreateResponse(purchase, jsonObject.getAsJsonObject("data"));
			else
				DonationManager.getInstance().handleException(purchase, true);
			
			if (ConfigServer.DEVELOPER)
			{
				LOGGER.info("CREATE ORDER");
				LOGGER.info("Status Code: {}", response.statusCode());
				LOGGER.info("Response Body: {}", response.body());
			}
		}
		catch (Exception e)
		{
			DonationManager.getInstance().handleException(purchase, true);
			LOGGER.warn("Falha ao criar a Order (Binance) para a purchase #{}.", e, purchase.getId());
		}
	}

	private void queryOrder(Purchase purchase)
	{
		try
		{
//			final JsonObject jsonBody = new JsonObject();
//			jsonBody.addProperty("prepayId", purchase.getPaymentId());

			final String payload = String.format("{\"prepayId\": %s}", purchase.getPaymentId());
			final String nonce = randomString(32);
			final String timestamp = String.valueOf(System.currentTimeMillis());
			final String signature = hashing(payload, nonce, timestamp);

			final HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(BINANCE_PAY_URL + "/v2/order/query"))
				.header("Content-Type", "application/json")
				.header("BinancePay-Timestamp", timestamp)
				.header("BinancePay-Nonce", nonce)
				.header("BinancePay-Certificate-SN", ConfigDonation.DONATION_BINANCE_API_KEY)
				.header("BinancePay-Signature", signature)
				.POST(HttpRequest.BodyPublishers.ofString(payload))
				.build();

			final HttpResponse<String> response = _httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			final Gson gson = new Gson();
			final JsonObject jsonObject = gson.fromJson(response.body(), JsonObject.class);
			final String queryResponse = jsonObject.get("status").getAsString();
			if (queryResponse.equals("SUCCESS"))
				handleCheckResponse(purchase, jsonObject.getAsJsonObject("data"));
			else
				DonationManager.getInstance().handleException(purchase, false);

			if (ConfigServer.DEVELOPER)
			{
				LOGGER.info("QUERY ORDER");
				LOGGER.info("Status Code: {}", response.statusCode());
				LOGGER.info("Response Body: {}", response.body());
			}
		}
		catch (Exception e)
		{
			DonationManager.getInstance().handleException(purchase, false);
			LOGGER.warn("Falha ao obter a Order (Binance) da purchase #{}.", e, purchase.getId());
		}
	}

	private void closeOrder(Purchase purchase)
	{
		try
		{
//			final JsonObject jsonBody = new JsonObject();
//			jsonBody.addProperty("prepayId", purchase.getPaymentId());

			final String payload = String.format("{\"prepayId\": %s}", purchase.getPaymentId());
			final String nonce = randomString(32);
			final String timestamp = String.valueOf(System.currentTimeMillis());
			final String signature = hashing(payload, nonce, timestamp);

			final HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(BINANCE_PAY_URL + "/v2/order/close"))
				.header("Content-Type", "application/json")
				.header("BinancePay-Timestamp", timestamp)
				.header("BinancePay-Nonce", nonce)
				.header("BinancePay-Certificate-SN", ConfigDonation.DONATION_BINANCE_API_KEY)
				.header("BinancePay-Signature", signature)
				.POST(HttpRequest.BodyPublishers.ofString(payload))
				.build();

			final HttpResponse<String> response = _httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (ConfigServer.DEVELOPER)
			{
				LOGGER.info("CLOSE ORDER");
				LOGGER.info("Status Code: {}", response.statusCode());
				LOGGER.info("Response Body: {}", response.body());
			}
		}
		catch (Exception e)
		{
		}
	}
}
