package io.github.stellhub.stellguard.telemetry;

import io.github.stellhub.stellguard.AuthenticationFailureClass;
import io.github.stellhub.stellguard.AuthenticationRequest;
import io.github.stellhub.stellguard.AuthenticationResult;
import io.github.stellhub.stellguard.StellguardClientOptions;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** OpenTelemetry 指标记录器。 */
public final class OpenTelemetryStellguardTelemetry implements StellguardTelemetry {
    private static final String METER_NAME = "io.github.stellhub.stellguard";
    private static final String INSTRUMENTATION_VERSION = "0.0.1";

    private final StellguardClientOptions options;
    private final LongCounter authRequests;
    private final DoubleHistogram authDuration;
    private final LongCounter authFailOpen;
    private final LongCounter agentGrpcCalls;
    private final DoubleHistogram agentGrpcDuration;
    private final AtomicLong agentAvailable = new AtomicLong(0);
    private final AtomicLong credentialTtlSeconds = new AtomicLong(0);
    private final AtomicLong trustBundleVersion = new AtomicLong(0);
    private final AtomicReference<String> agentState = new AtomicReference<>("unknown");
    private final AtomicReference<String> trustDomain = new AtomicReference<>("");
    private final List<ObservableLongGauge> observableGauges = new ArrayList<>();

    /**
     * 创建 OpenTelemetry 指标记录器。
     *
     * @param openTelemetry OpenTelemetry 实例
     * @param options 客户端配置
     */
    public OpenTelemetryStellguardTelemetry(
            OpenTelemetry openTelemetry, StellguardClientOptions options) {
        this.options = Objects.requireNonNull(options, "options must not be null");
        OpenTelemetry telemetry = openTelemetry == null ? OpenTelemetry.noop() : openTelemetry;
        Meter meter =
                telemetry
                        .getMeterProvider()
                        .meterBuilder(METER_NAME)
                        .setInstrumentationVersion(INSTRUMENTATION_VERSION)
                        .build();
        this.authRequests =
                meter
                        .counterBuilder("stellguard.auth.requests")
                        .setDescription("StellGuard authentication requests")
                        .build();
        this.authDuration =
                meter
                        .histogramBuilder("stellguard.auth.duration")
                        .setDescription("StellGuard authentication duration")
                        .setUnit("ms")
                        .build();
        this.authFailOpen =
                meter
                        .counterBuilder("stellguard.auth.fail_open")
                        .setDescription("StellGuard requests allowed by fail-open or bypass policy")
                        .build();
        this.agentGrpcCalls =
                meter
                        .counterBuilder("stellguard.agent.grpc.calls")
                        .setDescription("StellGuard agent gRPC calls")
                        .build();
        this.agentGrpcDuration =
                meter
                        .histogramBuilder("stellguard.agent.grpc.duration")
                        .setDescription("StellGuard agent gRPC duration")
                        .setUnit("ms")
                        .build();

        observableGauges.add(
                meter
                        .gaugeBuilder("stellguard.agent.available")
                        .ofLongs()
                        .setDescription("Last observed StellGuard agent availability")
                        .buildWithCallback(
                                measurement ->
                                        measurement.record(
                                                agentAvailable.get(),
                                                Attributes.of(AttributeKey.stringKey("agent.state"), agentState.get()))));
        observableGauges.add(
                meter
                        .gaugeBuilder("stellguard.credential.ttl")
                        .ofLongs()
                        .setDescription("Last observed StellGuard credential TTL")
                        .setUnit("s")
                        .buildWithCallback(
                                measurement ->
                                        measurement.record(
                                                credentialTtlSeconds.get(),
                                                Attributes.of(AttributeKey.stringKey("trust.domain"), trustDomain.get()))));
        observableGauges.add(
                meter
                        .gaugeBuilder("stellguard.trust_bundle.version")
                        .ofLongs()
                        .setDescription("Last observed StellGuard trust bundle version")
                        .buildWithCallback(
                                measurement ->
                                        measurement.record(
                                                trustBundleVersion.get(),
                                                Attributes.of(AttributeKey.stringKey("trust.domain"), trustDomain.get()))));
    }

    /**
     * 记录认证请求。
     *
     * @param request 认证请求
     * @param result 认证结果
     * @param duration 认证耗时
     */
    @Override
    public void recordAuthentication(
            AuthenticationRequest request, AuthenticationResult result, Duration duration) {
        Attributes attributes = authenticationAttributes(request, result);
        authRequests.add(1, attributes);
        authDuration.record(toMillis(duration), attributes);
        if (result.allowedByPolicyOverride()
                && result.failureClass() != AuthenticationFailureClass.NONE) {
            authFailOpen.add(1, attributes);
        }
        agentAvailable.set(
                result.failureClass() == AuthenticationFailureClass.AGENT_UNAVAILABLE ? 0 : 1);
        if (!result.agentState().isBlank()) {
            agentState.set(result.agentState());
        }
        if (!result.trustDomain().isBlank()) {
            trustDomain.set(result.trustDomain());
        }
        credentialTtlSeconds.set(Math.max(0, result.credentialTtl().toSeconds()));
        trustBundleVersion.set(Math.max(0, result.bundleVersion()));
    }

    /**
     * 记录 agent gRPC 调用。
     *
     * @param rpc RPC 名称
     * @param grpcStatus gRPC 状态
     * @param duration 调用耗时
     */
    @Override
    public void recordAgentCall(String rpc, String grpcStatus, Duration duration) {
        Attributes attributes =
                Attributes.builder()
                        .put(AttributeKey.stringKey("rpc"), rpc == null ? "unknown" : rpc)
                        .put(AttributeKey.stringKey("grpc.status"), grpcStatus == null ? "UNKNOWN" : grpcStatus)
                        .build();
        agentGrpcCalls.add(1, attributes);
        agentGrpcDuration.record(toMillis(duration), attributes);
    }

    /** 关闭 OpenTelemetry observable callback。 */
    @Override
    public void close() {
        for (ObservableLongGauge observableGauge : observableGauges) {
            observableGauge.close();
        }
        observableGauges.clear();
    }

    private Attributes authenticationAttributes(
            AuthenticationRequest request, AuthenticationResult result) {
        AttributesBuilder builder =
                Attributes.builder()
                        .put(AttributeKey.stringKey("decision"), result.decision().name())
                        .put(AttributeKey.stringKey("failure.class"), result.failureClass().name())
                        .put(AttributeKey.stringKey("reason"), result.reason().name());
        for (Map.Entry<String, String> entry : request.metricAttributes().entrySet()) {
            putSafe(builder, entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry :
                options.metricAttributesProvider().authenticationAttributes(request, result).entrySet()) {
            putSafe(builder, entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    private static void putSafe(AttributesBuilder builder, String key, String value) {
        if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
            builder.put(AttributeKey.stringKey(key.trim()), value.trim());
        }
    }

    private static double toMillis(Duration duration) {
        return Math.max(0, duration.toNanos() / 1_000_000.0d);
    }
}
