/*
* Copyleft © 2024-2026 L2Brproject
*/
package ext.mods.security.fail2ban.netty;

import ext.mods.security.fail2ban.core.BanManager;
import ext.mods.security.fail2ban.core.ClientIpResolver;
import ext.mods.security.fail2ban.core.PanicMode;
import ext.mods.security.fail2ban.detection.Decision;
import ext.mods.security.fail2ban.detection.DetectionEngine;
import ext.mods.security.fail2ban.simd.CleanRoomManager;
import ext.mods.security.fail2ban.simd.PacketVector128;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.AttributeKey;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Netty ChannelInboundHandler for Fail2Ban: intercepts connections and applies bans.
 * MUST be first in pipeline (before codec/business logic).
 * Sharable across multiple channels for efficiency.
 */
@ChannelHandler.Sharable
public class Fail2BanChannelHandler extends ChannelInboundHandlerAdapter {
	private static final Logger LOGGER = Logger.getLogger(Fail2BanChannelHandler.class.getName());
	private static final AttributeKey<Long> LAST_PACKET_TIME = AttributeKey.valueOf("FAIL2BAN_LAST_PACKET_TIME");

	private final BanManager banManager;
	private final DetectionEngine detectionEngine;
	private final PanicMode panicMode;

	public Fail2BanChannelHandler(BanManager banManager) {
		this.banManager = banManager;
		this.detectionEngine = new DetectionEngine();
		this.panicMode = PanicMode.getInstance();
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		// Extract IP from remote address
		String ip = extractIp(ctx);

		// If connecting from a trusted proxy (e.g. Cloudflared tunnel on 127.0.0.1),
		// do NOT drop the multiplexed tunnel at TCP connection level. Real IP will be validated in channelRead.
		if (!ClientIpResolver.isTrustedProxy(ip)) {
			// Panic Mode check (highest priority)
			if (!panicMode.allowConnection(ip)) {
				ctx.close();
				return;
			}

			// Check if direct IP is currently banned
			if (banManager.isBanned(ip)) {
				LOGGER.fine("[BAN] Closing connection from banned IP: " + ip);
				ctx.close();
				return;
			}
		}

		// Continue pipeline
		ctx.fireChannelActive();
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		String socketIp = extractIp(ctx);
		String targetIp = socketIp;

		PacketVector128 vector = null;

		// 1. For raw TCP ByteBuf traffic (GameServer / LoginServer packets)
		if (msg instanceof ByteBuf buf) {
			if (banManager.isBanned(socketIp)) {
				ctx.close();
				return;
			}
			int len = buf.readableBytes();
			int opcode = (len > 0) ? (buf.getByte(buf.readerIndex()) & 0xFF) : 0;
			long now = System.currentTimeMillis();
			Long lastTime = ctx.channel().attr(LAST_PACKET_TIME).get();
			long delta = (lastTime != null) ? Math.max(0, now - lastTime) : 25L;
			ctx.channel().attr(LAST_PACKET_TIME).set(now);
			vector = PacketVector128.synthesizeTcp(socketIp, len, opcode, delta, 64240);
		}
		// 2. For HTTP requests, resolve real client IP (Cloudflare Tunnel / CDN / Proxy)
		else if (msg instanceof HttpRequest request) {
			String realIp = ClientIpResolver.resolveRealIp(socketIp, request.headers());
			targetIp = realIp;

			// Check if real client IP is banned
			if (banManager.isBanned(realIp)) {
				LOGGER.info("[BAN] Dropping HTTP request from banned real IP: " + realIp + " (via proxy " + socketIp + ")");
				ctx.close();
				return;
			}

			String uri = request.uri();
			String method = request.method().toString();
			String payload = method + " " + uri;
			int headerCount = request.headers() != null ? request.headers().size() : 0;
			vector = PacketVector128.synthesizeHttp(realIp, method, uri.length(), headerCount, 0);

			// Detect attacks against real IP
			Decision decision = detectionEngine.evaluate(realIp, payload);

			switch (decision) {
				case BAN_IMMEDIATE:
					LOGGER.warning("[DETECT] Ban immediate for real IP " + realIp + " (" + payload + ") via proxy " + socketIp);
					banManager.ban(realIp, "scanner", "Detection engine: " + payload, 86400000); // 24h
					ctx.close();
					return;

				case RECORD_FAIL:
					banManager.recordFailure(realIp, "bruteforce", "HTTP: " + payload);
					break;

				case ALLOW:
					// Normal traffic
					break;
			}
		}

		// 3. Feed vector into SIMD Clean Room for online training or anomaly evaluation
		if (vector != null) {
			CleanRoomManager cleanRoom = CleanRoomManager.getInstance();
			CleanRoomManager.AnomalyDecision decision = cleanRoom.processPacket(vector);
			if (decision.isAnomaly() && cleanRoom.isCalibrated()) {
				LOGGER.warning("[SIMD-ANOMALY] High divergence from Golden Profile for IP: " + targetIp + 
					" (Z=" + decision.zScore() + ", Dist=" + decision.cosineDist() + ", Reason=" + decision.reason() + ")");
				banManager.ban(targetIp, "simd-anomaly", "Desvio critico do Perfil Dourado (Z=" + String.format("%.2f", decision.zScore()) + ")", 3600000L);
				ctx.close();
				return;
			}
		}

		// Pass to next handler
		ctx.fireChannelRead(msg);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		String ip = extractIp(ctx);
		
		// Protocol errors = potential attack
		LOGGER.log(Level.FINE, "[ERROR] Exception from " + ip, cause);
		banManager.recordFailure(ip, "scanner", "Protocol error: " + cause.getClass().getSimpleName());
		
		ctx.close();
	}
	
	private String extractIp(ChannelHandlerContext ctx) {
		try {
			InetSocketAddress addr = (InetSocketAddress) ctx.channel().remoteAddress();
			if (addr != null) {
				return addr.getAddress().getHostAddress();
			}
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Failed to extract IP", e);
		}
		return "UNKNOWN";
	}
}
