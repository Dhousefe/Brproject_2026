/*
 * Copyright (c) Dev A.L.N. Todos os direitos reservados.
 */

package ext.mods.PixMod.donationmanager;


import com.google.zxing.WriterException;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import ext.mods.commons.pool.ConnectionPool;
import ext.mods.commons.data.Pagination;
import ext.mods.commons.logging.CLogger;
import ext.mods.commons.pool.ThreadPool;
import ext.mods.commons.util.ArraysUtil;

import ext.mods.PixMod.donationmanager.paymenthandlers.MercadoPago;
import ext.mods.PixMod.donationmanager.paymenthandlers.binance.Binance;
import ext.mods.PixMod.donationmanager.paymenthandlers.paypal.PayPal;
import ext.mods.PixMod.donationmanager.purchase.Purchase;
import ext.mods.PixMod.donationmanager.purchase.PaymentMethod;
import ext.mods.PixMod.donationmanager.purchase.PurchaseStatus;
import ext.mods.PixMod.donationmanager.tasks.CryptoCurrencyTask;
import ext.mods.PixMod.donationmanager.tasks.DonationAutomaticPaymentTask;
import ext.mods.PixMod.donationmanager.tasks.DonationTaskManager;
import ext.mods.Config;
import ext.mods.gameserver.data.HTMLData;
import ext.mods.gameserver.data.xml.ItemData;
import ext.mods.gameserver.model.World;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.commons.lang.StringUtil;
import ext.mods.gameserver.network.serverpackets.ActionFailed;
import ext.mods.gameserver.network.serverpackets.NpcHtmlMessage;
import ext.mods.gameserver.network.serverpackets.PledgeCrest;
import ext.mods.gameserver.network.serverpackets.ShowCalculator;
import ext.mods.gameserver.network.serverpackets.TutorialCloseHtml;
import ext.mods.gameserver.network.serverpackets.TutorialShowHtml;
import ext.mods.PixMod.donation.DonationAnnounce;
import ext.mods.gameserver.enums.FloodProtector;
import ext.mods.config.ConfigDonation;
import ext.mods.config.ConfigHexId;
import br.project.spi.donation.CreatePurchaseResult;
import br.project.spi.donation.DonationConfigDto;
import br.project.spi.donation.DonationService;
import br.project.spi.donation.PurchaseStatusDto;
import br.project.spi.donation.PurchaseSummaryDto;
import ext.mods.gameserver.data.sql.PlayerInfoTable;

public class DonationManager implements DonationService
{
	private static final CLogger LOGGER = new CLogger(DonationManager.class.getName());

	private static final int HTML_ID = 9999;
	private static final String HTML_PATH = "html/mods/donation/";
	
	/** Memória do jogador: {@code pt} ou {@code en} — pastas sob {@code mods/donation/} (.pix / .pay). */
	public static final String MEMO_DONATION_HTML_LOCALE = "donation_html_locale";
	
	private static String donationHtmlFile(Player player, String file)
	{
		return HTML_PATH + getDonationHtmlSubfolder(player) + (file == null ? "" : file);
	}
	
	/** Ex.: {@code pt/} ou {@code en/}, ou vazio (HTML na raiz de donation). */
	public static String getDonationHtmlSubfolder(Player player)
	{
		if (player == null)
			return "";
		final Object o = player.getMemos().get(MEMO_DONATION_HTML_LOCALE);
		if (!(o instanceof String))
			return "";
		String s = ((String) o).trim().toLowerCase(java.util.Locale.ENGLISH);
		if (s.isEmpty())
			return "";
		if (!s.matches("[a-z]{2}"))
			return "";
		return s + "/";
	}
	
	private static final String HTML_EMPTY_TABLE = "<table><tr><td></td></tr><tr><td width=70>---</td><td width=115>---</td><td width=50>---</td><td width=75>---</td></tr><tr><td></td></tr></table>";
	private static final String HTML_LINE = "<img src=L2UI.SquareGray width=280 height=1>";
	public static final String HTML_PURCHASE_MESSAGE = "<br><table bgcolor=000000 cellpadding=4><tr><td align=center width=280><font color=ff0000>%s</font></td></tr><tr><td>%s</td></tr></table>";
	private static final String HTML_BANNER = "<tr><td></td><td><table bgcolor=ff0000><tr><td width=265 align=center>%s</td></tr></table></td></tr>";
	private static final String HTML_CHOICE_CHECKBOX = "<td fixwidth=1><button action=\"bypass donation %s_selector %s\" width=14 height=14 back=L2UI.control.checkBox_%s fore=L2UI.control.checkBox_%3$s></td><td align=left width=%d valign=top>%s</td>";
	private static final String HTML_CALCULATOR = "<tr><td align=center><a action=\"bypass donation calculator\">Calculadora</a></td></tr>";
	private static final String HTML_CHOICE_CLEAR = "<td align=center><button value=\"-\" action=\"bypass donation clear_selectors\" width=25 height=17 back=sek.cbui94 fore=sek.cbui92></td>";
	private static final String HTML_INPUT_BOX = "<edit var=\"Quantity\" type=number width=50 height=13 length=3>";
	private static final String HTML_SEARCH_CRYPTO = "<td width=14><button action=\"bypass donation search\" width=14 height=14 back=L2UI_ch3.QuestWndPlusBtn_over fore=L2UI_ch3.QuestWndPlusBtn></td>";
	private static final String HTML_QRCODE_BUTTON = "<td><button value=\"QR Code\" action=\"bypass donation qrcode %id%\" width=75 height=21 back=L2UI_ch3.Btn1_normalOn fore=L2UI_ch3.Btn1_normal></td>";
	private static final String HTML_LINK_BUTTON = "<td><button value=\"Link\" action=\"bypass donation link %id% false\" width=75 height=21 back=L2UI_ch3.Btn1_normalOn fore=L2UI_ch3.Btn1_normal></td>";
	private static final String HTML_CHECK_BUTTON = "<td><button value=\"Paguei\" action=\"bypass -h donation check %id%\" width=75 height=21 back=L2UI_ch3.Btn1_normalOn fore=L2UI_ch3.Btn1_normal></td>";
	private static final String HTML_REFRESH_BUTTON = "<td><table><tr><td><button action=\"bypass -h donation index\" width=14 height=14 back=L2UI_ch3.ShortcutWnd.shortcut_rotate_over fore=L2UI_ch3.ShortcutWnd.shortcut_rotate></td></tr></table></td>";

	private static final String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@(.+)$";
	private static final String CURRENCY_CODE_PATERN = "^[a-zA-Z]{3,6}$";

	private final static Map<Integer, Purchase> _waitingPurchases = new ConcurrentHashMap<>();
	private final static Map<Integer, CheckoutChoice> _checkoutChoice = new ConcurrentHashMap<>();
	private final static Map<String, IPaymentHandler> _paymentHandlers = new HashMap<>();
	
	public DonationManager()
	{
		// Tasks
		DonationTaskManager.getInstance();
		DonationAutomaticPaymentTask.getInstance();
		CurrencyManager.getInstance();
		
		// Handlers
		registerPaymentHandler("MP", new MercadoPago());
		registerPaymentHandler("PAYPAL", new PayPal());
		registerPaymentHandler("BINANCE", new Binance());
		
		// Banco de dados
		DonationData.getInstance().restore();
	}
	
	private static void registerPaymentHandler(String id, IPaymentHandler handler)
	{
		_paymentHandlers.put(id, handler);
	}
	
