package ext.mods.gameserver.network.pacing;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import ext.mods.commons.pool.ThreadPool;
import ext.mods.config.ConfigServer;
import ext.mods.gameserver.model.World;
import ext.mods.gameserver.model.actor.Npc;
import ext.mods.gameserver.model.actor.Player;

/**
 * Gerenciador de cadenciamento suave (pacing) e coalescing de pacotes NpcInfo e DeleteObject.
 *
 * Mechanical Sympathy:
 * - Evita congelamentos (freezes/FPS drops) no cliente L2 Unreal Engine 2.5 ao instanciar
 *   dezenas de atores 3D num único tick.
 * - Despacha os primeiros K monstros instantaneamente e cadencia os excedentes em micro-lotes
 *   (ex: 8 monstros a cada 40ms) garantindo 60 FPS contínuos no cliente.
 * - Coalescing: se um mob for deletado ou sair da área antes de ser transmitido ao cliente,
 *   cancela o NpcInfo pendente e suprime o DeleteObject correspondente (zero overhead de rede/CPU).
 * - Utiliza coleções primitivas Fastutil (zero heap allocation em regime permanente).
 */
public final class NpcSpawnPacer
{
	private final Player _player;
	private final ReentrantLock _lock = new ReentrantLock();

	// Fila primitiva de IDs de NPCs pendentes para despacho cadenciado
	private final IntArrayList _pendingQueue = new IntArrayList(32);
	private final IntOpenHashSet _pendingSet = new IntOpenHashSet(32);

	private ScheduledFuture<?> _dispatchTask = null;
	private int _recentBurstCount = 0;
	private long _lastBurstResetTime = 0L;

	public NpcSpawnPacer(Player player)
	{
		_player = player;
	}

	/**
	 * Submete um NPC para ser conhecido pelo jogador.
	 * Se estiver dentro do limite de burst imediato, envia agora.
	 * Caso contrário, enfileira para envio suave cadenciado.
	 */
	public void queueNpc(Npc npc)
	{
		if (npc == null || _player == null || !_player.isOnline())
			return;

		if (!ConfigServer.ENABLE_NPC_INFO_PACING)
		{
			sendImmediate(npc);
			return;
		}

		final long now = System.currentTimeMillis();
		final int burstLimit = Math.max(1, ConfigServer.NPC_INFO_IMMEDIATE_BURST_LIMIT);

		_lock.lock();
		try
		{
			if (now - _lastBurstResetTime > 150L)
			{
				_recentBurstCount = 0;
				_lastBurstResetTime = now;
			}

			// Se a fila pendente está vazia e ainda há cota de burst imediato:
			if (_pendingQueue.isEmpty() && _recentBurstCount < burstLimit)
			{
				_recentBurstCount++;
				sendImmediate(npc);
				return;
			}

			final int objId = npc.getObjectId();
			if (_pendingSet.add(objId))
			{
				_pendingQueue.add(objId);

				if (_dispatchTask == null || _dispatchTask.isDone())
				{
					final long interval = Math.max(10, ConfigServer.NPC_INFO_PACING_INTERVAL_MS);
					_dispatchTask = ThreadPool.scheduleAtFixedRate(this::dispatchPacedBatch, interval, interval);
				}
			}
		}
		finally
		{
			_lock.unlock();
		}
	}

	/**
	 * Cancela o envio de um NPC pendente caso ele saia da visão ou morra antes do despacho.
	 * @return true se o NPC foi cancelado antes de ser transmitido (permitindo suprimir o DeleteObject).
	 */
	public boolean cancelPending(int objectId)
	{
		if (!ConfigServer.ENABLE_DELETE_OBJECT_COALESCING)
			return false;

		_lock.lock();
		try
		{
			if (_pendingSet.remove(objectId))
			{
				_pendingQueue.rem(objectId);
				if (_pendingQueue.isEmpty() && _dispatchTask != null)
				{
					_dispatchTask.cancel(false);
					_dispatchTask = null;
				}
				return true;
			}
			return false;
		}
		finally
		{
			_lock.unlock();
		}
	}

	/**
	 * Pulso periódico de despacho cadenciado.
	 */
	private void dispatchPacedBatch()
	{
		if (_player == null || !_player.isOnline())
		{
			clear();
			return;
		}

		final int batchSize = Math.max(1, ConfigServer.NPC_INFO_PACED_BATCH_SIZE);
		final int[] toDispatch = new int[batchSize];
		int count = 0;

		_lock.lock();
		try
		{
			while (!_pendingQueue.isEmpty() && count < batchSize)
			{
				final int objId = _pendingQueue.removeInt(0);
				_pendingSet.remove(objId);
				toDispatch[count++] = objId;
			}

			if (_pendingQueue.isEmpty() && _dispatchTask != null)
			{
				_dispatchTask.cancel(false);
				_dispatchTask = null;
			}
		}
		finally
		{
			_lock.unlock();
		}

		if (count == 0)
			return;

		try (var batch = _player.openPacketBatch())
		{
			for (int i = 0; i < count; i++)
			{
				final int objId = toDispatch[i];
				final Npc npc = World.getInstance().getNpc(objId);
				if (npc != null && npc.isVisible() && npc.isVisibleTo(_player) && _player.knows(npc))
				{
					sendImmediate(npc);
				}
			}
		}
	}

	private void sendImmediate(Npc npc)
	{
		if (npc == null || _player == null || !_player.isOnline())
			return;

		npc.sendInfo(_player);

		if (npc.isVisibleTo(_player))
		{
			npc.getAI().describeStateToPlayer(_player);
		}
	}

	/**
	 * Libera todos os recursos e cancela agendamentos ao desconectar o jogador.
	 */
	public void clear()
	{
		_lock.lock();
		try
		{
			if (_dispatchTask != null)
			{
				_dispatchTask.cancel(false);
				_dispatchTask = null;
			}
			_pendingQueue.clear();
			_pendingSet.clear();
			_recentBurstCount = 0;
		}
		finally
		{
			_lock.unlock();
		}
	}
}
