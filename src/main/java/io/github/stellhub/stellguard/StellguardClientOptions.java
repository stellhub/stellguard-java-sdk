package io.github.stellhub.stellguard;

import io.opentelemetry.api.OpenTelemetry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/** StellGuard SDK 客户端配置。 */
public final class StellguardClientOptions {
    public static final Path DEFAULT_SOCKET_PATH = Path.of("/var/run/stellguard/agent.sock");
    public static final Duration DEFAULT_DEADLINE = Duration.ofSeconds(3);
    public static final Duration DEFAULT_CACHE_TTL = Duration.ofSeconds(1);

    private final Path socketPath;
    private final Duration deadline;
    private final String audience;
    private final String expectedTrustDomain;
    private final boolean includePrivateKeyForAuthentication;
    private final boolean failOpenOnAgentUnavailable;
    private final boolean allowOnAuthenticationDenied;
    private final boolean checkAgentStatusOnNotFound;
    private final Duration cacheTtl;
    private final OpenTelemetry openTelemetry;
    private final MetricAttributesProvider metricAttributesProvider;

    private StellguardClientOptions(Builder builder) {
        this.socketPath = Objects.requireNonNull(builder.socketPath, "socketPath must not be null");
        this.deadline = validatePositive(builder.deadline, "deadline");
        this.audience = normalize(builder.audience);
        this.expectedTrustDomain = normalize(builder.expectedTrustDomain);
        this.includePrivateKeyForAuthentication = builder.includePrivateKeyForAuthentication;
        this.failOpenOnAgentUnavailable = builder.failOpenOnAgentUnavailable;
        this.allowOnAuthenticationDenied = builder.allowOnAuthenticationDenied;
        this.checkAgentStatusOnNotFound = builder.checkAgentStatusOnNotFound;
        this.cacheTtl = validateNotNegative(builder.cacheTtl, "cacheTtl");
        this.openTelemetry =
                builder.openTelemetry == null ? OpenTelemetry.noop() : builder.openTelemetry;
        this.metricAttributesProvider =
                builder.metricAttributesProvider == null
                        ? MetricAttributesProvider.none()
                        : builder.metricAttributesProvider;
    }

    /**
     * 创建配置构建器。
     *
     * @return 配置构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 返回 agent UDS 路径。
     *
     * @return agent UDS 路径
     */
    public Path socketPath() {
        return socketPath;
    }

    /**
     * 返回单次 gRPC 调用超时。
     *
     * @return 单次 gRPC 调用超时
     */
    public Duration deadline() {
        return deadline;
    }

    /**
     * 返回默认 audience。
     *
     * @return 默认 audience
     */
    public String audience() {
        return audience;
    }

    /**
     * 返回期望 trust domain。
     *
     * @return 期望 trust domain
     */
    public String expectedTrustDomain() {
        return expectedTrustDomain;
    }

    /**
     * 判断认证路径是否请求私钥。
     *
     * @return true 表示请求私钥
     */
    public boolean includePrivateKeyForAuthentication() {
        return includePrivateKeyForAuthentication;
    }

    /**
     * 判断 agent 不可用时是否默认放行。
     *
     * @return true 表示放行
     */
    public boolean failOpenOnAgentUnavailable() {
        return failOpenOnAgentUnavailable;
    }

    /**
     * 判断 agent 正常拒绝时是否放行。
     *
     * @return true 表示放行
     */
    public boolean allowOnAuthenticationDenied() {
        return allowOnAuthenticationDenied;
    }

    /**
     * 判断 NotFound 时是否查询 agent 状态。
     *
     * @return true 表示查询 agent 状态
     */
    public boolean checkAgentStatusOnNotFound() {
        return checkAgentStatusOnNotFound;
    }

    /**
     * 返回认证结果短缓存时间。
     *
     * @return 缓存时间
     */
    public Duration cacheTtl() {
        return cacheTtl;
    }

    /**
     * 返回 OpenTelemetry 实例。
     *
     * @return OpenTelemetry 实例
     */
    public OpenTelemetry openTelemetry() {
        return openTelemetry;
    }

    /**
     * 返回指标属性提供器。
     *
     * @return 指标属性提供器
     */
    public MetricAttributesProvider metricAttributesProvider() {
        return metricAttributesProvider;
    }

