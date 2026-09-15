package ext.mods.gameserver.model.actor.player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ext.mods.commons.pool.ConnectionPool;
import ext.mods.config.ConfigProject;
import ext.mods.gameserver.model.actor.Player;

/**
 * account_premium JDBC for a {@link Player} (att-ver-3.0 onda 3).
 */
public final class PlayerPremium
{
	private static final Logger LOGGER = LoggerFactory.getLogger(PlayerPremium.class);
	
	private static final String INSERT_PREMIUMSERVICE = "INSERT INTO account_premium (account_name,premium_service,enddate) values(?,?,?) ON DUPLICATE KEY UPDATE premium_service=?, enddate=?";
	private static final String RESTORE_PREMIUMSERVICE = "SELECT premium_service,enddate FROM account_premium WHERE account_name=?";
	private static final String UPDATE_PREMIUMSERVICE = "UPDATE account_premium SET premium_service=?,enddate=? WHERE account_name=?";
	
	private final Player _owner;
	
	public PlayerPremium(Player owner)
	{
		_owner = owner;
	}
	
	public void createPSdb()
	{
		if (!ConfigProject.USE_PREMIUM_SERVICE)
			return;
		
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(INSERT_PREMIUMSERVICE))
		{
			ps.setString(1, _owner.getAccountNamePlayer());
			ps.setInt(2, 0);
			ps.setLong(3, 0);
			ps.setInt(4, 0);
			ps.setLong(5, 0);
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			LOGGER.warn("PremiumService: Could not insert char data: {}", e.toString());
		}
	}
	
	public static void psTimeOver(String account)
	{
		if (!ConfigProject.USE_PREMIUM_SERVICE)
			return;
		
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(UPDATE_PREMIUMSERVICE))
		{
			ps.setInt(1, 0);
			ps.setLong(2, 0);
			ps.setString(3, account);
			ps.execute();
		}
		catch (SQLException e)
		{
			LOGGER.warn("PremiumService: Could not increase data: {}", e.toString());
		}
	}
	
	public long getPremServiceData()
	{
		if (!ConfigProject.USE_PREMIUM_SERVICE)
			return 0;
		
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(RESTORE_PREMIUMSERVICE))
		{
			ps.setString(1, _owner.getAccountName());
			try (ResultSet rs = ps.executeQuery())
			{
				if (rs.next())
					return rs.getLong("enddate");
			}
		}
		catch (SQLException e)
		{
			LOGGER.warn("PremiumService: Could not restore prem service data: {}", e.toString());
		}
		
		return 0;
	}
	
	public void restorePremServiceData(Player player, String account)
	{
		if (!ConfigProject.USE_PREMIUM_SERVICE)
		{
			player.getPremium().createPSdb();
			player.setPremiumService(0);
			return;
		}
		
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement statement = con.prepareStatement(RESTORE_PREMIUMSERVICE))
		{
			statement.setString(1, account);
			try (ResultSet rset = statement.executeQuery())
			{
				if (rset.next())
				{
					if (rset.getLong("enddate") <= System.currentTimeMillis())
					{
						psTimeOver(account);
						player.setPremiumService(0);
						
						player.getMemos().unset("name_color");
						player.getMemos().unset("title_color");
						
						player.getAppearance().setNameColor(0xFFFFFF);
						player.getAppearance().setTitleColor(0xFFFF77);
						player.broadcastUserInfo();
					}
					else
						player.setPremiumService(rset.getInt("premium_service"));
				}
				else
				{
					player.getPremium().createPSdb();
					player.setPremiumService(0);
					player.getMemos().unset("name_color");
					player.getMemos().unset("title_color");
					
					player.getAppearance().setNameColor(0xFFFFFF);
					player.getAppearance().setTitleColor(0xFFFF77);
					player.broadcastUserInfo();
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warn("PremiumService: Could not restore data for: {}", account, e);
		}
	}
}
