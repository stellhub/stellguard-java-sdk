package io.github.stellhub.stellguard.agent;

import io.github.stellhub.stellguard.AuthenticationFailureClass;
import io.github.stellhub.stellguard.AuthenticationReason;
import io.grpc.Status;
import java.util.Objects;

/**
 * agent 调用失败映射结果。
 *
 * @param failureClass 失败分类
 * @param reason 失败原因
 * @param grpcCode gRPC 状态码
 */
public record AgentCallFailure(
        AuthenticationFailureClass failureClass, AuthenticationReason reason, Status.Code grpcCode) {

    public AgentCallFailure {
        failureClass = Objects.requireNonNull(failureClass, "failureClass must not be null");
        reason = Objects.requireNonNull(reason, "reason must not be null");
        grpcCode = Objects.requireNonNull(grpcCode, "grpcCode must not be null");
    }
}
