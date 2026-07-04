package io.github.stellhub.stellguard;

import io.github.stellhub.stellguard.agent.AgentWorkloadCredentialClient;
import io.github.stellhub.stellguard.agent.GrpcAgentWorkloadCredentialClient;
import io.github.stellhub.stellguard.internal.DefaultStellguardAuthenticator;
import java.util.Objects;

/** StellGuard 认证客户端。 */
public interface StellguardAuthenticator extends AutoCloseable {

    /**
     * 连接本机 stellguard-agent 并创建认证客户端。
     *
     * @param options 客户端配置
     * @return 认证客户端
     */
    static StellguardAuthenticator connect(StellguardClientOptions options) {
        Objects.requireNonNull(options, "options must not be null");
        return DefaultStellguardAuthenticator.create(
                options, GrpcAgentWorkloadCredentialClient.connect(options));
    }

    /**
     * 基于指定 agent 客户端创建认证客户端。
     *
     * @param options 客户端配置
     * @param agentClient agent 客户端
     * @return 认证客户端
     */
    static StellguardAuthenticator create(
            StellguardClientOptions options, AgentWorkloadCredentialClient agentClient) {
        return DefaultStellguardAuthenticator.create(options, agentClient);
    }

    /**
     * 执行认证。
     *
     * @param request 认证请求
     * @return 认证结果
     */
    AuthenticationResult authenticate(AuthenticationRequest request);

    /** 关闭认证客户端。 */
    @Override
    void close();
}
