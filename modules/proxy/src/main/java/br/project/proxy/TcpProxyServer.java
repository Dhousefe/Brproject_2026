package br.project.proxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TCP pass-through reverse proxy using Netty.
 *
 * Flow:
 *   client -> TcpProxyServer (bindPort) -> TcpProxyFrontend -> TcpProxyBackend -> targetHost:targetPort
 *
 * Both sides share the same EventLoopGroup; the proxy just relays bytes.
 * Per-route RateLimiter gates new connections before we open a backend socket.
 */
public final class TcpProxyServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(TcpProxyServer.class);

    private final ProxyRoute route;
    private final FixedWindowRateLimiter limiter;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final AtomicLong acceptedConnections = new AtomicLong();
    private final AtomicLong rejectedConnections = new AtomicLong();
    private Channel serverChannel;

    public TcpProxyServer(ProxyRoute route, FixedWindowRateLimiter limiter) {
        this.route = route;
        this.limiter = limiter;
        this.bossGroup = new NioEventLoopGroup(1);
        this.workerGroup = new NioEventLoopGroup();
    }

    public void start() throws InterruptedException {
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .option(ChannelOption.SO_BACKLOG, 256)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childOption(ChannelOption.AUTO_READ, false)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline().addLast(new TcpFrontendHandler(route, limiter,
                        acceptedConnections, rejectedConnections));
                }
            });

        ChannelFuture f = b.bind(route.bindHost(), route.bindPort()).sync();
        serverChannel = f.channel();
        LOG.info("[proxy/tcp] route='{}' listening on {}:{} -> {}:{} (rateLimit enabled={}, conn/{}s={}, req/{}s={})",
            route.name(),
            route.bindHost(), route.bindPort(),
            route.targetHost(), route.targetPort(),
            route.rateLimit().enabled(),
            route.rateLimit().windowSeconds(),
            route.rateLimit().effectiveConnectionLimit(),
            route.rateLimit().effectiveRequestLimit());
    }

    public int bindPort() {
        if (serverChannel == null) return -1;
        SocketAddress addr = serverChannel.localAddress();
        if (addr instanceof InetSocketAddress isa) {
            return isa.getPort();
        }
        return -1;
    }

    public long accepted() {
        return acceptedConnections.get();
    }

    public long rejected() {
        return rejectedConnections.get();
    }

    @Override
    public void close() {
        try {
            if (serverChannel != null) {
                serverChannel.close().await();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }

    private static final class TcpFrontendHandler extends ChannelInboundHandlerAdapter {
        private final ProxyRoute route;
        private final FixedWindowRateLimiter limiter;
        private final AtomicLong accepted;
        private final AtomicLong rejected;
        private final java.util.Queue<Object> pendingMessages = new java.util.concurrent.ConcurrentLinkedQueue<>();
        private Channel outboundChannel;
        private String clientIp;

        TcpFrontendHandler(ProxyRoute route, FixedWindowRateLimiter limiter,
                           AtomicLong accepted, AtomicLong rejected) {
            this.route = route;
            this.limiter = limiter;
            this.accepted = accepted;
            this.rejected = rejected;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            String ip = remoteIp(ctx);
            this.clientIp = ip;
            if (ProxyBanCache.getInstance().isBanned(ip)) {
                rejected.incrementAndGet();
                LOG.warn("[PROXY-SECURITY-EVENT] type=BAN_DROP route='{}' ip={} reason=BANNED_BY_FAIL2BAN", route.name(), ip);
                ctx.close();
                return;
            }

            if (route.rateLimit().enabled()) {
                RateLimitDecision dec = limiter.tryAcquire(
                    route.name() + ":connect:" + ip,
                    route.rateLimit().effectiveConnectionLimit(),
                    route.rateLimit().windowSeconds());
                if (!dec.allowed()) {
                    rejected.incrementAndGet();
                    LOG.warn("[PROXY-SECURITY-EVENT] type=RATE_LIMIT_DENIED route='{}' ip={} count={}/{}",
                        route.name(), ip, dec.currentCount(), dec.limit());
                    ctx.close();
                    return;
                }
            }
            accepted.incrementAndGet();
            LOG.info("[PROXY-TRAFFIC-EVENT] proto=TCP route='{}' ip={} action=CONNECT targetPort={}",
                route.name(), ip, route.targetPort());

            io.netty.bootstrap.Bootstrap b = new io.netty.bootstrap.Bootstrap();
            b.group(ctx.channel().eventLoop())
                .channel(ctx.channel().getClass())
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.AUTO_CLOSE, true)
                .handler(new io.netty.channel.ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext backendCtx, Object msg) {
                                ctx.writeAndFlush(msg);
                            }
                        });
                    }
                });
            ChannelFuture cf = b.connect(route.targetHost(), route.targetPort());
            outboundChannel = cf.channel();

            cf.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    // Descarrega mensagens recebidas antes do handshake do backend completar
                    Object pending;
                    while ((pending = pendingMessages.poll()) != null) {
                        outboundChannel.write(pending);
                    }
                    outboundChannel.flush();

                    // Reativa leitura automática no canal do frontend
                    ctx.channel().config().setAutoRead(true);
                    ctx.read();
                } else {
                    LOG.error("[proxy/tcp] backend connect failed route='{}' -> {}:{}",
                        route.name(), route.targetHost(), route.targetPort(), future.cause());
                    clearPendingMessages();
                    ctx.close();
                }
            });
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (outboundChannel != null && outboundChannel.isActive()) {
                outboundChannel.writeAndFlush(msg);
            } else {
                pendingMessages.add(msg);
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (clientIp != null) {
                LOG.info("[PROXY-TRAFFIC-EVENT] proto=TCP route='{}' ip={} action=DISCONNECT", route.name(), clientIp);
            }
            clearPendingMessages();
            if (outboundChannel != null) {
                io.netty.channel.Channel c = outboundChannel;
                outboundChannel = null;
                c.close();
            }
        }

        private void clearPendingMessages() {
            Object pending;
            while ((pending = pendingMessages.poll()) != null) {
                io.netty.util.ReferenceCountUtil.release(pending);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOG.debug("[proxy/tcp] frontend error route='{}'", route.name(), cause);
            ctx.close();
        }

        private static String remoteIp(ChannelHandlerContext ctx) {
            SocketAddress ra = ctx.channel().remoteAddress();
            if (ra instanceof InetSocketAddress isa) {
                return isa.getAddress().getHostAddress();
            }
            return "unknown";
        }
    }
}
