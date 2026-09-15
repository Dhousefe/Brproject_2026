/*
 * Copyright (c) Dev A.L.N. Todos os direitos reservados.
 */

package ext.mods.PixMod.donationmanager.paymenthandlers;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentAdditionalInfoRequest;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.client.payment.PaymentPayerRequest;
import com.mercadopago.client.payment.PaymentRefundClient;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferencePayerRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.net.MPResultsResourcesPage;
import com.mercadopago.net.MPSearchRequest;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ext.mods.commons.pool.ThreadPool;

import ext.mods.PixMod.donationmanager.DonationManager;
import ext.mods.PixMod.donationmanager.DonationValidation;
import ext.mods.PixMod.donationmanager.IPaymentHandler;
import ext.mods.PixMod.donationmanager.Mailersend;
import ext.mods.PixMod.donationmanager.purchase.Purchase;
import ext.mods.PixMod.donationmanager.purchase.PaymentMethod;
import ext.mods.PixMod.donationmanager.purchase.PurchaseStatus;
import ext.mods.Config;
import ext.mods.gameserver.model.World;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.config.ConfigDonation;

public class MercadoPago implements IPaymentHandler
{
	public MercadoPago()
	{
		reload();
	}
	
	@Override
	public void reload()
	{
		MercadoPagoConfig.setAccessToken(ConfigDonation.DONATION_MP_TOKEN);
	}
	
	@Override
	public void checkPurchase(Purchase purchase)
	{
		if (purchase.getPaymentMethod() == PaymentMethod.MP_PIX)
			ThreadPool.execute(() -> getPayment(purchase));
		else
			ThreadPool.execute(() -> searchPayment(purchase));
	}

	@Override
	public void sendPurchase(Purchase purchase)
	{
		if (purchase.getPaymentMethod() == PaymentMethod.MP_PIX)
			ThreadPool.execute(() -> createPayment(purchase));
		else
			ThreadPool.execute(() -> createPreference(purchase));
	}
	
	@Override
	public void refund(Purchase purchase)
	{
		if (purchase.getStatus() != PurchaseStatus.COMPLETED)
			return;
		
		ThreadPool.execute(() -> 
		{
			try
			{
				// A purchase continuará no banco de dados, apenas alteramos seu status
				final PaymentRefundClient client = new PaymentRefundClient();
				client.refund(Long.valueOf(purchase.getPaymentId()), purchase.getTotalPrice());
				purchase.changeStatus(PurchaseStatus.REFUNDED);
			}
			catch (Exception e)
			{
				LOGGER.warn("Falha ao cancelar a Preference da purchase #{}.", e, purchase.getId());
			}
		});
	}
	
	@Override
	public void resendEmail(Purchase purchase)
	{
		purchase.logEmailSending();
		Mailersend.sendPurchaseMail(purchase);
	}

	/*
	 * Cancela o Payment ou Preference para evitar pagamentos de compras canceladas, expiradas ou já pagas
	 * Fazemos uma tentativa. Não lidamos com erros.
	 */
	@Override
	public void closePurchase(Purchase purchase)
	{
		if (purchase.getPaymentMethod() == PaymentMethod.MP_PIX)
		{
			if (purchase.getPaymentId() == null)
				return;
			
			ThreadPool.execute(() -> closePayment(purchase));
		}
		else
		{
			if (purchase.getMpPreferenceId() == null)
				return;
			
			ThreadPool.execute(() -> closePreference(purchase));
		}
	}
	
	/*
	 * A Preference (link) foi criada no mercado pago, salvamos junto com a Purchase
	 */
	private void handleMPPreferenceResponse(Purchase p, Preference preference)
	{
		final Player player = World.getInstance().getPlayer(p.getPlayerId());
		p.setMpPreferenceId(preference.getId());
		p.changeStatus(PurchaseStatus.WAITING);
		DonationManager.getInstance().showCheckoutWindow(player, p);

		if (p.wantQrCode())
			DonationManager.getInstance().showQrCodeWindow(player, p);
		else
			resendEmail(p);
	}

	/*
	 * O Payment foi criado no mercado pago, salvamos junto com a Purchase
	 */
	private void handleMPPaymentResponse(Purchase p, Payment payment)
	{
		final Player player = World.getInstance().getPlayer(p.getPlayerId());
		p.setPaymentId(String.valueOf(payment.getId()));
		p.setQrCode(payment.getPointOfInteraction().getTransactionData().getQrCode());
		p.setLink(payment.getPointOfInteraction().getTransactionData().getTicketUrl());
		p.changeStatus(PurchaseStatus.WAITING);
		DonationManager.getInstance().showCheckoutWindow(player, p);
		
		if (p.wantQrCode())
			DonationManager.getInstance().showQrCodeWindow(player, p);
		else
			resendEmail(p);
	}

