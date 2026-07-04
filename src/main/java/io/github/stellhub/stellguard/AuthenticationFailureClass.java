package io.github.stellhub.stellguard;

/** 认证失败分类。 */
public enum AuthenticationFailureClass {
    NONE,
    AUTHENTICATION_DENIED,
    AGENT_UNAVAILABLE,
    SDK_CONFIGURATION_ERROR,
    SDK_ERROR
}
