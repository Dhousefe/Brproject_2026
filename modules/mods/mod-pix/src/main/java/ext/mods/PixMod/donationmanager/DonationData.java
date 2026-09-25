/*
 * Copyright (c) Dev A.L.N. Todos os direitos reservados.
 */

package ext.mods.PixMod.donationmanager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import ext.mods.commons.jdbc.DatabaseDialect;
import ext.mods.commons.logging.CLogger;
import ext.mods.commons.pool.ConnectionPool;

import ext.mods.PixMod.donationmanager.purchase.Purchase;
import ext.mods.PixMod.donationmanager.purchase.PurchaseStatus;
import ext.mods.PixMod.donationmanager.tasks.DonationTaskManager;
import ext.mods.Config;
import ext.mods.config.ConfigDonation;

public class DonationData
{
	private static final CLogger LOGGER = new CLogger(DonationData.class.getName());
	
	private static final String LOAD_PURCHASES = "SELECT * FROM donations";
	private static final String LOAD_PAYMENTS = "SELECT * FROM donations_payments";
	private static final String NEW_PURCHASE = "INSERT INTO donations (purchase_id, player_id,email,product_id,quantity,unit_price,date,status,payment_method,currency) VALUES (?,?,?,?,?,?,?,?,?,?)";
	private static final String DELETE_PURCHASE = "DELETE FROM donations WHERE purchase_id=?";
	private static final String DELETE_PAYMENT = "DELETE FROM donations_payments WHERE purchase_id=?";
	private static final String UPDATE_PURCHASE = "UPDATE donations SET payment_id=?, email=?, status=?, terms=? WHERE purchase_id=?";
	
	private final static Map<Integer, List<Purchase>> _playersPurchases = new ConcurrentHashMap<>();
	
	public Map<Integer, List<Purchase>> getPurchases()
	{
		return _playersPurchases;
	}
	
	public Purchase getPurchaseById(int id)
	{
		return _playersPurchases.values().stream().flatMap(List::stream).filter(p -> p.getId() == id).findFirst().orElse(null);
	}
	
	public List<Purchase> getPurchases(int playerId)
	{
		return _playersPurchases.computeIfAbsent(playerId, k -> new CopyOnWriteArrayList<>());
	}
	
	public void restore()
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(LOAD_PURCHASES);
			PreparedStatement ps2 = con.prepareStatement(LOAD_PAYMENTS))
		{
			// Vamos armazenar temporariamente as purchases aqui para não ter que ficar buscando na lista de _playersPurchases
			final Map<Integer, Purchase> tempMap = new HashMap<>();
			
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					final Purchase p = new Purchase(rs);
					
					// Essas compras estavam no banco de dados porque a config estava false
					// Agora o valor mudou, uma limpeza é feita
					if (ConfigDonation.DONATION_DELETE_INACTIVE && (p.getStatus() == PurchaseStatus.EXPIRED || p.getStatus() == PurchaseStatus.CANCELED || p.getStatus() == PurchaseStatus.FAILED))
					{
						delete(p, true, true);
						continue;
					}
					
					// Pode acontecer durante testes
//					if (p.getStatus() == PurchaseStatus.FINISHING)
//					{
//						inactivePurchase(p);
//						continue;
//					}
					
					if (p.getStatus() == PurchaseStatus.WAITING || p.getStatus() == PurchaseStatus.CREATED || p.getStatus() == PurchaseStatus.PENDING)
					{
						DonationTaskManager.getInstance().add(p);
						tempMap.put(p.getId(), p);
					}
					
					getPurchases(rs.getInt("player_id")).add(p);
				}
			}
			
			try (ResultSet rs2 = ps2.executeQuery())
			{
				while (rs2.next())
				{
					final Purchase p = tempMap.get(rs2.getInt("purchase_id"));
					p.setMpPreferenceId(rs2.getString("mp_preference_id"));
					p.setPaypalInvoiceId(rs2.getString("paypal_invoice_id"));
					p.setQrCode(rs2.getString("qrcode"));
					p.setLink(rs2.getString("link"));
				}
			}

			LOGGER.info("Loaded {} donations.", _playersPurchases.values().stream().mapToInt(List::size).sum());
		}
		catch (Exception e)
		{
			LOGGER.error("Não foi possível restaurar as doações.", e);
		}
	}
	
	public void delete(Purchase p, boolean purchase, boolean payment)
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			if (purchase)
			{
				try (PreparedStatement ps = con.prepareStatement(DELETE_PURCHASE))
				{
					ps.setInt(1, p.getId());
					ps.execute();					
				}
				
				final List<Purchase> list = _playersPurchases.get(p.getPlayerId());
				if (list != null)
					list.remove(p);
			}
			
			if (payment)
			{
				try (PreparedStatement ps2 = con.prepareStatement(DELETE_PAYMENT))
				{
					ps2.setInt(1, p.getId());
					ps2.execute();
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Não foi possível deletar a Purchase id #{}. Causa: {}", e, p.getId(), e.getMessage());
		}
	}
	
	public void update(Purchase p)
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(UPDATE_PURCHASE))
		{
			ps.setString(1, p.getPaymentId());
			ps.setString(2, p.getPlayerEmail());
			ps.setString(3, p.getStatus().name());
			ps.setInt(4, p.agreedTerms() ? 1 : 0);
			ps.setInt(5, p.getId());
			ps.execute();
			
			if (p.getStatus() == PurchaseStatus.WAITING || p.getStatus() == PurchaseStatus.COMPLETED)
			{
				try (PreparedStatement ps2 = con.prepareStatement(DatabaseDialect.upsert("donations_payments", "purchase_id, mp_preference_id, paypal_invoice_id, qrcode, link", "?,?,?,?,?", "purchase_id", "mp_preference_id, paypal_invoice_id, qrcode, link")))
				{
					ps2.setInt(1, p.getId());
					ps2.setString(2, p.getMpPreferenceId());
					ps2.setString(3, p.getPaypalInvoiceId());
					ps2.setString(4, p.getQrCode(true));
					ps2.setString(5, p.getLink(true));
					ps2.execute();
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Falhar ao atualizar a Purchase id #{} do player {}. Causa: {}", e, p.getId(), p.getPlayerName(), e.getMessage());
		}
	}
	
	public void store(Purchase p)
	{
		if (DonationManager.getInstance().getWaitingPurchases().containsKey(p.getPlayerId()))
		{
			p.updateDate(System.currentTimeMillis());
			DonationManager.getInstance().getWaitingPurchases().remove(p.getPlayerId());
		}

		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(NEW_PURCHASE))
		{
			ps.setInt(1, p.getId());
			ps.setInt(2, p.getPlayerId());
			ps.setString(3, p.getPlayerEmail());
			ps.setInt(4, p.getProductId());
			ps.setInt(5, p.getQuantity());
			ps.setBigDecimal(6, p.getUnitPrice());
			ps.setLong(7, p.getDate());
			ps.setString(8, p.getStatus().name());
			ps.setString(9, p.getPaymentMethod().name());
			ps.setString(10, p.getCurrency());
			ps.execute();
		}
		catch (Exception e)
		{
			LOGGER.error("Não foi possível criar a Purchase id #{} para o player {}. Causa: {}", e, p.getId(), p.getPlayerName(), e.getMessage());
		}
		finally
		{
			DonationTaskManager.getInstance().add(p);
			getPurchases(p.getPlayerId()).add(p);
		}
	}
	
	public static final DonationData getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder
	{
		protected static final DonationData INSTANCE = new DonationData();
	}
}