    private static Duration validatePositive(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static Duration validateNotNegative(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    /** StellGuard SDK 客户端配置构建器。 */
    public static final class Builder {
        private Path socketPath = DEFAULT_SOCKET_PATH;
        private Duration deadline = DEFAULT_DEADLINE;
        private String audience = "";
        private String expectedTrustDomain = "";
        private boolean includePrivateKeyForAuthentication;
        private boolean failOpenOnAgentUnavailable = true;
        private boolean allowOnAuthenticationDenied;
        private boolean checkAgentStatusOnNotFound = true;
        private Duration cacheTtl = DEFAULT_CACHE_TTL;
        private OpenTelemetry openTelemetry = OpenTelemetry.noop();
        private MetricAttributesProvider metricAttributesProvider = MetricAttributesProvider.none();

        private Builder() {}

        /**
         * 设置 agent UDS 路径。
         *
         * @param socketPath agent UDS 路径
         * @return 当前构建器
         */
        public Builder socketPath(Path socketPath) {
            this.socketPath = socketPath;
            return this;
        }

        /**
         * 设置单次 gRPC 调用超时。
         *
         * @param deadline 单次 gRPC 调用超时
         * @return 当前构建器
         */
        public Builder deadline(Duration deadline) {
            this.deadline = deadline;
            return this;
        }

        /**
         * 设置默认 audience。
         *
         * @param audience 默认 audience
         * @return 当前构建器
         */
        public Builder audience(String audience) {
            this.audience = audience;
            return this;
        }

        /**
         * 设置期望 trust domain。
         *
         * @param expectedTrustDomain 期望 trust domain
         * @return 当前构建器
         */
        public Builder expectedTrustDomain(String expectedTrustDomain) {
            this.expectedTrustDomain = expectedTrustDomain;
            return this;
        }

        /**
         * 设置认证路径是否请求私钥。
         *
         * @param includePrivateKeyForAuthentication true 表示请求私钥
         * @return 当前构建器
         */
        public Builder includePrivateKeyForAuthentication(boolean includePrivateKeyForAuthentication) {
            this.includePrivateKeyForAuthentication = includePrivateKeyForAuthentication;
            return this;
        }

        /**
         * 设置 agent 不可用时是否放行。
         *
         * @param failOpenOnAgentUnavailable true 表示放行
         * @return 当前构建器
         */
        public Builder failOpenOnAgentUnavailable(boolean failOpenOnAgentUnavailable) {
            this.failOpenOnAgentUnavailable = failOpenOnAgentUnavailable;
            return this;
        }

        /**
         * 设置 agent 正常拒绝时是否放行。
         *
         * @param allowOnAuthenticationDenied true 表示放行
         * @return 当前构建器
         */
        public Builder allowOnAuthenticationDenied(boolean allowOnAuthenticationDenied) {
            this.allowOnAuthenticationDenied = allowOnAuthenticationDenied;
            return this;
        }

        /**
         * 设置 NotFound 时是否查询 agent 状态。
         *
         * @param checkAgentStatusOnNotFound true 表示查询 agent 状态
         * @return 当前构建器
         */
        public Builder checkAgentStatusOnNotFound(boolean checkAgentStatusOnNotFound) {
            this.checkAgentStatusOnNotFound = checkAgentStatusOnNotFound;
            return this;
        }

        /**
         * 设置认证结果短缓存时间。
         *
         * @param cacheTtl 缓存时间
         * @return 当前构建器
         */
        public Builder cacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
            return this;
        }

        /**
         * 设置 OpenTelemetry 实例。
         *
         * @param openTelemetry OpenTelemetry 实例
         * @return 当前构建器
         */
        public Builder openTelemetry(OpenTelemetry openTelemetry) {
            this.openTelemetry = openTelemetry;
            return this;
        }

        /**
         * 设置指标属性提供器。
         *
         * @param metricAttributesProvider 指标属性提供器
         * @return 当前构建器
         */
        public Builder metricAttributesProvider(MetricAttributesProvider metricAttributesProvider) {
            this.metricAttributesProvider = metricAttributesProvider;
            return this;
        }

        /**
         * 构建客户端配置。
         *
         * @return 客户端配置
         */
        public StellguardClientOptions build() {
            return new StellguardClientOptions(this);
        }
    }
}
