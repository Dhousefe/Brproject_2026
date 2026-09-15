package br.project.proxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
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
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HTTP reverse proxy using Netty. Two pipelines per connection:
 *   - server side (inbound from client): HttpServerCodec + aggregator + HttpProxyFrontend
 *   - client side (outbound to backend): HttpClientCodec + HttpProxyBackend
 *
 * Rate limiting: HttpProxyFrontend checks both the per-window connection cap and the
 * per-window request cap. Denied requests get a 429 with Retry-After.
 */
public final class HttpProxyServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(HttpProxyServer.class);
    private static final Logger ACCESS = LoggerFactory.getLogger("proxy.access");

    private final ProxyRoute route;
    private final FixedWindowRateLimiter limiter;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final SslContext sslCtx;
    private Channel serverChannel;

    public HttpProxyServer(ProxyRoute route, FixedWindowRateLimiter limiter) {
        this.route = route;
        this.limiter = limiter;
        this.bossGroup = new NioEventLoopGroup(1);
        this.workerGroup = new NioEventLoopGroup();
        if (route.tlsEnabled()) {
            try {
                if (route.isAutoTls()) {
                    String certDomain = route.tlsDomain();
                    if (certDomain == null || certDomain.isBlank()) {
                        certDomain = route.bindHost();
                        if ("0.0.0.0".equals(certDomain) || "*".equals(certDomain) || certDomain.isBlank()) {
                            certDomain = "127.0.0.1";
                        }
                    }
                    CertificateManager.TlsCertKey tlsPair = CertificateManager.getOrCreateSelfSigned(certDomain);
                    this.sslCtx = SslContextBuilder.forServer(tlsPair.certFile(), tlsPair.keyFile()).build();
                    LOG.info("[proxy/http] Automatic self-signed TLS enabled (domain='{}') for route='{}' cert={}",
                        certDomain, route.name(), tlsPair.certFile().getAbsolutePath());
                } else {
                    this.sslCtx = SslContextBuilder.forServer(
                        new File(route.tlsCertPath()),
                        new File(route.tlsKeyPath())
                    ).build();
                    LOG.info("[proxy/http] Manual TLS enabled for route='{}' cert={}", route.name(), route.tlsCertPath());
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize TLS for route '" + route.name() + "': " + e.getMessage(), e);
            }
        } else {
            this.sslCtx = null;
        }
    }

    public void start() throws InterruptedException {
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .option(ChannelOption.SO_BACKLOG, 512)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    if (sslCtx != null) {
                        ch.pipeline().addLast("ssl", sslCtx.newHandler(ch.alloc()));
                    }
                    ch.pipeline().addLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS));
                    ch.pipeline().addLast(new HttpServerCodec());
                    ch.pipeline().addLast(new HttpObjectAggregator(route.maxContentLength()));
                    ch.pipeline().addLast(new HttpProxyFrontend(route, limiter, accepted, rejected));
                }
            });

        ChannelFuture f = b.bind(route.bindHost(), route.bindPort()).sync();
        serverChannel = f.channel();
        LOG.info("[proxy/http] route='{}' listening on {}:{} -> {}:{} (rateLimit enabled={}, conn/{}s={}, req/{}s={})",
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

    public long accepted() { return accepted.get(); }

    public long rejected() { return rejected.get(); }

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

    private static String remoteIp(ChannelHandlerContext ctx) {
        SocketAddress ra = ctx.channel().remoteAddress();
        if (ra instanceof InetSocketAddress isa) {
            return isa.getAddress().getHostAddress();
        }
        return "unknown";
    }

    private static final class HttpProxyFrontend extends SimpleChannelInboundHandler<FullHttpRequest> {
        private final ProxyRoute route;
        private final FixedWindowRateLimiter limiter;
        private final AtomicLong accepted;
        private final AtomicLong rejected;

        HttpProxyFrontend(ProxyRoute route, FixedWindowRateLimiter limiter,
                          AtomicLong accepted, AtomicLong rejected) {
            this.route = route;
            this.limiter = limiter;
            this.accepted = accepted;
            this.rejected = rejected;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            String ip = remoteIp(ctx);
            // If the connection comes from a trusted proxy (e.g. cloudflared tunnel or loopback),
            // do not drop or rate-limit the TCP socket here to avoid dropping the multiplexed tunnel.
            // Enforcement for real client IPs occurs in channelRead0.
            if (!ClientIpExtractor.isTrustedProxy(ip)) {
                if (ProxyBanCache.getInstance().isBanned(ip)) {
                    rejected.incrementAndGet();
                    LOG.warn("[PROXY-SECURITY-EVENT] type=BAN_DROP route='{}' ip={} reason=BANNED_BY_FAIL2BAN", route.name(), ip);
                    ctx.close();
                    return;
                }

                if (route.rateLimit().enabled()) {
                    RateLimitDecision connDec = limiter.tryAcquire(
                        route.name() + ":conn:" + ip,
                        route.rateLimit().effectiveConnectionLimit(),
                        route.rateLimit().windowSeconds());
                    if (!connDec.allowed()) {
                        rejected.incrementAndGet();
                        LOG.warn("[PROXY-SECURITY-EVENT] type=RATE_LIMIT_DENIED route='{}' ip={} count={}/{}",
                            route.name(), ip, connDec.currentCount(), connDec.limit());
                        ctx.writeAndFlush(tooManyRequests(connDec));
                        ctx.close();
                        return;
                    }
                }
            }
            accepted.incrementAndGet();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
            String peerIp = remoteIp(ctx);
            String ip = ClientIpExtractor.extractRealIp(peerIp, req.headers());
            String method = req.method().name();
            String path = req.uri();
            String sample = extractPayloadSample(req);

            boolean isWs = "websocket".equalsIgnoreCase(req.headers().get(HttpHeaderNames.UPGRADE));
            String proto = isWs
                ? (route.tlsEnabled() ? "WSS" : "WEBSOCKET")
                : (route.tlsEnabled() ? "HTTPS" : "HTTP");

            if (ProxyBanCache.getInstance().isBanned(ip)) {
                rejected.incrementAndGet();
                LOG.warn("[PROXY-SECURITY-EVENT] type=BAN_DROP route='{}' ip={} method={} uri='{}' sample='{}' reason=BANNED_BY_FAIL2BAN",
                    route.name(), ip, method, path, sample);
                ctx.writeAndFlush(forbiddenBanned(ip))
                    .addListener(ChannelFutureListener.CLOSE);
                return;
            }

            if (route.rateLimit().enabled()) {
                RateLimitDecision reqDec = limiter.tryAcquire(
                    route.name() + ":req:" + ip,
                    route.rateLimit().effectiveRequestLimit(),
                    route.rateLimit().windowSeconds());
                if (!reqDec.allowed()) {
                    rejected.incrementAndGet();
                    LOG.warn("[PROXY-SECURITY-EVENT] type=RATE_LIMIT_DENIED route='{}' ip={} method={} uri='{}' sample='{}' count={}/{}",
                        route.name(), ip, method, path, sample, reqDec.currentCount(), reqDec.limit());
                    ctx.writeAndFlush(tooManyRequests(reqDec));
                    return;
                }
            }

            if (isWs) {
                LOG.info("[PROXY-TRAFFIC-EVENT] proto={} route='{}' ip={} method={} uri='{}' status=101 latency=0ms",
                    proto, route.name(), ip, method, path);
            }

            URI uri = buildBackendUri(req.uri(), route.preserveHost());
            long startNanos = System.nanoTime();
            FullHttpRequest outbound = new io.netty.handler.codec.http.DefaultFullHttpRequest(
                req.protocolVersion(),
                req.method(),
                uri.toString(),
                req.content().retainedDuplicate());

            copyHeaders(req.headers(), outbound.headers(), ip, req, route);
            HttpUtil.setContentLength(outbound, outbound.content().readableBytes());
            outbound.headers().set(HttpHeaderNames.HOST, route.targetHost() + ":" + route.targetPort());
            outbound.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);

            io.netty.bootstrap.Bootstrap b = new io.netty.bootstrap.Bootstrap();
            b.group(ctx.channel().eventLoop())
                .channel(ctx.channel().getClass())
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(new io.netty.channel.ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS));
                        ch.pipeline().addLast(new HttpClientCodec());
                        ch.pipeline().addLast(new HttpContentDecompressor());
                        ch.pipeline().addLast(new HttpObjectAggregator(route.maxContentLength()));
                        ch.pipeline().addLast(new HttpProxyBackend(ctx.channel(), startNanos, ip, method, path, route.name(), proto));
                    }
                });

            ChannelFuture cf = b.connect(route.targetHost(), route.targetPort());
            cf.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    future.channel().writeAndFlush(outbound);
                } else {
                    io.netty.util.ReferenceCountUtil.release(outbound);
                    long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
                    LOG.error("[proxy/http] backend connect failed route='{}' -> {}:{} after {}ms",
                        route.name(), route.targetHost(), route.targetPort(), latencyMs, future.cause());
                    ACCESS.info("{} {} {} 502 {}ms [{}] CONNECT_FAIL", ip, method, path, latencyMs, route.name());
                    if (ctx.channel().isActive()) {
                        ctx.writeAndFlush(badGateway());
                    }
                }
            });
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOG.debug("[proxy/http] frontend error route='{}'", route.name(), cause);
            ctx.close();
        }

        private static URI buildBackendUri(String originalUri, boolean preserveHost) {
            try {
                if (originalUri == null || originalUri.isEmpty()) return new URI("/");
                if (originalUri.startsWith("http://") || originalUri.startsWith("https://")) {
                    if (preserveHost) return URI.create(originalUri);
                    URI u = URI.create(originalUri);
                    String rebuilt = (u.getRawQuery() == null)
                        ? u.getRawPath()
                        : u.getRawPath() + "?" + u.getRawQuery();
                    return new URI(rebuilt);
                }
                if (originalUri.contains("://")) return URI.create(originalUri);
                return URI.create(originalUri);
            } catch (Exception e) {
                return URI.create("/");
            }
        }

        private static void copyHeaders(io.netty.handler.codec.http.HttpHeaders src,
                                        io.netty.handler.codec.http.HttpHeaders dst,
                                        String clientIp, FullHttpRequest original,
                                        ProxyRoute route) {
            for (Map.Entry<String, String> h : src.entries()) {
                String name = h.getKey().toLowerCase(Locale.ROOT);
                if (isHopByHop(name)) continue;
                dst.set(h.getKey(), h.getValue());
            }
            if (route.addForwardedHeaders()) {
                dst.set("X-Forwarded-For", clientIp);
                dst.set("X-Forwarded-Proto", route.tlsEnabled() ? "https" : "http");
            }
        }

        private static boolean isHopByHop(String name) {
            return switch (name) {
                case "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
                     "te", "trailer", "transfer-encoding", "upgrade", "host" -> true;
                default -> false;
            };
        }

        private static FullHttpResponse tooManyRequests(RateLimitDecision dec) {
            byte[] body = ("Rate limit exceeded: " + dec.currentCount() + " > " + dec.limit() + "\n")
                .getBytes(CharsetUtil.UTF_8);
            ByteBuf content = Unpooled.wrappedBuffer(body);
            FullHttpResponse resp = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.TOO_MANY_REQUESTS, content);
            resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            resp.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.length);
            resp.headers().set("Retry-After", String.valueOf(Math.max(1, dec.retryAfterMillis() / 1000)));
            return resp;
        }

        private static FullHttpResponse badGateway() {
            byte[] body = "Bad Gateway\n".getBytes(CharsetUtil.UTF_8);
            ByteBuf content = Unpooled.wrappedBuffer(body);
            FullHttpResponse resp = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_GATEWAY, content);
            resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            resp.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.length);
            return resp;
        }

        private static FullHttpResponse forbiddenBanned(String ip) {
            String json = "{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied. Your IP (" + ip + ") is banned by security policy.\"}";
            byte[] body = json.getBytes(CharsetUtil.UTF_8);
            ByteBuf content = Unpooled.wrappedBuffer(body);
            FullHttpResponse resp = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN, content);
            resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
            resp.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.length);
            resp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            return resp;
        }

        private static String extractPayloadSample(FullHttpRequest req) {
            try {
                if (req.content() != null && req.content().readableBytes() > 0) {
                    int len = Math.min(req.content().readableBytes(), 160);
                    byte[] slice = new byte[len];
                    req.content().getBytes(req.content().readerIndex(), slice);
                    String text = new String(slice, java.nio.charset.StandardCharsets.UTF_8)
                        .replaceAll("[\\r\\n\\t]+", " ")
                        .trim();
                    if (text.length() > 140) text = text.substring(0, 140) + "...";
                    return text;
                }
            } catch (Exception ignored) {}
            return req.method().name() + " " + req.uri();
        }
    }

    private static final class HttpProxyBackend extends SimpleChannelInboundHandler<FullHttpResponse> {
        private final Channel frontendChannel;
        private final long startNanos;
        private final String clientIp;
        private final String method;
        private final String path;
        private final String routeName;
        private final String proto;

        HttpProxyBackend(Channel frontendChannel, long startNanos, String clientIp,
                         String method, String path, String routeName, String proto) {
            this.frontendChannel = frontendChannel;
            this.startNanos = startNanos;
            this.clientIp = clientIp;
            this.method = method;
            this.path = path;
            this.routeName = routeName;
            this.proto = proto != null ? proto : "HTTP";
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) {
            if (!frontendChannel.isActive()) {
                ctx.close();
                return;
            }
            FullHttpResponse copy = new DefaultFullHttpResponse(
                msg.protocolVersion(), msg.status(), msg.content().retainedDuplicate());
            copy.headers().add(msg.headers());
            copy.headers().remove("Transfer-Encoding");
            HttpUtil.setContentLength(copy, copy.content().readableBytes());
            frontendChannel.writeAndFlush(copy);

            // Access log: ip method path status latencyMs
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
            ACCESS.info("{} {} {} {} {}ms [{}]", clientIp, method, path, msg.status().code(), latencyMs, routeName);
            LOG.info("[PROXY-TRAFFIC-EVENT] proto={} route='{}' ip={} method={} uri='{}' status={} latency={}ms",
                proto, routeName, clientIp, method, path, msg.status().code(), latencyMs);

            // Close backend connection cleanly to prevent orphan connections timing out after 30s
            ctx.close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (frontendChannel.isActive()) {
                frontendChannel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
                frontendChannel.close();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
            LOG.warn("[proxy/http] backend error route='{}' ip={} {} {} after {}ms: {}",
                routeName, clientIp, method, path, latencyMs, cause.getMessage());
            ACCESS.info("{} {} {} 502 {}ms [{}] ERR:{}", clientIp, method, path, latencyMs, routeName,
                cause.getClass().getSimpleName());
            LOG.info("[PROXY-TRAFFIC-EVENT] proto={} route='{}' ip={} method={} uri='{}' status=502 latency={}ms",
                proto, routeName, clientIp, method, path, latencyMs);
            // Send 502 to client if still connected and close frontend to avoid desynchronization
            if (frontendChannel.isActive()) {
                frontendChannel.writeAndFlush(serviceUnavailable(cause))
                    .addListener(ChannelFutureListener.CLOSE);
            }
            ctx.close();
        }

        private static FullHttpResponse serviceUnavailable(Throwable cause) {
            String reason = cause != null ? cause.getClass().getSimpleName() : "unknown";
            byte[] body = ("Service temporarily unavailable (" + reason + "). Please retry.\n")
                .getBytes(CharsetUtil.UTF_8);
            ByteBuf content = Unpooled.wrappedBuffer(body);
            FullHttpResponse resp = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_GATEWAY, content);
            resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            resp.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.length);
            resp.headers().set("Retry-After", "5");
            return resp;
        }
    }

}
