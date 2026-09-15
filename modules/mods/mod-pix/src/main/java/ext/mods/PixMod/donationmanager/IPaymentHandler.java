/*
 * Copyright (c) Dev A.L.N. Todos os direitos reservados.
 */

package ext.mods.PixMod.donationmanager;

import java.util.UUID;

import ext.mods.commons.logging.CLogger;

import ext.mods.PixMod.donationmanager.purchase.Purchase;

public interface IPaymentHandler
{
	final CLogger LOGGER = new CLogger(IPaymentHandler.class.getName());
	
	void reload();
	void checkPurchase(Purchase purchase);
	void sendPurchase(Purchase purchase);
	void closePurchase(Purchase purchase);
	void refund(Purchase purchase);
	void resendEmail(Purchase purchase);
	
	/**
	 * Gera uma string aleatória a partir do UUID
	 * @param lenght : tamanho (máximo 32)
	 * @return
	 */
	public default String randomString(int lenght)
	{
        return UUID.randomUUID().toString().replaceAll("-", "").substring(0, lenght);
    }
}
