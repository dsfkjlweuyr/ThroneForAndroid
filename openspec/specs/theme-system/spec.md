# Theme System Specification

## Purpose

定义 Android 应用主题色、夜间模式、动态主题和沉浸式系统栏的持久化与运行时行为，确保用户设置、资源映射、Activity 生命周期、服务通知外观与系统配置变化始终一致。

## Requirements

### Requirement: 主题选择持久化且映射完整

应用 MUST 通过 DataStore 持久化预设主题 ID、是否使用系统动态主题色以及夜间模式。预设主题选择器 MUST 展示 `material_colors` 中定义的全部主题色，并将从 1 开始的主题 ID 映射到对应应用与 Dialog style。系统动态主题 MUST 使用保留 ID `MONET = 0`。

#### Scenario: 用户选择预设主题

- **GIVEN** 用户未启用系统动态主题色
- **WHEN** 用户在颜色选择器点击一种预设颜色
- **THEN** 对应主题 ID 被持久化
- **AND** 应用主题与 Dialog 主题均可映射到对应 style

### Requirement: Android 12 动态主题开关控制预设选择器

系统动态主题色开关 MUST 只在 Android 12 及以上显示。启用时 MUST 使用 Monet 主题并禁用预设颜色选择器；关闭时 MUST 恢复并启用用户原有的预设主题。

#### Scenario: 启用系统动态主题

- **GIVEN** 设备运行 Android 12 或更高版本
- **WHEN** 用户启用系统主题色
- **THEN** 当前 Activity 与 Application 使用 Monet 主题
- **AND** 预设主题选择器不可用

### Requirement: 主题变更通过重启应用生效与夜间模式即时应用

应用主题色或系统动态主题（莫奈取色）开关发生变更时，应用 MUST 提示用户重启应用（通过 `needRestart()`），在用户确认应用时完整重启应用以使主题和通知栏外观一致生效，避免就地重建 Activity 导致底栏动画闪烁及正在运行的代理服务被迫重载断连。夜间模式变化 MUST 通过 AppCompatDelegate 立即应用。

#### Scenario: 用户切换应用主题色或动态主题

- **GIVEN** 用户在设置中切换预设主题色或启用/关闭系统动态主题色
- **WHEN** 偏好项发生变更
- **THEN** 新设置被持久化
- **AND** 弹出底部提示询问用户重启应用以应用更改
- **AND** 不立即强制重建当前 Activity 或重载代理服务

### Requirement: Activity 在布局加载前应用主题

主题 Activity 基类 MUST 在 `super.onCreate` 和布局加载之前应用普通或 Dialog 主题以及夜间模式。系统 `uiMode` 变化时 MUST 重建 Activity，使跟随系统的夜间设置生效。

#### Scenario: 系统深色模式变化

- **GIVEN** 应用夜间模式设置为跟随系统
- **WHEN** 系统在浅色与深色模式之间切换
- **THEN** Activity 被重建
- **AND** 新布局使用正确的夜间资源和主题

### Requirement: 系统栏保持沉浸式和可读性

Android 8.0 及以上的状态栏与导航栏 MUST 使用透明颜色，并由 Activity 启用 edge-to-edge。导航栏图标 MUST 保持与主题底色可读；状态栏图标 MUST 根据主题和夜间状态维持足够对比度，黑色主题的特殊规则 MUST 被保留。

#### Scenario: 黑色主题浅色模式

- **GIVEN** 用户选择黑色主题且应用处于非夜间模式
- **WHEN** Activity 配置系统栏外观
- **THEN** 状态栏与导航栏保持沉浸式
- **AND** 系统栏图标在黑色背景上清晰可见

### Requirement: 新主题必须端到端注册

新增预设主题 MUST 同步增加颜色资源和 `material_colors` 条目、Theme 常量、应用 style 映射、Dialog style 映射以及实际 style 定义。新增 ID MUST 保持唯一且不占用 `MONET = 0`。

#### Scenario: 添加新的预设主题

- **GIVEN** 开发者增加一个新的主题颜色
- **WHEN** 该改动完成
- **THEN** 颜色选择器能够展示该主题
- **AND** 应用与 Dialog 均可使用其 style
- **AND** 已有主题 ID 的语义未改变
