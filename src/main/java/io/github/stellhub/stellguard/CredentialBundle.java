package io.github.stellhub.stellguard;

import io.github.stellhub.stellguard.agent.v1.TrustBundle;
import java.time.Instant;

public record CredentialBundle(
        String certificatePem,
        String privateKeyPem,
        String trustBundlePem,
        String issuer,
        String keyId,
        String serialNumber,
        Instant expiresAt) {

    /** 将 gRPC 响应转换为 SDK 公开模型。 */
    static CredentialBundle fromProto(io.github.stellhub.stellguard.agent.v1.CredentialBundle value) {
        return new CredentialBundle(
                value.getCertificatePem(),
                value.getPrivateKeyPem(),
                value.getTrustBundlePem(),
                value.getIssuer(),
                value.getKeyId(),
                value.getSerialNumber(),
                Instant.ofEpochSecond(value.getExpiresAtUnixSeconds()));
    }

    /** 从 trust bundle 响应构造只包含信任根的凭据视图。 */
    static CredentialBundle fromTrustBundle(TrustBundle value) {
        return new CredentialBundle(
                "",
                "",
                value.getTrustBundlePem(),
                value.getTrustDomain(),
                value.getKeyId(),
                "",
                Instant.EPOCH);
    }
}
