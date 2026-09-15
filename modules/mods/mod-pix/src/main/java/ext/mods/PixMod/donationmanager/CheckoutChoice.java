/*
 * Copyright (c) Dev A.L.N. Todos os direitos reservados.
 */

package ext.mods.PixMod.donationmanager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import ext.mods.PixMod.donationmanager.purchase.PaymentMethod;
import ext.mods.gameserver.model.actor.Player;

public class CheckoutChoice
{
	private String _gateway;
	private String _currency;
	private List<String> _customCryptos;
	
	public void changeCurrencyChoice(Player player, String currency)
	{
		final List<String> currencies = Arrays.stream(PaymentMethod.values()).flatMap(p -> p.getAllCurrencies().stream()).collect(Collectors.toList());
		if (!currencies.contains(currency) && !CurrencyManager.getInstance().isCryptoAvailable(currency))
			return;
		
		// Uma preferencia de currency já estava definida
		if (getCurrency() != null)
		{
			// A moeda selecionada é igual a preferencia anterior
			if (getCurrency().equals(currency))
			{
				// unselect
				setCurrency(null);
			}
			else
			{
				// Nova preferencia
				setCurrency(currency);
			}
		}
		else
		{
			// Nenhuma preferencia existia, apenas definimos a nova
			setCurrency(currency);
		}
		
		// Uma preferencia de gateway já estava definida
		if (getGateway() != null)
		{
			if (getGateway().equals("BINANCE"))
			{
				// Uma nova moeda foi adicionada por este player
				if (!PaymentMethod.valueOf(getGateway()).getAllCurrencies().contains(currency))
					getCustomCryptos().add(currency);
			}
			
			// Não existe uma preferencia de moeda
			if (getCurrency() == null)
			{
				// O gateway possui somente um método de pagamento
				if (PaymentMethod.valueOf(getGateway()).getAllCurrencies().size() == 1)
				{
					// unselect
					setGateway(null);
				}
			}
		}
		else
		{
			final List<PaymentMethod> pmList = Arrays.stream(PaymentMethod.values()).filter(p -> p.getAllCurrencies().contains(getCurrency())).collect(Collectors.toList());
			
			// A currency escolhida possui somente um gateway
			if (pmList.size() == 1)
			{
				// O gateway é definido automaticamente
				setGateway(pmList.iterator().next().name());
			}
		}
			
		DonationManager.getInstance().showIndexWindow(player);
	}
	
	public void changeGatewayChoice(Player player, String gateway)
	{
		try
		{
			final PaymentMethod pm = PaymentMethod.valueOf(gateway);
			boolean deactivating = false;
			
			// Uma preferencia de gateway já estava definida
			if (getGateway() != null)
			{
				// O gateway selecionado é igual a preferencia anterior
				if (getGateway().equals(gateway))
				{
					// Está desativando o checkout anterior
					deactivating = true;
					
					// unselect
					setGateway(null);
					setCurrency(null);
				}
				else
				{
					// Nova preferencia
					setGateway(gateway);
				}
			}
			else
			{
				// Nenhuma preferencia existia, definimos a nova
				setGateway(gateway);
			}
			
			// Uma preferencia de currency já estava definida
			if (getCurrency() != null)
			{
				// O novo método de pagamento é incompatível com a moeda anterior
				if (!pm.getAllCurrencies().contains(getCurrency()))
					setCurrency(null);
				
			}
			else if (!deactivating)
			{
				final List<PaymentMethod> pmList = Arrays.stream(PaymentMethod.values()).filter(p -> p.name().equals(gateway) && p.getAllCurrencies().size() == 1).collect(Collectors.toList());
				
				// O gateway escolhido possui apenas uma currency
				if (pmList.size() == 1)
				{
					// A currency é definida automaticamente se só houver uma
					setCurrency(pmList.iterator().next().getMainCurrency());
				}
			}
			
			DonationManager.getInstance().showIndexWindow(player);
		}
		catch (IllegalArgumentException e)
		{
		}
	}
	
	public void changeCustom(Player player, String currency)
	{
		if (!CurrencyManager.getInstance().isCryptoAvailable(currency))
			return;
		
		if (getCustomCryptos().contains(currency))
			getCustomCryptos().remove(currency);
		else
		{
			if (getCustomCryptos().size() >= 8)
			{
				DonationManager.getInstance().showSearchWindow(player, "Você atingiu o número máximo.", null);
				return;
			}
			
			getCustomCryptos().add(currency);
		}
			
		
		DonationManager.getInstance().showSearchWindow(player, null, null);
	}
	
	/**
	 * Com vários gateways ativos (ex.: PIX + PayPal), o jogador abria a loja sem seleção.
	 * Pré-seleciona PIX ({@link PaymentMethod#MP_PIX}) + moeda principal quando o PIX estiver habilitado.
	 */
	public void applyDefaultCheckoutWhenMultipleGateways()
	{
		if (_gateway != null || _currency != null)
			return;

		final List<PaymentMethod> enabled = Arrays.stream(PaymentMethod.values()).filter(PaymentMethod::isEnabled).collect(Collectors.toList());
		if (enabled.size() <= 1)
			return;

		if (enabled.contains(PaymentMethod.MP_PIX))
		{
			setGateway(PaymentMethod.MP_PIX.name());
			setCurrency(PaymentMethod.MP_PIX.getMainCurrency());
			return;
		}

		for (PaymentMethod pm : enabled)
		{
			setGateway(pm.name());
			if (pm.getAllCurrencies().size() == 1)
				setCurrency(pm.getMainCurrency());
			break;
		}
	}

	public String getGateway()
	{
		// Se somente um método de pagamento estiver ativo, ele será definido como padrão
		if (_gateway == null && _currency == null)
		{
			final List<PaymentMethod> pmList = Arrays.stream(PaymentMethod.values()).filter(p -> p.isEnabled()).collect(Collectors.toList());
			if (pmList.size() == 1)
			{
				final PaymentMethod pm = pmList.iterator().next();
				_gateway = pm.name();
				
				if (pm.getAllCurrencies().size() == 1)
					_currency = pm.getMainCurrency();
			}
		}
		
		return _gateway;
	}
	
	public void setGateway(String gateway)
	{
		_gateway = gateway;
	}

	public String getCurrency()
	{
		return _currency;
	}

	public void setCurrency(String currency)
	{
		_currency = currency;
	}
	
	public String getGatewayName()
	{
		return PaymentMethod.valueOf(_gateway).getDisplayName();
	}
	
	public BigDecimal getGatewayPrice()
	{
		return getGatewayPrice(1);
	}
	
	public BigDecimal getGatewayPrice(int qnt)
	{
		return PaymentMethod.valueOf(_gateway).getPrice(_currency, qnt);
	}
	
	/**
	 * @return : Lista de moedas padrão e "adicionadas" por este player através da função de pesquisa. Pode acontecer caso DONATION_BINANCE_PAY_CURRENCY esteja vazio
	 */
	public final List<String> getCustomCryptos()
	{
		if (_customCryptos == null)
			_customCryptos = new ArrayList<>();
		
		return _customCryptos;
	}
}
