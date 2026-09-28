package br.project.proxy;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;

/**
 * Factory providing optimal transport selection (Native Linux Epoll vs Java NIO)
 * based on platform capabilities, ensuring zero-regression across OS environments.
 */
public final class NettyTransportSupport {

    private static final Logger LOG = LoggerFactory.getLogger(NettyTransportSupport.class);
    private static final boolean EPOLL_AVAILABLE;

    static {
        boolean available = false;
        try {
            available = Epoll.isAvailable();
            if (available) {
                LOG.info("[proxy/transport] Native Linux Epoll transport is available and active.");
            } else {
                Throwable cause = Epoll.unavailabilityCause();
                LOG.info("[proxy/transport] Native Linux Epoll unavailable ({}); falling back to standard Java NIO.",
                    cause != null ? cause.getMessage() : "unsupported OS");
            }
        } catch (Throwable t) {
            LOG.warn("[proxy/transport] Epoll availability check failed; falling back to Java NIO: {}", t.getMessage());
        }
        EPOLL_AVAILABLE = available;
    }

    private NettyTransportSupport() {}

    public static boolean isEpollAvailable() {
        return EPOLL_AVAILABLE;
    }

    public static EventLoopGroup createEventLoopGroup(int nThreads, String poolName) {
        ThreadFactory tf = new DefaultThreadFactory(poolName, true);
        if (EPOLL_AVAILABLE) {
            return new EpollEventLoopGroup(nThreads, tf);
        }
        return new NioEventLoopGroup(nThreads, tf);
    }

    public static Class<? extends ServerSocketChannel> serverSocketChannelClass() {
        if (EPOLL_AVAILABLE) {
            return EpollServerSocketChannel.class;
        }
        return NioServerSocketChannel.class;
    }

    public static Class<? extends SocketChannel> socketChannelClass() {
        if (EPOLL_AVAILABLE) {
            return EpollSocketChannel.class;
        }
        return NioSocketChannel.class;
    }
}
