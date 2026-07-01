# StellGuard Java SDK

StellGuard Java SDK is the framework-neutral Java client for the StellGuard zero-trust identity platform. It talks to `stellguard-agent` over gRPC through a Unix Domain Socket and exposes a small API for fetching workload certificates, trust bundles, and local mTLS identity material.

The SDK is designed to become the Java identity integration layer for `stellflux` and other Stell JVM frameworks while keeping the core package independent from Spring, servlet runtimes, and application frameworks.

## Positioning

- `stellguard-service` is the zero-trust identity control plane.
- `stellguard-agent` runs beside workloads and owns node/workload identity mediation.
- `stellguard-java-sdk` talks only to the local agent through gRPC over Unix Domain Socket.
- `stellflux` can later wrap this SDK with Spring Boot auto-configuration and runtime conventions.

## Capabilities

- Connect to `stellguard-agent` through Unix Domain Socket transport.
- Fetch SPIFFE-style workload certificates.
- Fetch the active trust bundle from the local agent.
- Attach an optional agent token as gRPC metadata.
- Keep the public API framework-neutral for future `stellflux` integration.

## Quick Start

```java
import io.github.stellhub.stellguard.CredentialBundle;
import io.github.stellhub.stellguard.CredentialRequest;
import io.github.stellhub.stellguard.StellguardClient;
import io.github.stellhub.stellguard.StellguardClientOptions;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public class Example {
    public static void main(String[] args) {
        StellguardClientOptions options = StellguardClientOptions.builder()
                .socketPath(Path.of("/var/run/stellguard/agent.sock"))
                .deadline(Duration.ofSeconds(3))
                .build();

        try (StellguardClient client = StellguardClient.connect(options)) {
            CredentialBundle bundle = client.fetchWorkloadCertificate(
                    CredentialRequest.builder()
                            .spiffeId("spiffe://stell.local/workload/api")
                            .dnsNames(List.of("api.local"))
                            .ttl(Duration.ofMinutes(15))
                            .build());

            System.out.println(bundle.keyId());
        }
    }
}
```

## Development

```bash
mvn test
```

## Contract

The initial gRPC contract is stored in `src/main/proto/stellguard/agent/v1/agent.proto`. It is intentionally agent-facing rather than service-facing: applications should not call the central control plane directly for workload credentials.
