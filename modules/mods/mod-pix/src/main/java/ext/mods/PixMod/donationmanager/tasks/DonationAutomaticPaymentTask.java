/*
 * Copyright (c) Dev A.L.N. Todos os direitos reservados.
 */

package ext.mods.PixMod.donationmanager.tasks;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.net.MPResultsResourcesPage;
import com.mercadopago.net.MPSearchRequest;
import com.mercadopago.resources.payment.Payment;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import ext.mods.commons.pool.ThreadPool;

import ext.mods.PixMod.donationmanager.DonationData;
import ext.mods.PixMod.donationmanager.paymenthandlers.MercadoPago;
import ext.mods.PixMod.donationmanager.purchase.PaymentMethod;
import ext.mods.PixMod.donationmanager.purchase.Purchase;
import ext.mods.PixMod.donationmanager.purchase.PurchaseStatus;

public class DonationAutomaticPaymentTask implements Runnable
{
	protected DonationAutomaticPaymentTask()
	{
		ThreadPool.scheduleAtFixedRate(this, 5000, 5000);
	}
	
	@Override
	public void run()
	{
		final List<Purchase> purchases = DonationData.getInstance().getPurchases().values().stream().flatMap(List::stream).filter(p ->
		{
			// Está ocupada
			if (p.isBusy())
				return false;
			
			// Compras recentes
			if (p.getStatus() != PurchaseStatus.WAITING || p.getDate() + TimeUnit.MINUTES.toMillis(5) < System.currentTimeMillis())
				return false;
			
			// Somente MP por enquanto
			if (p.getPaymentMethod() != PaymentMethod.MP_LINK && p.getPaymentMethod() != PaymentMethod.MP_PIX)
				return false;
			
			return true;
		}).collect(Collectors.toList());
		
		// Não é necessário consultar o MP
		if (purchases.isEmpty())
			return;
		
		// Data mais antiga para ser usada como critério na busca
		final long beginDate = purchases.stream().mapToLong(Purchase::getDate).min().orElse(0);
		final long endDate = purchases.stream().mapToLong(Purchase::getExpiration).max().orElse(0);
		if (beginDate == 0 || endDate == 0)
			return;
		
		try
		{
			final PaymentClient paymentClient = new PaymentClient();
			final Map<String, Object> filters = new HashMap<>();
			filters.put("sort", "date_created");
			filters.put("criteria", "desc");
			filters.put("range", "date_created");
			filters.put("begin_date", Instant.ofEpochMilli(beginDate).toString());
			filters.put("end_date", "NOW");

			final MPSearchRequest searchRequest = MPSearchRequest.builder().offset(0).limit(100).filters(filters).build();
			final MPResultsResourcesPage<Payment> search = paymentClient.search(searchRequest);
			
			for (Payment payment : search.getResults())
			{
				if (payment.getExternalReference() == null)
					continue;
				
				final int purchaseId = Integer.valueOf(payment.getExternalReference());
				final Purchase purchase = DonationData.getInstance().getPurchaseById(purchaseId);
				
				// Mudanças desde o status anterior
				if (purchase.getStatus() != PurchaseStatus.WAITING || purchase.isBusy())
					continue;
				
				// Nosso block
				purchase.setBusy(true);

				switch (payment.getStatus())
				{
					case "in_mediation":
					case "cancelled":
					case "refunded":
					case "charged_back":
					case "rejected":
					case "approved":
						((MercadoPago) purchase.getPaymentHandler()).handleMPCheck(purchase, payment);
				}
				
				purchase.setBusy(false);
			}
		}
		catch (MPApiException e)
		{
			System.out.println(e.getApiResponse().getContent());
			e.printStackTrace();
		}
		catch (MPException e)
		{
			e.printStackTrace();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static final DonationAutomaticPaymentTask getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder
	{
		protected static final DonationAutomaticPaymentTask INSTANCE = new DonationAutomaticPaymentTask();
	}
}
