package io.github.stellhub.stellguard.internal;

import io.github.stellhub.stellguard.AuthenticationDecision;
import io.github.stellhub.stellguard.AuthenticationFailureClass;
import io.github.stellhub.stellguard.AuthenticationReason;
import io.github.stellhub.stellguard.AuthenticationResult;
import io.github.stellhub.stellguard.StellguardClientOptions;
import java.time.Duration;
import java.util.Objects;

/** 认证决策策略。 */
public final class AuthenticationDecisionPolicy {
    private final StellguardClientOptions options;

    /**
     * 创建认证决策策略。
     *
     * @param options 客户端配置
     */
    public AuthenticationDecisionPolicy(StellguardClientOptions options) {
        this.options = Objects.requireNonNull(options, "options must not be null");
    }

    /**
     * 根据失败分类生成最终认证结果。
     *
     * @param failureClass 失败分类
     * @param reason 失败原因
     * @param agentState agent 状态
     * @param trustDomain trust domain
     * @param bundleVersion trust bundle 版本
     * @param credentialTtl 凭据剩余有效期
     * @return 认证结果
     */
    public AuthenticationResult decide(
            AuthenticationFailureClass failureClass,
            AuthenticationReason reason,
            String agentState,
            String trustDomain,
            long bundleVersion,
            Duration credentialTtl) {
        if (failureClass == AuthenticationFailureClass.NONE) {
            return AuthenticationResult.authenticated(
                    agentState, trustDomain, bundleVersion, credentialTtl);
        }
        if (failureClass == AuthenticationFailureClass.AGENT_UNAVAILABLE) {
            return new AuthenticationResult(
                    options.failOpenOnAgentUnavailable()
                            ? AuthenticationDecision.ALLOW
                            : AuthenticationDecision.DENY,
                    failureClass,
                    reason,
                    false,
                    options.failOpenOnAgentUnavailable(),
                    agentState,
                    trustDomain,
                    bundleVersion,
                    credentialTtl);
        }
        if (failureClass == AuthenticationFailureClass.AUTHENTICATION_DENIED) {
            boolean allowed = options.allowOnAuthenticationDenied();
            return new AuthenticationResult(
                    allowed ? AuthenticationDecision.ALLOW : AuthenticationDecision.DENY,
                    failureClass,
                    allowed ? AuthenticationReason.AUTHENTICATION_DENIED_BYPASSED : reason,
                    false,
                    allowed,
                    agentState,
                    trustDomain,
                    bundleVersion,
                    credentialTtl);
        }
        return new AuthenticationResult(
                AuthenticationDecision.DENY,
                failureClass,
                reason,
                false,
                false,
                agentState,
                trustDomain,
                bundleVersion,
                credentialTtl);
    }
}
