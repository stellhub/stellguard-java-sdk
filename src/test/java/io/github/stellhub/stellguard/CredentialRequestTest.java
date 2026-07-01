package io.github.stellhub.stellguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class CredentialRequestTest {

    @Test
    void buildsProtoRequest() {
        CredentialRequest request = CredentialRequest.builder()
                .spiffeId("spiffe://stell.local/workload/api")
                .dnsNames(List.of(" api.local ", "", "api.internal"))
                .ttl(Duration.ofMinutes(5))
                .forceRotate(true)
                .build();

        var proto = request.toProto();

        assertEquals("spiffe://stell.local/workload/api", proto.getSpiffeId());
        assertEquals(List.of("api.local", "api.internal"), proto.getDnsNamesList());
        assertEquals(300, proto.getTtlSeconds());
        assertEquals(true, proto.getForceRotate());
    }

    @Test
    void rejectsNonSpiffeId() {
        var builder = CredentialRequest.builder().spiffeId("https://stell.local/workload/api");

        assertThrows(IllegalArgumentException.class, builder::build);
    }
}
