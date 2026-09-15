/*
 * Copyright (c) Dev A.L.N. Todos os direitos reservados.
 */

package ext.mods.PixMod.donationmanager.paymenthandlers.paypal;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.util.Base64;

import ext.mods.commons.pool.ThreadPool;

import ext.mods.PixMod.donationmanager.DonationManager;
import ext.mods.PixMod.donationmanager.IPaymentHandler;
import ext.mods.PixMod.donationmanager.paymenthandlers.paypal.Invoice.BillingInfo;
import ext.mods.PixMod.donationmanager.paymenthandlers.paypal.Invoice.Detail;
import ext.mods.PixMod.donationmanager.paymenthandlers.paypal.Invoice.Invoicer;
import ext.mods.PixMod.donationmanager.paymenthandlers.paypal.Invoice.Item;
import ext.mods.PixMod.donationmanager.paymenthandlers.paypal.Invoice.PaymentTerm;
import ext.mods.PixMod.donationmanager.paymenthandlers.paypal.Invoice.Phone;
import ext.mods.PixMod.donationmanager.paymenthandlers.paypal.Invoice.Recipient;
import ext.mods.PixMod.donationmanager.paymenthandlers.paypal.Invoice.UnitAmount;
import ext.mods.PixMod.donationmanager.purchase.Purchase;
import ext.mods.PixMod.donationmanager.purchase.PurchaseStatus;
import ext.mods.Config;
import ext.mods.gameserver.model.World;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.config.ConfigDonation;

public class PayPal implements IPaymentHandler
{
	private String _accessToken;
	private long _accessTokenExpire;
	
	private final HttpClient _httpClient = HttpClient.newHttpClient();
	
	@Override
	public void reload()
	{
	}

	@Override
	public void checkPurchase(Purchase purchase)
	{
		ThreadPool.execute(() -> getInvoice(purchase));
	}

	@Override
	public void sendPurchase(Purchase purchase)
	{
		ThreadPool.execute(() -> createInvoice(purchase));
	}

	@Override
	public void closePurchase(Purchase purchase)
	{
		if (purchase.getPaypalInvoiceId() == null)
			return;

		ThreadPool.execute(() -> cancelInvoice(purchase));
	}
	
	@Override
	public void refund(Purchase purchase)
	{
	}

	@Override
	public void resendEmail(Purchase purchase)
	{
		purchase.logEmailSending();
		ThreadPool.execute(() -> sendInvoiceReminder(purchase));
	}

	private static void handleSendResponse(Purchase p)
	{
		final Player player = World.getInstance().getPlayer(p.getPlayerId());
		p.changeStatus(PurchaseStatus.WAITING);
		DonationManager.getInstance().showCheckoutWindow(player, p);
		
		if (p.wantQrCode())
			DonationManager.getInstance().showQrCodeWindow(player, p);
		else
		{
			p.logEmailSending();
			// O email será enviado em sendInvoice()
//			resendEmail(p);
		}
	}
	
	private void handlePaypalCheck(Purchase p, String status, String paymentId)
	{
		// https://developer.paypal.com/docs/api/invoicing/v2/#invoices_search-invoices!path=status&t=request
		switch (status)
		{
			case "SENT":
			case "UNPAID":
			case "PAYMENT_PENDING":
				if (p.getStatus() == PurchaseStatus.FINISHING)
				{
					// Cancelar o Invoice
					// É necessário no caso do paypal porque o vencimento é de no mínimo 1 dia, e o nosso é menor do que isso
					closePurchase(p);

					DonationManager.getInstance().inactivePurchase(p);
					return;
				}

				DonationManager.getInstance().onPaymentNotFound(p);
				break;

			case "CANCELLED":
			case "REFUNDED":
			case "MARKED_AS_REFUNDED":
				p.changeStatus(PurchaseStatus.FAILED);
				DonationManager.getInstance().onPaymentFailed(p);
				break;

			case "MARKED_AS_PAID":
				// att-ver-3.0: never fulfill on operator-marked paid (no funds capture)
				LOGGER.warn("PayPal MARKED_AS_PAID ignored for purchase #{} (not a verified capture)", p.getId());
				return;
			
			case "PAID":
				if (p.getStatus() == PurchaseStatus.COMPLETED)
					return;

				// Associar o pagamento da Invoice a purchase
				// O link de pagamento exibirá o status PAGO, portanto não poderá ser pago novamente e não é mais do nosso interesse
				p.setPaymentId(paymentId);

				// Entregar itens
				DonationManager.getInstance().onCompletedPayment(p);
				break;
		}
	}

