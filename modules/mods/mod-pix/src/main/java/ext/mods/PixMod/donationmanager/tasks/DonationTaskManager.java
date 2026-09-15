/*
 * Copyright (c) Dev A.L.N. Todos os direitos reservados.
 */

package ext.mods.PixMod.donationmanager.tasks;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import ext.mods.commons.pool.ThreadPool;

import ext.mods.PixMod.donationmanager.DonationManager;
import ext.mods.PixMod.donationmanager.purchase.Purchase;
import ext.mods.PixMod.donationmanager.purchase.PurchaseStatus;

public class DonationTaskManager implements Runnable
{
	private final Set<Purchase> _list = ConcurrentHashMap.newKeySet();

	protected DonationTaskManager()
	{
		ThreadPool.scheduleAtFixedRate(this, 10000, 10000);
	}

	public void add(Purchase p)
	{
		_list.add(p);
	}

	public void remove(Purchase p)
	{
		_list.remove(p);
	}

	@Override
	public void run()
	{
		if (_list.isEmpty())
			return;

		for (Purchase purchase : _list)
		{
			// Voltamos depois
			if (purchase.isBusy())
				continue;
			
			// Essa compra não irá expirar mais
			if (purchase.getStatus() != PurchaseStatus.WAITING && purchase.getStatus() != PurchaseStatus.CREATED && purchase.getStatus() != PurchaseStatus.PENDING)
			{
				_list.remove(purchase);
				continue;
			}

			if (purchase.timeExpired())
			{
				// Essa compra pode ter sido paga e o player ainda não checou o status dela
				// Não removemos da task porque se sua execução for bloqueada, tentaremos novamente
				if (purchase.getPaymentId() != null || purchase.getMpPreferenceId() != null || purchase.getPaypalInvoiceId() != null)
				{
					purchase.changeStatus(PurchaseStatus.FINISHING);
					purchase.getPaymentHandler().checkPurchase(purchase);
					continue;
				}

				DonationManager.getInstance().deleteOrExpire(purchase);
				_list.remove(purchase);
			}
		}
	}

	public static final DonationTaskManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder
	{
		protected static final DonationTaskManager INSTANCE = new DonationTaskManager();
	}
}
