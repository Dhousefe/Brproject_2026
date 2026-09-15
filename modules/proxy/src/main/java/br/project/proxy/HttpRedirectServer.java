package br.project.proxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Simple HTTP server that responds 301 to all requests, redirecting to HTTPS.
 * Used as a companion to the TLS-enabled HttpProxyServer on port 443.
 */
public final class HttpRedirectServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(HttpRedirectServer.class);

    private final ProxyRoute route;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private Channel serverChannel;

    public HttpRedirectServer(ProxyRoute route) {
        this.route = route;
        this.bossGroup = new NioEventLoopGroup(1);
        this.workerGroup = new NioEventLoopGroup(1);
    }

    public void start() throws InterruptedException {
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .option(ChannelOption.SO_BACKLOG, 128)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline().addLast(new ReadTimeoutHandler(5, TimeUnit.SECONDS));
                    ch.pipeline().addLast(new HttpServerCodec());
                    ch.pipeline().addLast(new HttpObjectAggregator(8192));
                    ch.pipeline().addLast(new RedirectHandler());
                }
            });

        serverChannel = b.bind(route.bindHost(), route.bindPort()).sync().channel();
        LOG.info("[proxy/redirect] listening on {}:{} -> {}",
            route.bindHost(), route.bindPort(), route.targetHost());
    }

    @Override
    public void close() {
        try {
            if (serverChannel != null) serverChannel.close().await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }

    private final class RedirectHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            String ip = remoteIp(ctx);
            if (ProxyBanCache.getInstance().isBanned(ip)) {
                LOG.warn("[PROXY-SECURITY-EVENT] type=BAN_DROP route='{}' ip={} reason=BANNED_BY_FAIL2BAN", route.name(), ip);
                ctx.close();
                return;
            }
            ctx.fireChannelActive();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
            String peerIp = remoteIp(ctx);
            String ip = ClientIpExtractor.extractRealIp(peerIp, req.headers());
            if (ProxyBanCache.getInstance().isBanned(ip) || ProxyBanCache.getInstance().isBanned(peerIp)) {
                LOG.warn("[PROXY-SECURITY-EVENT] type=BAN_DROP route='{}' ip={} reason=BANNED_BY_FAIL2BAN", route.name(), ip);
                ctx.close();
                return;
            }

            String host = req.headers().get(HttpHeaderNames.HOST);
            if (host == null || host.isBlank()) {
                host = route.targetHost();
            }
            // Strip port from host if present
            int colonIdx = host.indexOf(':');
            if (colonIdx > 0) host = host.substring(0, colonIdx);

            int targetPort = route.targetPort();
            String location;
            if (targetPort > 0 && targetPort != 443 && targetPort != route.bindPort()) {
                location = "https://" + host + ":" + targetPort + req.uri();
            } else {
                location = "https://" + host + req.uri();
            }

            byte[] body = ("Moved to " + location + "\n").getBytes(CharsetUtil.UTF_8);
            DefaultFullHttpResponse resp = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.MOVED_PERMANENTLY,
                Unpooled.wrappedBuffer(body));
            resp.headers().set(HttpHeaderNames.LOCATION, location);
            resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            resp.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.length);
            resp.headers().set(HttpHeaderNames.CONNECTION, "close");
            ctx.writeAndFlush(resp);
            ctx.close();
        }

        private String remoteIp(ChannelHandlerContext ctx) {
            java.net.SocketAddress addr = ctx.channel().remoteAddress();
            if (addr instanceof java.net.InetSocketAddress isa) {
                return isa.getAddress() != null ? isa.getAddress().getHostAddress() : isa.getHostString();
            }
            return "0.0.0.0";
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }
}
