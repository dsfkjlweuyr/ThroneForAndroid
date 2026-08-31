## Why

当普通主节点、一个共享节点以及包含该共享节点的链式代理同时被路由规则引用时，`ConfigBuilder` 会先为共享节点生成可读标签，之后却让链继续 detour 到未生成的预计算标签，导致 sing-box 启动失败。启动失败后的清理又会把底层 `file already closed` 返回为主线程未处理异常，使可报告的配置错误升级为后台进程闪退。

## What Changes

- 调整链式 outbound 的全局复用解析顺序：建立 detour 前先确定共享节点最终使用的真实标签，保证生成配置中的每个 detour 都能解析到实际 endpoint/outbound。
- 增加共享成员拓扑回归覆盖，包括“普通主节点 + 独立共享节点 + 包含共享节点的额外链式 outbound”的构建顺序。
- 使启动失败后的 box 清理具备幂等和错误边界：已由启动回滚关闭的资源不得因再次清理导致 Android `:bg` 进程崩溃，同时保留可诊断的原始启动错误与非预期关闭错误。
- 分两个最小批次实施并分别经过 CI/真机反馈门：先修复链标签复用，再修复失败清理。
- 无破坏性 API 或数据迁移。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `android-application`: 配置生成必须保证共享链成员的最终标签与所有 detour 引用一致，并使配置启动失败以服务错误结束而非应用闪退。
- `libcore-integration`: Go/JNI box 生命周期必须在启动部分失败和重复清理场景下安全关闭，并保留原始失败诊断。

## Impact

- Android 配置生成：`app/src/main/java/io/nekohasekai/sagernet/fmt/ConfigBuilder.kt` 及对应 JVM 测试。
- Android 后台服务清理：`app/src/main/java/io/nekohasekai/sagernet/bg/proto/BoxInstance.kt`、`BaseService.kt`，可能涉及现有生命周期诊断日志。
- libcore：`libcore/box.go` 的启动/关闭状态及 Go 测试。
- 能力规范：`openspec/specs/android-application/spec.md`、`openspec/specs/libcore-integration/spec.md`。
- 不改变 sing-box 版本、外部 API、构建链或开发工具布局；目标内核版本继续以 `nb4a.properties` 的 `SINGBOX_VERSION` 为准。
