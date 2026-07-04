package io.github.stellhub.stellguard.internal;

import io.github.stellhub.stellguard.AuthenticationFailureClass;
import io.github.stellhub.stellguard.AuthenticationReason;
import java.time.Duration;
import java.util.Objects;

record ValidationResult(
        boolean valid,
        AuthenticationFailureClass failureClass,
        AuthenticationReason reason,
        String trustDomain,
        long bundleVersion,
        Duration credentialTtl) {

    ValidationResult {
        failureClass = Objects.requireNonNull(failureClass, "failureClass must not be null");
        reason = Objects.requireNonNull(reason, "reason must not be null");
        trustDomain = trustDomain == null ? "" : trustDomain;
        credentialTtl = credentialTtl == null ? Duration.ZERO : credentialTtl;
    }

    static ValidationResult valid(String trustDomain, long bundleVersion, Duration credentialTtl) {
        return new ValidationResult(
                true,
                AuthenticationFailureClass.NONE,
                AuthenticationReason.AUTHENTICATED,
                trustDomain,
                bundleVersion,
                credentialTtl);
    }

    static ValidationResult invalid(
            AuthenticationFailureClass failureClass,
            AuthenticationReason reason,
            String trustDomain,
            long bundleVersion,
            Duration credentialTtl) {
        return new ValidationResult(
                false, failureClass, reason, trustDomain, bundleVersion, credentialTtl);
    }
}
