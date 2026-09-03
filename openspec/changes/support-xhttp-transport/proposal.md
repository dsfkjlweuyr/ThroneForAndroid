## Why

ThroneForAndroid 在历史版本中通过第三方 `starifly/sing-box` fork 支持了 VLESS + XHTTP (SplitHTTP) 传输协议，并在 Android 上层构建了完备的 UI（模式选择、Extra JSON 参数）、URL 导入导出、Clash xhttp-opts 解析与配置生成逻辑。
然而，在项目完成向官方 `SagerNet/sing-box` 内核（pinned `v1.13.16`）的架构迁移后，由于官方 sing-box 原生不支持 `xhttp` 传输层（仅支持 `http`、`ws`、`quic`、`grpc`、`httpupgrade`），导致 Android 导出的 xhttp 配置在底层 `box.New` 阶段因 `unknown transport type: xhttp` 无法通过 JSON 反序列化而直接报错崩溃，xhttp 节点完全处于不可用状态。
经过对官方 sing-box、同行 husi 以及 Throne 电脑版（`sing-box-throne` 与 `Throne` 本体）的系统调研，发现 `sing-box-throne`（`origin/xhttp` 分支）具备由 starifly 维护的成熟纯 Go 原生 `transport/v2rayxhttp` 实现。为严格遵守项目主规范中“libcore 仅接入官方 sing-box，不修改或依赖 upstream fork”的铁律，同时兼顾与 Throne 电脑版的完全对齐，本变更决定采用“方案 1A”：将 `sing-box-throne` 的原生 XHTTP 传输驱动移植到 `libcore/protocol/vless` 扩展层中，作为 out-of-tree 自定义 outbound 注册，彻底恢复 T4A 的 xhttp 节点支持能力。

## What Changes

- **内核传输驱动移植与接入 (libcore)**:
  - 将 `sing-box-throne` (`origin/xhttp`) 的 `transport/v2rayxhttp` 核心驱动及 Xray 通用支持模块引入 `libcore/protocol/vless/xhttp`。
  - 在 `libcore/protocol/vless` 中实现支持 XHTTP 传输选项的扩展 VLESS outbound 注册，覆盖官方 `protocol/vless` 注册。
  - 完整支持 `auto`、`packet-up`、`stream-up`、`stream-one` 模式，支持 HTTP/2 分块流式传输、X-Padding 混淆、Reality/TLS 握手以及 `xmux` 连接复用。
  - 保证传输驱动完全适配现有的 `adapter.PlatformInterface`、`fd protect` 和网络切换生命周期。
- **配置与数据模型完全对齐 (Android & libcore)**:
  - 严格保持与 `sing-box-throne` 的配置 JSON 契约对齐，确保 Android 现有的 `StandardV2RayBean`、`V2RayFmt`、`XhttpExtraConverter` 生成的配置可被无缝解析。
  - 增强配置校验与容错，防止非法 xhttp 模式导致底层启动异常。
- **规范与文档更新**:
  - 在 `openspec/specs/libcore-integration/spec.md` 中确立 VLESS-XHTTP 扩展 outbound 的接入约束。
  - 新增 `openspec/specs/xhttp-transport/spec.md` 规范，详细定义 XHTTP 传输契约与行为要求。

## Capabilities

### New Capabilities
- `xhttp-transport`: 定义 XHTTP (SplitHTTP) 客户端传输协议规范，对齐 `sing-box-throne` 的实现标准，涵盖多模式握手传输、X-Padding、xmux 复用以及 Reality/TLS 组合。

### Modified Capabilities
- `libcore-integration`: 明确自定义协议扩展覆盖 VLESS-XHTTP，通过 out-of-tree 方式注册，保持官方 sing-box 源码树零侵入，满足网络监控、生命周期管理与安全隔离要求。
- `android-application`: 规范 VLESS XHTTP 配置持久化、链接解析、Clash 导入与生成 JSON 的字段约束与兼容性。

## Impact

- **代码影响**:
  - `libcore/box_include.go`: 注册自定义 VLESS outbound。
  - `libcore/protocol/vless/`: 移植自 `sing-box-throne` 的 xhttp 客户端传输驱动与 VLESS 包装器。
  - `app/src/main/java/io/nekohasekai/sagernet/fmt/v2ray/`: 审阅并对齐配置映射。
- **构建与依赖**:
  - `nb4a.properties` 中 `SINGBOX_VERSION=v1.13.16` 官方内核基线保持不变。
  - 不引入外部二进制文件，不增加多进程复杂度。
