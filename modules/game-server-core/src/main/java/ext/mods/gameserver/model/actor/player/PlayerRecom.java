package ext.mods.gameserver.model.actor.player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.model.actor.Player;

/**
 * Recommendation counters + character_recommends JDBC (att-ver-3.0 onda 3).
 */
public final class PlayerRecom
{
	private static final Logger LOGGER = LoggerFactory.getLogger(PlayerRecom.class);
	
	private static final String RESTORE_CHAR_RECOMS = "SELECT char_id,target_id FROM character_recommends WHERE char_id=?";
	private static final String ADD_CHAR_RECOM = "INSERT INTO character_recommends (char_id,target_id) VALUES (?,?)";
	private static final String UPDATE_TARGET_RECOM_HAVE = "UPDATE characters SET rec_have=? WHERE obj_Id=?";
	private static final String UPDATE_CHAR_RECOM_LEFT = "UPDATE characters SET rec_left=? WHERE obj_Id=?";
	
	private final Player _owner;
	
	private int _recomHave;
	private int _recomLeft;
	private final List<Integer> _recomChars = new ArrayList<>();
	
	public PlayerRecom(Player owner)
	{
		_owner = owner;
	}
	
	public int getRecomHave()
	{
		return _recomHave;
	}
	
	public void setRecomHave(int value)
	{
		_recomHave = clampRecomHave(value);
	}
	
	public void editRecomHave(int value)
	{
		_recomHave = clampRecomHave(_recomHave + value);
	}
	
	public int getRecomLeft()
	{
		return _recomLeft;
	}
	
	public void setRecomLeft(int value)
	{
		_recomLeft = clampRecomLeft(value);
	}
	
	public void decRecomLeft()
	{
		if (_recomLeft > 0)
			_recomLeft--;
	}
	
	public List<Integer> getRecomChars()
	{
		return _recomChars;
	}
	
	public boolean canRecom(Player target)
	{
		return !_recomChars.contains(target.getObjectId());
	}
	
	/** Pure clamp for rec_have (0..255). Package-visible for unit tests. */
	static int clampRecomHave(int value)
	{
		return Math.clamp(value, 0, 255);
	}
	
	/** Pure clamp for rec_left (0..9). Package-visible for unit tests. */
	static int clampRecomLeft(int value)
	{
		return Math.clamp(value, 0, 9);
	}
	
	/**
	 * Reverse in-memory side-effects of {@link #giveRecom} after a failed DB transaction.
	 * Package-visible for unit tests (pure list + counter logic).
	 *
	 * @param recomChars giver's given-target list (mutated)
	 * @param targetObjectId target that was recommended
	 * @param previousRecomLeft recom-left before the failed give
	 * @return previous recom-left to restore on the giver
	 */
	static int reverseGiveRecomState(List<Integer> recomChars, int targetObjectId, int previousRecomLeft)
	{
		recomChars.remove(Integer.valueOf(targetObjectId));
		return clampRecomLeft(previousRecomLeft);
	}
	
	public void giveRecom(Player target)
	{
		final int previousRecomLeft = _recomLeft;
		final int targetObjectId = target.getObjectId();
		
		target.editRecomHave(1);
		decRecomLeft();
		_recomChars.add(targetObjectId);
		
		boolean committed = false;
		try (Connection con = ConnectionPool.getConnection())
		{
			final boolean previousAutoCommit = con.getAutoCommit();
			con.setAutoCommit(false);
			try
			{
				try (PreparedStatement ps = con.prepareStatement(ADD_CHAR_RECOM))
				{
					ps.setInt(1, _owner.getObjectId());
					ps.setInt(2, targetObjectId);
					ps.execute();
				}
				
				try (PreparedStatement ps = con.prepareStatement(UPDATE_TARGET_RECOM_HAVE))
				{
					ps.setInt(1, target.getRecomHave());
					ps.setInt(2, targetObjectId);
					ps.execute();
				}
				
				try (PreparedStatement ps = con.prepareStatement(UPDATE_CHAR_RECOM_LEFT))
				{
					ps.setInt(1, getRecomLeft());
					ps.setInt(2, _owner.getObjectId());
					ps.execute();
				}
				
				con.commit();
				committed = true;
			}
			catch (Exception e)
			{
				try
				{
					con.rollback();
				}
				catch (Exception re)
				{
					LOGGER.warn("Couldn't rollback recommendation transaction.", re);
				}
				throw e;
			}
			finally
			{
				try
				{
					con.setAutoCommit(previousAutoCommit);
				}
				catch (Exception ignored)
				{
				}
			}
		}
		catch (Exception e)
		{
			if (!committed)
			{
				// Reverse in-memory mutations so client/server state matches DB.
				target.editRecomHave(-1);
				_recomLeft = reverseGiveRecomState(_recomChars, targetObjectId, previousRecomLeft);
			}
			LOGGER.error("Couldn't update player recommendations.", e);
		}
	}
	
	/** Load given-recom target ids for this character. */
	public void restore()
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(RESTORE_CHAR_RECOMS))
		{
			ps.setInt(1, _owner.getObjectId());
			
			try (ResultSet rset = ps.executeQuery())
			{
				while (rset.next())
					_recomChars.add(rset.getInt("target_id"));
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't restore recommendations.", e);
		}
	}
}
