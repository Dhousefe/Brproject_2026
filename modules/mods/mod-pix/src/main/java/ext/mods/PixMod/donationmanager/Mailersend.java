/*
 * Copyright (c) Dev A.L.N. Todos os direitos reservados.
 */

package ext.mods.PixMod.donationmanager;

import com.mailersend.sdk.MailerSend;
import com.mailersend.sdk.emails.Email;
import com.mailersend.sdk.exceptions.MailerSendException;

import ext.mods.commons.logging.CLogger;
import ext.mods.commons.pool.ThreadPool;

import ext.mods.PixMod.donationmanager.purchase.Purchase;
import ext.mods.Config;
import ext.mods.config.ConfigDonation;
import ext.mods.config.ConfigServer;

public class Mailersend
{
	private static final CLogger LOGGER = new CLogger(Mailersend.class.getName());

	private static MailerSend _mailerSend;

	// TODO e o reload?
	static
	{
		_mailerSend = new MailerSend();
		_mailerSend.setToken(ConfigDonation.DONATION_MAILER_TOKEN);
	}

	public static void sendPurchaseMail(Purchase purchase)
	{
		if (!purchase.getPaymentMethod().sendMail())
			return;
		
		if (ConfigDonation.DONATION_MAILER_TOKEN.isEmpty())
		{
			LOGGER.info("O e-mail para a compra {} não foi enviado porque o token do MailerSend não foi definido.", purchase.getId());
			return;
		}
		
		if (ConfigServer.DEVELOPER)
			LOGGER.info("Enviando e-mail através do mailersend para a purchase #{}.", purchase.getId());
		
		if (ConfigDonation.DONATION_MAILER_TEMPLATE.isEmpty())
			ThreadPool.execute(() -> sendPlainMail(purchase));
		else
			ThreadPool.execute(() -> sendTemplateMail(purchase));
	}

	/*
	 * Apenas para demonstração
	 */
	public void sendPlainMail(String recipient, String subject, String message)
	{
		final Email email = new Email();
		email.setFrom("name", ConfigDonation.DONATION_MAILER_ADDRESS);
		email.addRecipient("name", recipient);
		email.setSubject(subject);
		email.setPlain(message);

		try
		{
			_mailerSend.emails().send(email);
		}
		catch (MailerSendException e)
		{
			LOGGER.error("Não foi possível enviar o e-mail para o endereço {}.", e, recipient);
		}
	}

	private static void sendTemplateMail(Purchase purchase)
	{
		final Email email = new Email();
		email.setFrom("name", ConfigDonation.DONATION_MAILER_ADDRESS);
		email.addRecipient("name", purchase.getPlayerEmail());
		email.setSubject("Link de pagamento para à compra realizada no " + ConfigDonation.DONATION_SERVER_NAME);
		email.setTemplateId(ConfigDonation.DONATION_MAILER_TEMPLATE);
		email.addPersonalization("link", purchase.getLink());
		email.addPersonalization("name", purchase.getPlayerName());

		try
		{
			_mailerSend.emails().send(email);
		}
		catch (MailerSendException e)
		{
			LOGGER.error("Não foi possível enviar o e-mail da compra #{}.", e, purchase.getId());
		}
	}

	private static void sendPlainMail(Purchase purchase)
	{
		final Email email = new Email();
		email.setFrom("name", ConfigDonation.DONATION_MAILER_ADDRESS);
		email.addRecipient("name", purchase.getPlayerEmail());
		email.setSubject("Link de pagamento para à compra realizada no " + ConfigDonation.DONATION_SERVER_NAME);
		email.setPlain("Olá " + purchase.getPlayerName() + ","
			+ "\n\n"
			+ "Obrigado por sua contribuição!"
			+ "\n\n"
			+ "Acesse o link abaixo para fazer o pagamento através do " + purchase.getPaymentMethod().getName() + "."
			+ "\n\n"
			+ "Após isso volte ao nosso servidor e confirme sua compra."
			+ "\n\n"
			+ purchase.getLink()
		);

		try
		{
			_mailerSend.emails().send(email);
		}
		catch (MailerSendException e)
		{
			LOGGER.error("Não foi possível enviar o e-mail da compra #{}.", e, purchase.getId());
		}
	}
}
