# StellGuard Java SDK

[English](./README.md) | 简体中文

StellGuard Java SDK 是 StellGuard 零信任身份平台面向 Java 生态的框架无关认证客户端。它只通过 Unix Domain Socket gRPC 连接本机 `stellguard-agent`，并把 agent 维护的 workload 身份状态转换成结构化认证结果。

SDK 设计目标是成为后续 `stellflux` 适配层的 Java 核心能力，同时保持核心包不依赖 Spring Boot、Servlet、网关或具体应用框架。

## 定位

- `stellguard-service` 是零信任身份控制面，负责 agent session、node attestation、workload certificate issuance、trust bundle 分发、CA 轮换和审计事件。
- `stellguard-agent` 运行在 workload 旁边，负责连接 service、维护本地 workload 凭据，并通过本机 Unix Domain Socket 暴露 workload API。
- `stellguard-java-sdk` 只连接本机 agent，不直接调用中心 service，不签发证书，不管理私钥，也不代理业务流量。
- `stellflux` 后续可以基于该 SDK 提供 Spring Boot 自动配置、Filter、Interceptor、配置绑定和 OpenTelemetry 接线。

## 能力

- 提供框架无关的 `StellguardAuthenticator` API。
- 返回结构化 `AuthenticationResult`，而不是简单 boolean。
- agent 不可用或降级时默认 fail-open。
- agent 正常工作且明确拒绝认证时默认 fail-closed。
- 支持在灰度或观察模式下通过配置让正常认证失败放行。
- 通过框架侧传入的 `OpenTelemetry` 记录指标。
- 围绕 `stellguard-agent` workload API 封装底层 gRPC 客户端边界。

## 认证语义

SDK 不向 `stellguard-service` 申请新证书。认证结果来自本机 agent 当前已经建立的 workload 身份状态：

1. SDK 通过 UDS gRPC 调用本机 agent 的 `FetchWorkloadCredential`。
2. 认证路径使用 `include_private_key=false`。
3. 可选 `audience` 和期望 trust domain 会用于校验返回的 workload credential。
4. 有效 credential 返回 `ALLOW / NONE / AUTHENTICATED`。
5. 健康 agent 明确拒绝时返回 `AUTHENTICATION_DENIED`，默认拦截。
6. agent 不可用、启动中、降级、超时、本地缓存凭据无效或 UDS 不可达时返回 `AGENT_UNAVAILABLE`，默认放行。

## 决策策略

| 场景 | 失败分类 | 默认决策 | 配置 |
| --- | --- | --- | --- |
| agent 返回有效 workload credential | `NONE` | `ALLOW` | 不需要 |
| agent 不可用、降级或超时 | `AGENT_UNAVAILABLE` | `ALLOW` | `failOpenOnAgentUnavailable` |
| agent 健康且拒绝 workload 或 audience | `AUTHENTICATION_DENIED` | `DENY` | `allowOnAuthenticationDenied` |
| SDK 配置非法 | `SDK_CONFIGURATION_ERROR` | `DENY` | 不建议覆盖 |
| SDK 侧未知异常 | `SDK_ERROR` | `DENY` | 不建议覆盖 |

当 `allowOnAuthenticationDenied=true` 时，SDK 仍然保留原始失败分类。返回结果应标记为被策略覆盖后放行，方便可观测性和治理系统区分真正认证成功与灰度旁路。

## 目标用法

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

## 配置

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `socketPath` | `/var/run/stellguard/agent.sock` | 本机 agent workload UDS 路径。 |
| `deadline` | `3s` | 单次 agent gRPC 调用超时。 |
| `audience` | 空 | 认证请求可使用的默认 audience。 |
| `expectedTrustDomain` | 空 | 用于校验返回 credential 的 trust domain。 |
| `includePrivateKeyForAuthentication` | `false` | 认证路径不应请求私钥。 |
| `failOpenOnAgentUnavailable` | `true` | 当 agent 故障导致认证无法完成时放行业务流量。 |
| `allowOnAuthenticationDenied` | `false` | 当健康 agent 明确拒绝认证时仍允许放行。 |
| `checkAgentStatusOnNotFound` | `true` | 通过查询 agent status 细分 `NOT_FOUND` 响应。 |
| `cacheTtl` | `1s` | 可选短缓存，用于降低高频路径上的 UDS 压力。 |
| `openTelemetry` | No-op | 由框架侧传入；SDK 不创建 exporter。 |

Spring Boot 属性绑定、Servlet Filter、Gateway Filter、RPC Interceptor 和治理行为属于 `stellflux` 或其他框架适配层，不属于核心 SDK。

## OpenTelemetry

SDK 通过调用方传入的 `OpenTelemetry` 记录指标。它不创建全局 SDK、exporter、reader 或 collector endpoint。

推荐 meter：

```text
io.github.stellhub.stellguard
```

推荐指标：

| 指标 | 类型 | 用途 |
| --- | --- | --- |
| `stellguard.auth.requests` | Counter | 按决策和失败分类统计认证请求数。 |
| `stellguard.auth.duration` | Histogram | 认证耗时。 |
| `stellguard.auth.fail_open` | Counter | 因 fail-open 或策略旁路而放行的请求数。 |
| `stellguard.agent.grpc.calls` | Counter | 按方法和状态统计 agent gRPC 调用。 |
| `stellguard.agent.grpc.duration` | Histogram | agent gRPC 调用耗时。 |
| `stellguard.agent.available` | ObservableGauge | 最近一次观测到的 agent 可用性。 |
| `stellguard.credential.ttl` | ObservableGauge | 最近一次观测到的 credential 剩余有效期。 |
| `stellguard.trust_bundle.version` | ObservableGauge | 最近一次观测到的 trust bundle 版本。 |

指标属性必须保持低基数。SDK 不应把 token、private key、certificate PEM、完整 SPIFFE ID、完整 audience 或异常堆栈作为属性记录。

## Agent 契约

SDK 基于 `stellguard-agent` workload API：

- `FetchWorkloadCredential`：认证主路径，使用 `include_private_key=false`。
- `GetAgentStatus`：失败分类和可观测性辅助路径。
- `GetTrustBundle`：bundle 刷新和本地校验辅助路径。
- `WatchWorkloadCredential`：后续可用于维护内存 credential snapshot。

参考契约：<https://github.com/stellhub/stellguard-agent/blob/main/proto/stellguard/agent/v1/workload.proto>

## 架构

完整设计、失败分类、模块边界和实现验收标准见 [docs/architecture.md](./docs/architecture.md)。

## 开发

```bash
mvn test
```

测试套件应能在没有真实 agent 的环境中运行。gRPC 行为、失败映射、决策策略和 telemetry 应通过可 mock 的客户端边界覆盖。UDS transport 测试可以按平台条件启用。