	public IPaymentHandler getPaymentHandler(String id)
	{
		return _paymentHandlers.get(id);
	}
	
	public void reload()
	{
		_paymentHandlers.values().forEach(IPaymentHandler::reload);
		_checkoutChoice.clear();
		CurrencyManager.getInstance().reload();
	}
	
	public boolean isEnabled()
	{
		if (!ConfigDonation.DONATION_ENABLED || ConfigDonation.DONATION_PURCHASABLE_ITEM == 0 || ItemData.getInstance().getTemplate(ConfigDonation.DONATION_PURCHASABLE_ITEM) == null)
			return false;
		
		if ((ConfigDonation.DONATION_MP_PIX || ConfigDonation.DONATION_MP_LINK) && ConfigDonation.DONATION_MP_TOKEN.isEmpty())
			return false;
		
		if (ConfigDonation.DONATION_PAYPAL_LINK && (ConfigDonation.DONATION_PAYPAL_CLIENT_ID.isEmpty() || ConfigDonation.DONATION_PAYPAL_CLIENT_SECRET.isEmpty() || ConfigDonation.DONATION_PAYPAL_ACCOUNT_EMAIL.isEmpty()))
			return false;
		
		if (ConfigDonation.DONATION_BINANCE_PAY && (ConfigDonation.DONATION_BINANCE_API_KEY.isEmpty() || ConfigDonation.DONATION_BINANCE_SECRET_KEY.isEmpty()))
			return false;
		
		return true;
	}
	
	private static CheckoutChoice getChoice(int playerId)
	{
		return _checkoutChoice.computeIfAbsent(playerId, k -> new CheckoutChoice());
	}
	
	public void handleBypass(Player player, String command)
	{
		try
		{
			final StringTokenizer st = new StringTokenizer(command, " ");
			final String action = st.nextToken();

			if (action.equals("htm"))
			{
				final String file = st.nextToken();
				if (file.equals("info.htm"))
					showCustomWindow(player, file);
			}
			else if (action.equals("email"))
			{
				final String next = st.nextToken();
				if (next.equals("view"))
				{
					// Remover compras pendentes
					if (_waitingPurchases.containsKey(player.getObjectId()))
						_waitingPurchases.remove(player.getObjectId());

					showEmailWindow(player, (String) player.getMemos().get("donation_email"));
				}
				else if (next.equals("set"))
					setPlayerEmail(player, st.hasMoreTokens() ? st.nextToken() : null);
			}
			else if (action.equals("index"))
			{
				if (st.countTokens() == 2)
					showIndexWindow(player, Boolean.valueOf(st.nextToken()), Boolean.valueOf(st.nextToken()));
				else
					showIndexWindow(player);
			}
			else if (action.equals("gateway_selector"))
				getChoice(player.getObjectId()).changeGatewayChoice(player, st.nextToken());
			else if (action.equals("currency_selector"))
				getChoice(player.getObjectId()).changeCurrencyChoice(player, st.nextToken());
			else if (action.equals("custom_selector"))
				getChoice(player.getObjectId()).changeCustom(player, st.nextToken());
			else if (action.equals("clear_selectors"))
			{
				final CheckoutChoice cc = getChoice(player.getObjectId());
				cc.setCurrency(null);
				cc.setGateway(null);
				showIndexWindow(player);
			}
			else if (action.equals("clear_cryptos"))
			{
				getChoice(player.getObjectId()).getCustomCryptos().clear();
				showSearchWindow(player, null, null);
			}
			else if (action.equals("buy"))
				newPurchase(player, st.hasMoreTokens() ? Integer.valueOf(st.nextToken()) : 0);
			else if (action.equals("calculator"))
			{
				if (ConfigDonation.DONATION_CALCULATOR)
					player.sendPacket(new ShowCalculator(4393));
			}
			else if (action.equals("search"))
			{
				if (st.hasMoreTokens())
					searchCriptoCurrency(player, st.nextToken().toUpperCase());
				else
					showSearchWindow(player, null, null);
			}
			else
			{
				final int id = Integer.valueOf(st.nextToken());
				if (action.equals("check"))
					checkPaymentStatus(player, id);
				else if (action.equals("status"))
					showPurchaseStatusWindow(player, id);
				else if (action.equals("hide"))
					hidePurchase(player, id);
				else if (action.equals("history"))
					showPurchaseHistoryWindow(player, id);
				else if (action.equals("cancel"))
					cancelPurchase(player, id);
				else if (action.equals("qrcode"))
					showQrCodeWindow(player, id);
				else if (action.equals("link"))
					showLinkWindow(player, id, st.nextToken().equals("true"));
				else if (action.equals("confirm"))
					showConfirmActionWindow(player, id, st.nextToken());
				else if (action.equals("closeqr"))
				{
					if (ConfigDonation.DONATION_ENABLED)
					{
						showPurchaseStatusWindow(player, id);
						player.sendPacket(TutorialCloseHtml.STATIC_PACKET);
					}
				}
				else if (action.equals("terms"))
					handleTermsActions(player, id, st.nextToken());	
			}
		}
		catch (Exception e)
		{
			// Não existem problemas no bypass, por que um player causaria isso?
			if (!player.isGM())
				player.logout(true);
			
			LOGGER.warn("Falha ao lidar com o bypass do DonationManager. Player: {}, Command: {}", e, player.getName(), command);
		}
	}
	
	/**
	 * Entrega a compra feita por um player que estava offline no momento em que os itens seriam entregues
	 * @param player
	 */
	public void offlinePlayer(Player player)
	{
		for (Purchase p : DonationData.getInstance().getPurchases(player.getObjectId()))
		{
			if (p.getStatus() != PurchaseStatus.OFFLINE)
				continue;
			if (!p.tryBeginOfflineDelivery())
				continue;

			player.addItem(p.getProductId(), p.getQuantity(), true);
			p.changeStatus(PurchaseStatus.COMPLETED);
			p.clearBusy();
			if (ConfigDonation.ANNOUNCE_DONATOR_ITEM_GLOBAL)
				DonationAnnounce.announceCompleted(player.getName());
		}
	}
	
	public Map<Integer, Purchase> getWaitingPurchases()
	{
		return _waitingPurchases;
	}

	public void showIndexWindow(Player player)
	{
		showIndexWindow(player, false, false);
	}
	
