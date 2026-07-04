# StellGuard Java SDK

English | [简体中文](./README_CN.md)

StellGuard Java SDK is the framework-neutral Java authentication client for the StellGuard zero-trust identity platform. It talks only to the local `stellguard-agent` through Unix Domain Socket gRPC and converts the agent workload identity state into a structured authentication decision.

The SDK is designed as the core Java integration layer for future `stellflux` adapters while keeping the core package independent from Spring Boot, servlet runtimes, gateways, and application frameworks.

## Positioning

- `stellguard-service` is the zero-trust identity control plane. It owns agent sessions, node attestation, workload certificate issuance, trust bundle distribution, CA rotation, and audit events.
- `stellguard-agent` runs beside workloads. It connects to the service, maintains local workload credentials, and exposes workload APIs over a local Unix Domain Socket.
- `stellguard-java-sdk` talks only to the local agent. It does not call the central service directly, issue certificates, manage keys, or proxy application traffic.
- `stellflux` can wrap this SDK later with Spring Boot auto-configuration, filters, interceptors, configuration binding, and OpenTelemetry wiring.

## What It Provides

- A framework-neutral `StellguardAuthenticator` API.
- A structured `AuthenticationResult` instead of a bare boolean.
- Default fail-open behavior when the local agent is unavailable or degraded.
- Default fail-closed behavior when the agent is healthy and explicitly denies authentication.
- A configuration switch that allows normal authentication denial to pass during rollout or observe-only phases.
- OpenTelemetry metrics recorded through an `OpenTelemetry` instance supplied by the framework side.
- A low-level gRPC client boundary around the `stellguard-agent` workload API.

## Authentication Semantics

The SDK does not request new certificates from `stellguard-service`. Authentication is derived from the local agent's current workload identity state:

1. The SDK calls `FetchWorkloadCredential` on the local agent through UDS gRPC.
2. The authentication path uses `include_private_key=false`.
3. Optional `audience` and expected trust-domain settings are used to validate the returned workload credential.
4. A valid credential produces `ALLOW / NONE / AUTHENTICATED`.
5. A healthy agent rejection produces `AUTHENTICATION_DENIED`, which is denied by default.
6. Agent unavailability, startup, degraded state, timeout, invalid cached credential, or unreachable UDS produces `AGENT_UNAVAILABLE`, which is allowed by default.

## Decision Policy

| Scenario | Failure class | Default decision | Configuration |
| --- | --- | --- | --- |
| Agent returns a valid workload credential | `NONE` | `ALLOW` | Not needed |
| Agent is unavailable, degraded, or times out | `AGENT_UNAVAILABLE` | `ALLOW` | `failOpenOnAgentUnavailable` |
| Agent is healthy and denies the workload or audience | `AUTHENTICATION_DENIED` | `DENY` | `allowOnAuthenticationDenied` |
| SDK configuration is invalid | `SDK_CONFIGURATION_ERROR` | `DENY` | Not recommended to override |
| Unexpected SDK-side error | `SDK_ERROR` | `DENY` | Not recommended to override |

When `allowOnAuthenticationDenied=true`, the SDK still returns the original failure class. The result should be marked as allowed by policy override so observability and governance systems can distinguish real authentication success from rollout bypass.

## Target Usage

