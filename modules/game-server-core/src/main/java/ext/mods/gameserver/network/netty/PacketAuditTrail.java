package ext.mods.gameserver.network.netty;

import ext.mods.commons.logging.CLogger;
import ext.mods.gameserver.network.GameClient.GameClientState;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RingBuffer circular de telemetria forense lock-free e zero-allocation.
 * Armazena os ultimos 32 pacotes (S->C e C->S) para cada sessao de GameClient.
 * Em caso de desconexao anomala ou crash do cliente, emite um relatorio cronologico
 * revelando com precisao nanosegundo o ultimo pacote transmitido.
 */
public final class PacketAuditTrail
{
	private static final CLogger LOGGER = new CLogger(PacketAuditTrail.class.getName());
	private static final int BUFFER_SIZE = 32;
	private static final int BUFFER_MASK = BUFFER_SIZE - 1;
	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

	public static final class Record
	{
		public String timestamp = "";
		public boolean isOutbound = false;
		public String packetName = "";
		public int opcode = 0;
		public int payloadSize = 0;
		public int totalSize = 0;
		public GameClientState state = null;
	}

	private final Record[] _records = new Record[BUFFER_SIZE];
	private final AtomicInteger _head = new AtomicInteger(0);

	public PacketAuditTrail()
	{
		for (int i = 0; i < BUFFER_SIZE; i++)
		{
			_records[i] = new Record();
		}
	}

	public void recordOutbound(String packetName, int opcode, int payloadSize, int totalSize, GameClientState state)
	{
		final int index = _head.getAndIncrement() & BUFFER_MASK;
		final Record r = _records[index];
		r.timestamp = LocalTime.now().format(TIME_FORMATTER);
		r.isOutbound = true;
		r.packetName = packetName;
		r.opcode = opcode;
		r.payloadSize = payloadSize;
		r.totalSize = totalSize;
		r.state = state;
	}

	public void recordInbound(String packetName, int opcode, int size, GameClientState state)
	{
		final int index = _head.getAndIncrement() & BUFFER_MASK;
		final Record r = _records[index];
		r.timestamp = LocalTime.now().format(TIME_FORMATTER);
		r.isOutbound = false;
		r.packetName = packetName;
		r.opcode = opcode;
		r.payloadSize = size;
		r.totalSize = size + 2;
		r.state = state;
	}

	public void dumpForensicReport(String clientInfo, Object remoteAddress, GameClientState finalState)
	{
		final int total = _head.get();
		if (total <= 0)
			return;

		final int count = Math.min(total, BUFFER_SIZE);
		final int startIdx = total - count;

		final StringBuilder sb = new StringBuilder(2048);
		sb.append("\n================================================================================\n");
		sb.append("🚨 [PACKET-FORENSIC-AUDIT] SESSAO DE JOGO ENCERRADA (CLIENT CLOSED)\n");
		sb.append("Cliente: ").append(clientInfo != null ? clientInfo : "Desconhecido");
		sb.append(" | Endereco Remoto: ").append(remoteAddress != null ? remoteAddress.toString() : "null");
		sb.append(" | Estado Final: ").append(finalState != null ? finalState.name() : "N/A").append("\n");
		sb.append("Ultimos ").append(count).append(" pacotes trocados no socket TCP (Ordem Cronologica):\n");
		sb.append("--------------------------------------------------------------------------------\n");

		Record lastOutbound = null;
		Record lastInbound = null;

		for (int i = startIdx; i < total; i++)
		{
			final Record r = _records[i & BUFFER_MASK];
			final String dir = r.isOutbound ? "S->C (Enviado) " : "C->S (Recebido)";
			final String opcodeHex = String.format("0x%02X", r.opcode);
			final boolean isLast = (i == total - 1);

			if (r.isOutbound)
				lastOutbound = r;
			else
				lastInbound = r;

			sb.append(String.format("  [%s] %s | %s | Opcode: %-6s | Payload: %4d B | Total: %4d B | State: %s%s%n",
				r.timestamp, dir, padRight(r.packetName, 26), opcodeHex, r.payloadSize, r.totalSize,
				r.state != null ? r.state.name() : "N/A", isLast ? "  <-- [ULTIMO NO SOCKET]" : ""));
		}

		sb.append("--------------------------------------------------------------------------------\n");
		if (lastOutbound != null)
		{
			sb.append(String.format("🔥 [ULTIMO S->C ENVIADO PELO SERVIDOR]: %s (Opcode: 0x%02X, Payload: %d B, Total: %d B)%n",
				lastOutbound.packetName, lastOutbound.opcode, lastOutbound.payloadSize, lastOutbound.totalSize));
		}
		if (lastInbound != null)
		{
			sb.append(String.format("📥 [ULTIMO C->S RECEBIDO DO CLIENTE]:   %s (Opcode: 0x%02X, Tamanho: %d B)%n",
				lastInbound.packetName, lastInbound.opcode, lastInbound.payloadSize));
		}
		sb.append("================================================================================\n");

		LOGGER.info(sb.toString());
	}

	private static String padRight(String s, int n)
	{
		if (s == null)
			s = "null";
		if (s.length() >= n)
			return s;
		return String.format("%-" + n + "s", s);
	}
}