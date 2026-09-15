/*
 * Copyright (c) Dev A.L.N. Todos os direitos reservados.
 */

package ext.mods.PixMod.donationmanager.purchase;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import ext.mods.PixMod.donationmanager.CheckoutChoice;
import ext.mods.PixMod.donationmanager.DonationData;
import ext.mods.PixMod.donationmanager.DonationManager;
import ext.mods.PixMod.donationmanager.DonationValidation;
import ext.mods.PixMod.donationmanager.IPaymentHandler;
import ext.mods.Config;
import ext.mods.gameserver.data.sql.PlayerInfoTable;
import ext.mods.gameserver.data.xml.ItemData;
import ext.mods.gameserver.idfactory.IdFactory;
import ext.mods.config.ConfigDonation;

public class Purchase
{
	private final int _id;
	private final int _player;
	private final int _product;
	private final int _quantity;
	private final BigDecimal _unitPrice;
	private long _date;
	private long _lastEmailTime;
	private String _paymentId;
	private String _mpPreferenceId;
	private String _paypalInvoiceId;
	private String _email;
	private String _message;
	private String _ipAddress;
	private String _qrCode;
	private String _link;
	private String _currency;
	private boolean _busy;
	private boolean _wantQrCode;
	private boolean _terms;
	private int _emailCount;
	private PurchaseStatus _status;
	private PaymentMethod _paymentMethod;

	public Purchase(ResultSet rs) throws SQLException
	{
		final String pmStr = rs.getString("payment_method");
		PaymentMethod pm = PaymentMethod.MP_PIX;
		if (pmStr != null)
		{
			try
			{
				pm = PaymentMethod.valueOf(pmStr);
			}
			catch (Exception ignored)
			{
			}
		}
		_paymentMethod = pm;
		_id = rs.getInt("purchase_id");
		_paymentId = rs.getString("payment_id");
		_player = rs.getInt("player_id");
		_product = rs.getInt("product_id");
		_quantity = rs.getInt("quantity");
		BigDecimal price = null;
		try
		{
			price = rs.getBigDecimal("unit_price");
		}
		catch (Exception ignored)
		{
		}
		if (price == null)
		{
			try
			{
				price = BigDecimal.valueOf(rs.getDouble("unit_price"));
			}
			catch (Exception ignored)
			{
				price = BigDecimal.ONE;
			}
		}
		_unitPrice = price;
		_date = rs.getLong("date");
		_email = rs.getString("email");
		final String stStr = rs.getString("status");
		PurchaseStatus st = PurchaseStatus.WAITING;
		if (stStr != null)
		{
			try
			{
				st = PurchaseStatus.valueOf(stStr);
			}
			catch (Exception ignored)
			{
			}
		}
		_status = st;
		_currency = rs.getString("currency");
		_terms = rs.getBoolean("terms");
		_wantQrCode = _paymentMethod != null && _paymentMethod.isQrCode();
	}

	public Purchase(int player, int quantity, String email, String ipAddress, CheckoutChoice cc)
	{
		final PaymentMethod pm = PaymentMethod.valueOf(cc.getGateway());
		
		_id = IdFactory.getInstance().getNextId();
		_player = player;
		_product = ConfigDonation.DONATION_PURCHASABLE_ITEM;
		_quantity = quantity;
		_unitPrice = pm.getPrice(cc.getCurrency());
		_email = email;
		_ipAddress = ipAddress;
		_status = ConfigDonation.DONATION_REQUIRE_TERMS ? PurchaseStatus.PENDING : PurchaseStatus.CREATED;
		_date = System.currentTimeMillis();
		_paymentMethod = pm;
		_currency = cc.getCurrency();
		_wantQrCode = pm.isQrCode();
	}

	public final int getId()
	{
		return _id;
	}

	public final int getPlayerId()
	{
		return _player;
	}

	public final int getProductId()
	{
		return _product;
	}

	public final int getQuantity()
	{
		return _quantity;
	}

	public final BigDecimal getUnitPrice()
	{
		return _unitPrice != null ? _unitPrice : BigDecimal.ONE;
	}

	public final BigDecimal getTotalPrice()
	{
		return DonationValidation.totalPrice(_unitPrice, _quantity, _paymentMethod);
	}

	public long getDate()
	{
		return _date;
	}

	public void updateDate(long value)
	{
		_date = value;
	}

	public String getPlayerEmail()
	{
		return _email;
	}
	
	public String getIpAddress()
	{
		return _ipAddress;
	}

	public void setPlayerEmail(String address)
	{
		_email = address;
		DonationData.getInstance().update(this);
	}

	public PurchaseStatus getStatus()
	{
		return _status;
	}

	public void changeStatus(PurchaseStatus newStatus)
	{
		_status = newStatus;
		
		// Não é necessário salvar agora, vamos voltar em breve
		if (newStatus == PurchaseStatus.FINISHING)
			return;
		
		DonationData.getInstance().update(this);
	}
	
	/**
	 * Single-flight claim before granting items for a newly confirmed payment.
	 * Prevents double-delivery when poll + manual check race.
	 *
	 * @return true if this thread owns fulfillment
	 */
	public synchronized boolean tryBeginFulfillment()
	{
		if (!DonationValidation.isFulfillableStatus(_status))
			return false;
		if (_busy)
			return false;
		_busy = true;
		return true;
	}
	
	/**
	 * Claim offline delivery on login (status must already be {@link PurchaseStatus#OFFLINE}).
	 */
	public synchronized boolean tryBeginOfflineDelivery()
	{
		if (_status != PurchaseStatus.OFFLINE)
			return false;
		if (_busy)
			return false;
		_busy = true;
		return true;
	}
	
