package io.github.stellhub.stellguard;

import java.nio.file.Path;
import java.time.Duration;

public record StellguardClientOptions(Path socketPath, Duration deadline, String agentToken) {
    private static final Path DEFAULT_SOCKET_PATH = Path.of("/var/run/stellguard/agent.sock");
    private static final Duration DEFAULT_DEADLINE = Duration.ofSeconds(3);

    /** 创建客户端配置构建器。 */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Path socketPath = DEFAULT_SOCKET_PATH;
        private Duration deadline = DEFAULT_DEADLINE;
        private String agentToken;

        private Builder() {}

        /** 设置 stellguard-agent 的 Unix Domain Socket 路径。 */
        public Builder socketPath(Path socketPath) {
            this.socketPath = socketPath;
            return this;
        }

        /** 设置单次 gRPC 调用超时时间。 */
        public Builder deadline(Duration deadline) {
            this.deadline = deadline;
            return this;
        }

        /** 设置访问本地 agent 时使用的 bearer token。 */
        public Builder agentToken(String agentToken) {
            this.agentToken = agentToken;
            return this;
        }

        /** 构建不可变客户端配置。 */
        public StellguardClientOptions build() {
            if (socketPath == null) {
                throw new IllegalArgumentException("socketPath is required");
            }
            if (deadline == null || deadline.isZero() || deadline.isNegative()) {
                throw new IllegalArgumentException("deadline must be positive");
            }
            return new StellguardClientOptions(socketPath, deadline, agentToken);
        }
    }
}
