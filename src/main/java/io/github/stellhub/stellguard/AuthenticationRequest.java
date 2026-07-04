package io.github.stellhub.stellguard;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单次认证请求。
 *
 * @param audience 期望匹配的 audience
 * @param expectedTrustDomain 期望的 trust domain
 * @param expectedSpiffeId 期望的 SPIFFE ID
 * @param expectedDnsName 期望的 DNS SAN
 * @param metricAttributes 调用方显式传入的低基数指标属性
 */
public record AuthenticationRequest(
        String audience,
        String expectedTrustDomain,
        String expectedSpiffeId,
        String expectedDnsName,
        Map<String, String> metricAttributes) {

    public AuthenticationRequest {
        audience = normalize(audience);
        expectedTrustDomain = normalize(expectedTrustDomain);
        expectedSpiffeId = normalize(expectedSpiffeId);
        expectedDnsName = normalize(expectedDnsName);
        metricAttributes =
                metricAttributes == null
                        ? Map.of()
                        : Collections.unmodifiableMap(new LinkedHashMap<>(metricAttributes));
    }

    /**
     * 创建请求构建器。
     *
     * @return 请求构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 返回带默认 audience 的有效 audience。
     *
     * @param defaultAudience 默认 audience
     * @return 有效 audience
     */
    public String effectiveAudience(String defaultAudience) {
        if (!audience.isBlank()) {
            return audience;
        }
        return normalize(defaultAudience);
    }

    /**
     * 返回带默认 trust domain 的有效 trust domain。
     *
     * @param defaultTrustDomain 默认 trust domain
     * @return 有效 trust domain
     */
    public String effectiveTrustDomain(String defaultTrustDomain) {
        if (!expectedTrustDomain.isBlank()) {
            return expectedTrustDomain;
        }
        return normalize(defaultTrustDomain);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    /** 认证请求构建器。 */
    public static final class Builder {
        private String audience = "";
        private String expectedTrustDomain = "";
        private String expectedSpiffeId = "";
        private String expectedDnsName = "";
        private final Map<String, String> metricAttributes = new LinkedHashMap<>();

        private Builder() {}

        /**
         * 设置 audience。
         *
         * @param audience audience
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
         * 设置期望 SPIFFE ID。
         *
         * @param expectedSpiffeId 期望 SPIFFE ID
         * @return 当前构建器
         */
        public Builder expectedSpiffeId(String expectedSpiffeId) {
            this.expectedSpiffeId = expectedSpiffeId;
            return this;
        }

        /**
         * 设置期望 DNS SAN。
         *
         * @param expectedDnsName 期望 DNS SAN
         * @return 当前构建器
         */
        public Builder expectedDnsName(String expectedDnsName) {
            this.expectedDnsName = expectedDnsName;
            return this;
        }

        /**
         * 添加指标属性。
         *
         * @param key 属性名
         * @param value 属性值
         * @return 当前构建器
         */
        public Builder metricAttribute(String key, String value) {
            if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                this.metricAttributes.put(key.trim(), value.trim());
            }
            return this;
        }

        /**
         * 构建认证请求。
         *
         * @return 认证请求
         */
        public AuthenticationRequest build() {
            return new AuthenticationRequest(
                    audience, expectedTrustDomain, expectedSpiffeId, expectedDnsName, metricAttributes);
        }
    }
}
