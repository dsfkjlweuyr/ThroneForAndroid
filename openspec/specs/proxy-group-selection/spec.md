# Proxy Group Selection Specification

## Purpose

定义 ThroneForAndroid（T4A）现有分组手动 selector 的持久化、配置生成、运行时切换和状态同步语义，并为未来负载均衡 `auto-selector` 划定独立边界，避免把“免重载手动切换”误认为“自动测速选优或负载均衡”。

## Terminology

- **manual selector（手动 selector）**：现有的分组级 `isSelector` 能力。应用把组内全部节点预构建到同一个 sing-box 实例中，由用户选择当前 outbound；它只优化切换生命周期，不负责自动决策。
- **auto-selector（自动 selector）**：预留给未来功能的术语。它根据延迟、可用性、负载或其他策略自动选择 outbound，可能包含主动探测、健康状态和故障转移；当前实现不具备该能力。
- **selected profile**：用户最近选择、准备使用的节点，对应 Android 的 `selectedProxy`。
- **current profile**：当前或最近实际运行的节点，对应 Android 的 `currentProfile`。
- **selector snapshot**：正式连接配置构建时纳入运行实例的组成员、节点配置、代理链和 ID/tag 映射快照。

## Current Architecture

现有链路以 [`ProxyGroup.isSelector`](../../../app/src/main/java/io/nekohasekai/sagernet/database/ProxyGroup.kt:22) 为唯一分组持久化开关：

1. 分组设置页通过 [`groupIsSelector`](../../../app/src/main/res/xml/group_preferences.xml:27) 编辑临时偏好缓存，并由 [`ProxyGroup.init()`](../../../app/src/main/java/io/nekohasekai/sagernet/ui/GroupSettingsActivity.kt:46) 与 [`ProxyGroup.serialize()`](../../../app/src/main/java/io/nekohasekai/sagernet/ui/GroupSettingsActivity.kt:74) 在 Room 实体和编辑缓存之间转换。
2. 正式配置由 [`buildConfig()`](../../../app/src/main/java/io/nekohasekai/sagernet/fmt/ConfigBuilder.kt:105) 读取当前节点所属分组；仅当不是测速、不是导出且 `isSelector=true` 时构建 selector。
3. 配置生成器为组内每个节点构建完整代理链，在最前面插入 tag 为 `proxy` 的 sing-box `selector` outbound，并将启动节点设为默认项。
4. [`BaseService.reload()`](../../../app/src/main/java/io/nekohasekai/sagernet/bg/BaseService.kt:187) 对同一 selector 组尝试调用 [`BoxInstance.SelectOutbound()`](../../../libcore/box.go:320)，从而避免销毁 VPN 和重建 sing-box。
5. App 内切换成功后，Go 通过 [`selector_OnProxySelected()`](../../../app/src/main/java/moe/matsuri/nb4a/NativeInterface.kt:254) 回传节点变化，更新连接、流量归属、通知栏和 Binder 客户端状态。

## Requirements

### Requirement: 手动 selector 是分组级显式能力

Android 应用 MUST 将 manual selector 作为 `ProxyGroup` 的布尔属性持久化，并 MUST 默认关闭。设置入口 MUST 将其描述为“免重载切换节点”或等价语义，MUST NOT 将其描述为自动选优、健康检查、故障转移或负载均衡。

#### Scenario: 用户启用现有 selector

- **GIVEN** 一个包含多个节点的普通或订阅分组
- **WHEN** 用户在分组设置中启用 selector 并保存
- **THEN** 该分组的 `isSelector` 被持久化为 true
- **AND** 其他分组的 selector 状态不受影响
- **AND** UI 不承诺任何自动选点行为

### Requirement: 正式连接将 selector 组构建为单一运行实例

当所选节点所属分组启用 manual selector 时，正式连接配置 MUST 为该组当前存在的全部节点分别构建完整 outbound 链，并 MUST 创建 tag 为 `proxy` 的 sing-box `selector` outbound。selector 的默认 outbound MUST 对应启动连接时所选节点，主代理路由 MUST 指向 `proxy` selector，而不是直接绑定某个成员节点。

#### Scenario: 从 selector 组启动连接

- **GIVEN** 分组启用 manual selector 且用户选择组内节点 B
- **WHEN** 应用生成正式连接配置
- **THEN** 组内节点均出现在 selector 的候选 outbound 中
- **AND** 节点 B 是 selector 的初始默认 outbound
- **AND** 代理流量以 `proxy` selector 作为主代理出口

### Requirement: 测速与导出不扩展为整组 selector

单节点 URL 测速和节点配置导出 MUST NOT 因所属分组启用 manual selector 而构建整组 selector。测速 MUST 继续测试被指定的单一节点，导出 MUST 继续表达被导出的目标节点；测速结果 MUST NOT 自动改变 manual selector 的当前选择。

#### Scenario: 测试 selector 组中的节点

- **GIVEN** 分组启用 manual selector
- **WHEN** 用户对组内一个或多个节点执行 URL 测速
- **THEN** 每个测试实例只构建对应被测节点
- **AND** 测速结果不会自动选择 selector 成员
- **AND** 正在运行的 selector 不因测试完成而切换

