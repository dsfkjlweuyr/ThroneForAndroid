## ADDED Requirements

### Requirement: 工具选项卡集成自定义图标管理

应用 MUST 在 `ToolsFragment` 的工具视图适配器中提供“自定义图标”选项卡。该选项卡 MUST 继承 `NamedFragment` 并提供对应的视图与布局。选项卡标题及页面内用户可见文本 MUST 使用标准的 Android 字符串资源，并至少在英文（`values/strings.xml`）与简体中文（`values-zh-rCN/strings.xml`）中保持本地化对齐，不得硬编码文本。

#### Scenario: 打开工具页面查看自定义图标选项卡
- **GIVEN** 用户导航进入主界面“工具”页面
- **WHEN** 页面完成加载
- **THEN** 顶部标签栏显示“自定义图标”（中文语言下）或“Custom Icon”（英文语言下）选项卡
- **AND** 点击该选项卡能够平滑切换至自定义图标管理与预览界面
