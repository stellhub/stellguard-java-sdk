package io.github.stellhub.stellguard.agent;

import io.github.stellhub.stellguard.AuthenticationFailureClass;
import io.github.stellhub.stellguard.AuthenticationReason;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

/** agent gRPC 异常映射器。 */
public final class AgentCallExceptionMapper {

    private AgentCallExceptionMapper() {}

    /**
     * 将 gRPC 异常映射为 SDK 失败分类。
     *
     * @param exception gRPC 异常
     * @return 失败映射结果
     */
    public static AgentCallFailure map(StatusRuntimeException exception) {
        Status.Code code = exception.getStatus().getCode();
        return switch (code) {
            case PERMISSION_DENIED, UNAUTHENTICATED ->
                    new AgentCallFailure(
                            AuthenticationFailureClass.AUTHENTICATION_DENIED,
                            AuthenticationReason.AUTHENTICATION_DENIED,
                            code);
            case NOT_FOUND ->
                    new AgentCallFailure(
                            AuthenticationFailureClass.AGENT_UNAVAILABLE,
                            AuthenticationReason.AGENT_NOT_READY,
                            code);
            case UNAVAILABLE ->
                    new AgentCallFailure(
                            AuthenticationFailureClass.AGENT_UNAVAILABLE,
                            AuthenticationReason.AGENT_GRPC_UNAVAILABLE,
                            code);
            case DEADLINE_EXCEEDED ->
                    new AgentCallFailure(
                            AuthenticationFailureClass.AGENT_UNAVAILABLE,
                            AuthenticationReason.AGENT_GRPC_TIMEOUT,
                            code);
            case CANCELLED, INTERNAL, UNKNOWN, DATA_LOSS, RESOURCE_EXHAUSTED ->
                    new AgentCallFailure(
                            AuthenticationFailureClass.AGENT_UNAVAILABLE,
                            AuthenticationReason.AGENT_GRPC_UNAVAILABLE,
                            code);
            case INVALID_ARGUMENT ->
                    new AgentCallFailure(
                            AuthenticationFailureClass.SDK_CONFIGURATION_ERROR,
                            AuthenticationReason.SDK_CONFIGURATION_ERROR,
                            code);
            default ->
                    new AgentCallFailure(
                            AuthenticationFailureClass.SDK_ERROR, AuthenticationReason.SDK_ERROR, code);
        };
    }
}