	/*
	 * Lida com pagamentos não encontrados de uma Preference
	 */
	private static void handleMPCheck(Purchase p)
	{
		if (p.getStatus() == PurchaseStatus.FINISHING)
		{
			DonationManager.getInstance().inactivePurchase(p);
			return;
		}
		
		// Podemos estar aqui por causa da verificação final da task, então nesse caso não exibimos a janela porque o player pode não estar interagindo
		final Player player = World.getInstance().getPlayer(p.getPlayerId());
		if (player == null || !player.isOnline())
			return;

		if (p.getStatus() != PurchaseStatus.EXPIRED)
			p.setMessagePaymentNotFound();

		DonationManager.getInstance().showPurchaseStatusWindow(player, p);
	}

	/*
	 * Um Payment foi encontrado, vamos verificar seu status
	 */
	public void handleMPCheck(Purchase p, Payment payment)
	{
		// https://www.mercadopago.com.br/developers/pt/docs/checkout-api/response-handling/collection-results
		// Poderíamos utilizar o binary_mode?
		switch (payment.getStatus())
		{
			case "in_mediation":
			case "cancelled":
			case "refunded":
			case "charged_back":
			case "rejected":
				DonationManager.getInstance().onPaymentFailed(p);
				break;

			case "pending":
			case "in_process":
			case "authorized":
				if (p.getStatus() == PurchaseStatus.FINISHING)
				{
					// Não é necessário cancelar o pagamento no MP porque já estamos na data de vencimento
					DonationManager.getInstance().inactivePurchase(p);
					return;
				}
				
				DonationManager.getInstance().onPaymentNotFound(p);
				break;
					
			case "approved":
				// Pode acontecer em caso de concorrência?
				if (p.getStatus() == PurchaseStatus.COMPLETED)
					return;
				
				// att-ver-3.0: never fulfill without amount/currency match
				if (!DonationValidation.paymentMatchesExpected(
					payment.getTransactionAmount(),
					payment.getCurrencyId(),
					p.getTotalPrice(),
					p.getCurrency()))
				{
					LOGGER.warn("MercadoPago approved but amount/currency mismatch for purchase #{} paid={} {} expected={} {}",
						p.getId(),
						payment.getTransactionAmount(),
						payment.getCurrencyId(),
						p.getTotalPrice(),
						p.getCurrency());
					return;
				}
				
				// Associar a Preference ao Payment
				// O link ainda fica disponível para pagamento, por isso tratamos ele. O mesmo não acontece com o PIX
				if (p.getPaymentMethod() == PaymentMethod.MP_LINK)
				{
					p.setPaymentId(String.valueOf(payment.getId()));
					closePurchase(p);
				}
				else if (p.getPaymentId() == null)
					p.setPaymentId(String.valueOf(payment.getId()));

				DonationManager.getInstance().onCompletedPayment(p);
				break;
		}
	}
	
	/*
	 * Atualizar a data de vencimento da preference para evitar que ainda continue disponível para pagamento
	 */
	private static void closePreference(Purchase purchase)
	{
		// Já se venceu no MP
		if (purchase.timeExpired())
			return;
		
		try
		{
			final PreferenceClient client = new PreferenceClient();
			final PreferenceRequest updateRequest = PreferenceRequest.builder().dateOfExpiration(OffsetDateTime.now().plusSeconds(15)).build();
			client.update(purchase.getMpPreferenceId(), updateRequest);
		}
		catch (Exception e)
		{
			LOGGER.warn("Falha ao cancelar a Preference da purchase #{}.", e, purchase.getId());
		}
	}
	
	/*
	 * https://www.mercadopago.com.br/developers/pt/reference/payments/_payments_id/put
	 */
	private static void closePayment(Purchase purchase)
	{
		if (purchase.timeExpired())
			return;
		
		try
		{
			final PaymentClient client = new PaymentClient();
			client.cancel(Long.valueOf(purchase.getPaymentId()));
		}
		catch (MPApiException e)
		{
			LOGGER.warn("Falha ao cancelar o Payment da purchase #{}.", e, purchase.getId());
		}
		catch (MPException e)
		{
		}
	}

