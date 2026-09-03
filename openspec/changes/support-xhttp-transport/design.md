## Context

详见 `proposal.md`。
当前 ThroneForAndroid 在架构上采用官方 `SagerNet/sing-box v1.13.16` 内核，并通过 gomobile 生成 AAR。`openspec/specs/libcore-integration/spec.md` 严格要求：
1. `libcore` 仅接入官方内核源码，禁止修改官方仓库或使用第三方 fork。
2. 自定义协议扩展必须基于官方的注册表（`Registry`）和插件机制，不得破坏架构边界。

`sing-box-throne`（`origin/xhttp` 分支）具备由 starifly 维护的成熟纯 Go 原生 `transport/v2rayxhttp` 客户端驱动。本设计详细阐述如何将该驱动以独立的 out-of-tree 模块接入 `libcore`，实现与 Throne 电脑版协议行为的完全对齐。

## Goals / Non-Goals

**Goals:**
- 将 `sing-box-throne` 的原生 XHTTP 传输驱动移植为 `libcore/protocol/vless/xhttp` 模块。
- 在 `libcore/protocol/vless` 中实现扩展的 VLESS outbound，并在 `nekoboxAndroidOutboundRegistry` 中注册，覆盖官方默认 VLESS 出站。
- 完整支持 `auto`、`packet-up`、`stream-up`、`stream-one` 模式，支持 HTTP/2 分块传输、X-Padding 混淆、Reality/TLS 握手以及 `xmux` 复用。
- 保证拨号完全经由官方 `adapter.PlatformInterface` 与 `fd protect` 服务，切网时通过 `InterfaceUpdateListener` 正确重置连接。
- 保持官方 `SagerNet/sing-box`（`v1.13.16`）依赖纯净，不修改 upstream 源码。
- 保持 Android 既有的 `StandardV2RayBean`、UI 与 `XhttpExtraConverter` 格式兼容。

**Non-Goals:**
- XHTTP 服务端 Inbound 驱动（移动端仅需要 Client Outbound）。
- 为 Trojan / VMess 扩展 XHTTP（主流生态及 T4A 仅使用 VLESS-XHTTP）。
- 引入外部 Xray-core 二进制或多进程旁路运行。

## Decisions

### 1. 采用 Out-of-tree 扩展注册覆盖官方 VLESS 出站
- **选择**: 在 `libcore/protocol/vless` 中定义自定义 VLESS outbound 构造器，并于 `libcore/box_include.go` 的 `nekoboxAndroidOutboundRegistry` 中重新注册 `C.TypeVLESS`。
- **原因**:
  - sing-box 官方 `protocol/vless` 直接依赖其内部 `option.V2RayTransportOptions`，后者在 `UnmarshalJSON` 中硬编码了类型白名单，直接传入 `"type": "xhttp"` 会导致 JSON 解析失败。
  - 通过实现包含拓展 `Transport` 字段的 `VLESSOutboundOptions`，当传输类型为 `"xhttp"` 时走自定义 `xhttp.NewClient`；当传输类型为官方原生支持类型（`ws`、`grpc` 等）或未配置传输层时，无缝委托官方 `v2ray.NewClientTransport`。
- **替代方案**:
  - *修改 sing-box upstream 源码*: 违反项目主规范对官方内核纯度的约束，增加后续升核维护成本。
  - *使用 Xray 进程旁路 (Throne 桌面本体方式)*: 引入 30MB+ 体积及双进程保活复杂度，移动端保护性差。

### 2. 移植精简版 Xray Buffer 与 XHTTP 传输驱动
- **选择**: 将 `sing-box-throne` 中 `transport/v2rayxhttp` 所需的辅助包（精简版 `xray/buf`, `xray/pipe`, `xray/bytespool`, `xray/signal`）以内部私有包形式置于 `libcore/protocol/vless/internal/`，上层对外统一暴露 `xhttp.NewClient` 接口。
- **原因**: 该驱动已在 Throne 电脑版经过长期验证，逻辑成熟。内嵌在 `libcore` 私有目录下可避免引入非受控外部 Go module，不污染全局依赖树。
- **替代方案**:
  - *全新基于 net/http 手写*: 开发周期长，且难以保证与 Xray/Throne 电脑版在 `x_padding` 填充、分块边界处理上的字节级一致性。

### 3. 严格对齐 Android 配置生成与 Go 反序列化契约
- **选择**: `libcore` 的 `XHTTPOptions` 字段与 JSON tag 必须与 `sing-box-throne` 保持 100% 一致：
  - `mode`: string (`auto`, `packet-up`, `stream-up`, `stream-one`)
  - `host`: string
  - `path`: string
  - `x_padding_bytes`: string / range
  - `no_grpc_header`: bool
  - `sc_max_each_post_bytes`: int / range
  - `sc_min_posts_interval_ms`: int / range
  - `xmux`: object (`max_concurrency`, `max_connections`, `c_max_reuse_times`, `h_keep_alive_period` 等)
  - `download`: object
- **原因**: Android 端的 `V2RayFmt.kt` 与 `XhttpExtraConverter.kt` 已经严格按照该 schema 发射配置，保证前后端零摩擦对接。

## Risks / Trade-offs

- **[Risk 1] 复杂移动网络环境下的 HTTP/2 长连接断流**
  - *Mitigation*: 实现 `adapter.InterfaceUpdateListener`，在切网或默认接口更新时，主动关闭现有传输层实例，清理 `xmux` 连接池，强制后续请求走新连接重握。
- **[Risk 2] gomobile 编译依赖与类型导出限制**
  - *Mitigation*: 所有的 XHTTP 与 VLESS 扩展均作为 Go 内部包运作，通过官方 `outbound.Registry` 挂载，不直接向 gomobile 导出新的 Go 类或 API，确保 JNI 界面维持原样。
- **[Risk 3] 缺少 CI 本地编译环境导致的语法或签名回归**
  - *Mitigation*: 遵循项目快速滚动验证与静态工具检查（`uv run tools/diagnostics/...`），分批次提交并通过 GitHub Actions 编译验证。
