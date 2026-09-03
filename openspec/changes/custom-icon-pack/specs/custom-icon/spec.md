## Purpose

定义自定义图标包的格式规范、校验机制、本地持久化、可视化与交互预览（包含单色 Alpha 蒙版及开关态仿真），以及与系统快速设置磁贴（TileService）的运行时集成契约。

## ADDED Requirements

### Requirement: 自定义图标包格式与分辨率校验

应用 MUST 支持通过文件选择器导入 `.zip` 格式的自定义图标包。图标包根目录 MUST 同时包含 `icon.png` 和 `tile.png` 两个文件。应用 MUST 在解压解析时校验这两个图片文件的分辨率，两者均 MUST 严格等于 512x512 像素。若压缩包损坏、缺少任一必要文件、或任一图片分辨率不满足 512x512 像素，应用 MUST 拒绝导入并向用户展示明确的错误提示信息，且不得破坏已生效的图标配置。

#### Scenario: 成功导入合规的图标包
- **GIVEN** 用户拥有一个合法的 ZIP 压缩包，其根目录下包含 512x512 的 `icon.png` 与 512x512 的 `tile.png`
- **WHEN** 用户在自定义图标页面选择并导入该文件
- **THEN** 系统完成解压和校验，保存图标到应用私有目录
- **AND** 页面立即更新显示该图标包的预览

#### Scenario: 导入缺少必要文件的图标包
- **GIVEN** 图标包 ZIP 中仅包含 `icon.png`，缺少 `tile.png`
- **WHEN** 用户尝试导入该文件
- **THEN** 系统拒绝导入
- **AND** 向用户弹出明确提示指示缺少 `tile.png`
- **AND** 现有图标配置保持不变

#### Scenario: 导入分辨率不符合要求的图标包
- **GIVEN** 图标包 ZIP 中的 `icon.png` 为 256x256 或 `tile.png` 为 1024x1024
- **WHEN** 用户尝试导入该文件
- **THEN** 系统解析图像尺寸并拒绝导入
- **AND** 向用户弹出明确提示说明图片分辨率必须严格为 512x512

### Requirement: 自定义图标预览与 Tile 开关状态仿真

自定义图标页面 MUST 分别提供应用图标（`icon.png`）和快捷设置磁贴（`tile.png`）的预览效果：
1. `icon.png` MUST 直接展示其彩色原貌；
2. `tile.png` MUST 只提取 Alpha 通道作为单色蒙版进行渲染，忽略原图的 RGB 色彩通道；
3. 磁贴预览卡片 MUST 模拟 Android 系统的快速设置开关视觉表现，并支持点击交互切换开启（Active）与关闭（Inactive）状态；
4. 当处于开启状态时，磁贴背景采用高亮主题强调色，图标显示前景色；当处于关闭状态时，磁贴背景采用次级/暗色背景，图标显示次级前景色。

#### Scenario: 预览应用图标
- **GIVEN** 已加载自定义图标包（或默认图标）
- **WHEN** 用户进入自定义图标页面
- **THEN** `icon.png` 以原图全彩形式正常显示于应用图标预览区域

#### Scenario: 交互切换磁贴开关仿真效果
- **GIVEN** 用户处于自定义图标预览页面，模拟磁贴初始处于关闭（Inactive）状态
- **WHEN** 用户点击模拟磁贴卡片
- **THEN** 模拟磁贴状态切换为开启（Active）状态
- **AND** 背景色切换为主题强调色，提取 Alpha 的单色图标以反色高亮显示
- **WHEN** 用户再次点击该模拟磁贴卡片
- **THEN** 状态切回关闭（Inactive）状态，恢复暗色/次级背景与对应图标色调

### Requirement: 图标包持久化与恢复默认

成功导入的自定义图标 MUST 安全存储于应用专属的私有存储空间，应用在重新启动后 MUST 依然保持已导入的自定义图标状态。自定义图标页面 MUST 提供“恢复默认”操作；当用户执行恢复默认时，应用 MUST 清理已保存的自定义图标文件，并重置预览区域为内置的默认图标与默认磁贴。

#### Scenario: 恢复默认图标
- **GIVEN** 应用当前正在使用已导入的自定义图标包
- **WHEN** 用户点击“恢复默认”并确认
- **THEN** 应用删除私有目录中的自定义 `icon.png` 与 `tile.png`
- **AND** 预览界面立即刷新为内置默认的 App 图标和默认 Tile 图标

#### Scenario: 应用重启后保持自定义图标
- **GIVEN** 用户此前已导入自定义图标包
- **WHEN** 应用进程完全退出并重新启动
- **THEN** 应用从私有目录中自动加载自定义图标
- **AND** 自定义图标页面与系统磁贴均显示用户导入的自定义图标

### Requirement: TileService 运行时集成自定义磁贴图标

当设备处于 Android 7.0 (API 24) 及以上时，系统的快速设置服务 `TileService` 在更新磁贴状态（包括 Connected、Connecting、Stopped、Stopping）时，MUST 优先检查是否存在自定义磁贴。若存在，MUST 将提取 Alpha 通道后的自定义磁贴位图包装为 `Icon.createWithBitmap` 传递给系统的 `qsTile`；若不存在，则使用内置默认资源 `R.drawable.ic_throne_tile`。

#### Scenario: 存在自定义图标时更新快速设置磁贴
- **GIVEN** 用户已成功导入自定义图标包
- **WHEN** `TileService` 收到状态变更并执行 `updateTile`
- **THEN** 系统磁贴图标使用自定义 `tile.png` 生成的单色蒙版 `Icon`
- **AND** 磁贴状态根据服务连接状态正常切为 Active 或 Inactive

#### Scenario: 恢复默认后更新快速设置磁贴
- **GIVEN** 用户执行了恢复默认操作
- **WHEN** `TileService` 刷新磁贴
- **THEN** 系统磁贴图标回退至内置 `R.drawable.ic_throne_tile`
