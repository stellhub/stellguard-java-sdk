package io.github.stellhub.stellguard;

import io.github.stellhub.stellguard.StellguardAgentChannels.AgentChannel;
import io.github.stellhub.stellguard.agent.v1.FetchTrustBundleRequest;
import io.github.stellhub.stellguard.agent.v1.StellGuardAgentGrpc;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class StellguardClient implements AutoCloseable {
    private static final Metadata.Key<String> AUTHORIZATION_HEADER =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final ManagedChannel managedChannel;
    private final AutoCloseable channelResource;
    private final StellGuardAgentGrpc.StellGuardAgentBlockingStub blockingStub;
    private final Duration deadline;

    private StellguardClient(
            ManagedChannel managedChannel, AutoCloseable channelResource, Channel rpcChannel, Duration deadline) {
        this.managedChannel = managedChannel;
        this.channelResource = channelResource;
        this.blockingStub = StellGuardAgentGrpc.newBlockingStub(rpcChannel);
        this.deadline = deadline;
    }

    /** 通过 Unix Domain Socket 连接本地 stellguard-agent。 */
    public static StellguardClient connect(StellguardClientOptions options) {
        StellguardClientOptions resolved = options == null ? StellguardClientOptions.builder().build() : options;
        AgentChannel agentChannel = StellguardAgentChannels.openUnixDomainSocket(resolved.socketPath());
        Channel rpcChannel = withMetadata(agentChannel.channel(), resolved.agentToken());
        return new StellguardClient(agentChannel.channel(), agentChannel, rpcChannel, resolved.deadline());
    }

    /** 使用外部托管的 channel 创建客户端，便于测试或框架集成。 */
    public static StellguardClient usingChannel(ManagedChannel channel, Duration deadline, String agentToken) {
        if (channel == null) {
            throw new IllegalArgumentException("channel is required");
        }
        Duration resolvedDeadline = deadline == null ? Duration.ofSeconds(3) : deadline;
        return new StellguardClient(channel, () -> {}, withMetadata(channel, agentToken), resolvedDeadline);
    }

    /** 从本地 agent 获取 workload certificate。 */
    public CredentialBundle fetchWorkloadCertificate(CredentialRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        return CredentialBundle.fromProto(stubWithDeadline().fetchWorkloadCertificate(request.toProto()));
    }

    /** 从本地 agent 获取指定 trust domain 的信任根。 */
    public CredentialBundle fetchTrustBundle(String trustDomain) {
        String value = trustDomain == null ? "" : trustDomain.trim();
        FetchTrustBundleRequest request = FetchTrustBundleRequest.newBuilder().setTrustDomain(value).build();
        return CredentialBundle.fromTrustBundle(stubWithDeadline().fetchTrustBundle(request));
    }

    /** 关闭 SDK 持有的 channel 资源。 */
    @Override
    public void close() {
        try {
            channelResource.close();
        } catch (Exception ignored) {
            managedChannel.shutdownNow();
        }
    }

    private StellGuardAgentGrpc.StellGuardAgentBlockingStub stubWithDeadline() {
        return blockingStub.withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static Channel withMetadata(Channel channel, String agentToken) {
        if (agentToken == null || agentToken.isBlank()) {
            return channel;
        }
        Metadata metadata = new Metadata();
        metadata.put(AUTHORIZATION_HEADER, "Bearer " + agentToken.trim());
        return ClientInterceptors.intercept(channel, MetadataUtils.newAttachHeadersInterceptor(metadata));
    }
}
