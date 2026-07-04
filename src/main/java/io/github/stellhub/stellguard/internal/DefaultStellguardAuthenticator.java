package io.github.stellhub.stellguard.internal;

import io.github.stellhub.stellguard.AuthenticationFailureClass;
import io.github.stellhub.stellguard.AuthenticationReason;
import io.github.stellhub.stellguard.AuthenticationRequest;
import io.github.stellhub.stellguard.AuthenticationResult;
import io.github.stellhub.stellguard.StellguardAuthenticator;
import io.github.stellhub.stellguard.StellguardClientOptions;
import io.github.stellhub.stellguard.agent.AgentCallExceptionMapper;
import io.github.stellhub.stellguard.agent.AgentCallFailure;
import io.github.stellhub.stellguard.agent.AgentWorkloadCredentialClient;
import io.github.stellhub.stellguard.proto.agent.v1.AgentStatus;
import io.github.stellhub.stellguard.proto.agent.v1.WorkloadCredential;
import io.github.stellhub.stellguard.telemetry.OpenTelemetryStellguardTelemetry;
import io.github.stellhub.stellguard.telemetry.StellguardTelemetry;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** 默认 StellGuard 认证客户端。 */
public final class DefaultStellguardAuthenticator implements StellguardAuthenticator {
    private static final String RPC_FETCH_WORKLOAD_CREDENTIAL = "FetchWorkloadCredential";
    private static final String RPC_GET_AGENT_STATUS = "GetAgentStatus";

    private final StellguardClientOptions options;
    private final AgentWorkloadCredentialClient agentClient;
    private final CredentialValidator credentialValidator;
    private final AuthenticationDecisionPolicy decisionPolicy;
    private final CredentialSnapshotCache cache;
    private final StellguardTelemetry telemetry;
    private final Clock clock;

