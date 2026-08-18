# Repository Governance Specification

## Purpose

定义 ThroneForAndroid 的仓库边界、规范来源、构建协作方式与维护工具布局，避免项目知识和运行脚本再次散落。

## Requirements

### Requirement: OpenSpec 是当前规范的唯一入口

仓库当前有效的架构、功能约束和协作规则 MUST 维护在 `openspec/specs/` 的主规范中。新增或修改项目结构、关键模块、协议、配置、构建流程或维护工具布局时，实施改动 MUST 同步更新受影响的主规范。已完成的大型迁移记录 MAY 保存在 `openspec/history/`，但 MUST NOT 被当作尚待执行的 change。

#### Scenario: 架构改动同步规范

- **GIVEN** 一次实现改变了 libcore 接驳、Android 关键模块、构建链或工具目录
- **WHEN** 实现准备完成
- **THEN** 对应的 `openspec/specs/` 主规范已同步反映新状态
- **AND** 历史记录仅承担背景和决策追溯职责

### Requirement: 仓库目录职责明确

仓库 MUST 按以下边界组织：`app/` 承载 Android 应用，`libcore/` 承载 Go 核心接驳，`buildScript/` 与 `buildSrc/` 承载构建辅助逻辑，`gradle/` 承载 Gradle Wrapper，`.github/` 承载 GitHub 自动化，`openspec/` 仅承载 OpenSpec 配置、规范、变更和历史资料，`tools/diagnostics/` 承载仓库维护用诊断脚本。OpenSpec 运行目录中 MUST NOT 放置项目诊断程序或其运行产物。

#### Scenario: 添加维护工具

- **GIVEN** 开发者需要新增一次性调研、格式解析、竞态模拟或静态校验程序
- **WHEN** 该程序需要纳入仓库供后续复用
- **THEN** 程序被放入 `tools/diagnostics/`
- **AND** `.roo/` 与 `openspec/` 中没有新增该程序

### Requirement: Python 工具使用 uv 文件工作流

仓库维护用 Python 代码 MUST 先写入 `tools/diagnostics/` 下的 `.py` 文件，再从仓库根目录使用 `uv run tools/diagnostics/<script>.py [arguments]` 执行。维护流程 MUST NOT 使用 `python`、`python3` 或 `python -c` 直接执行项目辅助代码。脚本若需要定位仓库根目录，MUST 从脚本路径按两级父目录推导，例如 `Path(__file__).resolve().parents[2]`。

#### Scenario: 执行临时分析

- **GIVEN** 需要用 Python 扫描或转换仓库内容
- **WHEN** 开发者准备执行分析逻辑
- **THEN** 完整逻辑先保存为 `tools/diagnostics/` 下的脚本
- **AND** 使用 `uv run` 执行该文件
- **AND** 不通过命令行内联 Python 代码

### Requirement: 工具布局可静态校验

仓库 MUST 提供可通过 `uv run tools/diagnostics/roo_check_repo_governance.py` 执行的布局校验。该校验 MUST 检查旧根目录规范未回流、`.roo/` 与 `openspec/` 中不存在 Python 维护工具、仓库根目录不存在散落 Python 文件、诊断脚本语法有效且文档中不存在旧执行路径。

#### Scenario: 提交规范或诊断工具改动

- **GIVEN** 开发者迁移、新增或修改了规范与 Python 维护工具
- **WHEN** 运行仓库治理静态校验
- **THEN** 不合规目录、旧引用和 Python 语法错误会导致非零退出
- **AND** 合规布局会输出成功结果

### Requirement: 本地开发不依赖 Go 或 Android 编译环境

开发流程 MUST 假定本地未安装 Go 环境且未克隆 sing-box 源码。AI/开发者 MUST NOT 要求用户在本地编译 Go 核心或 Android APK；可执行不依赖这些环境的静态校验。Go 核心、Android APK 和真机行为 MUST 由 GitHub Actions 与用户真机验证。外部依赖的源码、版本和 API 定义 SHOULD 通过官方网络来源查询。

#### Scenario: 验证跨 Go 与 Android 的修改

- **GIVEN** 一次修改影响 Go 核心或 Android 编译
- **WHEN** 本地实现完成
- **THEN** 本地只运行可用的静态检查
- **AND** 最终编译交由 GitHub Actions
- **AND** 运行时行为交由真机验证

### Requirement: 外部仓库由用户提供明确路径

开发过程中若需要参考外部仓库的本地副本，AI/开发者 MUST 先告知用户所需仓库及参考目的，由用户将其克隆到 `C:\repos` 下并提供具体路径。AI/开发者 MUST NOT 自行遍历、搜索或猜测 `C:\repos` 下的其他目录，也 MUST NOT 将其中未获用户明确指定的项目视为可用参考资料。

#### Scenario: 实现需要参考外部仓库

- **GIVEN** 当前实现仅凭本仓库内容和官方网络来源无法可靠完成
- **WHEN** AI/开发者需要读取某个外部仓库的源码
- **THEN** 先向用户说明所需仓库及参考目的
- **AND** 等待用户完成克隆并告知具体路径
- **AND** 仅访问用户明确提供的外部仓库路径
- **AND** 不遍历或搜索 `C:\repos` 下的其他项目

### Requirement: 构建版本来源唯一

sing-box 版本 MUST 以 `nb4a.properties` 的 `SINGBOX_VERSION` 为唯一真实来源。源码获取、版本注入以及 CI 的 libcore 缓存键 MUST 随该值变化，避免构建或复用错误版本的 AAR。

#### Scenario: 升级 sing-box

- **GIVEN** 维护者修改了 `SINGBOX_VERSION`
- **WHEN** GitHub Actions 构建 libcore
- **THEN** 构建脚本获取并校验对应官方 tag
- **AND** 缓存键失效以重新生成 AAR
