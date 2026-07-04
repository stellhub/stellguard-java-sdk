package io.github.stellhub.stellguard;

import java.time.Duration;
import java.util.Objects;

/**
 * 认证结果。
 *
 * @param decision 最终决策
 * @param failureClass 失败分类
 * @param reason 结果原因
 * @param authenticated 是否真实认证成功
 * @param allowedByPolicyOverride 是否由策略覆盖后放行
 * @param agentState 最近观测到的 agent 状态
 * @param trustDomain trust domain
 * @param bundleVersion trust bundle 版本
 * @param credentialTtl 凭据剩余有效期
 */
public record AuthenticationResult(
        AuthenticationDecision decision,
        AuthenticationFailureClass failureClass,
        AuthenticationReason reason,
        boolean authenticated,
        boolean allowedByPolicyOverride,
        String agentState,
        String trustDomain,
        long bundleVersion,
        Duration credentialTtl) {

    public AuthenticationResult {
        decision = Objects.requireNonNull(decision, "decision must not be null");
        failureClass = Objects.requireNonNull(failureClass, "failureClass must not be null");
        reason = Objects.requireNonNull(reason, "reason must not be null");
        agentState = agentState == null ? "" : agentState;
        trustDomain = trustDomain == null ? "" : trustDomain;
        credentialTtl = credentialTtl == null ? Duration.ZERO : credentialTtl;
    }

    /**
     * 创建认证成功结果。
     *
     * @param agentState agent 状态
     * @param trustDomain trust domain
     * @param bundleVersion trust bundle 版本
     * @param credentialTtl 凭据剩余有效期
     * @return 认证成功结果
     */
    public static AuthenticationResult authenticated(
            String agentState, String trustDomain, long bundleVersion, Duration credentialTtl) {
        return new AuthenticationResult(
                AuthenticationDecision.ALLOW,
                AuthenticationFailureClass.NONE,
                AuthenticationReason.AUTHENTICATED,
                true,
                false,
                agentState,
                trustDomain,
                bundleVersion,
                credentialTtl);
    }

    /**
     * 判断最终是否放行。
     *
     * @return true 表示放行
     */
    public boolean allowed() {
        return decision == AuthenticationDecision.ALLOW;
    }

    /**
     * 判断最终是否拦截。
     *
     * @return true 表示拦截
     */
    public boolean denied() {
        return decision == AuthenticationDecision.DENY;
    }
}
