package io.github.stellhub.stellguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class StellguardClientOptionsTest {

    @Test
    void shouldUseArchitectureDefaults() {
        StellguardClientOptions options = StellguardClientOptions.builder().build();

        assertEquals(Path.of("/var/run/stellguard/agent.sock"), options.socketPath());
        assertEquals(Duration.ofSeconds(3), options.deadline());
        assertEquals(Duration.ofSeconds(1), options.cacheTtl());
        assertFalse(options.includePrivateKeyForAuthentication());
        assertTrue(options.failOpenOnAgentUnavailable());
        assertFalse(options.allowOnAuthenticationDenied());
        assertTrue(options.checkAgentStatusOnNotFound());
    }

    @Test
    void shouldRejectInvalidDeadline() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StellguardClientOptions.builder().deadline(Duration.ZERO).build());
    }

    @Test
    void shouldRejectNegativeCacheTtl() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StellguardClientOptions.builder().cacheTtl(Duration.ofMillis(-1)).build());
    }
}
