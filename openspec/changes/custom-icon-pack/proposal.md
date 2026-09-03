## Why

为了提升客户端的可定制性与个性化视觉体验，支持用户自定义应用图标与系统快速设置（Quick Settings Tile）磁贴图标。通过在“工具”页面引入自定义图标包管理机制与即时交互预览，用户可以导入符合规格的图标包，预览应用图标原图以及提取 Alpha 通道后模拟系统真实开关态的磁贴效果，并在系统 TileService 中动态生效。

## What Changes

- 在 [`app/src/main/java/io/nekohasekai/sagernet/ui/ToolsFragment.kt`](app/src/main/java/io/nekohasekai/sagernet/ui/ToolsFragment.kt) 的工具标签页中新增“自定义图标”一栏。
- 新增自定义图标包（ZIP 格式）导入功能，解压并校验必须包含严格为 512x512 分辨率的 `icon.png` 和 `tile.png`。
- 校验失败（缺少文件、非 ZIP 格式、图片尺寸不符合 512x512）时向用户反馈明确的错误提示。
- 提供可视化预览界面：
  - `icon.png`：直接显示原图，展示彩色应用图标。
  - `tile.png`：只提取 Alpha 通道生成单色蒙版，并结合主题配色渲染模拟的系统 Quick Settings Tile；点击该预览磁贴可在“开启（Active）”与“关闭（Inactive）”状态间切换，真实还原 Android 快速设置开关的视觉反馈。
- 支持持久化存储与“恢复默认”重置操作：导入成功后保存于应用私有存储目录；重置时清理自定义资源并回退至内置默认图标。
- 系统快速设置联动：[`app/src/main/java/io/nekohasekai/sagernet/bg/TileService.kt`](app/src/main/java/io/nekohasekai/sagernet/bg/TileService.kt) 动态检测并应用自定义 Tile 图标（通过 `Icon.createWithBitmap`），与系统 Tile 状态保持一致。

## Capabilities

### New Capabilities
- `custom-icon`: 涵盖自定义图标包的 ZIP 规范、512x512 尺寸校验、文件解压持久化、预览交互（原图直显与 Alpha 蒙版点击开关模拟）以及 TileService 运行时动态加载。

### Modified Capabilities
- `android-application`: 在 `ToolsFragment` 中扩展“自定义图标”工具选项卡，按规范补充多语言资源与 UI 职责分工。

## Impact

- 影响模块与代码：
  - [`app/src/main/java/io/nekohasekai/sagernet/ui/ToolsFragment.kt`](app/src/main/java/io/nekohasekai/sagernet/ui/ToolsFragment.kt)：添加新选项卡。
  - 新增 `CustomIconFragment.kt`、`CustomIconManager.kt` 及相关布局/图形资源（如 `layout_custom_icon.xml`）。
  - [`app/src/main/java/io/nekohasekai/sagernet/bg/TileService.kt`](app/src/main/java/io/nekohasekai/sagernet/bg/TileService.kt)：支持动态加载自定义 Tile 图标。
  - 资源文件：`app/src/main/res/values/strings.xml` 与 `values-zh-rCN/strings.xml` 等多语言定义。
- 不影响 libcore（sing-box Go 内核）、不影响构建链或 Gradle 脚本。
