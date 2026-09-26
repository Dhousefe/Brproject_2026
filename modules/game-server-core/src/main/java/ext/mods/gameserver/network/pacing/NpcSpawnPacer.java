package ext.mods.gameserver.network.pacing;

import java.util.Arrays;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import ext.mods.commons.pool.ThreadPool;
import ext.mods.config.ConfigServer;
import ext.mods.gameserver.geoengine.simd.SimdGeoMath;
import ext.mods.gameserver.model.World;
import ext.mods.gameserver.model.WorldObject;
import ext.mods.gameserver.model.actor.Npc;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.instance.CastleBlacksmith;
import ext.mods.gameserver.model.actor.instance.CastleDoorman;
import ext.mods.gameserver.model.actor.instance.CastleGatekeeper;
import ext.mods.gameserver.model.actor.instance.ClanHallDoorman;
import ext.mods.gameserver.model.actor.instance.ClanHallManagerNpc;
import ext.mods.gameserver.model.actor.instance.ClassMaster;
import ext.mods.gameserver.model.actor.instance.Doorman;
import ext.mods.gameserver.model.actor.instance.DungeonGatekeeper;
import ext.mods.gameserver.model.actor.instance.EventManager;
import ext.mods.gameserver.model.actor.instance.Fisherman;
import ext.mods.gameserver.model.actor.instance.Gatekeeper;
import ext.mods.gameserver.model.actor.instance.GrandBoss;
import ext.mods.gameserver.model.actor.instance.Merchant;
import ext.mods.gameserver.model.actor.instance.OlympiadManagerNpc;
import ext.mods.gameserver.model.actor.instance.RaidBoss;
import ext.mods.gameserver.model.actor.instance.SchemeBuffer;
import ext.mods.gameserver.model.actor.instance.VillageMaster;
import ext.mods.gameserver.model.actor.instance.WarehouseKeeper;