    private DefaultStellguardAuthenticator(
            StellguardClientOptions options,
            AgentWorkloadCredentialClient agentClient,
            CredentialValidator credentialValidator,
            AuthenticationDecisionPolicy decisionPolicy,
            CredentialSnapshotCache cache,
            StellguardTelemetry telemetry,
            Clock clock) {
        this.options = Objects.requireNonNull(options, "options must not be null");
        this.agentClient = Objects.requireNonNull(agentClient, "agentClient must not be null");
        this.credentialValidator =
                Objects.requireNonNull(credentialValidator, "credentialValidator must not be null");
        this.decisionPolicy = Objects.requireNonNull(decisionPolicy, "decisionPolicy must not be null");
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 创建默认认证客户端。
     *
     * @param options 客户端配置
     * @param agentClient agent 客户端
     * @return 默认认证客户端
     */
    public static DefaultStellguardAuthenticator create(
            StellguardClientOptions options, AgentWorkloadCredentialClient agentClient) {
        return new DefaultStellguardAuthenticator(
                options,
                agentClient,
                new CredentialValidator(),
                new AuthenticationDecisionPolicy(options),
                new CredentialSnapshotCache(options.cacheTtl()),
                new OpenTelemetryStellguardTelemetry(options.openTelemetry(), options),
                Clock.systemUTC());
    }

    /**
     * 执行认证。
     *
     * @param request 认证请求
     * @return 认证结果
     */
    @Override
    public AuthenticationResult authenticate(AuthenticationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Instant startedAt = clock.instant();
        AuthenticationResult result;
        Optional<AuthenticationResult> cached = cache.get(request, options);
        if (cached.isPresent()) {
            result = cached.get();
            telemetry.recordAuthentication(request, result, elapsedSince(startedAt));
            return result;
        }

        try {
            result = authenticateUncached(request);
        } catch (StatusRuntimeException exception) {
            result = handleStatusRuntimeException(exception);
        } catch (IllegalArgumentException exception) {
            result =
                    decisionPolicy.decide(
                            AuthenticationFailureClass.SDK_CONFIGURATION_ERROR,
                            AuthenticationReason.SDK_CONFIGURATION_ERROR,
                            "",
                            "",
                            0,
                            Duration.ZERO);
        } catch (RuntimeException exception) {
            result =
                    decisionPolicy.decide(
                            AuthenticationFailureClass.SDK_ERROR,
                            AuthenticationReason.SDK_ERROR,
                            "",
                            "",
                            0,
                            Duration.ZERO);
        }

        cache.put(request, options, result);
        telemetry.recordAuthentication(request, result, elapsedSince(startedAt));
        return result;
    }

    /** 关闭认证客户端。 */
    @Override
    public void close() {
        try {
            agentClient.close();
        } finally {
            telemetry.close();
        }
    }

    private AuthenticationResult authenticateUncached(AuthenticationRequest request) {
        String audience = request.effectiveAudience(options.audience());
        WorkloadCredential credential = fetchWorkloadCredential(audience);
        ValidationResult validationResult = credentialValidator.validate(credential, request, options);
        if (validationResult.valid()) {
            return AuthenticationResult.authenticated(
                    "ready",
                    validationResult.trustDomain(),
                    validationResult.bundleVersion(),
                    validationResult.credentialTtl());
        }
        return decisionPolicy.decide(
                validationResult.failureClass(),
                validationResult.reason(),
                "",
                validationResult.trustDomain(),
                validationResult.bundleVersion(),
                validationResult.credentialTtl());
    }

    private AuthenticationResult handleStatusRuntimeException(StatusRuntimeException exception) {
        AgentCallFailure failure = AgentCallExceptionMapper.map(exception);
        if (failure.grpcCode() == Status.Code.NOT_FOUND && options.checkAgentStatusOnNotFound()) {
            return refineNotFoundWithAgentStatus(failure);
        }
        return decisionPolicy.decide(
                failure.failureClass(), failure.reason(), "", "", 0, Duration.ZERO);
    }

    private AuthenticationResult refineNotFoundWithAgentStatus(AgentCallFailure originalFailure) {
        try {
            AgentStatus status = getAgentStatus();
            String state = normalizeState(status.getState());
            if ("ready".equals(state)) {
                return decisionPolicy.decide(
                        AuthenticationFailureClass.AUTHENTICATION_DENIED,
                        AuthenticationReason.AUTHENTICATION_DENIED,
                        state,
                        status.getTrustDomain(),
                        status.getBundleVersion(),
                        Duration.ZERO);
            }
            return decisionPolicy.decide(
                    AuthenticationFailureClass.AGENT_UNAVAILABLE,
                    AuthenticationReason.AGENT_NOT_READY,
                    state,
                    status.getTrustDomain(),
                    status.getBundleVersion(),
                    Duration.ZERO);
        } catch (StatusRuntimeException exception) {
            AgentCallFailure statusFailure = AgentCallExceptionMapper.map(exception);
            return decisionPolicy.decide(
                    AuthenticationFailureClass.AGENT_UNAVAILABLE,
                    statusFailure.reason(),
                    "",
                    "",
                    0,
                    Duration.ZERO);
        } catch (RuntimeException exception) {
            return decisionPolicy.decide(
                    originalFailure.failureClass(), originalFailure.reason(), "", "", 0, Duration.ZERO);
        }
    }

    private WorkloadCredential fetchWorkloadCredential(String audience) {
        Instant startedAt = clock.instant();
        try {
            WorkloadCredential credential =
                    agentClient.fetchWorkloadCredential(
                            audience, options.includePrivateKeyForAuthentication(), options.deadline());
            telemetry.recordAgentCall(
                    RPC_FETCH_WORKLOAD_CREDENTIAL, Status.Code.OK.name(), elapsedSince(startedAt));
            return credential;
        } catch (StatusRuntimeException exception) {
            telemetry.recordAgentCall(
                    RPC_FETCH_WORKLOAD_CREDENTIAL,
                    exception.getStatus().getCode().name(),
                    elapsedSince(startedAt));
            throw exception;
        } catch (RuntimeException exception) {
            telemetry.recordAgentCall(
                    RPC_FETCH_WORKLOAD_CREDENTIAL, Status.Code.UNKNOWN.name(), elapsedSince(startedAt));
            throw exception;
        }
    }

    private AgentStatus getAgentStatus() {
        Instant startedAt = clock.instant();
        try {
            AgentStatus status = agentClient.getAgentStatus(options.deadline());
            telemetry.recordAgentCall(
                    RPC_GET_AGENT_STATUS, Status.Code.OK.name(), elapsedSince(startedAt));
            return status;
        } catch (StatusRuntimeException exception) {
            telemetry.recordAgentCall(
                    RPC_GET_AGENT_STATUS, exception.getStatus().getCode().name(), elapsedSince(startedAt));
            throw exception;
        } catch (RuntimeException exception) {
            telemetry.recordAgentCall(
                    RPC_GET_AGENT_STATUS, Status.Code.UNKNOWN.name(), elapsedSince(startedAt));
            throw exception;
        }
    }

    private Duration elapsedSince(Instant startedAt) {
        return Duration.between(startedAt, clock.instant());
    }

    private static String normalizeState(String state) {
        return state == null ? "" : state.trim().toLowerCase(Locale.ROOT);
    }
}