	public synchronized void clearBusy()
	{
		_busy = false;
	}
	
	public String getPlayerName()
	{
		return PlayerInfoTable.getInstance().getPlayerName(_player);
	}

	/**
	 * @return : ID da purchase no serviço de pagamento
	 */
	public String getPaymentId()
	{
		return _paymentId;
	}

	public void setPaymentId(String id)
	{
		_paymentId = id;
	}

	public String getMpPreferenceId()
	{
		return _mpPreferenceId;
	}

	public void setMpPreferenceId(String id)
	{
		_mpPreferenceId = id;
	}

	private String getMpPreferenceLink()
	{
		return _mpPreferenceId != null ? "https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=" + _mpPreferenceId : null;
	}
	
	private String getPaypalInvoiceLink()
	{
		return _paypalInvoiceId != null ? "https://www.sandbox.paypal.com/invoice/p/#" + _paypalInvoiceId : null;
	}
	
	public void setLink(String url)
	{
		_link = url;
	}

	public String getLink()
	{
		return getLink(false);
	}
	
	public String getLink(boolean toStore)
	{
		switch (_paymentMethod)
		{
			case MP_LINK:
				return toStore ? null : getMpPreferenceLink();
			case PAYPAL:
				return toStore ? null : getPaypalInvoiceLink();
			default:
				return _link;
		}
	}

	public String getProductName()
	{
		return ItemData.getInstance().getTemplate(_product).getName();
	}

	public void setQrCode(String value)
	{
		_qrCode = value;
	}
	
	public String getQrCode()
	{
		return getQrCode(false);
	}
	
	public String getQrCode(boolean toStore)
	{
		switch (_paymentMethod)
		{
			case MP_LINK:
				return toStore ? null : getMpPreferenceLink();
			case PAYPAL:
				return toStore ? null : getPaypalInvoiceLink();
			default:
				return _qrCode;
		}
	}

	public long getExpiration()
	{
		switch (getPaymentMethod())
		{
			case MP_LINK:
				return getDate() + TimeUnit.MINUTES.toMillis(Math.max(ConfigDonation.DONATION_MP_LINK_EXPIRATION_TIME, 1));
			case MP_PIX:
				// O prazo mínimo para o pix é de 30 minutos
				// https://www.mercadopago.com.br/developers/pt/docs/checkout-api/integration-configuration/integrate-with-pix
				return getDate() + TimeUnit.MINUTES.toMillis(Math.max(ConfigDonation.DONATION_MP_PIX_EXPIRATION_TIME, 30));
			case PAYPAL:
				return getDate() + TimeUnit.MINUTES.toMillis(Math.max(ConfigDonation.DONATION_PAYPAL_LINK_EXPIRATION_TIME, 1));
			case BINANCE:
				return getDate() + TimeUnit.MINUTES.toMillis(Math.max(ConfigDonation.DONATION_BINANCE_EXPIRATION_TIME, 1));
			default:
				return 0;
		}
		
	}

	public boolean timeExpired()
	{
		return System.currentTimeMillis() > getExpiration();
	}

	public void setMessage(String value)
	{
		_message = value;
	}
	
	public void setMessagePaymentNotFound()
	{
		setMessage(String.format(DonationManager.HTML_PURCHASE_MESSAGE, "Pagamento não encontrado", "Se você já pagou, aguarde um pouco e verifique novamente."));
	}

	public String getMessage()
	{
		return _message;
	}

	public PaymentMethod getPaymentMethod()
	{
		return _paymentMethod;
	}

	public int getEmailCoint()
	{
		return _emailCount;
	}

	public void logEmailSending()
	{
		_lastEmailTime = System.currentTimeMillis();
		_emailCount++;
	}

	public void setBusy(Boolean status)
	{
		_busy = status;
	}

	public boolean isBusy()
	{
		return _busy;
	}
	
	public String getFormatedPrice()
	{
		if (_paymentMethod == PaymentMethod.BINANCE)
			return getTotalPrice().toString();
		
		final NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.getDefault());
		nf.setCurrency(Currency.getInstance(_currency));
		return nf.format(getTotalPrice());
	}

	public String resume()
	{
		return String.format("%d %s - %s", getQuantity(), getProductName(), getFormatedPrice());
	}
	
	public void setPaypalInvoiceId(String id)
	{
		_paypalInvoiceId = id;
	}
	
	public String getPaypalInvoiceId()
	{
		return _paypalInvoiceId;
	}
	
	public String getCurrency()
	{
		return _currency;
	}
	
	public void setWantQrCode(boolean status)
	{
		_wantQrCode = status;
	}
	
	/*
	 * Alguns métodos de pagamento podem exibir o qrcode mesmo quando esse não é a forma de pagamento padrão
	 * Se o player optar por isso, então tratamos o checkout da compra de forma adequada a essa solicitação
	 */
	public boolean wantQrCode()
	{
		return _wantQrCode;
	}
	
	public void setTermsStatus(boolean value)
	{
		_terms = value;
	}
	
	public boolean agreedTerms()
	{
		return DonationValidation.agreedTerms(ConfigDonation.DONATION_REQUIRE_TERMS, _terms);
	}
	
	public long getLastEmailTime()
	{
		return _lastEmailTime;
	}
	
	public IPaymentHandler getPaymentHandler()
	{
		return DonationManager.getInstance().getPaymentHandler(_paymentMethod.getId());
	}
}
