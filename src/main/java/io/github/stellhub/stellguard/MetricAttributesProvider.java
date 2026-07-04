package io.github.stellhub.stellguard;

import java.util.Map;

/** 指标属性提供器。 */
@FunctionalInterface
public interface MetricAttributesProvider {

    /**
     * 返回单次认证的额外指标属性。
     *
     * @param request 认证请求
     * @param result 认证结果
     * @return 低基数指标属性
     */
    Map<String, String> authenticationAttributes(
            AuthenticationRequest request, AuthenticationResult result);

    /**
     * 返回空属性提供器。
     *
     * @return 空属性提供器
     */
    static MetricAttributesProvider none() {
        return (request, result) -> Map.of();
    }
}
