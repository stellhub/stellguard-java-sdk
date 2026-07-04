package io.github.stellhub.stellguard.internal;

import io.github.stellhub.stellguard.AuthenticationRequest;
import io.github.stellhub.stellguard.AuthenticationResult;
import io.github.stellhub.stellguard.StellguardClientOptions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 认证结果短缓存。 */
public final class CredentialSnapshotCache {
    private final Duration cacheTtl;
    private final Clock clock;
    private final Map<CacheKey, CacheEntry> entries = new ConcurrentHashMap<>();

    /**
     * 创建认证结果短缓存。
     *
     * @param cacheTtl 缓存时间
     */
    public CredentialSnapshotCache(Duration cacheTtl) {
        this(cacheTtl, Clock.systemUTC());
    }

    CredentialSnapshotCache(Duration cacheTtl, Clock clock) {
        this.cacheTtl = cacheTtl;
        this.clock = clock;
    }

    /**
     * 查询缓存结果。
     *
     * @param request 认证请求
     * @param options 客户端配置
     * @return 缓存结果
     */
    public Optional<AuthenticationResult> get(
            AuthenticationRequest request, StellguardClientOptions options) {
        if (cacheTtl.isZero()) {
            return Optional.empty();
        }
        CacheEntry entry = entries.get(CacheKey.from(request, options));
        if (entry == null || entry.expiresAt().isBefore(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(entry.result());
    }

    /**
     * 写入缓存结果。
     *
     * @param request 认证请求
     * @param options 客户端配置
     * @param result 认证结果
     */
    public void put(
            AuthenticationRequest request, StellguardClientOptions options, AuthenticationResult result) {
        if (cacheTtl.isZero()) {
            return;
        }
        entries.put(
                CacheKey.from(request, options), new CacheEntry(result, clock.instant().plus(cacheTtl)));
    }

    private record CacheKey(
            String audience,
            String expectedTrustDomain,
            String expectedSpiffeId,
            String expectedDnsName) {
        private static CacheKey from(AuthenticationRequest request, StellguardClientOptions options) {
            return new CacheKey(
                    request.effectiveAudience(options.audience()),
                    request.effectiveTrustDomain(options.expectedTrustDomain()),
                    request.expectedSpiffeId(),
                    request.expectedDnsName());
        }
    }

    private record CacheEntry(AuthenticationResult result, Instant expiresAt) {}
}