	public void showIndexWindow(Player player, boolean showGatewaySelector, boolean showCurrencySelector)
	{
		if (!isEnabled())
		{
			showCustomWindow(player, player.isGM() ? "index_disabled.htm" : "index_disabled_player.htm");
			return;
		}
		
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setItemId(HTML_ID);
		html.setFile(player.getLocale(), donationHtmlFile(player, "index_single.htm"));
		
		final StringBuilder sb = new StringBuilder();
		final CheckoutChoice cc = getChoice(player.getObjectId());
		cc.applyDefaultCheckoutWhenMultipleGateways();
		final Set<String> currencySet = new HashSet<>();
		String bannerMsg;
		
		if (showCurrencySelector || !showGatewaySelector && cc.getGateway() != null && cc.getCurrency() == null)
		{
			sb.append("<tr>");
			
			// Exibir somente as currencies compatíveis com o checkout selecionado (se houver um)
			for (PaymentMethod pm : PaymentMethod.values())
			{
				if (!pm.isEnabled())
					continue;
				
				if (cc.getGateway() != null && !pm.name().equals(cc.getGateway()))
					continue;

				// As cripto só são exibidas quando a binance está selecionada
				if (pm == PaymentMethod.BINANCE && !pm.name().equals(cc.getGateway()))
					continue;
				
				// Se o player não fez uma escolha, exibimos as moedas padrão
				if (pm == PaymentMethod.BINANCE && cc.getCustomCryptos().isEmpty())
					cc.getCustomCryptos().addAll(CurrencyManager.DEFAULT_CRYPTO_CURRENCIES);
				
				for (String currency : pm == PaymentMethod.BINANCE ? cc.getCustomCryptos() : pm.getAllCurrencies())
				{
					if (!CurrencyManager.getInstance().isExchangeAvailable(pm, currency))
						continue;
					
					if (currencySet.contains(currency))
						continue;
					
					if (!currencySet.isEmpty() && currencySet.size() % (pm == PaymentMethod.BINANCE ? 4 : 5) == 0)
						sb.append("</tr><tr>");
					
					sb.append(String.format(HTML_CHOICE_CHECKBOX, "currency", currency, currency.equals(cc.getCurrency()) ? "checked" : "unable", 32, currency));
					currencySet.add(currency);
					
					if (ConfigDonation.DONATION_BINANCE_PAY_CURRENCY[0].isEmpty() && "BINANCE".equals(cc.getGateway()) && cc.getCustomCryptos().size() == currencySet.size())
					{
						// Opção para pesquisar por moedas
						sb.append(HTML_SEARCH_CRYPTO);
					}
				}
			}
			
			if (currencySet.isEmpty())
			{
				// Nenhuma moeda disponível para pagamento
				sb.append("<td width=80 height=20 align=center>Indisponível</td>");
			}
			
			sb.append("</tr>");
			bannerMsg = "Escolha uma moeda de pagamento";
		}
		else if (showGatewaySelector || !showCurrencySelector && (cc.getGateway() == null || cc.getCurrency() == null))
		{
			sb.append("<tr>");
			
			// Exibir somente gateways compatíveis com a moeda selecionada
			for (PaymentMethod pm : PaymentMethod.values())
			{
				if (!pm.isEnabled())
					continue;
				
				if (cc.getCurrency() != null && !pm.getAllCurrencies().contains(cc.getCurrency()))
					continue;
				
				sb.append(String.format(HTML_CHOICE_CHECKBOX, "gateway", pm.name(), pm.name().equals(cc.getGateway()) ? "checked" : "unable", pm.getHtmlColumnWidth(), pm.getDisplayName()));
			}
			
			sb.append("</tr>");
			bannerMsg = "Escolha um método de pagamento";
		}
		else
			bannerMsg = "Escolha quanto deseja comprar";
		
		String price = "";
		String quantity = "";
		
		// Já temos as informações necessárias, podemos formatar os valores
		if (cc.getGateway() != null && cc.getCurrency() != null)
		{
			final PaymentMethod pm = PaymentMethod.valueOf(cc.getGateway());
			
			// Preview dos métodos selecionados
			if (!showCurrencySelector && !showGatewaySelector)
			{
				// Gateway
				sb.append("<tr>");
				sb.append(String.format(HTML_CHOICE_CHECKBOX, "gateway", cc.getGateway(), "checked", pm.getHtmlColumnWidth(), pm.getDisplayName()));
				
				// Currency
				sb.append(String.format(HTML_CHOICE_CHECKBOX, "currency", cc.getCurrency(), "checked", cc.getCurrency().length() > 3 ? 40 : 25, cc.getCurrency()));
				sb.append("</tr>");
			}
			
			// Os valores em cripto não precisam de formatação específica
			if (pm == PaymentMethod.BINANCE)
			{
				if (ConfigDonation.DONATION_DROPDOWN)
				{
					final StringBuilder sb2 = new StringBuilder();
					for (int qnt : pm.getDropdown())
						sb2.append(String.format("%d (%s);", qnt, pm.getPrice(cc.getCurrency(), qnt).toString()));
						
					quantity = sb2.toString();
				}
				
				price = "- " + cc.getGatewayPrice() + " (unidade)";
			}
			else
			{
				// O símbolo do euro (€) não está disponível no cliente
				final NumberFormat nf = NumberFormat.getCurrencyInstance();
				nf.setCurrency(Currency.getInstance(cc.getCurrency()));
				
				if (ConfigDonation.DONATION_DROPDOWN)
				{
					// Infelizmente não podemos exibir o símbolo da moeda no dropdown porque o cliente não o permite no bypass
					final StringBuilder sb2 = new StringBuilder();
					for (int qnt : pm.getDropdown())
						sb2.append(String.format("%d (%s);", qnt, pm.getPrice(cc.getCurrency(), qnt)));
					
					quantity = sb2.toString();
				}
				
				price = "- " + nf.format(cc.getGatewayPrice()) + " (unidade)";
			}
			
			html.replace("%gateway%", "Pagamento");
			html.replace("%currency%", "Moeda");
			html.replace("%refresh%", !pm.getMainCurrency().equals(cc.getCurrency()) ? HTML_REFRESH_BUTTON : "");
		}
		else
		{
			html.replace("%gateway%", cc.getGateway() == null ? "Pagamento" : cc.getGatewayName());
			html.replace("%currency%", cc.getCurrency() == null ? "Moeda" : cc.getCurrency());
			html.replace("%refresh%", "");
		}
		
		if (ConfigDonation.DONATION_DROPDOWN)
			quantity = String.format("<combobox var=Quantity list=\"%s\" width=105>", quantity);
		else
			quantity = HTML_INPUT_BOX;
		
		html.replace("%icon%", ItemData.getInstance().getTemplate(ConfigDonation.DONATION_PURCHASABLE_ITEM).getIcon());
		html.replace("%item_name%", ItemData.getInstance().getTemplate(ConfigDonation.DONATION_PURCHASABLE_ITEM).getName());
		html.replace("%price%", price);
		html.replace("%list%", sb.toString());
		html.replace("%banner%", String.format(HTML_BANNER, bannerMsg));
		html.replace("%gateway_selector%", String.valueOf(!showGatewaySelector));
		html.replace("%currency_selector%", String.valueOf(!showCurrencySelector));
		html.replace("%calculator%", ConfigDonation.DONATION_CALCULATOR ? HTML_CALCULATOR : "");
		html.replace("%quantity%", quantity.isEmpty() ? "<combobox var=Quantity list=\"\" width=90>" : quantity);
		html.replace("%clear_selectors%", cc.getCurrency() != null || cc.getGateway() != null ? HTML_CHOICE_CLEAR : "");
		player.sendPacket(html);
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}

	public void deleteOrExpire(Purchase purchase)
	{
		if (ConfigDonation.DONATION_DELETE_INACTIVE)
			DonationData.getInstance().delete(purchase, true, true);
		else
		{
			purchase.changeStatus(PurchaseStatus.EXPIRED);
			DonationData.getInstance().delete(purchase, false, true); // true porque não precisamos salvar, já pagamento não foi feito
		}
	}
	
	private static Purchase getPurchase(int playerId, int purchaseId)
	{
		return DonationData.getInstance().getPurchases(playerId).stream().filter(p -> p.getId() == purchaseId).findFirst().orElse(null);
	}
	
	public void showCustomWindow(Player player, String file, String replace)
	{
		if (player == null || !player.isOnline())
			return;

		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile(player.getLocale(), donationHtmlFile(player, file));
		html.setItemId(HTML_ID);
		html.replace("%replace%", replace != null ? replace : "");
		player.sendPacket(html);
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	public void showCustomWindow(Player player, String file)
	{
		showCustomWindow(player, file, null);
	}

	public void showCustomWindow(int playerId, String file)
	{
		showCustomWindow(World.getInstance().getPlayer(playerId), file, null);
	}

	private static void showFloodWindow(Player player, int purchaseId)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile(player.getLocale(), donationHtmlFile(player, "flood.htm"));
		html.setItemId(HTML_ID);
		html.replace("%id%", purchaseId);
		html.replace("%bypass%", "bypass donation " + (purchaseId != 0 ? "status " + purchaseId : "index"));
		player.sendPacket(html);
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}

