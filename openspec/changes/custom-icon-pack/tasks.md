## 1. 核心管理逻辑与格式校验 (CustomIconManager)

- [x] 1.1 创建 `CustomIconManager` 工具类，定义图标存储路径（应用私有目录 `custom_icon/`）与文件常量（`icon.png` 与 `tile.png`）。
- [x] 1.2 实现安全解压与严格规格校验：防止路径穿越，严格校验 ZIP 包含 `icon.png` 和 `tile.png`，并利用 `inJustDecodeBounds` 校验分辨率必须严格为 512x512 像素。
- [x] 1.3 实现位图加载与 Tile 单色 Alpha 蒙版提取算法（`loadTileAlphaBitmap`），并实现原子替换保存与“恢复默认”清除逻辑。
- [x] 1.4 编写针对 ZIP 校验与规格检查的单元测试用例，覆盖缺少文件、尺寸不符合 512x512 以及合法图标包等场景。
- [x] 1.5 **批次 1 验证门禁**：
  - 适用场景：GitHub Actions 测试 Job。
  - 预期结果：ZIP 解压规格校验与 Alpha 提取逻辑测试全部通过。
  - 最低证据：测试用例运行绿灯且日志确认非法尺寸和合法包被正确判定。

## 2. 界面与交互预览实现 (ToolsFragment 与 CustomIconFragment)

- [x] 2.1 在 `app/src/main/res/values/strings.xml` 与 `values-zh-rCN/strings.xml` 中添加自定义图标相关多语言文案（包括选项卡名称、按钮文案、校验错误信息、状态提示等）。
- [x] 2.2 创建布局文件 `layout_custom_icon.xml`，设计导入/重置操作区、彩色应用图标展示卡片以及仿 Android 快速设置（Quick Settings）样式的磁贴预览卡片。
- [x] 2.3 实现 `CustomIconFragment`（继承 `NamedFragment`），注册 ActivityResult 启动文件选择器，处理导入结果与异常反馈（Snackbar/Toast）。
- [x] 2.4 在 `CustomIconFragment` 中实现磁贴开关交互模拟：直接渲染 `icon.png` 原图，提取 `tile.png` 的 Alpha 通道作为单色蒙版，点击磁贴卡片可在 Active（开启高亮）与 Inactive（关闭次级）视觉状态间切换。
- [x] 2.5 在 [`app/src/main/java/io/nekohasekai/sagernet/ui/ToolsFragment.kt`](app/src/main/java/io/nekohasekai/sagernet/ui/ToolsFragment.kt) 的工具列表中挂载 `CustomIconFragment`。
- [x] 2.6 **批次 2 验证门禁**：
  - 适用场景：GitHub Actions CI 构建与真机/模拟器 UI 场景验证。
  - 预期结果：进入“工具”页面出现“自定义图标”选项卡，进入可正常选择 zip 导入，错误规格弹出提示，正确规格刷新预览，点击磁贴可平滑切换开/关仿真状态。
  - 最低证据：CI APK 构建成功，真机截图或操作日志验证 UI 与交互正常。

## 3. TileService 运行时集成与端到端联动

- [x] 3.1 改造 [`app/src/main/java/io/nekohasekai/sagernet/bg/TileService.kt`](app/src/main/java/io/nekohasekai/sagernet/bg/TileService.kt)，支持从 `CustomIconManager` 动态加载自定义磁贴 `Icon`（`Icon.createWithBitmap`），不存在时无缝回退至内置 `R.drawable.ic_throne_tile`。
- [x] 3.2 在图标包导入成功与恢复默认时，触发 `TileService.requestListeningState` 刷新系统快捷磁贴显示，并在界面最下方提供“应用并重启”操作。
- [ ] 3.3 **批次 3 验证门禁**：
  - 适用场景：Android 7.0+ (API 24+) 真机系统状态栏快捷开关验证与端到端 CI 验证。
  - 预期结果：系统下拉通知栏中的代理快捷开关显示自定义图标，服务启动/停止时状态切换正常；恢复默认后快捷开关还原为默认图标。
  - 最低证据：完整 CI 构建通过，真机下拉通知栏快捷开关视觉验证生效。