```java
import io.github.stellhub.stellguard.AuthenticationRequest;
import io.github.stellhub.stellguard.AuthenticationResult;
import io.github.stellhub.stellguard.StellguardAuthenticator;
import io.github.stellhub.stellguard.StellguardClientOptions;
import io.opentelemetry.api.OpenTelemetry;

import java.nio.file.Path;
import java.time.Duration;

public class Example {
    public static void main(String[] args) {
        OpenTelemetry openTelemetry = OpenTelemetry.noop();

        StellguardClientOptions options = StellguardClientOptions.builder()
                .socketPath(Path.of("/var/run/stellguard/agent.sock"))
                .deadline(Duration.ofSeconds(3))
                .expectedTrustDomain("stell.local")
                .failOpenOnAgentUnavailable(true)
                .allowOnAuthenticationDenied(false)
                .openTelemetry(openTelemetry)
                .build();

        try (StellguardAuthenticator authenticator = StellguardAuthenticator.connect(options)) {
            AuthenticationResult result = authenticator.authenticate(
                    AuthenticationRequest.builder()
                            .audience("orders-api")
                            .build());

            if (!result.allowed()) {
                throw new SecurityException("StellGuard authentication denied: " + result.reason());
            }
        }
    }
}
```

## Configuration

| Option | Default | Description |
| --- | --- | --- |
| `socketPath` | `/var/run/stellguard/agent.sock` | Local agent workload UDS path. |
| `deadline` | `3s` | Timeout for a single agent gRPC call. |
| `audience` | Empty | Optional default audience used by authentication requests. |
| `expectedTrustDomain` | Empty | Optional trust domain validation for returned credentials. |
| `includePrivateKeyForAuthentication` | `false` | Authentication should not request private keys. |
| `failOpenOnAgentUnavailable` | `true` | Allows traffic when the agent failure is the reason authentication cannot be completed. |
| `allowOnAuthenticationDenied` | `false` | Allows traffic even when a healthy agent denies authentication. |
| `checkAgentStatusOnNotFound` | `true` | Refines `NOT_FOUND` responses by checking agent status. |
| `cacheTtl` | `1s` | Optional short-lived cache to reduce UDS pressure on hot paths. |
| `openTelemetry` | No-op | Supplied by the framework side; the SDK does not create exporters. |

Spring Boot property binding, servlet filters, gateway filters, RPC interceptors, and governance behavior belong in `stellflux` or another framework adapter, not in the core SDK.

## OpenTelemetry

The SDK records metrics through the caller-provided `OpenTelemetry` instance. It does not create a global SDK, exporter, reader, or collector endpoint.

Recommended meter:

```text
io.github.stellhub.stellguard
```

Recommended metrics:

| Metric | Type | Purpose |
| --- | --- | --- |
| `stellguard.auth.requests` | Counter | Authentication request count by decision and failure class. |
| `stellguard.auth.duration` | Histogram | Authentication latency. |
| `stellguard.auth.fail_open` | Counter | Requests allowed because of fail-open or policy bypass. |
| `stellguard.agent.grpc.calls` | Counter | Agent gRPC calls by method and status. |
| `stellguard.agent.grpc.duration` | Histogram | Agent gRPC latency. |
| `stellguard.agent.available` | ObservableGauge | Last observed agent availability. |
| `stellguard.credential.ttl` | ObservableGauge | Last observed credential time-to-live. |
| `stellguard.trust_bundle.version` | ObservableGauge | Last observed trust bundle version. |

Metric attributes must stay low-cardinality. The SDK should not record tokens, private keys, certificate PEM, full SPIFFE IDs, full audiences, or exception stack traces as attributes.

## Agent Contract

The SDK is based on the `stellguard-agent` workload API:

- `FetchWorkloadCredential`: main authentication path, using `include_private_key=false`.
- `GetAgentStatus`: failure classification and observability helper.
- `GetTrustBundle`: bundle refresh and local validation helper.
- `WatchWorkloadCredential`: future optimization for maintaining an in-memory credential snapshot.

Reference contract: <https://github.com/stellhub/stellguard-agent/blob/main/proto/stellguard/agent/v1/workload.proto>

## Architecture

See [docs/architecture.md](./docs/architecture.md) for the full design, failure taxonomy, module boundaries, and implementation acceptance criteria.

## Development

```bash
mvn test
```

The test suite should run without a live agent. gRPC behavior, failure mapping, decision policy, and telemetry should be covered with mockable client boundaries. UDS transport tests can be platform-gated.
