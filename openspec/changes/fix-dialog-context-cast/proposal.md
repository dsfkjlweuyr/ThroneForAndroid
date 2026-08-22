## Why

规则资源更新失败时，应用本应显示错误提示，但 `AlertDialog` 暴露的是主题包装后的 `ContextThemeWrapper`，现有安全显示逻辑却将其直接强制转换为 `Activity`，触发 `ClassCastException` 并吞掉原始提示。该问题虽然不改变更新结果或数据状态，却会破坏用户可见的失败反馈，并且同一辅助函数的其他调用入口也受影响。

## What Changes

- 修正对话框安全显示逻辑，不再假设 `AlertDialog.context` 可直接转换为 `Activity`。
- 在显示前从 Context wrapper 链安全解析宿主 Activity，并检查宿主是否仍适合接收窗口。
- 无法找到有效 Activity 或宿主正在结束/销毁时，安全跳过显示并记录可诊断原因，不再抛出类型转换异常。
- 保持规则更新、配置状态错误等调用方现有业务流程不变；失败时仍使用原有错误标题、消息和确认按钮。
- 增加覆盖主题包装 Context、无 Activity Context、已结束/销毁 Activity 的回归验证。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `android-application`: 增加 Android 用户可见错误对话框必须兼容主题包装 Context、且仅在有效宿主 Activity 上显示的行为契约。

## Impact

- 主要影响 `app/src/main/java/io/nekohasekai/sagernet/ktx/Dialogs.kt` 的 `tryToShow` 及其 Context/Activity 解析逻辑。
- 直接覆盖 `AssetsActivity` 的规则资源更新失败入口与 `ConfigurationFragment` 的配置状态错误入口；需回归检查全部 `tryToShow` 调用点。
- 不涉及 libcore、sing-box API、数据库 schema、网络行为、构建链或开发工具布局。
- 不引入外部依赖，不改变公开 API、资源文本或持久化格式。
