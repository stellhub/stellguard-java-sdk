package io.github.stellhub.stellguard.internal;

import io.github.stellhub.stellguard.AuthenticationFailureClass;
import io.github.stellhub.stellguard.AuthenticationReason;
import io.github.stellhub.stellguard.AuthenticationRequest;
import io.github.stellhub.stellguard.StellguardClientOptions;
import io.github.stellhub.stellguard.proto.agent.v1.WorkloadCredential;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** workload credential 校验器。 */
public final class CredentialValidator {
    private static final int DNS_SAN_TYPE = 2;
    private static final int URI_SAN_TYPE = 6;

    private final Clock clock;

    /** 创建默认校验器。 */
    public CredentialValidator() {
        this(Clock.systemUTC());
    }

    CredentialValidator(Clock clock) {
        this.clock = clock;
    }

    /**
     * 校验 agent 返回的 workload credential。
     *
     * @param credential workload credential
     * @param request 认证请求
     * @param options 客户端配置
     * @return 校验结果
     */
    public ValidationResult validate(
            WorkloadCredential credential,
            AuthenticationRequest request,
            StellguardClientOptions options) {
        String trustDomain = credential.getTrustDomain();
        long bundleVersion = credential.getBundleVersion();
        Duration credentialTtl = Duration.ZERO;

        if (credential.getCertificatePem().isBlank() || credential.getTrustBundlePem().isBlank()) {
            return ValidationResult.invalid(
                    AuthenticationFailureClass.AGENT_UNAVAILABLE,
                    AuthenticationReason.CREDENTIAL_INVALID,
                    trustDomain,
                    bundleVersion,
                    credentialTtl);
        }

        X509Certificate leaf;
        Collection<X509Certificate> trustBundle;
        try {
            leaf = parseFirstCertificate(credential.getCertificatePem());
            trustBundle = parseCertificates(credential.getTrustBundlePem());
        } catch (CertificateException exception) {
            return ValidationResult.invalid(
                    AuthenticationFailureClass.AGENT_UNAVAILABLE,
                    AuthenticationReason.CREDENTIAL_INVALID,
                    trustDomain,
                    bundleVersion,
                    credentialTtl);
        }

        Instant now = clock.instant();
        credentialTtl = ttl(leaf, now);
        try {
            leaf.checkValidity(java.util.Date.from(now));
        } catch (CertificateException exception) {
            return ValidationResult.invalid(
                    AuthenticationFailureClass.AGENT_UNAVAILABLE,
                    AuthenticationReason.CREDENTIAL_EXPIRED,
                    trustDomain,
                    bundleVersion,
                    credentialTtl);
        }

        try {
            validateTrustChain(leaf, trustBundle);
        } catch (CertificateException
                | InvalidAlgorithmParameterException
                | NoSuchAlgorithmException
                | CertPathValidatorException exception) {
            return ValidationResult.invalid(
                    AuthenticationFailureClass.AGENT_UNAVAILABLE,
                    AuthenticationReason.CREDENTIAL_INVALID,
                    trustDomain,
                    bundleVersion,
                    credentialTtl);
        }

        String expectedTrustDomain = request.effectiveTrustDomain(options.expectedTrustDomain());
        if (!expectedTrustDomain.isBlank() && !expectedTrustDomain.equals(trustDomain)) {
            return ValidationResult.invalid(
                    AuthenticationFailureClass.AUTHENTICATION_DENIED,
                    AuthenticationReason.TRUST_DOMAIN_MISMATCH,
                    trustDomain,
                    bundleVersion,
                    credentialTtl);
        }

        if (!request.expectedSpiffeId().isBlank()
                && !request.expectedSpiffeId().equals(credential.getSpiffeId())) {
            return ValidationResult.invalid(
                    AuthenticationFailureClass.AUTHENTICATION_DENIED,
                    AuthenticationReason.SPIFFE_ID_MISMATCH,
                    trustDomain,
                    bundleVersion,
                    credentialTtl);
        }

        if (!request.expectedDnsName().isBlank()
                && !credential.getDnsNamesList().contains(request.expectedDnsName())) {
            return ValidationResult.invalid(
                    AuthenticationFailureClass.AUTHENTICATION_DENIED,
                    AuthenticationReason.DNS_NAME_MISMATCH,
                    trustDomain,
                    bundleVersion,
                    credentialTtl);
        }

        String audience = request.effectiveAudience(options.audience());
        if (!audience.isBlank() && !matchesAudience(credential, audience)) {
            return ValidationResult.invalid(
                    AuthenticationFailureClass.AUTHENTICATION_DENIED,
                    AuthenticationReason.AUDIENCE_MISMATCH,
                    trustDomain,
                    bundleVersion,
                    credentialTtl);
        }

        if (!credential.getSpiffeId().isBlank() && !uriSans(leaf).contains(credential.getSpiffeId())) {
            return ValidationResult.invalid(
                    AuthenticationFailureClass.AGENT_UNAVAILABLE,
                    AuthenticationReason.CREDENTIAL_INVALID,
                    trustDomain,
                    bundleVersion,
                    credentialTtl);
        }

        if (!request.expectedDnsName().isBlank()
                && !dnsSans(leaf).contains(request.expectedDnsName())) {
            return ValidationResult.invalid(
                    AuthenticationFailureClass.AUTHENTICATION_DENIED,
                    AuthenticationReason.DNS_NAME_MISMATCH,
                    trustDomain,
                    bundleVersion,
                    credentialTtl);
        }

        return ValidationResult.valid(trustDomain, bundleVersion, credentialTtl);
    }

