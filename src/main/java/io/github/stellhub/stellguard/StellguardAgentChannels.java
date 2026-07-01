package io.github.stellhub.stellguard;

import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueueDomainSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.unix.DomainSocketAddress;
import java.nio.file.Path;
import java.util.Locale;

public final class StellguardAgentChannels {
    private StellguardAgentChannels() {}

    /** 打开到本地 stellguard-agent 的 Unix Domain Socket gRPC 通道。 */
    public static AgentChannel openUnixDomainSocket(Path socketPath) {
        if (socketPath == null) {
            throw new IllegalArgumentException("socketPath is required");
        }

        Transport transport = selectTransport();
        EventLoopGroup group = transport.newEventLoopGroup();
        ManagedChannel channel = NettyChannelBuilder.forAddress(new DomainSocketAddress(socketPath.toString()))
                .eventLoopGroup(group)
                .channelType(transport.channelType())
                .usePlaintext()
                .build();
        return new AgentChannel(channel, group);
    }

    private static Transport selectTransport() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("linux")) {
            return new Transport(EpollDomainSocketChannel.class, EpollEventLoopGroup::new);
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            return new Transport(KQueueDomainSocketChannel.class, KQueueEventLoopGroup::new);
        }
        throw new UnsupportedOperationException("Unix Domain Socket transport requires Linux epoll or macOS kqueue");
    }

    private record Transport(Class<? extends Channel> channelType, EventLoopGroupFactory factory) {
        EventLoopGroup newEventLoopGroup() {
            return factory.create();
        }
    }

    @FunctionalInterface
    private interface EventLoopGroupFactory {
        EventLoopGroup create();
    }

    public record AgentChannel(ManagedChannel channel, EventLoopGroup eventLoopGroup) implements AutoCloseable {

        /** 关闭 gRPC channel 和底层 event loop。 */
        @Override
        public void close() {
            channel.shutdownNow();
            eventLoopGroup.shutdownGracefully();
        }
    }
}