/**
 * Gerenciador de cadenciamento suave (pacing) e coalescing de pacotes NpcInfo e DeleteObject.
 *
 * By Dhousefe:
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
	/**
	 * Identifica se um NPC possui função essencial de infraestrutura e serviço no jogo.
	 * NPCs essenciais recebem prioridade máxima imediata no envio de NpcInfo.
	 */
	public static boolean isEssentialNpc(Npc npc)
	{
		if (npc == null)
			return false;

		return npc instanceof Gatekeeper
			|| npc instanceof CastleGatekeeper
			|| npc instanceof DungeonGatekeeper
			|| npc instanceof SchemeBuffer
			|| npc instanceof VillageMaster
			|| npc instanceof ClassMaster
			|| npc instanceof WarehouseKeeper
			|| npc instanceof ClanHallManagerNpc
			|| npc instanceof ClanHallDoorman
			|| npc instanceof CastleDoorman
			|| npc instanceof Doorman
			|| npc instanceof Merchant
			|| npc instanceof Fisherman
			|| npc instanceof CastleBlacksmith
			|| npc instanceof OlympiadManagerNpc
			|| npc instanceof EventManager;
	}

	/**
	 * Identifica se um NPC é um Boss ou relacionado a Raid (GrandBoss, RaidBoss ou Minions de Boss).
	 * Bosses possuem prioridade máxima instantânea absoluta no envio de NpcInfo.
	 */
	public static boolean isBossNpc(Npc npc)
	{
		if (npc == null)
			return false;

		return npc.isRaidBoss()
			|| npc.isRaidRelated()
			|| npc instanceof RaidBoss
			|| npc instanceof GrandBoss;
	}

	/**
	 * Identifica se um NPC representa uma ameaça iminente ou combate ativo contra o jogador/summon.
	 * Evita "dano fantasma" (phantom damage) onde o player toma dano/debuff de mobs antes de vê-los.
	 */
	public static boolean isThreateningPlayer(Player player, Npc npc)
	{
		if (npc == null || player == null)
			return false;

		
		final WorldObject target = npc.getTarget();
		if (target == player || (player.getSummon() != null && target == player.getSummon()))
			return true;

		
		if (npc.getAttack().isAttackingNow() || npc.getCast().isCastingNow())
		{
			if (target == player || (player.getSummon() != null && target == player.getSummon()))
				return true;
		}

		
		if (npc.isAggressive())
		{
			final int aggroRange = (npc.getTemplate() != null) ? npc.getTemplate().getAggroRange() : 0;
			
			final double dangerRadius = Math.max(650.0, aggroRange + 150.0);
			if (player.distance3D(npc) <= dangerRadius)
				return true;
		}

		return false;
	}

	/**
	 * Avalia se um NPC deve ignorar o cadenciamento e ser transmitido imediatamente ao cliente.
	 */
	public static boolean shouldBypassPacing(Player player, Npc npc)
	{
		if (npc == null || player == null)
			return false;

		
		if (isBossNpc(npc))
			return true;

		
		if (isEssentialNpc(npc))
			return true;

		
		if (isThreateningPlayer(player, npc))
			return true;

		
		if (player.distance3D(npc) <= 450.0)
			return true;

		return false;
	}

	public void queueNpc(Npc npc)
	{
		if (npc == null || _player == null || !_player.isOnline())
			return;

		if (!ConfigServer.ENABLE_NPC_INFO_PACING)
		{
			sendImmediate(npc);
			return;
		}

		
		if (shouldBypassPacing(_player, npc))
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
	 * Verifica se um NPC específico ainda está aguardando envio na fila de cadenciamento.
	 * Consulta O(1) thread-safe através do _pendingSet.
	 *
	 * @param objectId O objectId do NPC
	 * @return true se o NPC está pendente de despacho
	 */
	public boolean isPending(int objectId)
	{
		_lock.lock();
		try
		{
			return _pendingSet.contains(objectId);
		}
		finally
		{
			_lock.unlock();
		}
	}

	/**
	 * Força o envio imediato e prioritário de um NPC que estava pendente,
	 * retirando-o da fila cadenciada e transmitindo seu NpcInfo no socket.
	 * Fundamental para sincronização atômica pré-combate do AutoFarm.
	 *
	 * @param objectId O objectId do NPC a ser liberado imediatamente
	 * @return true se o NPC foi retirado da fila e despachado agora
	 */
	public boolean flushImmediate(int objectId)
	{
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

				final Npc npc = World.getInstance().getNpc(objectId);
				if (npc != null && npc.isVisible() && npc.isVisibleTo(_player) && _player.knows(npc))
				{
					sendImmediate(npc);
					return true;
				}
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
		final int playerX = _player.getX();
		final int playerY = _player.getY();

		_lock.lock();
		try
		{
			final int totalPending = _pendingQueue.size();
			if (totalPending == 0)
			{
				if (_dispatchTask != null)
				{
					_dispatchTask.cancel(false);
					_dispatchTask = null;
				}
				return;
			}

			if (totalPending <= batchSize)
			{
				while (!_pendingQueue.isEmpty())
				{
					final int objId = _pendingQueue.removeInt(0);
					_pendingSet.remove(objId);
					toDispatch[count++] = objId;
				}
			}
			else
			{
				
				final int candidatesCount = Math.min(totalPending, 64);
				final int[] candidateIds = new int[candidatesCount];
				final int[] targetX = new int[candidatesCount];
				final int[] targetY = new int[candidatesCount];
				final long[] distSq = new long[candidatesCount];
				final byte[] tiers = new byte[candidatesCount];
				int validCandidates = 0;

				for (int i = 0; i < totalPending && validCandidates < candidatesCount; i++)
				{
					final int objId = _pendingQueue.getInt(i);
					final Npc npc = World.getInstance().getNpc(objId);
					if (npc != null && npc.isVisible())
					{
						candidateIds[validCandidates] = objId;
						targetX[validCandidates] = npc.getX();
						targetY[validCandidates] = npc.getY();


						if (isBossNpc(npc) || isThreateningPlayer(_player, npc))
							tiers[validCandidates] = 0;
						else if (npc.isAggressive())
							tiers[validCandidates] = 1;
						else
							tiers[validCandidates] = 2;

						validCandidates++;
					}
				}

				if (validCandidates > 0)
				{
					
					SimdGeoMath.batchDistanceSquared2D(playerX, playerY, targetX, targetY, distSq, validCandidates);

					final Integer[] indices = new Integer[validCandidates];
					for (int i = 0; i < validCandidates; i++)
						indices[i] = i;

					
					Arrays.sort(indices, (a, b) -> {
						final int tierDiff = Byte.compare(tiers[a], tiers[b]);
						if (tierDiff != 0)
							return tierDiff;
						return Long.compare(distSq[a], distSq[b]);
					});
					final int toSelect = Math.min(batchSize, validCandidates);
					for (int i = 0; i < toSelect; i++)
					{
						final int bestIndex = indices[i];
						final int bestObjId = candidateIds[bestIndex];
						if (_pendingSet.remove(bestObjId))
						{
							_pendingQueue.rem(bestObjId);
							toDispatch[count++] = bestObjId;
						}
					}
				}
				else
				{
					while (!_pendingQueue.isEmpty() && count < batchSize)
					{
						final int objId = _pendingQueue.removeInt(0);
						_pendingSet.remove(objId);
						toDispatch[count++] = objId;
					}
				}
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
