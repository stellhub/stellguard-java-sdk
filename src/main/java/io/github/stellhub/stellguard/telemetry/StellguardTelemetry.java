package io.github.stellhub.stellguard.telemetry;

import io.github.stellhub.stellguard.AuthenticationRequest;
import io.github.stellhub.stellguard.AuthenticationResult;
import java.time.Duration;

/** StellGuard 指标记录器。 */
public interface StellguardTelemetry extends AutoCloseable {

    /**
     * 记录认证请求。
     *
     * @param request 认证请求
     * @param result 认证结果
     * @param duration 认证耗时
     */
    void recordAuthentication(
            AuthenticationRequest request, AuthenticationResult result, Duration duration);

    /**
     * 记录 agent gRPC 调用。
     *
     * @param rpc RPC 名称
     * @param grpcStatus gRPC 状态
     * @param duration 调用耗时
     */
    void recordAgentCall(String rpc, String grpcStatus, Duration duration);

    /** 关闭指标记录器。 */
    @Override
    default void close() {}
}
