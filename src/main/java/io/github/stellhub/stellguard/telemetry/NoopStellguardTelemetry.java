package io.github.stellhub.stellguard.telemetry;

import io.github.stellhub.stellguard.AuthenticationRequest;
import io.github.stellhub.stellguard.AuthenticationResult;
import java.time.Duration;

/** 空指标记录器。 */
public final class NoopStellguardTelemetry implements StellguardTelemetry {

    /**
     * 记录认证请求。
     *
     * @param request 认证请求
     * @param result 认证结果
     * @param duration 认证耗时
     */
    @Override
    public void recordAuthentication(
            AuthenticationRequest request, AuthenticationResult result, Duration duration) {}

    /**
     * 记录 agent gRPC 调用。
     *
     * @param rpc RPC 名称
     * @param grpcStatus gRPC 状态
     * @param duration 调用耗时
     */
    @Override
    public void recordAgentCall(String rpc, String grpcStatus, Duration duration) {}
}