### Requirement: 同一 selector 快照内的手动切换避免完整重载

服务运行期间，当目标节点属于当前 manual selector 组且当前运行快照存在该节点的 outbound tag 时，应用 MUST 通过运行中 sing-box selector 切换 outbound，MUST NOT 为该次切换销毁 VPN 或重建整个 box。切换后应用 MUST 重置既有上游连接，使后续连接使用新 outbound。

#### Scenario: 用户切换到同组已有节点

- **GIVEN** manual selector 组已经启动且运行快照包含节点 A 和节点 B
- **AND** 当前使用节点 A
- **WHEN** 用户选择节点 B
- **THEN** 应用在现有 box 中选择节点 B 的 outbound tag
- **AND** VPN 与 box 不执行完整重启
- **AND** 既有连接被重置以应用新出口

### Requirement: 跨组或快照外目标必须采用完整重载

目标节点不属于当前 selector 组，或当前 selector 快照不存在目标节点的 ID/tag 映射时，应用 MUST NOT 把该操作视为成功的热切换，并 MUST 回退到完整配置重建。仅比较分组 ID不足以证明目标节点存在于运行快照。

#### Scenario: 订阅更新后选择新加入节点

- **GIVEN** selector 组正在运行
- **AND** 订阅更新向数据库加入了运行快照中不存在的节点 C
- **WHEN** 用户选择节点 C
- **THEN** 应用检测到当前快照不存在节点 C 的 outbound tag
- **AND** 应用执行完整重载以纳入节点 C
- **AND** UI 不得在内核仍使用旧节点时把热切换报告为成功

### Requirement: 切换结果同步到 Android 运行状态

manual selector 切换成功后，Android 平台层 MUST 将 outbound tag 解析回节点 ID，并 MUST 同步流量统计主节点、服务通知标题和 Binder selector 回调。前台主界面收到回调时 MUST 同步 `selectedProxy` 与 `currentProfile`，并刷新旧节点和新节点的显示状态。

#### Scenario: App 内 selector 切换成功

- **GIVEN** App 触发的 selector 切换已被 sing-box 接受
- **WHEN** libcore 发出 selector 选择回调
- **THEN** Android 将 tag 解析为对应节点 ID
- **AND** 流量归属与通知标题切换到新节点
- **AND** 主界面和快捷设置磁贴收到状态更新

### Requirement: auto-selector 必须作为独立能力建模

未来 auto-selector MUST 与现有 manual selector 使用不同的用户可见名称和可判别的持久化模式，MUST NOT 通过重新解释 `isSelector=true` 来启用。auto-selector MUST 单独定义候选集、探测方式、决策指标、切换阈值、抖动抑制、失败策略、手动覆盖、后台生命周期和流量统计语义。manual selector MUST 保持“用户选择、内核原地切换”的兼容行为。

#### Scenario: 后续引入负载均衡功能

- **GIVEN** 项目准备增加自动选优或负载均衡
- **WHEN** 设计 auto-selector 的数据模型和 UI
- **THEN** 设计使用独立于 `isSelector` 布尔值的模式或策略字段
- **AND** 规范明确 manual 与 auto 两种模式的迁移和互斥或组合规则
- **AND** 现有用户启用 manual selector 后不会被静默迁移成自动模式

## Known Current Gaps

以下是基线实现的已知差距；它们不是 auto-selector 能力，也不应通过引入自动策略来掩盖：

1. [`BaseService.canReloadSelector()`](../../../app/src/main/java/io/nekohasekai/sagernet/bg/BaseService.kt:210) 当前主要比较 selector 分组 ID，尚未验证运行快照是否包含目标节点 tag；订阅新增节点后可能出现 UI 已选择但内核未切换且未完整重载。
2. 节点参数、组内成员、前置代理、落地代理或自定义配置在运行期间变化后，现有组 ID 比较无法识别快照过期；同组热切换可能继续使用旧配置。
3. [`BoxInstance.SelectOutbound()`](../../../libcore/box.go:320) 的 Android 回调仅覆盖 App 内调用。通过 Clash API/Yacd 切换时，官方内核当前不会回传等价事件，Android 的节点状态、通知和流量归属可能不同步。
4. selector 会把整组节点和完整代理链预构建进单一实例。大订阅组会增加启动时间、配置体积、内存占用和单个坏节点导致整组构建失败的风险。
5. 订阅更新只改变数据库和 UI 数据；正在运行的 selector 继续使用启动时快照，直至发生完整重载。

## Non-Goals of Manual Selector

现有 manual selector 明确不包含以下能力：

- 周期或事件驱动的主动健康检查；
- 根据 URLTest 延迟自动选择最低延迟节点；
- 按连接、请求、流量或权重进行负载分配；
- 节点故障自动摘除、恢复探测或故障转移；
- 切换阈值、冷却时间、迟滞或防抖策略；
- 跨分组候选集与策略嵌套。

上述任一能力均属于未来 auto-selector 的可能设计范围。
