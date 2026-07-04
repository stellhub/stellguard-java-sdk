package io.github.stellhub.stellguard.agent;

import io.github.stellhub.stellguard.proto.agent.v1.AgentStatus;
import io.github.stellhub.stellguard.proto.agent.v1.TrustBundle;
import io.github.stellhub.stellguard.proto.agent.v1.WorkloadCredential;
import java.time.Duration;

/** stellguard-agent workload API 客户端。 */
public interface AgentWorkloadCredentialClient extends AutoCloseable {

    /**
     * 获取当前 workload credential。
     *
     * @param audience audience
     * @param includePrivateKey 是否包含私钥
     * @param deadline 调用超时
     * @return workload credential
     */
    WorkloadCredential fetchWorkloadCredential(
            String audience, boolean includePrivateKey, Duration deadline);

    /**
     * 获取 trust bundle。
     *
     * @param trustDomain trust domain
     * @param deadline 调用超时
     * @return trust bundle
     */
    TrustBundle getTrustBundle(String trustDomain, Duration deadline);

    /**
     * 获取 agent 状态。
     *
     * @param deadline 调用超时
     * @return agent 状态
     */
    AgentStatus getAgentStatus(Duration deadline);

    /** 关闭 agent 客户端。 */
    @Override
    void close();
}
