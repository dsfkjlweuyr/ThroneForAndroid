## Why

T4A 现有分组 selector 只能由用户手动切换，不能根据节点延迟与健康状态自动选优、故障转移或恢复。桌面 Throne 已提供面向大订阅组的自动选择器，因此需要在 Android 端移植同等能力，同时保留当前手动 selector 的兼容语义。

## What Changes

- 新增独立于现有 `isSelector` 手动模式的自动选择器模式、持久化策略与分组设置界面；既有用户不会被静默迁移。
- 自动解析分组成员，按名称与国家过滤，复用仍在可信窗口内的 URL 测试结果，必要时预测未测成员，并只把排名靠前的受限成员集装入运行配置。
- 在 T4A 的官方 sing-box v1.13.16 源码获取流程中增加可审计、可重复应用的 Throne auto-selector 补丁叠加层；`SINGBOX_VERSION` 继续是官方基线版本的唯一来源，不把 fork 仓库变成依赖或版本来源。
- 为自动选择器生成 `auto-selector` outbound，支持分层主动探测、选中成员快速监测、延迟/抖动排名、切换容差、最大 RTT、拨号内联重试、本地断网暂停、健康预热、用户固定偏好及可选负载均衡。
- 扩展 libcore/Android 边界以查询运行状态、触发立即复检、设置或清除固定偏好，并把实际选中成员同步到通知、流量统计和前台 UI。
- 新增 Android 自动选择器状态界面，展示启动、探测、暂停、无可用节点、当前成员、成员健康与切换原因，并允许复检及临时/持久偏好操作。
- 订阅更新或候选配置变化影响当前运行成员时，自动重建候选池；全部运行成员持续失效时，从已排名的后备成员重建。
- 明确测速、导出、链式代理、手动 selector 与自动 selector 的边界，以及大组构建失败、无候选和本地断网时的降级行为。

## Capabilities

### New Capabilities

- `auto-selector`: 定义自动选择器的数据模型、候选规划、配置生成、内核运行策略、运行控制、状态呈现和订阅更新语义。

### Modified Capabilities

- `proxy-group-selection`: 将现有手动 selector 与新增自动模式建模为互斥且可判别的分组策略，并定义状态同步和生命周期边界。
- `libcore-integration`: 在保持官方 sing-box 基线与单一版本源的前提下，允许并约束仓库内 auto-selector 补丁叠加、gomobile 控制/状态 API 及验证方式。
- `repository-governance`: 将上游补丁目录、应用顺序、基线校验、CI 缓存和漂移检测纳入仓库治理规则。

## Impact

- **Android/数据库**：`ProxyGroup` Room schema、备份/导入、分组设置与状态 UI、订阅更新监听、配置构建、服务生命周期、通知与流量归属。
- **libcore**：box 实例中的自动选择器发现、状态 DTO、控制方法、Android 回调和 gomobile 公共接口。
- **构建链**：`buildScript/lib/core/get_source.sh` 在检出 `SagerNet/sing-box` 官方 `v1.13.16` 后应用仓库内补丁；CI 缓存键和静态校验纳入补丁内容。
- **外部基线与参考**：官方来源为 `SagerNet/sing-box` `v1.13.16`；行为参考来自 Throne 当前使用的 `github.com/Throneproj/sing-box` 补丁实现，但不把该 fork 作为 T4A 的构建依赖或版本来源。
- **规范**：新增 `auto-selector`，修改 `proxy-group-selection`、`libcore-integration` 与 `repository-governance`；不新增开发工具目录。
