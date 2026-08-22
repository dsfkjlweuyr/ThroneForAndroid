## 1. 共享对话框诊断与修复批次

- [x] 1.1 在共享对话框工具中加入可测试的 ContextWrapper 链解析，防止自引用 wrapper 循环；为无宿主、finishing、destroyed 与显示异常分别加入不含错误正文的定向日志，以验证主题 wrapper 可解析到页面 Activity 的诊断假设。
- [x] 1.2 更新安全显示逻辑，仅在解析到未 finishing/未 destroyed 的 Activity 时显示，并保持现有异常日志作为生命周期竞态的最后防线；不改动规则更新和配置状态调用方的业务流程。
- [x] 1.3 补充自动化回归覆盖：主题包装 Context 可解析并显示、无 Activity Context 安全跳过、自引用 wrapper 不循环，以及不可用 Activity 不显示；若现有单元测试环境无法可靠构造 Android 窗口，则保留纯 Context 解析测试，并将显示行为留待实机 Preview 部署时验证。
- [ ] 1.4 提交该最小批次后立即运行 GitHub Actions `CI / Build OSS APK`（执行 `app:testOssDebugUnitTest` 与 `app:assembleOssDebug`）；预期测试及 APK 编译通过，最低回传证据为 workflow/job 链接和失败时的完整 Gradle 错误片段。在结果返回前不继续追加实现改动。

## 2. 真机行为验证与规范收口

- [ ] 2.1 使用 CI 产出的 OSS Debug APK，在真机规则资源页面制造一次更新失败；预期出现包含原始可读消息的错误对话框，logcat 不含 `ClassCastException`，且定向诊断证据确认初始 Context 为主题 wrapper、最终宿主为有效 Activity。回传截图和过滤后的 logcat（不得包含错误消息正文或敏感配置）。
- [ ] 2.2 在真机再次触发慢速失败并在回调前退出规则资源页面，同时验证配置状态错误点击入口；预期前者安全跳过且无 `ClassCastException`、`BadTokenException` 或崩溃，后者在有效页面正常显示。回传两条场景的操作步骤、结果和过滤后的 logcat。
- [ ] 2.3 真机证据确认根因后，移除仅用于成功路径假设验证的高频日志，保留无宿主/生命周期跳过与异常日志；同步 `android-application` 主规范，并再次运行 GitHub Actions `CI / Build OSS APK`。预期单元测试与 APK 编译通过，最低证据为最终 job 链接、OpenSpec 严格校验通过输出及无新增敏感日志的审查记录。