	private void showConfirmActionWindow(Player player, int purchaseId, String action)
	{
		if (!isEnabled())
		{
			showIndexWindow(player);
			return;
		}
		
		if (!action.equals("cancel") && !action.equals("hide"))
			return;
		
		final Purchase p = getPurchase(player.getObjectId(), purchaseId);
		if (p == null)
			return;
		
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile(player.getLocale(), donationHtmlFile(player, "action.htm"));
		html.setItemId(HTML_ID);
		html.replace("%action%", action);
		html.replace("%purchase%", purchaseId);
		player.sendPacket(html);
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}

	private static void showEmailWindow(Player player, String address)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile(player.getLocale(), donationHtmlFile(player, "email.htm"));
		html.setItemId(HTML_ID);

		if (_waitingPurchases.containsKey(player.getObjectId()))
			html.replace("%address%", "<tr><td>Você será <font color=LEVEL>redirecionado ao pagamento</font> logo após concluir essa etapa.</td></tr>");
		else if (address == null)
			html.replace("%address%", "<tr><td align=center>Por favor, insira um endereço válido.</td></tr>");
		else
			html.replace("%address%", "<tr><td align=center>Endereço atual:</td></tr><tr><td align=center><font color=LEVEL>" + address + "</font></td></tr>");