	/*
	 * https://www.mercadopago.com.ar/developers/pt/docs/checkout-pro/integrate-preferences
	 * https://www.mercadopago.com.br/developers/pt/reference/preferences/_checkout_preferences/post
	 */
	private void createPreference(Purchase purchase)
	{
		try
		{
			purchase.setBusy(true);

			final PreferenceItemRequest itemRequest = PreferenceItemRequest.builder()
				.id(String.valueOf(purchase.getId()))
				.title(purchase.getProductName())
				.quantity(purchase.getQuantity())
				.currencyId(purchase.getCurrency())
				.unitPrice(purchase.getUnitPrice())
				.build();

			final PreferencePayerRequest payerRequest = PreferencePayerRequest.builder()
				.name(purchase.getPlayerName())
				.email(purchase.getPlayerEmail())
				.build();

			final OffsetDateTime eol = OffsetDateTime.ofInstant(Instant.ofEpochMilli(purchase.getExpiration()), ZoneId.systemDefault());
			final PreferenceRequest request = PreferenceRequest.builder()
				.items(List.of(itemRequest))
				.payer(payerRequest)
				.dateOfExpiration(eol)
				.statementDescriptor(ConfigDonation.DONATION_SERVER_NAME)
				.expires(true)
				.externalReference(String.valueOf(purchase.getId())) // Preference não é associada ao Payment, temos que fazer isso
				.build();

			final PreferenceClient client = new PreferenceClient();
			final Preference preference = client.create(request);
			handleMPPreferenceResponse(purchase, preference);
			LOGGER.info(preference.getInitPoint());
		}
		catch (MPApiException e)
		{
			logException(e, purchase);
		}
		catch (MPException e)
		{
			logException(e, purchase);
		}
		finally
		{
			purchase.setBusy(false);
		}
	}

	/*
	 * https://www.mercadopago.com.br/developers/pt/docs/checkout-api/integration-configuration/integrate-with-pix
	 * MercadoPagoConfig.setSocketTimeout(20000); // default
	 */
	public Payment createPaymentSync(Purchase purchase) throws Exception
	{
		purchase.setBusy(true);
		try
		{
			final MPRequestOptions requestOptions = MPRequestOptions.builder()
				.customHeaders(Map.of("x-idempotency-key", randomString(14)))
				.build();

			final PaymentPayerRequest payerRequest = PaymentPayerRequest.builder()
				.firstName(purchase.getPlayerName())
				.email(purchase.getPlayerEmail())
				.build();

			final OffsetDateTime eol = OffsetDateTime.ofInstant(Instant.ofEpochMilli(purchase.getExpiration()), ZoneId.systemDefault());
			final PaymentCreateRequest paymentRequest = PaymentCreateRequest.builder()
				.transactionAmount(purchase.getTotalPrice())
				.description(purchase.getProductName())
				.paymentMethodId("pix")
				.dateOfExpiration(eol)
				.payer(payerRequest)
				.additionalInfo(PaymentAdditionalInfoRequest.builder().ipAddress(purchase.getIpAddress()).build())
				.externalReference(String.valueOf(purchase.getId()))
				.build();

			final PaymentClient client = new PaymentClient();
			final Payment payment = client.create(paymentRequest, requestOptions);

			purchase.setPaymentId(String.valueOf(payment.getId()));
			if (payment.getPointOfInteraction() != null && payment.getPointOfInteraction().getTransactionData() != null)
			{
				purchase.setQrCode(payment.getPointOfInteraction().getTransactionData().getQrCode());
				purchase.setLink(payment.getPointOfInteraction().getTransactionData().getTicketUrl());
			}
			purchase.changeStatus(PurchaseStatus.WAITING);
			return payment;
		}
		finally
		{
			purchase.setBusy(false);
		}
	}

	public Payment checkPaymentSync(Purchase purchase) throws Exception
	{
		if (purchase.getPaymentId() == null)
			return null;

		purchase.setBusy(true);
		try
		{
			final PaymentClient paymentClient = new PaymentClient();
			final Payment payment = paymentClient.get(Long.valueOf(purchase.getPaymentId()));
			handleMPCheck(purchase, payment);
			return payment;
		}
		finally
		{
			purchase.setBusy(false);
		}
	}