    private static boolean matchesAudience(WorkloadCredential credential, String audience) {
        String normalized = audience.trim();
        return normalized.equals(credential.getSpiffeId())
                || normalized.equals(credential.getTrustDomain())
                || normalized.equals(credential.getAgentId())
                || normalized.equals(credential.getIdentityId())
                || credential.getDnsNamesList().contains(normalized);
    }

    private static X509Certificate parseFirstCertificate(String pem) throws CertificateException {
        Collection<X509Certificate> certificates = parseCertificates(pem);
        return certificates.iterator().next();
    }

    private static Collection<X509Certificate> parseCertificates(String pem)
            throws CertificateException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        byte[] bytes = pem.getBytes(StandardCharsets.US_ASCII);
        Collection<? extends Certificate> certificates =
                certificateFactory.generateCertificates(new ByteArrayInputStream(bytes));
        if (certificates.isEmpty()) {
            throw new CertificateException("certificate pem is empty");
        }
        List<X509Certificate> x509Certificates = new ArrayList<>(certificates.size());
        for (Certificate certificate : certificates) {
            if (!(certificate instanceof X509Certificate x509Certificate)) {
                throw new CertificateException("certificate is not x509");
            }
            x509Certificates.add(x509Certificate);
        }
        return x509Certificates;
    }

    private static void validateTrustChain(
            X509Certificate leaf, Collection<X509Certificate> trustBundle)
            throws CertificateException,
                    InvalidAlgorithmParameterException,
                    NoSuchAlgorithmException,
                    CertPathValidatorException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        CertPath certPath = certificateFactory.generateCertPath(List.of(leaf));
        Set<TrustAnchor> trustAnchors = new HashSet<>();
        for (X509Certificate certificate : trustBundle) {
            trustAnchors.add(new TrustAnchor(certificate, null));
        }
        PKIXParameters parameters = new PKIXParameters(trustAnchors);
        parameters.setRevocationEnabled(false);
        CertPathValidator.getInstance("PKIX").validate(certPath, parameters);
    }

    private static Duration ttl(X509Certificate certificate, Instant now) {
        Duration ttl = Duration.between(now, certificate.getNotAfter().toInstant());
        return ttl.isNegative() ? Duration.ZERO : ttl;
    }

    private static Set<String> dnsSans(X509Certificate certificate) {
        return subjectAlternativeNames(certificate, DNS_SAN_TYPE);
    }

    private static Set<String> uriSans(X509Certificate certificate) {
        return subjectAlternativeNames(certificate, URI_SAN_TYPE);
    }

    private static Set<String> subjectAlternativeNames(X509Certificate certificate, int type) {
        Set<String> values = new HashSet<>();
        try {
            Collection<List<?>> names = certificate.getSubjectAlternativeNames();
            if (names == null) {
                return values;
            }
            for (List<?> name : names) {
                if (name.size() >= 2 && name.get(0) instanceof Integer sanType && sanType == type) {
                    values.add(String.valueOf(name.get(1)));
                }
            }
            return values;
        } catch (CertificateException exception) {
            return Set.of();
        }
    }
}
