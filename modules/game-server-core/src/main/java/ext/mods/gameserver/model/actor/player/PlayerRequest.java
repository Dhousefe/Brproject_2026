package ext.mods.gameserver.model.actor.player;

import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.container.player.Request;

/**
 * Social / invite request surface for a {@link Player} (att-ver-3.0 batch).
 */
public final class PlayerRequest
{
	/** Same constant historically on Player. */
	public static final int REQUEST_TIMEOUT = 15;
	
	private final Player _owner;
	private final Request _request;
	
	private Player _activeRequester;
	private long _requestExpireTime;
	
	public PlayerRequest(Player owner)
	{
		_owner = owner;
		_request = new Request(owner);
	}
	
	public Request getRequest()
	{
		return _request;
	}
	
	public void setActiveRequester(Player requester)
	{
		_activeRequester = requester;
	}
	
	public Player getActiveRequester()
	{
		if (_activeRequester != null && _activeRequester.isRequestExpired() && !_owner.getTrade().hasActiveTrade())
			_activeRequester = null;
		
		return _activeRequester;
	}
	
	public boolean isProcessingRequest()
	{
		return getActiveRequester() != null || _requestExpireTime > System.currentTimeMillis();
	}
	
	public boolean isProcessingTransaction()
	{
		return getActiveRequester() != null || _owner.getTrade().hasActiveTrade() || _requestExpireTime > System.currentTimeMillis();
	}
	
	public void onTransactionRequest(Player partner)
	{
		_requestExpireTime = System.currentTimeMillis() + REQUEST_TIMEOUT * 1000L;
		partner.setActiveRequester(_owner);
	}
	
	public boolean isRequestExpired()
	{
		return _requestExpireTime <= System.currentTimeMillis();
	}
	
	public void onTransactionResponse()
	{
		_requestExpireTime = 0;
	}
}