		player.sendPacket(html);
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}

	private void showPurchaseStatusWindow(Player player, int id)
	{
		final Purchase p = getPurchase(player.getObjectId(), id);
		if (p == null)
			return;

		showPurchaseStatusWindow(player, p);
	}

	public void showPurchaseStatusWindow(Player player, Purchase p)
	{
		if (player == null || !player.isOnline())
			return;
		
//		if (!isEnabled())
//		{
//			showIndexWindow(player);
//			return;
//		}

		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		final long expiration = TimeUnit.MILLISECONDS.toMinutes(p.getExpiration() - System.currentTimeMillis());
		
		if (p.getStatus() == PurchaseStatus.WAITING || p.getStatus() == PurchaseStatus.CREATED || p.getStatus() == PurchaseStatus.PENDING)
		{
			html.setFile(player.getLocale(), donationHtmlFile(player, "status_waiting.htm"));
			if (p.getMessage() != null)
			{
				html.replace("%msg%", p.getMessage());
				p.setMessage(null);
			}
			else
				html.replace("%msg%", "");
			
			if (p.getPaymentMethod().sendMail())
				html.replace("%link%", HTML_LINK_BUTTON);
			else
				html.replace("%link%", "");

			if (p.getStatus() == PurchaseStatus.WAITING)
				html.replace("%check%", HTML_CHECK_BUTTON);
			else
				html.replace("%check%", "");

			html.replace("%qrcode%", HTML_QRCODE_BUTTON);
		}
		else
		{
			html.setFile(player.getLocale(), donationHtmlFile(player, "status_others.htm"));
			html.replace("%hide%", ConfigDonation.DONATION_HIDE_ENDED ? "<td width=52><a action=\"bypass donation confirm %id% hide\">Excluir</a></td>"  : "");
		}
		
		if (ConfigDonation.DONATION_REQUIRE_TERMS)
		{
			// essa compra foi feita quando essa config estava desativada
			if (!p.agreedTerms() && p.getStatus() != PurchaseStatus.PENDING)
				html.replace("%terms%", "");
			else
			{
				// Buscamos a tabela de um arquivo diferente
				html.replace("%terms%", HTMLData.getInstance().getHtm(player.getLocale(), donationHtmlFile(player, "table_terms.htm")));
				
				if (!p.agreedTerms())
				{
					html.replace("%bgcolor%", "bgcolor=ff0000");
					html.replace("%terms_cb%", "unable");
					html.replace("%bypass%", "action=\"bypass donation terms %id% checkbox\"");
				}
				else
				{
					html.replace("%terms_cb%", "checked");
					html.replace("%bgcolor%", "");
					html.replace("%bypass%", p.getStatus() == PurchaseStatus.PENDING ? "action=\"bypass donation terms %id% checkbox\"" : "");
				}
			}
		}
		else
			html.replace("%terms%", "");

		html.setItemId(HTML_ID);
		html.replace("%id%", p.getId());
		html.replace("%id_externo%", p.getPaymentId() == null ? "0" : p.getPaymentId());
		html.replace("%payment%", p.getPaymentMethod().getName());
		html.replace("%payment_type%", p.getPaymentMethod().getTypeName());
		html.replace("%currency%", p.getPaymentMethod() == PaymentMethod.MP_PIX ? "" : String.format("(%s)", p.getCurrency()));
		html.replace("%email%", p.getPlayerEmail());
		html.replace("%status%", p.getStatus().getDesc());
		html.replace("%date%", new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(p.getDate()));
		html.replace("%expiration%", + expiration > 1 ? expiration + " minutos" : " menos de 1 minuto");
		html.replace("%player%", player.getName());
		html.replace("%resume%", p.resume());
		player.sendPacket(html);
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}

	private static void showPurchaseHistoryWindow(Player player, int page)
	{
//		if (!isEnabled())
//		{
//			showIndexWindow(player);
//			return;
//		}
		
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile(player.getLocale(), donationHtmlFile(player, "history.htm"));
		html.setItemId(HTML_ID);

		final Pagination<Purchase> pagination = new Pagination<>(DonationData.getInstance().getPurchases(player.getObjectId()).stream(), page, 9, p -> p.getStatus() != PurchaseStatus.HIDDEN, Comparator.comparing(Purchase::getDate).reversed());
		for (Purchase p : pagination)
		{
			final String price = p.getFormatedPrice();
			pagination.append("<table><tr><td></td></tr><tr><td width=70>");
			pagination.append(new SimpleDateFormat("dd-MM-yyyy").format(p.getDate()));
			pagination.append("</td><td width=90>");
			pagination.append(StringUtil.trimAndDress(p.getProductName(), 12));
			pagination.append("</td><td width=75>");
			pagination.append(price.length() > 10 ? "..." : price);
			pagination.append("</td><td width=75>");
			pagination.append(String.format("<a action=\"bypass donation status %d %d\">", p.getId(), page), p.getStatus().getName(), "<a>");
			pagination.append("</td></tr><tr><td></td></tr></table>");
			pagination.append(HTML_LINE);
		}

		if (pagination.isEmpty())
			pagination.append(HTML_EMPTY_TABLE);
		else if (pagination.totalEntries() > 9)
			pagination.generatePages("bypass donation history %page%");

		html.replace("%table%", pagination.getContent());
		player.sendPacket(html);
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}

	public void showCheckoutWindow(Purchase purchase)
	{
		showCheckoutWindow(World.getInstance().getPlayer(purchase.getPlayerId()), purchase);
	}
	
	public void showCheckoutWindow(Player player, Purchase purchase)
	{
		if (player == null || !player.isOnline())
			return;
		
		if (!isEnabled())
		{
			showIndexWindow(player);
			return;
		}
		
		String msg = null;
		switch (purchase.getPaymentMethod())
		{
			case BINANCE:
				msg = "Use o aplicativo da Binance para escanear o código.<br1>\n"
					+ "Você também pode solicitar um e-mail contendo um link para à página de pagamento.";
				break;
			case MP_LINK:
			case PAYPAL:
				msg = "Use a câmera do seu celular para escanear o código e fazer o pagamento através do seu celular (no aplicativo ou navegador)<br1>"
					+ "Você também pode solicitar um e-mail contendo um link para à página de pagamento.";
				break;
			case MP_PIX:
				msg = "Use o aplicativo do seu banco para escanear o código do PIX.<br1>"
				+ "O MercadoPago irá lhe enviar um e-mail com o código \"cópia e cola\", você pode utilizá-lo também.";
				break;
		}

		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile(player.getLocale(), donationHtmlFile(player, purchase.wantQrCode() ? "checkout_qrcode.htm" : "checkout_link.htm"));
		html.setItemId(HTML_ID);
		html.replace("%id%", purchase.getId());
		html.replace("%resume%", purchase.resume());
		html.replace("%msg%", msg);
		html.replace("%payment%", purchase.getPaymentMethod().getName());
		player.sendPacket(html);
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}

	private void showQrCodeWindow(Player player, int purchaseId)
	{
		final Purchase p = getPurchase(player.getObjectId(), purchaseId);
		if (p == null)
			return;

		showQrCodeWindow(player, p);
	}

	public void showQrCodeWindow(Player player, Purchase p)
	{
		if (player == null || !player.isOnline())
			return;
		
		if (!isEnabled())
		{
			showIndexWindow(player);
			return;
		}
		
		if (!p.agreedTerms() || p.getStatus() != PurchaseStatus.WAITING && p.getStatus() != PurchaseStatus.CREATED && p.getStatus() != PurchaseStatus.PENDING)
		{
			showPurchaseStatusWindow(player, p);
			return;
		}

		if (p.getStatus() == PurchaseStatus.CREATED || p.getStatus() == PurchaseStatus.PENDING)
		{
			if (!isThreadSafe(player.getObjectId()))
				return;
			
			// Pediu o QRCODE, vamos dar prioridade a isso
			p.setWantQrCode(true);
			
			p.getPaymentHandler().sendPurchase(p);
			showCustomWindow(player, "requesting.htm");
			return;
		}
		
		if (p.getQrCode() == null)
			return;

		try
		{
			final byte[] qrCode = DDSConverter.createQRCode(p.getQrCode());
			final NpcHtmlMessage html = new NpcHtmlMessage(0);
			html.setFile(player.getLocale(), donationHtmlFile(player, "qrcode.htm"));
			
			if (p.getPaymentMethod() == PaymentMethod.MP_PIX)
			{
				final StringBuilder sb = new StringBuilder();
				if (!ConfigDonation.DONATION_MP_PIX_ACCOUNT_OWNER.isEmpty())
					sb.append("<tr><td align=center>TITULAR: " + ConfigDonation.DONATION_MP_PIX_ACCOUNT_OWNER.toUpperCase() + "</td></tr>");
				
				if (!ConfigDonation.DONATION_MP_PIX_ACCOUNT_CPF.isEmpty())
					sb.append("<tr><td align=center>CPF: " + ConfigDonation.DONATION_MP_PIX_ACCOUNT_CPF + "</td></tr>");
				
				if (!ConfigDonation.DONATION_MP_PIX_ACCOUNT_BANK.isEmpty())
					sb.append("<tr><td align=center>BANCO: " + ConfigDonation.DONATION_MP_PIX_ACCOUNT_BANK.toUpperCase() + "</td></tr>");
				
				html.replace("%info%", sb.length() == 0 ? "" : "<br><br><table width=280 cellpadding=4>" + sb.toString() + "</table>");
			}
			else
				html.replace("%info%", "");
			
			html.replace("%purchase%", p.getId());
			html.replace("%serverId%", ConfigHexId.SERVER_ID);
			player.sendPacket(new PledgeCrest(p.getId(), qrCode));
			player.sendPacket(new TutorialShowHtml(html.getContent()));
		}
		catch (WriterException e)
		{
		}
	}

	private void showLinkWindow(Player player, int purchaseId, boolean sendMail)
	{
		final Purchase p = getPurchase(player.getObjectId(), purchaseId);
		if (p == null)
			return;
		
		if (!isEnabled())
		{
			showIndexWindow(player);
			return;
		}
		
		if (!isThreadSafe(player.getObjectId()))
			return;

		if (!p.agreedTerms() || p.getStatus() != PurchaseStatus.WAITING && p.getStatus() != PurchaseStatus.CREATED && p.getStatus() != PurchaseStatus.PENDING)
		{
			showPurchaseStatusWindow(player, p);
			return;
		}

		if (p.getStatus() == PurchaseStatus.CREATED || p.getStatus() == PurchaseStatus.PENDING)
		{
			// Pediu o LINK, então vamos dar prioridade a isso
			if (p.getPaymentMethod().sendMail())
				p.setWantQrCode(false);
			
			showCustomWindow(player, "requesting.htm");
			p.getPaymentHandler().sendPurchase(p);
			return;
		}

		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile(player.getLocale(), donationHtmlFile(player, "link.htm"));
		html.setItemId(HTML_ID);
		html.replace("%id%", p.getId());
		html.replace("%resend%", String.format("<td width=64><a action=\"bypass -h donation link %d true\">%s</a></td>", p.getId(), p.getEmailCoint() == 0 ? "Enviar" : "Reenviar"));

		if (sendMail)
		{
			// O PayPal permite até dois reenvios por dia
			// https://developer.paypal.com/docs/api/invoicing/v2/#invoices_remind
			final int maxEmails = p.getPaymentMethod() == PaymentMethod.PAYPAL ? 2 : ConfigDonation.DONATION_MAXIMUM_NUMBER_EMAILS;
			
			if (p.getEmailCoint() >= maxEmails || maxEmails == 0)
				html.replace("%msg%", "Não é possível enviar um novo e-mail.");
			else
			{
				if (p.getExpiration() - System.currentTimeMillis() < TimeUnit.MINUTES.toMillis(1))
					html.replace("%msg%", "Não foi possível enviar um novo e-mail.");
				else if (p.getLastEmailTime() != 0 && p.getLastEmailTime() + TimeUnit.MINUTES.toMillis(2) > System.currentTimeMillis())
					html.replace("%msg%", "Aguarde antes de enviar um novo e-mail.");
				else
				{
					html.replace("%msg%", "E-mail reenviado!");
					p.getPaymentHandler().resendEmail(p);
				}
			}
			
			html.replace("%font%", "FF0000");
		}
		else
		{
			html.replace("%font%", "LEVEL");
			html.replace("%msg%", p.getEmailCoint() == 0 ? "Solicitar e-mail para pagamento" : "Já enviamos seu e-mail.");
		}

		player.sendPacket(html);
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	public void showSearchWindow(Player player, String msg, String search)
	{
//		if (!isEnabled())
//		{
//			showIndexWindow(player);
//			return;
//		}
		
		final PaymentMethod pm = PaymentMethod.valueOf("BINANCE");
		if (!ConfigDonation.DONATION_BINANCE_PAY && pm.getAltCurrencies() != null)
			return;
		
		final List<String> customs = getChoice(player.getObjectId()).getCustomCryptos();
		final StringBuilder sb1 = new StringBuilder();
		int count1 = 0;

		// Moedas em comum para todos
		for (String currency : CurrencyManager.DEFAULT_CRYPTO_CURRENCIES)
		{
			final String column = String.format(HTML_CHOICE_CHECKBOX, "custom", currency, customs.contains(currency) ? "checked" : "unable", 115, String.format("%s (%s)", currency, CurrencyManager.getInstance().convert(pm, currency, 1)));
			sb1.append(count1 % 2 == 0 ? "<tr>" + column : column + "</tr>");
			count1++;
		}
		
		// Moedas customs de todos os usuários
		final StringBuilder sb2 = new StringBuilder();
		int count2 = 0;
		
		for (Entry<String, BigDecimal> entry : CurrencyManager.getInstance().getCryptoExchangeRates().entrySet())
		{
			if (CurrencyManager.DEFAULT_CRYPTO_CURRENCIES.contains(entry.getKey()))
				continue;
			
			final String column = String.format(HTML_CHOICE_CHECKBOX, "custom", entry.getKey(), customs.contains(entry.getKey()) ? "checked" : "unable", 125, String.format("<font color=%s> %s (%s)</font>", entry.getKey().equals(search) ? "ff0000" : "ffffff", entry.getKey(), entry.getValue().toString()));
			sb2.append(count2 % 2 == 0 ? "<tr>" + column : column + "</tr>");
			count2++;
		}
		
		// TODO pagination
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile(player.getLocale(), donationHtmlFile(player, "search.htm"));
		html.setItemId(HTML_ID);
		html.replace("%list%", sb1.toString());
		html.replace("%result%", sb2.toString());
		html.replace("%msg%", msg != null ? msg + "<br>" : "");
		html.replace("%clean%", customs.isEmpty() ? "" : "<td width=35><a action=\"bypass donation clear_cryptos\">Limpar</a></td>");
		player.sendPacket(html);
	}

	private void setPlayerEmail(Player player, String email)
	{
		if (email == null || email.equals("0") || email.length() > 44 || email.length() < 6)
		{
			showEmailWindow(player, null);
			return;
		}

		// Phase 6: pure validation helper (unit-tested)
		if (!DonationValidation.isValidEmailFormat(email) || !DonationValidation.isEmailDomainAllowed(email, ConfigDonation.DONATION_ALLOWED_EMAILS))
		{
			showEmailWindow(player, null);
			return;
		}

		final Purchase p = _waitingPurchases.get(player.getObjectId());
		if (p != null)
		{
			p.setPlayerEmail(email);
			DonationData.getInstance().store(p);
			showPurchaseStatusWindow(player, p);
		}
		else
			showEmailWindow(player, email);

		player.getMemos().set("donation_email", email);
	}

	private void hidePurchase(Player player, int purchaseId)
	{
		if (!isEnabled())
		{
			showIndexWindow(player);
			return;
		}
		
		if (!ConfigDonation.DONATION_HIDE_ENDED)
			return;
		
		final Purchase p = getPurchase(player.getObjectId(), purchaseId);
		if (p == null)
			return;

		if (p.getStatus() != PurchaseStatus.COMPLETED && ConfigDonation.DONATION_DELETE_INACTIVE)
			DonationData.getInstance().delete(p, true, true);
		else
			p.changeStatus(PurchaseStatus.HIDDEN);
		
		showPurchaseHistoryWindow(player, 1);
	}

	private void cancelPurchase(Player player, int id)
	{
		if (!isEnabled())
		{
			showIndexWindow(player);
			return;
		}
		
		final Purchase p = getPurchase(player.getObjectId(), id);
		if (p == null)
			return;
		
		if (p.getStatus() == PurchaseStatus.HIDDEN)
		{
			showPurchaseHistoryWindow(player, 1);
			return;
		}
		
		if (p.isBusy())
		{
			showCustomWindow(player, "busy.htm");
			return;
		}

		if (p.getStatus() != PurchaseStatus.PENDING && p.getStatus() != PurchaseStatus.CREATED && p.getStatus() != PurchaseStatus.WAITING)
		{
			showPurchaseStatusWindow(player, p);
			return;
		}
		
		if (!isThreadSafe(player.getObjectId()))
			return;
		
		if (p.getQrCode() != null)
			player.sendPacket(TutorialCloseHtml.STATIC_PACKET);
			
		if (ConfigDonation.DONATION_DELETE_INACTIVE)
		{
			DonationData.getInstance().delete(p, true, true);
			showIndexWindow(player);
		}
		else
		{
			p.changeStatus(PurchaseStatus.CANCELED);
			DonationData.getInstance().delete(p, false, true);
			showPurchaseStatusWindow(player, p);
		}
		
		// é importante que o ID do pagamento esteja disponível no closePurchase()
		DonationTaskManager.getInstance().remove(p);
		p.getPaymentHandler().closePurchase(p);
	}
	
	private void searchCriptoCurrency(Player player, String search)
	{
//		if (!isEnabled())
//		{
//			showIndexWindow(player);
//			return;
//		}
		
		if (!ConfigDonation.DONATION_BINANCE_PAY)
			return;
		
		if (!search.matches(CURRENCY_CODE_PATERN))
		{
			showSearchWindow(player, "inválido", null);
			return;
		}
		
		if (CurrencyManager.getInstance().isCryptoAvailable(search))
		{
			showSearchWindow(player, null, search);
			return;
		}
		
		showCustomWindow(player, "requesting.htm");
		ThreadPool.execute(() -> CryptoCurrencyTask.getInstance().search(player.getObjectId(), search));
	}
	
	private void handleTermsActions(Player player, int id, String target)
	{
		if (!isEnabled())
		{
			showIndexWindow(player);
			return;
		}
		
		final Purchase p = getPurchase(player.getObjectId(), id);
		if (p == null)
			return;
		
		if (target.equals("htm"))
		{
			showCustomWindow(player, "terms.htm", String.valueOf(id));
			return;
		}
		
		if (target.equals("checkbox"))
		{
			p.setTermsStatus(!p.agreedTerms());
			showPurchaseStatusWindow(player, p);
		}
	}

	private void newPurchase(Player player, int quantity)
	{
		if (!isEnabled())
		{
			showIndexWindow(player);
			return;
		}
		
		if (!isThreadSafe(player.getObjectId()))
			return;
		
		if (!DonationValidation.isValidQuantity(quantity))
		{
			showIndexWindow(player);
			return;
		}
		
		final CheckoutChoice cc = getChoice(player.getObjectId());
		if (cc.getGateway() == null || cc.getCurrency() == null)
		{
			showIndexWindow(player);
			return;
		}
		
		final String email = (String) player.getMemos().get("donation_email");

		// Se ainda estamos definindo o e-mail, não consideramos flood
		if (email != null && (player.getClient() == null || !player.getClient().performAction(FloodProtector.DONATION_PAY)))
		{
			showFloodWindow(player, 0);
			return;
		}

		// A compra sempre é salva. Vamos utilizá-la para o bypass de continuar a compra caso o email não exista
		final Purchase purchase = new Purchase(player.getObjectId(), quantity, email, player.getClient() != null ? player.getClient().getIpAddress() : "127.0.0.1", cc);
		if (email == null)
		{
			_waitingPurchases.put(player.getObjectId(), purchase);
			showEmailWindow(player, null);
		}
		else
		{
			DonationData.getInstance().store(purchase);
			showPurchaseStatusWindow(player, purchase);
		}
		
		player.sendPacket(TutorialCloseHtml.STATIC_PACKET);
	}
	
	/*
	 * Um player pesquisou por uma moeda na Binance, e a busca foi concluída, vamos exibir o resultado
	 */
	public void onSearchCrypto(int playerId, String search)
	{
		final Player player = World.getInstance().getPlayer(playerId);
		if (player == null || !player.isOnline())
			return;
		
		if (!isEnabled())
		{
			showIndexWindow(player);
			return;
		}
		
		if (!CurrencyManager.getInstance().isCryptoAvailable(search))
		{
			showSearchWindow(player, "Não foi possível encontrar a moeda " + search + ".", null);
			return;
		}
		
		showSearchWindow(player, null, search);
	}

	public void onCompletedPayment(Purchase p)
	{
		if (p.getPaymentId() == null)
			return;
		// Atomic claim — only one fulfillment path may deliver items.
		if (!p.tryBeginFulfillment())
			return;

		final Player player = World.getInstance().getPlayer(p.getPlayerId());
		if (player != null && player.isOnline())
		{
			player.sendPacket(TutorialCloseHtml.STATIC_PACKET);
			p.changeStatus(PurchaseStatus.COMPLETED);

			final NpcHtmlMessage html = new NpcHtmlMessage(0);
			html.setFile(player.getLocale(), donationHtmlFile(player, "thankyou.htm"));
			html.setItemId(HTML_ID);
			html.replace("%icon%", ItemData.getInstance().getTemplate(p.getProductId()).getIcon());
			html.replace("%resume%", p.resume());
			player.addItem(p.getProductId(), p.getQuantity(), true);
			if (ConfigDonation.ANNOUNCE_DONATOR_ITEM_GLOBAL)
				DonationAnnounce.announceCompleted(player.getName());
			player.sendPacket(html);
			p.clearBusy();
		}
		else
		{
			// Vai receber no próximo login (busy cleared so offlinePlayer can claim)
			p.changeStatus(PurchaseStatus.OFFLINE);
			p.clearBusy();
		}
		
		if (ConfigDonation.DONATION_DELETE_PAYMENT_DATA)
			DonationData.getInstance().delete(p, false, true);
	}

	private void checkPaymentStatus(Player player, int purchaseId)
	{
//		if (!isEnabled())
//		{
//			showIndexWindow(player);
//			return;
//		}
		
		final Purchase purchase = getPurchase(player.getObjectId(), purchaseId);
		if (purchase == null || purchase.getStatus() != PurchaseStatus.WAITING)
			return;
		
		if (!isThreadSafe(player.getObjectId()))
			return;
		
		if (purchase.getStatus() != PurchaseStatus.WAITING)
		{
			player.sendPacket(TutorialCloseHtml.STATIC_PACKET);
			showPurchaseStatusWindow(player, purchase);
			return;
		}

		if (player.getClient() == null || !player.getClient().performAction(FloodProtector.DONATION_CHECK))
		{
			showFloodWindow(player, purchaseId);
			return;
		}

		purchase.getPaymentHandler().checkPurchase(purchase);
		showCustomWindow(player, "requesting.htm");
	}

	/**
	 * @param p
	 * @param clear : Caso não seja possível criar a no serviço de pagamento, não salvamos ela
	 */
	public void handleException(Purchase p, boolean clear)
	{
		if (clear && ConfigDonation.DONATION_DELETE_INACTIVE)
			DonationData.getInstance().delete(p, true, true);
		else
			p.changeStatus(PurchaseStatus.FAILED);

		showCustomWindow(p.getPlayerId(), "exception.htm");
	}
	
	/*
	 * Deleta ou expira uma purchase e a remove da task
	 */
	public void inactivePurchase(Purchase p)
	{
		DonationTaskManager.getInstance().remove(p);
		deleteOrExpire(p);
	}
	
	public void onPaymentNotFound(Purchase p)
	{
		final Player player = World.getInstance().getPlayer(p.getPlayerId());
		if (player == null || !player.isOnline())
			return;
			
		p.setMessagePaymentNotFound();
		DonationManager.getInstance().showPurchaseStatusWindow(player, p);
	}
	
	public void onPaymentFailed(Purchase p)
	{
		p.changeStatus(PurchaseStatus.FAILED);
		
		final Player player = World.getInstance().getPlayer(p.getPlayerId());
		if (player == null || !player.isOnline())
			return;
		
		player.sendPacket(TutorialCloseHtml.STATIC_PACKET);
		DonationManager.getInstance().showPurchaseStatusWindow(player, p);
	}
	
	/*
	 * Para evitar problemas de concorrência
	 * Seria melhor verificar apenas pela purchase atual ao invés de todas?
	 */
	public boolean isThreadSafe(int playerId)
	{
		if (DonationData.getInstance().getPurchases(playerId).stream().anyMatch(Purchase::isBusy))
		{
			showCustomWindow(playerId, "busy.htm");
			return false;
		}
		
		return true;
	}

	@Override
	public DonationConfigDto getConfig()
	{
		final int itemId = ConfigDonation.DONATION_PURCHASABLE_ITEM;
		final var template = ItemData.getInstance().getTemplate(itemId);
		final String itemName = template != null ? template.getName() : "Coin of Luck";
		final String itemIcon = template != null ? template.getIcon() : "icon.etc_coins_gold_i00";
		BigDecimal parsedPrice;
		try
		{
			parsedPrice = new BigDecimal(ConfigDonation.DONATION_MP_PIX_PRICE);
		}
		catch (Exception e)
		{
			parsedPrice = BigDecimal.ONE;
		}
		final BigDecimal pixPrice = parsedPrice;
		return new DonationConfigDto(
			isEnabled(),
			itemId,
			itemName,
			itemIcon,
			pixPrice,
			"BRL",
			ConfigDonation.DONATION_MP_PIX_DROPDOWN != null ? ConfigDonation.DONATION_MP_PIX_DROPDOWN : new int[0],
			DonationValidation.MIN_QUANTITY,
			DonationValidation.MAX_QUANTITY,
			ConfigDonation.DONATION_MP_PIX_EXPIRATION_TIME,
			ConfigDonation.DONATION_SERVER_NAME != null ? ConfigDonation.DONATION_SERVER_NAME : "BrProject 2026"
		);
	}

	@Override
	public CreatePurchaseResult createPixPurchase(int playerId, String accountName, String email, int quantity, String clientIp)
	{
		if (!isEnabled())
			return new CreatePurchaseResult(false, "Sistema de doações desativado.", 0, "", 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, "BRL", null, null, "FAILED", 0L);

		if (!DonationValidation.isValidQuantity(quantity))
			return new CreatePurchaseResult(false, "Quantidade inválida. Escolha entre " + DonationValidation.MIN_QUANTITY + " e " + DonationValidation.MAX_QUANTITY + ".", 0, "", 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, "BRL", null, null, "FAILED", 0L);

		if (!DonationValidation.isValidEmailFormat(email))
			return new CreatePurchaseResult(false, "E-mail inválido.", 0, "", 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, "BRL", null, null, "FAILED", 0L);

		if (ConfigDonation.DONATION_ALLOWED_EMAILS != null && ConfigDonation.DONATION_ALLOWED_EMAILS.length > 0)
		{
			if (!DonationValidation.isEmailDomainAllowed(email, ConfigDonation.DONATION_ALLOWED_EMAILS))
				return new CreatePurchaseResult(false, "Domínio de e-mail não permitido para o antifraude do gateway.", 0, "", 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, "BRL", null, null, "FAILED", 0L);
		}

		final String charName = PlayerInfoTable.getInstance().getPlayerName(playerId);
		if (charName == null || charName.isEmpty())
			return new CreatePurchaseResult(false, "Personagem não encontrado.", 0, "", 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, "BRL", null, null, "FAILED", 0L);

		final CheckoutChoice cc = new CheckoutChoice();
		cc.setGateway("MP_PIX");
		cc.setCurrency("BRL");

		final Purchase purchase = new Purchase(playerId, quantity, email, clientIp != null ? clientIp : "127.0.0.1", cc);
		DonationData.getInstance().store(purchase);

		final IPaymentHandler handler = getPaymentHandler("MP");
		if (handler instanceof MercadoPago mp)
		{
			try
			{
				mp.createPaymentSync(purchase);
				return new CreatePurchaseResult(
					true,
					"PIX gerado com sucesso.",
					purchase.getId(),
					charName,
					purchase.getProductId(),
					purchase.getQuantity(),
					purchase.getUnitPrice(),
					purchase.getTotalPrice(),
					purchase.getCurrency(),
					purchase.getQrCode(false),
					purchase.getLink(false),
					purchase.getStatus().name(),
					purchase.getExpiration()
				);
			}
			catch (Exception e)
			{
				LOGGER.error("Falha ao gerar pagamento PIX no Mercado Pago para purchase #{}", e, purchase.getId());
				purchase.changeStatus(PurchaseStatus.FAILED);
				return new CreatePurchaseResult(false, "Erro ao gerar PIX no Mercado Pago: " + (e.getMessage() != null ? e.getMessage() : "indisponível"), purchase.getId(), charName, purchase.getProductId(), quantity, purchase.getUnitPrice(), purchase.getTotalPrice(), "BRL", null, null, "FAILED", 0L);
			}
		}
		else
		{
			return new CreatePurchaseResult(false, "Gateway Mercado Pago não registrado.", 0, "", 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, "BRL", null, null, "FAILED", 0L);
		}
	}

	@Override
	public PurchaseStatusDto getPurchaseStatus(int purchaseId, boolean forceCheck)
	{
		final Purchase purchase = DonationData.getInstance().getPurchaseById(purchaseId);
		if (purchase == null)
			return new PurchaseStatusDto(false, purchaseId, "NOT_FOUND", "Compra não encontrada.", false, "", 0, BigDecimal.ZERO);

		if (forceCheck && purchase.getStatus() == PurchaseStatus.WAITING)
		{
			final IPaymentHandler handler = purchase.getPaymentHandler();
			if (handler instanceof MercadoPago mp)
			{
				try
				{
					mp.checkPaymentSync(purchase);
				}
				catch (Exception e)
				{
					LOGGER.debug("Erro ao checar status imediato da purchase #{}: {}", purchaseId, e.getMessage());
				}
			}
		}

		final boolean delivered = purchase.getStatus() == PurchaseStatus.COMPLETED;
		final String charName = PlayerInfoTable.getInstance().getPlayerName(purchase.getPlayerId());

		return new PurchaseStatusDto(
			true,
			purchase.getId(),
			purchase.getStatus().name(),
			purchase.getStatus().getDesc(),
			delivered,
			charName != null ? charName : "Desconhecido",
			purchase.getQuantity(),
			purchase.getTotalPrice()
		);
	}

	@Override
	public java.util.List<PurchaseSummaryDto> getPurchasesForAccount(String accountName, int playerId)
	{
		final java.util.List<PurchaseSummaryDto> result = new java.util.ArrayList<>();
		try
		{
			if (playerId > 0)
			{
				final java.util.List<Purchase> purchases = DonationData.getInstance().getPurchases(playerId);
				if (purchases != null && !purchases.isEmpty())
				{
					final String charName = PlayerInfoTable.getInstance().getPlayerName(playerId);
					for (Purchase p : purchases)
					{
						if (p == null)
							continue;
						final BigDecimal total = p.getTotalPrice() != null ? p.getTotalPrice() : BigDecimal.ZERO;
						final String stName = p.getStatus() != null ? p.getStatus().name() : "UNKNOWN";
						final String pmName = p.getPaymentMethod() != null ? p.getPaymentMethod().name() : "MP_PIX";
						result.add(new PurchaseSummaryDto(
							p.getId(),
							p.getDate(),
							charName != null ? charName : "",
							p.getQuantity(),
							total,
							p.getCurrency() != null ? p.getCurrency() : "BRL",
							stName,
							pmName
						));
					}
				}
			}
			else
			{
				// In-memory lookup across loaded purchases
				for (java.util.Map.Entry<Integer, java.util.List<Purchase>> entry : DonationData.getInstance().getPurchases().entrySet())
				{
					final int charId = entry.getKey();
					final String charAccount = PlayerInfoTable.getInstance().getPlayerAccountName(charId);
					if (accountName != null && accountName.equalsIgnoreCase(charAccount))
					{
						final String charName = PlayerInfoTable.getInstance().getPlayerName(charId);
						final java.util.List<Purchase> list = entry.getValue();
						if (list != null)
						{
							for (Purchase p : list)
							{
								if (p == null)
									continue;
								final BigDecimal total = p.getTotalPrice() != null ? p.getTotalPrice() : BigDecimal.ZERO;
								final String stName = p.getStatus() != null ? p.getStatus().name() : "UNKNOWN";
								final String pmName = p.getPaymentMethod() != null ? p.getPaymentMethod().name() : "MP_PIX";
								result.add(new PurchaseSummaryDto(
									p.getId(),
									p.getDate(),
									charName != null ? charName : "",
									p.getQuantity(),
									total,
									p.getCurrency() != null ? p.getCurrency() : "BRL",
									stName,
									pmName
								));
							}
						}
					}
				}
			}

			// Fallback: if in-memory returned empty, query the database directly for persisted donations
			if (result.isEmpty() && accountName != null && !accountName.isBlank())
			{
				try (Connection con = ConnectionPool.getConnection();
					PreparedStatement ps = con.prepareStatement(
						"SELECT d.purchase_id, d.date, c.char_name, d.quantity, d.unit_price, d.currency, d.status, d.payment_method " +
						"FROM donations d " +
						"INNER JOIN characters c ON c.obj_Id = d.player_id " +
						"WHERE c.account_name = ? " +
						"ORDER BY d.date DESC LIMIT 50"))
				{
					ps.setString(1, accountName);
					try (ResultSet rs = ps.executeQuery())
					{
						while (rs.next())
						{
							final double uPrice = rs.getDouble("unit_price");
							final int qty = rs.getInt("quantity");
							final BigDecimal total = BigDecimal.valueOf(uPrice).multiply(BigDecimal.valueOf(Math.max(qty, 1))).setScale(2, java.math.RoundingMode.HALF_UP);
							result.add(new PurchaseSummaryDto(
								rs.getInt("purchase_id"),
								rs.getLong("date"),
								rs.getString("char_name") != null ? rs.getString("char_name") : "",
								qty,
								total,
								rs.getString("currency") != null ? rs.getString("currency") : "BRL",
								rs.getString("status") != null ? rs.getString("status") : "WAITING",
								rs.getString("payment_method") != null ? rs.getString("payment_method") : "MP_PIX"
							));
						}
					}
				}
				catch (Exception dbEx)
				{
					LOGGER.debug("Direct DB donation history lookup note: {}", dbEx.getMessage());
				}
			}

			result.sort((a, b) -> Long.compare(b.date(), a.date()));
		}
		catch (Exception e)
		{
			LOGGER.error("Erro ao listar histórico de doações para a conta {}", e, accountName);
		}
		return result;
	}

	public static DonationManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder
	{
		protected static final DonationManager INSTANCE = new DonationManager();
	}
}
