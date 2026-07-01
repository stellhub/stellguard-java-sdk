package io.github.stellhub.stellguard;

import io.github.stellhub.stellguard.agent.v1.FetchWorkloadCertificateRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record CredentialRequest(String spiffeId, List<String> dnsNames, Duration ttl, boolean forceRotate) {

    /** 创建证书请求构建器。 */
    public static Builder builder() {
        return new Builder();
    }

    /** 转换为 agent gRPC 请求。 */
    FetchWorkloadCertificateRequest toProto() {
        FetchWorkloadCertificateRequest.Builder builder = FetchWorkloadCertificateRequest.newBuilder()
                .setSpiffeId(spiffeId)
                .setForceRotate(forceRotate);
        builder.addAllDnsNames(dnsNames);
        if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
            builder.setTtlSeconds(ttl.toSeconds());
        }
        return builder.build();
    }

    public static final class Builder {
        private String spiffeId;
        private List<String> dnsNames = List.of();
        private Duration ttl = Duration.ofMinutes(15);
        private boolean forceRotate;

        private Builder() {}

        /** 设置 workload 的 SPIFFE ID。 */
        public Builder spiffeId(String spiffeId) {
            this.spiffeId = spiffeId;
            return this;
        }

        /** 设置证书 DNS SAN。 */
        public Builder dnsNames(List<String> dnsNames) {
            this.dnsNames = dnsNames == null ? List.of() : List.copyOf(dnsNames);
            return this;
        }

        /** 设置期望的证书 TTL。 */
        public Builder ttl(Duration ttl) {
            this.ttl = ttl;
            return this;
        }

        /** 要求 agent 强制轮换本地凭据。 */
        public Builder forceRotate(boolean forceRotate) {
            this.forceRotate = forceRotate;
            return this;
        }

        /** 构建不可变请求对象。 */
        public CredentialRequest build() {
            if (spiffeId == null || spiffeId.isBlank()) {
                throw new IllegalArgumentException("spiffeId is required");
            }
            if (!spiffeId.startsWith("spiffe://")) {
                throw new IllegalArgumentException("spiffeId must start with spiffe://");
            }
            if (ttl != null && ttl.isNegative()) {
                throw new IllegalArgumentException("ttl cannot be negative");
            }

            List<String> cleanDNSNames = new ArrayList<>();
            for (String dnsName : Objects.requireNonNullElse(dnsNames, List.<String>of())) {
                if (dnsName != null && !dnsName.isBlank()) {
                    cleanDNSNames.add(dnsName.trim());
                }
            }
            return new CredentialRequest(spiffeId.trim(), List.copyOf(cleanDNSNames), ttl, forceRotate);
        }
    }
}
