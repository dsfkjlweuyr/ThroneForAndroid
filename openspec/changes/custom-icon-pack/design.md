## Context

当前客户端基于 SagerNet 核心架构，界面层使用传统 View/Fragment 体系。在 [`app/src/main/java/io/nekohasekai/sagernet/ui/ToolsFragment.kt`](app/src/main/java/io/nekohasekai/sagernet/ui/ToolsFragment.kt) 中管理着工具选项卡（当前包含网络工具与备份还原）。
对于系统状态栏快捷磁贴，应用通过 [`app/src/main/java/io/nekohasekai/sagernet/bg/TileService.kt`](app/src/main/java/io/nekohasekai/sagernet/bg/TileService.kt) 向系统提供快速连接开关，当前使用的是写死在包内的矢量资源 `R.drawable.ic_throne_tile`。
本设计旨在引入自定义图标包管理机制，支持用户导入、校验、存储、预览自定义图标，并将磁贴图标动态注入到系统 `TileService` 中。

## Goals / Non-Goals

**Goals:**
- 在 `ToolsFragment` 中新增“自定义图标”工具选项卡，提供直观的导入、校验、预览与重置界面。
- 支持导入 ZIP 图标包，严格校验 `icon.png` 与 `tile.png` 文件存在性，并使用 `inJustDecodeBounds` 校验分辨率是否严格为 512x512。
- 实现安全的解压与原子持久化机制，避免文件损坏或解压过程中的路径穿越风险。
- 实现交互式预览：
  - `icon.png` 原样彩色渲染。
  - `tile.png` 提取 Alpha 通道作为单色蒙版，模拟 Android 快速设置（Quick Settings Tile）的视觉外观。
  - 磁贴预览卡片支持点击交互，在开启（Active）与关闭（Inactive）两种状态间切换，展示相应的背景色与图标色调。
- `TileService` 动态读取自定义磁贴位图并通过 `Icon.createWithBitmap` 呈现，无自定义时平滑回退至内置资源。

**Non-Goals:**
- 不涉及通过私有 API 动态篡改系统桌面 Launcher 图标（受 Android 平台安全机制限制，动态任意图片无法直接设置为 Application 桌面图标；若需桌面图标扩展，后续可通过创建桌面快捷方式 Shortcut 实现）。
- 不涉及从网络直接下载远程图标包，仅支持用户本地导入 ZIP。

## Decisions

### 1. 架构分层与管理类设计
新增 `CustomIconManager` 单例对象（位于 `io.nekohasekai.sagernet.utils` 或 `moe.matsuri.nb4a.utils`），集中负责：
- 目录管理：在 `context.filesDir/custom_icon/` 下维护 `icon.png` 与 `tile.png`。
- 导入与校验：解压 ZIP、校验文件名与 512x512 尺寸，采用两阶段替换（先解压到临时缓存，全部校验通过后再原子覆盖到正式目录）。
- 位图加载与 Alpha 提取：提供 `loadIconBitmap()` 以及专门为磁贴处理的 `loadTileAlphaBitmap()`。
- 重置：删除自定义文件并广播或通知更新。

*备选方案*：直接在 Fragment 内处理解压与文件 IO。
*评估结论*：不可取。TileService 位于 `:bg` 进程或与 UI 隔离的 Service 生命周期，集中在 `CustomIconManager` 更利于状态共享、缓存及单元测试。

### 2. 安全解压与尺寸校验
- **路径穿越防护**：在遍历 `ZipInputStream` 时，校验 `entry.name` 是否包含 `..` 或非法字符，只提取并处理根目录下的 `icon.png` 与 `tile.png`。
- **高效尺寸校验**：利用 `BitmapFactory.Options().apply { inJustDecodeBounds = true }` 检查 `outWidth == 512 && outHeight == 512` 以及图片类型是否为 PNG，避免完整载入非合规大图造成内存抖动。

### 3. Tile 图标 Alpha 通道提取算法
Android Quick Settings Tile 规范要求磁贴图标为单色蒙版，由系统依据磁贴状态施加色调（Tint）。
用户提供的 `tile.png` 可能包含各种颜色，设计采用以下策略提取其 Alpha 通道：
- 通过 `Bitmap.extractAlpha()` 提取出仅含透明度信息的蒙版位图；
- 或者使用 `ColorMatrix` / `Paint` 将 RGB 全部置为纯白（`0xFFFFFFFF`），仅保留原像素的 Alpha 值，生成标准的白色蒙版 ARGB_8888 Bitmap。
- 该处理后的位图既可直接供 `Icon.createWithBitmap` 传递给 Android 系统 `TileService`（系统会自动进行 Tint 染色），也可供应用内预览组件进行主题色染色。

### 4. 预览卡片交互与状态模拟
在 `layout_custom_icon.xml` 中布局：
- 应用图标预览：带微圆角的 ImageView，展示 `icon.png` 的彩色原图。
- 快速设置磁贴预览：
  - 采用类似 Android 12+ QS Tile 的胶囊形或圆角卡片布局（`MaterialCardView`）。
  - 内部放置模拟图标与状态标签（“已开启”/“已关闭”）。
  - 点击卡片时，在内部 `isTileActive` 状态间切换：
    - Active：背景为 `colorSecondary` / `colorPrimary`，图标与文字为高亮反色。
    - Inactive：背景为表面变体色（Surface Variant），图标与文字为次级变体色。
  - 支持触摸水波纹（Ripple）反馈，贴近真实系统的交互手感。

### 5. TileService 运行时集成
在 [`app/src/main/java/io/nekohasekai/sagernet/bg/TileService.kt`](app/src/main/java/io/nekohasekai/sagernet/bg/TileService.kt) 的 `updateTile` 中：
- 替换现有的静态 `iconIdle` / `iconBusy` / `iconConnected`，引入动态获取磁贴 `Icon` 的方法：
  - 当 `CustomIconManager.hasCustomTile()` 时，获取处理后的 Alpha 蒙版 Bitmap 并生成 `Icon.createWithBitmap(tileBitmap)`；
  - 否则，使用原有的 `Icon.createWithResource(this, R.drawable.ic_throne_tile)`。

## Risks / Trade-offs

- **[Risk 1: 恶意 ZIP 导致路径遍历或存储耗尽]**
  → **Mitigation**: 严格校验 entry 名称仅为 `icon.png` 或 `tile.png`，禁止任何目录层级与相对路径，限制解压的最大读取字节数（例如单个文件不超过 5MB）。
- **[Risk 2: 大图解析导致 OOM]**
  → **Mitigation**: 解压写入临时文件后，使用 `inJustDecodeBounds = true` 检查宽高必须严格为 512x512，如不符合直接丢弃，不执行完整解码。
- **[Risk 3: 多进程访问自定义图标文件的一致性]**
  → **Mitigation**: 导入与保存采用原子操作；读取时捕获 IO 异常并优雅回退到默认图标。

## Migration Plan

本功能为纯增量功能。新安装或未导入图标的用户，应用完全保留原有的默认图标与 Tile 行为，零破坏性。用户导入后即刻生效，支持一键“恢复默认”回到初始状态。
