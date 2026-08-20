## Why

T4A 仍把 WireGuard 节点生成成 sing-box 已在 1.13 移除的 legacy outbound；当前 libcore 只能返回弃用错误，因此已有的 WireGuard 配置无法实际启动。husi 已完成向 WireGuard endpoint 的迁移，可作为目标版本 sing-box v1.13.16 下恢复该能力的实现参照。

## What Changes

- 将 WireGuard 节点的 sing-box 配置输出从 `outbounds` 中的 legacy WireGuard outbound 迁移为顶层 `endpoints` 中的 WireGuard endpoint。
- 保留 T4A 现有 WireGuard 数据模型、编辑界面与 wg-quick 配置导入能力，并补齐 endpoint 字段映射：本地地址、私钥、MTU、监听端口、对端地址/端口、公钥、预共享密钥、保活间隔及 reserved。
- 支持导入 sing-box WireGuard endpoint JSON，并继续兼容已有数据库中的 WireGuardBean 数据。
- 调整配置构建流程，使 WireGuard endpoint 可以参与单节点、链式代理和 selector/urltest 等既有引用关系，同时不再触发 legacy outbound stub。
- 补充格式转换、导入与最终配置结构的自动化测试，以及 GitHub Actions 和真机场景的滚动验证门禁。
- **BREAKING**：T4A 新生成的 WireGuard sing-box JSON 不再包含 legacy `type: wireguard` outbound，而使用 sing-box 1.13 endpoint 结构；这是对已被上游删除格式的有意替换。

## Capabilities

### New Capabilities

- `wireguard-endpoint`: 定义 T4A 对 WireGuard 节点的持久化兼容、配置导入、sing-box endpoint 生成、链路引用及可运行性要求。

### Modified Capabilities

- `libcore-integration`: 要求 Android 配置生成与 libcore 注册的 sing-box v1.13 WireGuard endpoint API 对齐，并禁止依赖已移除的 WireGuard outbound。

## Impact

- Android 格式与配置层：`app/src/main/java/io/nekohasekai/sagernet/fmt/wireguard/`、`ConfigBuilder.kt`、`SingBoxOptions.java`、sing-box JSON 导入器及 WireGuard 文本配置导入器。
- Android 数据与 UI：复用现有 `WireGuardBean`、`WireGuardSettingsActivity` 与 preference 资源；如需补齐监听端口/保活字段，需保持 Kryo 版本向后读取兼容。
- libcore：复核 endpoint registry 与 `with_wireguard` 构建标签；目标是使用已经注册的官方 endpoint，不新增自定义 WireGuard 实现。legacy outbound stub 可保留用于明确诊断旧配置。
- 构建链：不改变 `nb4a.properties` 的 sing-box 单一版本来源；以 v1.13.16 官方 endpoint API 为目标，由 GitHub Actions 完成 Android/Go 构建验证。
- 调研依据：husi 的 `WireGuardFmt.kt`、`ConfigBuilder.partitionEndpoints()`、`SingBoxOptions.Endpoint_WireGuardOptions`、WireGuard 格式测试和 `libcore/distro/registry.go`；外部 API 的最终契约以 sing-box v1.13.16 官方源码/文档为准，而不是把本地 husi 仓库作为依赖。
