## MODIFIED Requirements

### Requirement: auto-selector 必须作为独立能力建模

auto-selector MUST 与现有 manual selector 使用不同的用户可见名称和可判别的持久化策略，MUST NOT 通过重新解释 `isSelector=true` 来启用。分组在普通单节点、manual selector 与 auto-selector 三种策略中 MUST 至多启用一种；从一种策略切换到另一种时 MUST 保留可迁移的通用分组配置，但 MUST NOT 把手动当前选择当作自动健康结论。manual selector MUST 保持“用户选择、内核原地切换”的兼容行为。

#### Scenario: 后续引入负载均衡功能

- **GIVEN** 项目增加自动选优或负载均衡
- **WHEN** 用户配置 auto-selector 的候选、探测、决策和均衡策略
- **THEN** 系统使用独立于旧 `isSelector` 布尔值的可判别自动策略与设置
- **AND** 现有 manual selector 用户不会被静默迁移成自动模式
- **AND** manual selector 保持用户选点和内核原地切换语义

#### Scenario: 现有手动 selector 用户升级应用

- **GIVEN** 升级前一个分组持久化了 `isSelector=true`
- **WHEN** 数据库迁移到包含自动选择器策略的新版本
- **THEN** 该分组仍使用 manual selector
- **AND** 不会被静默启用自动测速、故障转移或负载均衡

#### Scenario: 用户从手动切换到自动策略

- **GIVEN** 一个分组当前启用 manual selector
- **WHEN** 用户明确选择 auto-selector 并保存
- **THEN** 分组持久化为自动策略且不再同时启用 manual selector
- **AND** 自动策略的候选、探测、决策与状态语义由 auto-selector 能力定义

## ADDED Requirements

### Requirement: 手动与自动 selector 的运行控制不得混淆

应用 MUST 依据运行快照中的策略类型选择控制路径。manual selector 的用户选点 MUST 继续执行即时 outbound 切换；auto-selector 的用户选点 MUST 表达为可恢复的固定偏好，且恢复自动 MUST 清除该偏好。应用 MUST NOT 对 auto-selector 使用仅更新 `selectedProxy` 的手动热切换成功语义。

#### Scenario: 用户在自动选择器中选择成员

- **GIVEN** auto-selector 正在运行且成员 A 可用
- **WHEN** 用户在状态页选择 A
- **THEN** 应用通过 auto-selector 控制接口设置固定偏好
- **AND** UI 将其显示为“健康时优先”而不是永久锁定
- **AND** manual selector 的持久选择状态不被改写

