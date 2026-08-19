## MODIFIED Requirements

### Requirement: 仓库目录职责明确

仓库 MUST 按以下边界组织：`app/` 承载 Android 应用，`libcore/` 承载 Go 核心接驳，`buildScript/` 与 `buildSrc/` 承载构建辅助逻辑，仓库内专用补丁目录承载按上游组件和基线组织的可重放源码补丁，`gradle/` 承载 Gradle Wrapper，`.github/` 承载 GitHub 自动化，`openspec/` 仅承载 OpenSpec 配置、规范、变更和历史资料，`tools/diagnostics/` 承载仓库维护用诊断脚本。源码补丁 MUST NOT 放入 `openspec/`、`.roo/` 或临时下载的外部仓库路径；OpenSpec 运行目录中 MUST NOT 放置项目诊断程序或其运行产物。

#### Scenario: 添加维护工具

- **GIVEN** 开发者需要新增一次性调研、格式解析、竞态模拟或静态校验程序
- **WHEN** 该程序需要纳入仓库供后续复用
- **THEN** 程序被放入 `tools/diagnostics/`
- **AND** `.roo/` 与 `openspec/` 中没有新增该程序

#### Scenario: 纳入 sing-box 功能补丁

- **GIVEN** 项目需要在官方 sing-box 基线上叠加 auto-selector 能力
- **WHEN** 维护者提交可重放补丁
- **THEN** 补丁位于仓库内明确的专用补丁目录
- **AND** 构建不依赖 `C:\repos` 下的参考源码副本
- **AND** `openspec/` 与 `.roo/` 中没有补丁或生成产物

### Requirement: 构建版本来源唯一

sing-box 官方基线版本 MUST 以 `nb4a.properties` 的 `SINGBOX_VERSION` 为唯一真实来源。源码获取、版本注入、补丁基线校验以及 CI 的 libcore 缓存键 MUST 随该值变化；缓存键还 MUST 随补丁序列或补丁内容变化，避免构建或复用错误版本的 AAR。补丁元数据 MUST NOT 定义第二个 sing-box 版本来源。

#### Scenario: 升级 sing-box

- **GIVEN** 维护者修改了 `SINGBOX_VERSION`
- **WHEN** GitHub Actions 构建 libcore
- **THEN** 构建脚本获取并校验对应官方 tag
- **AND** 旧基线补丁若不能干净应用则构建失败并要求显式移植
- **AND** 缓存键失效以重新生成 AAR

## ADDED Requirements

### Requirement: 上游补丁必须可重放并检测漂移

每个纳入构建的上游补丁 MUST 以确定顺序应用到已验证的干净官方工作树，MUST 能通过静态检查或 CI 测试验证补丁完整性与关键注册点。补丁应用过程 MUST 在重复构建中幂等；出现上下文漂移、部分应用、未跟踪修改或参考 fork 依赖时 MUST 失败而不是继续生成产物。参考仓库只 MAY 用于移植与审阅，MUST NOT 成为构建输入。

#### Scenario: 官方基线与补丁发生冲突

- **GIVEN** `SINGBOX_VERSION` 对应源码不再匹配某个补丁上下文
- **WHEN** CI 应用补丁序列
- **THEN** 补丁阶段以非零状态终止
- **AND** 不进入 gomobile AAR 构建
- **AND** 维护者必须显式更新补丁和相关验证