	private void createPayment(Purchase purchase)
	{
		try
		{
			purchase.setBusy(true);
			
			final MPRequestOptions requestOptions = MPRequestOptions.builder()
				.customHeaders(Map.of("x-idempotency-key", randomString(14)))
				.build();

			final PaymentPayerRequest payerRequest = PaymentPayerRequest.builder()
				.firstName(purchase.getPlayerName())
				.email(purchase.getPlayerEmail())
//				.identification(IdentificationRequest.builder().type("CPF").number("19119119100").build())
				.build();

			final OffsetDateTime eol = OffsetDateTime.ofInstant(Instant.ofEpochMilli(purchase.getExpiration()), ZoneId.systemDefault());
			final PaymentCreateRequest paymentRequest = PaymentCreateRequest.builder()
				.transactionAmount(purchase.getTotalPrice())
				.description(purchase.getProductName())
				.paymentMethodId("pix")
				.dateOfExpiration(eol)
				.payer(payerRequest)
				.additionalInfo(PaymentAdditionalInfoRequest.builder().ipAddress(purchase.getIpAddress()).build())
				.externalReference(String.valueOf(purchase.getId()))
				.build();

			final PaymentClient client = new PaymentClient();
			final Payment payment = client.create(paymentRequest, requestOptions);
			handleMPPaymentResponse(purchase, payment);
		}
		catch (MPApiException e)
		{
			logException(e, purchase);
		}
		catch (MPException e)
		{
			logException(e, purchase);
		}
		finally
		{
			purchase.setBusy(false);
		}
	}

	/*
	 * https://www.mercadopago.com.br/developers/pt/reference/payments/_payments_search/get
	 */
	private void getPayment(Purchase purchase)
	{
		try
		{
			purchase.setBusy(true);

			final PaymentClient paymentClient = new PaymentClient();
			final Payment payment = paymentClient.get(Long.valueOf(purchase.getPaymentId()));
			handleMPCheck(purchase, payment);
		}
		catch (MPApiException e)
		{
			logException(e, purchase);
		}
		catch (MPException e)
		{
			logException(e, purchase);
		}
		finally
		{
			purchase.setBusy(false);
		}
	}

	/*
	 * https://www.mercadopago.com.br/developers/pt/reference/payments/_payments_search/get
	 */
	private void searchPayment(Purchase purchase)
	{
		try
		{
			purchase.setBusy(true);

			final PaymentClient paymentClient = new PaymentClient();
			final Map<String, Object> filters = new HashMap<>();
			filters.put("sort", "date_created");
			filters.put("criteria", "desc");
			filters.put("external_reference", purchase.getId());
			filters.put("range", "date_created");
			filters.put("begin_date", Instant.ofEpochMilli(purchase.getDate()).toString());
			filters.put("end_date", Instant.ofEpochMilli(purchase.getExpiration()).toString());

			final MPSearchRequest searchRequest = MPSearchRequest.builder().offset(0).limit(1).filters(filters).build(); // limit 0?
			final MPResultsResourcesPage<Payment> search = paymentClient.search(searchRequest);

			if (search.getResults().isEmpty())
				handleMPCheck(purchase);
			else
				handleMPCheck(purchase, search.getResults().get(0));
		}
		catch (MPApiException e)
		{
			logException(e, purchase);
		}
		catch (MPException e)
		{
			logException(e, purchase);
		}
		finally
		{
			purchase.setBusy(false);
		}
	}

	private static void logException(MPApiException e, Purchase p)
	{
		switch (e.getApiResponse().getStatusCode())
		{
			case 404:
				handleMPCheck(p);
				break;

			case 500:
				DonationManager.getInstance().handleException(p, false);
				LOGGER.warn("A API do Mercado Pago não está funcionando agora. É necessário aguardar.");
				break;

			default:
				DonationManager.getInstance().handleException(p, true);
				LOGGER.warn("Falha ao enviar requisição da purchase #{} para à API do Mercado Pago.", p.getId());
				LOGGER.warn("Status: {}, Content: {}", e, e.getApiResponse().getStatusCode(), e.getApiResponse().getContent());
		}
	}

	private static void logException(MPException e, Purchase p)
	{
		LOGGER.warn("Falha ao enviar requisição da purchase #{} para à API do Mercado Pago.", p.getId());
		DonationManager.getInstance().handleException(p, true);
	}

	/*
	 * Sem utilidade no momento
	 * https://www.mercadopago.com.br/developers/pt/reference/preferences/_checkout_preferences_id/get
	 */
//	private static void getPreference(Purchase purchase)
//	{
//		if (purchase.getStatus() == PurchaseStatus.EXPIRED)
//		{
//			DonationManager.getInstance().handleMPCheck(purchase);
//			return;
//		}
//
//		try
//		{
//			final PreferenceClient client = new PreferenceClient();
//			final Preference preference = client.get(purchase.getMpPreferenceId());
//		}
//		catch (MPApiException e)
//		{
//			logException(e, purchase);
//		}
//		catch (MPException e)
//		{
//			logException(e, purchase);
//		}
//	}
}