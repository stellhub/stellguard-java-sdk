package io.github.stellhub.stellguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.stellhub.stellguard.agent.AgentWorkloadCredentialClient;
import io.github.stellhub.stellguard.proto.agent.v1.AgentStatus;
import io.github.stellhub.stellguard.proto.agent.v1.TrustBundle;
import io.github.stellhub.stellguard.proto.agent.v1.WorkloadCredential;
import io.grpc.Status;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class StellguardAuthenticatorTest {
    private static final String CERTIFICATE_PEM =
            """
            -----BEGIN CERTIFICATE-----
            MIIDfjCCAmagAwIBAgIJAPbFGgxpJCftMA0GCSqGSIb3DQEBDAUAMEoxCzAJBgNV
            BAYTAkNOMREwDwYDVQQHEwhTaGFuZ2hhaTETMBEGA1UEChMKU3RlbGxHdWFyZDET
            MBEGA1UEAxMKb3JkZXJzLWFwaTAeFw0yNjA3MDQxMjI3MjdaFw0zNjA3MDExMjI3
            MjdaMEoxCzAJBgNVBAYTAkNOMREwDwYDVQQHEwhTaGFuZ2hhaTETMBEGA1UEChMK
            U3RlbGxHdWFyZDETMBEGA1UEAxMKb3JkZXJzLWFwaTCCASIwDQYJKoZIhvcNAQEB
            BQADggEPADCCAQoCggEBAKmHZbsQ9u9cw3G0HaqtNm4QOs3KN79uhDDUXBitwFMk
            cetnYoPwcf7YWnsPMbOp1bMIWsFHMVQdce6r850V9ZW6CiBrcmw4X2SBweU4cH+b
            xce70M6xNufQz/S4UVMZElvQ7utseLoikHfK8sutZ85Ynm7DtTo/qMR+iIJURb+g
            ohG8UOn6SFiRHfdikWnfqMNG+3Cx+4nQ/CULLNe0q7O7eDrUX91UJ5wpNkzX1V8i
            543L5I+BHTTgIBfiWvAUpn0agEG1I3u1qLgiM2nGUyV5G61/JBbwnc1uFpZgWSgr
            IWLkIjT7BC5/9aYAmOIa7KALor3Er+mzNHgQ0HwhPxECAwEAAaNnMGUwHQYDVR0O
            BBYEFC1x9RhHRTcgYxkt4aIej3WByUWnMEQGA1UdEQQ9MDuGLXNwaWZmZTovL3N0
            ZWxsLmxvY2FsL25zL2RlZmF1bHQvc2Evb3JkZXJzLWFwaYIKb3JkZXJzLWFwaTAN
            BgkqhkiG9w0BAQwFAAOCAQEAG9dX8NgxuXPqDPJ8jnoif7YugXl0Z2J7Eo7PXcmH
            LkEOgoPHXSv8mqXTJR34QxLAnvBnJu36ir6hxLMMyUTDA0o7YeSip+lO1zoJEq9M
            q1pZVgoBUo2Gj2djZ6RM6xu1V5BG5HFvYX/mYdPELnvn3AQO4mBPAqT/wzm6YXSH
            Rl2f+4RHAHDtnnwMF4Sgo/t2PAUAlWV1aZLdh+hqJUV37fPkWW/Taphf2uwZd+8K
            0IKT9YM6uC5wO5Cvq7MQoTpOZ7CteZDKqugF2E/DT7T5VF2xahTuo2Cdg1eKuxNx
            ZNbu5ewRvXn7r8aLbsZaDNuFy6XC9oHAKVJPAKhmwBuIrw==
            -----END CERTIFICATE-----
            """;

    @Test
    void shouldFailOpenWhenAgentUnavailableByDefault() {
        FakeAgentClient agentClient =
                new FakeAgentClient().fetchFailure(Status.UNAVAILABLE.asRuntimeException());

        AuthenticationResult result = authenticate(defaultOptions(), agentClient, defaultRequest());

        assertEquals(AuthenticationDecision.ALLOW, result.decision());
        assertEquals(AuthenticationFailureClass.AGENT_UNAVAILABLE, result.failureClass());
        assertEquals(AuthenticationReason.AGENT_GRPC_UNAVAILABLE, result.reason());
        assertTrue(result.allowedByPolicyOverride());
        assertFalse(result.authenticated());
    }

    @Test
    void shouldFailOpenWhenGrpcTransportCannotReachAgent() {
        StellguardClientOptions options =
                StellguardClientOptions.builder()
                        .socketPath(Path.of("/tmp/stellguard-java-sdk-missing-agent.sock"))
                        .deadline(Duration.ofMillis(50))
                        .cacheTtl(Duration.ZERO)
                        .build();

        try (StellguardAuthenticator authenticator = StellguardAuthenticator.connect(options)) {
            AuthenticationResult result = authenticator.authenticate(defaultRequest());

            assertEquals(AuthenticationDecision.ALLOW, result.decision());
            assertEquals(AuthenticationFailureClass.AGENT_UNAVAILABLE, result.failureClass());
            assertTrue(result.allowedByPolicyOverride());
            assertFalse(result.authenticated());
        }
    }

    @Test
    void shouldDenyWhenHealthyAgentRejectsByDefault() {
        FakeAgentClient agentClient =
                new FakeAgentClient().fetchFailure(Status.PERMISSION_DENIED.asRuntimeException());

        AuthenticationResult result = authenticate(defaultOptions(), agentClient, defaultRequest());

        assertEquals(AuthenticationDecision.DENY, result.decision());
        assertEquals(AuthenticationFailureClass.AUTHENTICATION_DENIED, result.failureClass());
        assertEquals(AuthenticationReason.AUTHENTICATION_DENIED, result.reason());
        assertFalse(result.allowedByPolicyOverride());
        assertFalse(result.authenticated());
    }

    @Test
    void shouldAllowHealthyAgentRejectionWhenConfigured() {
        FakeAgentClient agentClient =
                new FakeAgentClient().fetchFailure(Status.PERMISSION_DENIED.asRuntimeException());
        StellguardClientOptions options =
                StellguardClientOptions.builder()
                        .cacheTtl(Duration.ZERO)
                        .allowOnAuthenticationDenied(true)
                        .build();

        AuthenticationResult result = authenticate(options, agentClient, defaultRequest());

        assertEquals(AuthenticationDecision.ALLOW, result.decision());
        assertEquals(AuthenticationFailureClass.AUTHENTICATION_DENIED, result.failureClass());
        assertEquals(AuthenticationReason.AUTHENTICATION_DENIED_BYPASSED, result.reason());
        assertTrue(result.allowedByPolicyOverride());
        assertFalse(result.authenticated());
    }

    @Test
    void shouldTreatNotFoundAsAuthenticationDeniedWhenAgentIsReady() {
        FakeAgentClient agentClient =
                new FakeAgentClient()
                        .fetchFailure(Status.NOT_FOUND.asRuntimeException())
                        .status(
                                AgentStatus.newBuilder()
                                        .setState("ready")
                                        .setTrustDomain("stell.local")
                                        .setBundleVersion(12)
                                        .build());

        AuthenticationResult result = authenticate(defaultOptions(), agentClient, defaultRequest());

        assertEquals(AuthenticationDecision.DENY, result.decision());
        assertEquals(AuthenticationFailureClass.AUTHENTICATION_DENIED, result.failureClass());
        assertEquals(AuthenticationReason.AUTHENTICATION_DENIED, result.reason());
        assertEquals("ready", result.agentState());
        assertEquals(12, result.bundleVersion());
    }

    @Test
    void shouldTreatNotFoundAsAgentUnavailableWhenAgentIsStarting() {
        FakeAgentClient agentClient =
                new FakeAgentClient()
                        .fetchFailure(Status.NOT_FOUND.asRuntimeException())
                        .status(
                                AgentStatus.newBuilder()
                                        .setState("starting")
                                        .setTrustDomain("stell.local")
                                        .build());

        AuthenticationResult result = authenticate(defaultOptions(), agentClient, defaultRequest());

        assertEquals(AuthenticationDecision.ALLOW, result.decision());
        assertEquals(AuthenticationFailureClass.AGENT_UNAVAILABLE, result.failureClass());
        assertEquals(AuthenticationReason.AGENT_NOT_READY, result.reason());
        assertEquals("starting", result.agentState());
        assertTrue(result.allowedByPolicyOverride());
    }

    @Test
    void shouldAuthenticateValidCredential() {
        FakeAgentClient agentClient = new FakeAgentClient().credential(validCredential());

        AuthenticationResult result = authenticate(defaultOptions(), agentClient, defaultRequest());

        assertEquals(AuthenticationDecision.ALLOW, result.decision());
        assertEquals(AuthenticationFailureClass.NONE, result.failureClass());
        assertEquals(AuthenticationReason.AUTHENTICATED, result.reason());
        assertTrue(result.authenticated());
        assertFalse(result.allowedByPolicyOverride());
        assertEquals("stell.local", result.trustDomain());
        assertEquals(7, result.bundleVersion());
        assertTrue(result.credentialTtl().toDays() > 3000);
    }

    @Test
    void shouldDenyAudienceMismatchFromReturnedCredential() {
        FakeAgentClient agentClient = new FakeAgentClient().credential(validCredential());
        AuthenticationRequest request =
                AuthenticationRequest.builder()
                        .audience("billing-api")
                        .expectedTrustDomain("stell.local")
                        .build();

        AuthenticationResult result = authenticate(defaultOptions(), agentClient, request);

        assertEquals(AuthenticationDecision.DENY, result.decision());
        assertEquals(AuthenticationFailureClass.AUTHENTICATION_DENIED, result.failureClass());
        assertEquals(AuthenticationReason.AUDIENCE_MISMATCH, result.reason());
        assertFalse(result.authenticated());
    }

    private static AuthenticationResult authenticate(
            StellguardClientOptions options, FakeAgentClient agentClient, AuthenticationRequest request) {
        try (StellguardAuthenticator authenticator =
                StellguardAuthenticator.create(options, agentClient)) {
            return authenticator.authenticate(request);
        }
    }

    private static StellguardClientOptions defaultOptions() {
        return StellguardClientOptions.builder().cacheTtl(Duration.ZERO).build();
    }

    private static AuthenticationRequest defaultRequest() {
        return AuthenticationRequest.builder()
                .audience("orders-api")
                .expectedTrustDomain("stell.local")
                .build();
    }

    private static WorkloadCredential validCredential() {
        return WorkloadCredential.newBuilder()
                .setSlot("current")
                .setCertificatePem(CERTIFICATE_PEM)
                .setTrustBundlePem(CERTIFICATE_PEM)
                .setTrustDomain("stell.local")
                .setAgentId("agent-1")
                .setIdentityId("orders-api")
                .setSpiffeId("spiffe://stell.local/ns/default/sa/orders-api")
                .addDnsNames("orders-api")
                .setBundleVersion(7)
                .build();
    }

    private static final class FakeAgentClient implements AgentWorkloadCredentialClient {
        private WorkloadCredential credential = WorkloadCredential.getDefaultInstance();
        private AgentStatus status = AgentStatus.getDefaultInstance();
        private RuntimeException fetchFailure;
        private RuntimeException statusFailure;

        private FakeAgentClient credential(WorkloadCredential credential) {
            this.credential = credential;
            return this;
        }

        private FakeAgentClient fetchFailure(RuntimeException fetchFailure) {
            this.fetchFailure = fetchFailure;
            return this;
        }

        private FakeAgentClient status(AgentStatus status) {
            this.status = status;
            return this;
        }

        @SuppressWarnings("unused")
        private FakeAgentClient statusFailure(RuntimeException statusFailure) {
            this.statusFailure = statusFailure;
            return this;
        }

        @Override
        public WorkloadCredential fetchWorkloadCredential(
                String audience, boolean includePrivateKey, Duration deadline) {
            if (fetchFailure != null) {
                throw fetchFailure;
            }
            return credential;
        }

        @Override
        public TrustBundle getTrustBundle(String trustDomain, Duration deadline) {
            return TrustBundle.getDefaultInstance();
        }

        @Override
        public AgentStatus getAgentStatus(Duration deadline) {
            if (statusFailure != null) {
                throw statusFailure;
            }
            return status;
        }

        @Override
        public void close() {}
    }
}