	/*
	* https://developer.paypal.com/reference/production/
	*/
	private static String getUrl(String path)
	{
		return (ConfigDonation.DONATION_PAYPAL_SANDBOX_ENABLED ? "https://api-m.sandbox.paypal.com/" : "https://api-m.paypal.com/") + path;
	}

	/*
	* https://developer.paypal.com/api/rest/#link-getaccesstoken
	*/
	private String getAccessToken(Purchase purchase)
	{
		// Cache
		if (_accessToken != null && _accessTokenExpire - 60000 > System.currentTimeMillis())
			return _accessToken;
		
		// Somente um thread pode acessar por vez
		synchronized(this)
		{
			// Double check
			if (_accessToken != null && _accessTokenExpire - 60000 > System.currentTimeMillis())
				return _accessToken;
			
			try
			{
				final String credentials = ConfigDonation.DONATION_PAYPAL_CLIENT_ID + ":" + ConfigDonation.DONATION_PAYPAL_CLIENT_SECRET;
				final String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
				final HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(getUrl("v1/oauth2/token")))
						.header("Content-Type", "application/x-www-form-urlencoded")
						.header("Authorization", "Basic " + encodedCredentials)
						.POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
						.build();

				final HttpResponse<String> response = _httpClient.send(request, HttpResponse.BodyHandlers.ofString());
				final JsonObject jsonObject = JsonParser.parseString(response.body()).getAsJsonObject();
				_accessToken = "Bearer " + jsonObject.get("access_token").getAsString();
				_accessTokenExpire = System.currentTimeMillis() + jsonObject.get("expires_in").getAsLong() * 1000;
			}
			catch (Exception e)
			{
				LOGGER.warn("Falha ao obter o access_token do PayPal para a purchase #{}.", e, purchase.getId());
				DonationManager.getInstance().handleException(purchase, true);
			}

			return _accessToken;
		}
	}

	/*
	* https://developer.paypal.com/docs/api/invoicing/v2/#invoices_create
	*/
	private void createInvoice(Purchase purchase)
	{
		try
		{
			purchase.setBusy(true);

			final String accessToken = getAccessToken(purchase);
			if (accessToken == null)
				return;

			// O paypal permite apenas que uma data seja especificada como vencimento, não é possível definir um horário
			// https://developer.paypal.com/docs/api/invoicing/v2/#definition-invoice_payment_term
			final Invoice invoiceDetails = new Invoice();
			invoiceDetails.detail = new Detail();
			invoiceDetails.detail.invoice_number = String.valueOf(purchase.getId());
			invoiceDetails.detail.invoice_date = new SimpleDateFormat("yyyy-MM-dd").format(purchase.getDate());
			invoiceDetails.detail.currency_code = purchase.getCurrency();
			invoiceDetails.detail.payment_term = new PaymentTerm();
			invoiceDetails.detail.payment_term.term_type = "DUE_ON_DATE_SPECIFIED";
			invoiceDetails.detail.memo = "Compra realizada através do DonationManager.";

			if (!ConfigDonation.DONATION_PAYPAL_NOTE_MSG.isEmpty())
				invoiceDetails.detail.note = ConfigDonation.DONATION_PAYPAL_NOTE_MSG;

			invoiceDetails.invoicer = new Invoicer();
			invoiceDetails.invoicer.business_name = ConfigDonation.DONATION_SERVER_NAME;
			invoiceDetails.invoicer.email_address = ConfigDonation.DONATION_PAYPAL_ACCOUNT_EMAIL;

			if (!ConfigDonation.DONATION_PAYPAL_WEBSITE.isEmpty())
				invoiceDetails.invoicer.website = ConfigDonation.DONATION_PAYPAL_WEBSITE;

			if (!ConfigDonation.DONATION_PAYPAL_LOGO_IMAGE.isEmpty())
				invoiceDetails.invoicer.logo_url = ConfigDonation.DONATION_PAYPAL_LOGO_IMAGE;

			if (!ConfigDonation.DONATION_PAYPAL_PHONE_CODE.isEmpty() && !ConfigDonation.DONATION_PAYPAL_PHONE_NUMBER.isEmpty())
			{
				final Phone phone = new Phone();
				phone.country_code = ConfigDonation.DONATION_PAYPAL_PHONE_CODE;
				phone.national_number = ConfigDonation.DONATION_PAYPAL_PHONE_NUMBER;
				phone.phone_type = "MOBILE";
				invoiceDetails.invoicer.phones = new Phone[] { phone };
			}

			final Recipient recipient = new Recipient();
			recipient.billing_info = new BillingInfo();
			recipient.billing_info.business_name = purchase.getPlayerName();
			recipient.billing_info.email_address = purchase.getPlayerEmail();
			invoiceDetails.primary_recipients = new Recipient[] { recipient };

			final Item item1 = new Item();
			item1.name = purchase.getProductName();
			item1.quantity = String.valueOf(purchase.getQuantity());
			item1.unit_amount = new UnitAmount();
			item1.unit_amount.currency_code = purchase.getCurrency();
			item1.unit_amount.value = String.valueOf(purchase.getUnitPrice());
			invoiceDetails.items = new Item[] { item1 };

			final Gson gson = new GsonBuilder().create();
			final String jsonInputString = gson.toJson(invoiceDetails);

			final HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(getUrl("v2/invoicing/invoices")))
				.header("Authorization", accessToken)
				.header("Content-Type", "application/json")
				.header("Prefer", "return=representation")
				.POST(HttpRequest.BodyPublishers.ofString(jsonInputString))
				.build();

			final HttpResponse<String> response = _httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			final JsonObject jsonObject = gson.fromJson(response.body(), JsonObject.class);
			if (ConfigDonation.DONATION_PAYPAL_SANDBOX_ENABLED)
			{
				LOGGER.info("CREATE INVOICE");
				LOGGER.info("Status Code: {}", response.statusCode());
				LOGGER.info("Response Body: {}", response.body());
			}

			purchase.setPaypalInvoiceId(jsonObject.get("id").getAsString());
			sendInvoice(purchase);
		}
		catch (Exception e)
		{
			LOGGER.warn("Falha ao CRIAR a Invoice para a purchase #{}.", e, purchase.getId());
			DonationManager.getInstance().handleException(purchase, true);
		}
		finally
		{
			purchase.setBusy(false);
		}
	}

	/*
	* Quando um Invoice é criado, seu status padrão é DRAFT, para que ele seja pagável, é necessário "enviá-lo".
	* https://developer.paypal.com/docs/api/invoicing/v2/#invoices_send
	*/
	private void sendInvoice(Purchase purchase)
	{
		try
		{
			final String accessToken = getAccessToken(purchase);
			if (accessToken == null)
				return;

			// Essas mensagens customizadas aparentemente não funcionam, apesarem de estarem especificadas na API
//			final JsonObject jsonBody = new JsonObject();
//			jsonBody.addProperty("subject", "Compra realizada!");
//		 	jsonBody.addProperty("note", "Você já pode realizar o pagamento para obter os itens que comprou.");
//			jsonBody.addProperty("send_to_invoicer", false);
			
			final HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(getUrl("v2/invoicing/invoices/" + purchase.getPaypalInvoiceId() + "/send")))
				.header("Authorization", accessToken)
				.header("Content-Type", "application/json")
				.header("PayPal-Request-Id", randomString(14))
				.POST(HttpRequest.BodyPublishers.ofString(String.format("{\"send_to_recipient\": %s}", !purchase.wantQrCode())))
				.build();

			final HttpResponse<String> response = _httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200)
			{
				LOGGER.warn("Não foi possível enviar a purchase #{} para o PayPal. Verifique sua configuração, em especial o e-mail especificado.", purchase.getId());
				DonationManager.getInstance().handleException(purchase, true);
			}
			else
				handleSendResponse(purchase);

			if (ConfigDonation.DONATION_PAYPAL_SANDBOX_ENABLED)
			{
				LOGGER.info("SEND INVOICE");
				LOGGER.info("Status Code: {}", response.statusCode());
				LOGGER.info("Response Body: {}", response.body());
			}
		}
		catch (Exception e)
		{
			LOGGER.warn("Falha ao ENVIAR a Invoice para a purchase #{}.", e, purchase.getId());
			DonationManager.getInstance().handleException(purchase, true);
		}
	}

	/*
	* https://developer.paypal.com/docs/api/invoicing/v2/#invoices_get
	*/
	private void getInvoice(Purchase purchase)
	{
		try
		{
			purchase.setBusy(true);

			final String accessToken = getAccessToken(purchase);
			if (accessToken == null)
				return;

			final HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(getUrl("v2/invoicing/invoices/" + purchase.getPaypalInvoiceId())))
				.header("Authorization", accessToken)
				.header("Content-Type", "application/json")
				.GET()
				.build();

			final HttpResponse<String> response = _httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			final Gson gson = new Gson();
			final JsonObject jsonObject = gson.fromJson(response.body(), JsonObject.class);
			final String status = jsonObject.get("status").getAsString();

			if (status.equals("PAID") || status.equals("MARKED_AS_PAID"))
			{
				final String paymentId = jsonObject.getAsJsonObject("payments").getAsJsonArray("transactions").get(0).getAsJsonObject().get("payment_id").getAsString();
				handlePaypalCheck(purchase, status, paymentId);
			}
			else
				handlePaypalCheck(purchase, status, null);

			if (ConfigDonation.DONATION_PAYPAL_SANDBOX_ENABLED)
			{
				LOGGER.info("GET INVOICE");
				LOGGER.info("Status Code: {}", response.statusCode());
				LOGGER.info("Response Body: {}", response.body());
			}
		}
		catch (Exception e)
		{
			LOGGER.warn("Falha ao obter o Invoice da purchase #{}.", e, purchase.getId());
			DonationManager.getInstance().handleException(purchase, false);
		}
		finally
		{
			purchase.setBusy(false);
		}
	}

	/*
	* https://developer.paypal.com/docs/api/invoicing/v2/#invoices_remind
	*/
	private void sendInvoiceReminder(Purchase purchase)
	{
		try
		{
			purchase.setBusy(true);

			final String accessToken = getAccessToken(purchase);
			if (accessToken == null)
				return;

//			final JsonObject jsonBody = new JsonObject();
//			jsonBody.addProperty("subject", "Lembrete: Você possui uma compra com o pagamento pendente");
//			jsonBody.addProperty("note", " Por favor, pague antes do vencimento para não perder os itens que comprou..");
//			jsonBody.addProperty("send_to_invoicer", false);

			final HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(getUrl("v2/invoicing/invoices/" + purchase.getPaypalInvoiceId() + "/remind")))
				.header("Authorization", accessToken)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.noBody())
				.build();

			final HttpResponse<String> response = _httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (ConfigDonation.DONATION_PAYPAL_SANDBOX_ENABLED)
			{
				LOGGER.info("REMIND INVOICE");
				LOGGER.info("Status Code: {}", response.statusCode());
				LOGGER.info("Response Body: {}", response.body());
			}
		}
		catch (Exception e)
		{
			LOGGER.warn("Falha ao enviar ao enviar o Invoice Reminder da purchase #{}.", e, purchase.getId());
			DonationManager.getInstance().handleException(purchase, false);
		}
		finally
		{
			purchase.setBusy(false);
		}
	}

	/*
	* https://developer.paypal.com/docs/api/invoicing/v2/#invoices_cancel
	*/
	private void cancelInvoice(Purchase purchase)
	{
		try
		{
			final String accessToken = getAccessToken(purchase);
			if (accessToken == null)
				return;

			if (purchase.getPaypalInvoiceId() == null)
				return;

//			final JsonObject jsonBody = new JsonObject();
//			jsonBody.addProperty("subject", "Compra expirada ou cancelada");
//			jsonBody.addProperty("note", "Obrigado por ser membro do nosso servidor.");
//	        jsonBody.addProperty("send_to_invoicer", true);
//			jsonBody.addProperty("send_to_recipient", false);

			final HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(getUrl("v2/invoicing/invoices/" + purchase.getPaypalInvoiceId() + "/cancel")))
				.header("Authorization", accessToken)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString("{\"send_to_recipient\": false}"))
				.build();

			final HttpResponse<String> response = _httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (ConfigDonation.DONATION_PAYPAL_SANDBOX_ENABLED)
			{
				LOGGER.info("CANCEL INVOICE");
				LOGGER.info("Status Code: {}", response.statusCode());
				LOGGER.info("Response Body: {}", response.body());
			}
		}
		catch (Exception e)
		{
			LOGGER.warn("Falha ao cancelar o Invoice da purchase #{}.", e, purchase.getId());
		}
	}
}