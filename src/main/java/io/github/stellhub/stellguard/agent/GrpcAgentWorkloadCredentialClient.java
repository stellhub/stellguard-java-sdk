package io.github.stellhub.stellguard.agent;

import io.github.stellhub.stellguard.StellguardClientOptions;
import io.github.stellhub.stellguard.proto.agent.v1.AgentStatus;
import io.github.stellhub.stellguard.proto.agent.v1.FetchWorkloadCredentialRequest;
import io.github.stellhub.stellguard.proto.agent.v1.GetAgentStatusRequest;
import io.github.stellhub.stellguard.proto.agent.v1.GetTrustBundleRequest;
import io.github.stellhub.stellguard.proto.agent.v1.TrustBundle;
import io.github.stellhub.stellguard.proto.agent.v1.WorkloadCredential;
import io.github.stellhub.stellguard.proto.agent.v1.WorkloadCredentialServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDomainSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.unix.DomainSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** 基于 gRPC UDS 的 stellguard-agent workload API 客户端。 */
public final class GrpcAgentWorkloadCredentialClient implements AgentWorkloadCredentialClient {
    private final Path socketPath;
    private volatile ManagedChannel channel;
    private volatile EventLoopGroup eventLoopGroup;
    private volatile WorkloadCredentialServiceGrpc.WorkloadCredentialServiceBlockingStub stub;

    private GrpcAgentWorkloadCredentialClient(Path socketPath) {
        this.socketPath = Objects.requireNonNull(socketPath, "socketPath must not be null");
    }

    /**
     * 连接本机 agent。
     *
     * @param options 客户端配置
     * @return agent 客户端
     */
    public static GrpcAgentWorkloadCredentialClient connect(StellguardClientOptions options) {
        Objects.requireNonNull(options, "options must not be null");
        return new GrpcAgentWorkloadCredentialClient(options.socketPath());
    }

    /**
     * 获取当前 workload credential。
     *
     * @param audience audience
     * @param includePrivateKey 是否包含私钥
     * @param deadline 调用超时
     * @return workload credential
     */
    @Override
    public WorkloadCredential fetchWorkloadCredential(
            String audience, boolean includePrivateKey, Duration deadline) {
        FetchWorkloadCredentialRequest.Builder builder =
                FetchWorkloadCredentialRequest.newBuilder().setIncludePrivateKey(includePrivateKey);
        if (audience != null && !audience.isBlank()) {
            builder.setAudience(audience.trim());
        }
        return stub()
                .withDeadlineAfter(deadline.toNanos(), TimeUnit.NANOSECONDS)
                .fetchWorkloadCredential(builder.build());
    }

    /**
     * 获取 trust bundle。
     *
     * @param trustDomain trust domain
     * @param deadline 调用超时
     * @return trust bundle
     */
    @Override
    public TrustBundle getTrustBundle(String trustDomain, Duration deadline) {
        GetTrustBundleRequest.Builder builder = GetTrustBundleRequest.newBuilder();
        if (trustDomain != null && !trustDomain.isBlank()) {
            builder.setTrustDomain(trustDomain.trim());
        }
        return stub()
                .withDeadlineAfter(deadline.toNanos(), TimeUnit.NANOSECONDS)
                .getTrustBundle(builder.build());
    }

    /**
     * 获取 agent 状态。
     *
     * @param deadline 调用超时
     * @return agent 状态
     */
    @Override
    public AgentStatus getAgentStatus(Duration deadline) {
        return stub()
                .withDeadlineAfter(deadline.toNanos(), TimeUnit.NANOSECONDS)
                .getAgentStatus(GetAgentStatusRequest.newBuilder().build());
    }

    /** 关闭 agent 客户端。 */
    @Override
    public void close() {
        ManagedChannel currentChannel = channel;
        EventLoopGroup currentEventLoopGroup = eventLoopGroup;
        if (currentChannel == null || currentEventLoopGroup == null) {
            return;
        }
        currentChannel.shutdownNow();
        currentEventLoopGroup.shutdownGracefully();
        try {
            currentChannel.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private WorkloadCredentialServiceGrpc.WorkloadCredentialServiceBlockingStub stub() {
        WorkloadCredentialServiceGrpc.WorkloadCredentialServiceBlockingStub currentStub = stub;
        if (currentStub != null) {
            return currentStub;
        }
        synchronized (this) {
            if (stub == null) {
                initialize();
            }
            return stub;
        }
    }

    private void initialize() {
        try {
            UnixDomainSocketTransport transport = UnixDomainSocketTransport.create(socketPath);
            ManagedChannel createdChannel =
                    NettyChannelBuilder.forAddress(transport.address())
                            .channelType(transport.channelType())
                            .eventLoopGroup(transport.eventLoopGroup())
                            .usePlaintext()
                            .build();
            this.eventLoopGroup = transport.eventLoopGroup();
            this.channel = createdChannel;
            this.stub = WorkloadCredentialServiceGrpc.newBlockingStub(createdChannel);
        } catch (RuntimeException exception) {
            throw io.grpc.Status.UNAVAILABLE
                    .withDescription("stellguard-agent UDS transport is unavailable")
                    .withCause(exception)
                    .asRuntimeException();
        }
    }

    private record UnixDomainSocketTransport(
            DomainSocketAddress address,
            EventLoopGroup eventLoopGroup,
            Class<? extends Channel> channelType) {

        private static UnixDomainSocketTransport create(Path socketPath) {
            DomainSocketAddress address = new DomainSocketAddress(socketPath.toFile());
            if (Epoll.isAvailable()) {
                return new UnixDomainSocketTransport(
                        address, new EpollEventLoopGroup(1), EpollDomainSocketChannel.class);
            }
            if (KQueue.isAvailable()) {
                return new UnixDomainSocketTransport(
                        address, new KQueueEventLoopGroup(1), KQueueDomainSocketChannel.class);
            }
            throw new IllegalStateException(
                    "Unix Domain Socket transport requires Netty epoll or kqueue support");
        }
    }
}
