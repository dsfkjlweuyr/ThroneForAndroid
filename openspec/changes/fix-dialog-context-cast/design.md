## Context

见 `proposal.md` 的问题说明。当前 `Context.alert` 使用 `MaterialAlertDialogBuilder` 创建对话框，Material/AppCompat 会为对话框主题包装传入 Context，因此创建后的 `AlertDialog.context` 通常是 `ContextThemeWrapper` 而非 Activity 本体。`AlertDialog.tryToShow` 在显示前直接执行 `context as Activity`，这使正常的主题包装路径必然可能进入 `ClassCastException`；异常虽被捕获并记录，却导致原始错误对话框永远不显示。

已调查的可能来源包括：调用方误传 application Context、后台线程直接触发窗口操作、Activity 已 finishing、Activity 已 destroyed、Fragment 已 detached、Material 对话框主题包装，以及 `tryToShow` 的直接强制转换。现有调用点已切回主线程或由 UI 点击触发，且报告中的实际类型明确为 `ContextThemeWrapper`，因此最可能根因收敛为：主题包装是直接触发因素，未经 wrapper 链解析的强制转换是代码缺陷；生命周期状态是需要同时防护的次要风险。

约束：不修改规则更新业务、不增加依赖、不执行本地 Android 构建；构建和设备验证通过仓库 CI/真机流程完成。

## Goals / Non-Goals

**Goals:**

- 从任意层级的 Context wrapper 链解析可选宿主 Activity。
- 仅在宿主可接收窗口时调用对话框显示。
- 为跳过显示与异常路径保留足够诊断信息，验证根因且避免静默失败。
- 用最小范围修复统一保护现有及后续 `tryToShow` 调用方。

**Non-Goals:**

- 不改变规则资源更新的下载、校验、落盘或重试行为。
- 不把全部现有直接 `AlertDialog.show` 调用迁移到安全辅助函数。
- 不引入新的 DialogFragment 架构，不调整主题或错误文案。
- 不修改 libcore、数据库或网络配置。

## Decisions

### 1. 迭代解包 Context wrapper，而非直接转换

增加一个内部 Context-to-Activity 解析器：若当前 Context 是 Activity 则返回；若是 ContextWrapper 则继续检查 `baseContext`；否则返回 null。迭代过程中需要防止异常 wrapper 将 `baseContext` 指向自身而形成死循环。

选择该方案是因为对话框必须继续使用主题包装 Context 来保持 Material 样式，但生命周期判断需要真实宿主 Activity。备选方案是让所有调用方额外传 Activity；这会扩大 API 与调用点改动，并让辅助函数更易被误用。另一个备选是只检查 `context is Activity`，它仍无法覆盖报告中的标准主题包装场景。

### 2. 把宿主可用性作为显示前置条件

解析到 Activity 后检查 `isFinishing`，并在支持的 Android API 上检查 `isDestroyed`；仅当两者都为 false 时显示。解析不到 Activity 时不尝试显示，以避免 application/service Context 导致 `BadTokenException`。

选择保守跳过而不是无条件 `show`，因为该辅助函数的命名与历史用途均表明其职责是“尽力安全显示”，不能为了错误反馈再制造窗口异常。若 Activity 在检查后到显示前发生竞态，保留窄范围异常捕获作为最后防线。

### 3. 先加入定向诊断日志，再据验证结果固化修复

实现批次先记录初始 Context 类型、解包层级/结果，以及跳过原因（无宿主、finishing、destroyed）；不得记录错误消息正文等可能含敏感配置的数据。原有异常日志保留用于捕获窗口显示竞态。

这些日志用于验证两项假设：失败入口拿到的是 Material/AppCompat 的主题 wrapper；wrapper 链最终可解析到当前 `AssetsActivity` 或配置页面 Activity。验证通过后，保留低噪声的跳过/异常日志，成功显示路径无需持续记录。

备选方案是只删除强制转换后直接显示，但这无法解释或保护无宿主与生命周期场景，也不符合问题诊断要求。

### 4. 在统一辅助函数修复，不在规则更新入口做特判

根因位于共享的对话框安全显示入口；`AssetsActivity` 和 `ConfigurationFragment` 都通过该入口暴露问题。修复共享入口可保持调用方业务逻辑不变，并避免同类缺陷在配置状态错误提示中继续存在。

## Risks / Trade-offs

- [自定义 ContextWrapper 链异常或循环] → 迭代解包时检测 `baseContext === current` 并安全返回 null。
- [生命周期检查与 `show` 之间仍有竞态] → 对实际 `show` 保留窄范围异常捕获和错误日志。
- [跳过提示仍会让用户看不到错误] → 仅在确实没有有效窗口宿主时跳过；规则更新结果与页面进度恢复逻辑保持不变，并记录不含敏感信息的原因。
- [新增测试依赖或测试框架成本超过小修复] → 优先使用项目现有 Android 测试能力；若缺乏可运行的 UI 单测环境，以 CI 编译加真机的成功显示/快速退出场景作为最低证据，不为本修复引入生产依赖。

## Migration Plan

1. 在共享对话框工具中加入 Context wrapper 解包与定向诊断日志，进入 Android CI 编译验证阶段。
2. 在真机上制造规则更新失败，确认日志显示 wrapper 可解析到有效 Activity 且错误对话框出现。
3. 在失败回调返回前退出页面，确认对话框被安全跳过且无 `ClassCastException`、`BadTokenException` 或崩溃。
4. 若回归异常，回滚共享工具改动即可；无数据或配置迁移。
