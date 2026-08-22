# Android Application Specification

## Purpose

定义 Android 应用层的主要职责、关键用户能力以及与 libcore 之间必须保持的行为契约。

## Requirements

### Requirement: Android 模块职责分层

`app/` MUST 承载 UI、后台服务、数据库、配置解析和 JNI 平台适配。`io.nekohasekai.sagernet` MUST 保持 SagerNet 核心框架职责，`moe.matsuri.nb4a` MUST 承担 NekoBox/Throne 兼容扩展、附加协议绑定和工具职责。资源 MUST 按 Android 标准目录组织，并维持已支持语言的本地化字符串。

#### Scenario: 添加 Android 功能

- **GIVEN** 一个新功能需要 UI、持久化或 libcore 平台能力
- **WHEN** 实现该功能
- **THEN** 代码按核心框架与兼容扩展的职责放入对应包
- **AND** 用户可见文本进入资源并同步适用语言

### Requirement: sing-box 配置符合官方 schema

配置生成 MUST 以当前 `SINGBOX_VERSION` 对应的官方 schema 为准。入站嗅探与目标解析 MUST 使用位于路由规则前部的 `sniff`/`resolve` 动作；TUN 地址 MUST 使用合并后的 `address` 字段；双网络加速 MUST 映射为 `default_network_strategy: "hybrid"`。已被官方移除的 legacy 字段 MUST NOT 被发射。

#### Scenario: 生成正式连接配置

- **GIVEN** 用户启用流量嗅探、IPv6 或双网络加速等设置
- **WHEN** `ConfigBuilder` 生成 sing-box 配置
- **THEN** 输出可被目标官方内核 schema 接受
- **AND** 不包含已移除的入站 sniff、legacy TUN 地址或 `route.concurrent_dial` 字段

### Requirement: DNS 路由避免意外泄露

本机直解场景中的 `hosts` MUST 归一化为 `local`。远程 DNS 遇到 `hosts`、`local`、`localhost` 或 `fakeip` 占位符时 MUST 回退至明确的远程 DoH 地址并提示用户；远程 DNS MUST 显式经当前代理节点 detour。订阅自定义服务器 DNS 的 UI MUST 保持暂停开放，直到上游语义明确且强制解析、批量测速、正式连接三条路径能够一致实现。

#### Scenario: 用户把本机解析器配置为远程 DNS

- **GIVEN** 远程 DNS 设置为本机解析占位符
- **WHEN** 生成连接配置
- **THEN** 配置改用安全的远程 DoH 回退值
- **AND** DNS detour 指向当前节点
- **AND** 用户收到修改设置的提示

### Requirement: 测速与正式连接保持语义一致且实例隔离

URL 测速 MUST 使用不注册 PlatformLogWriter 的测试实例，从而不创建或共享 `cache.db`。测速 MUST 沿用用户的 IPv6 模式和 outbound `domain_strategy`，并通过默认 outbound 建立连接、预热后复用连接测量 RTT。每个 box MUST 拥有独立的平台接口 wrapper，默认接口监视器 MUST 在首拨前同步注册，fd protect 对非 VPN 未运行类故障 MUST fail-fast。

#### Scenario: 并发批量测速

- **GIVEN** 主进程同时测试多个节点
- **WHEN** 测试实例启动并拨号
- **THEN** 各实例不创建共享 cache 文件
- **AND** 各实例拥有正确的网络接口缓存和 protect 结果
- **AND** 测速使用与正式连接一致的协议族与域名策略

### Requirement: 混合入站行为由 TUN 设置显式控制

TUN 模式下，只要混合入站存在，VPN 服务 MUST 将系统 HTTP 代理指向本地混合端口。用户启用“禁用混合入站”时，配置 MUST 不生成 mixed 入站及其专属路由规则，VPN MUST 跳过系统 HTTP 代理设置，相关设置项 MUST 禁用。用户名为空时混合入站 MUST 免认证；用户名非空时应用内部 HTTP 请求 MUST 跟随运行时认证信息。

#### Scenario: TUN 模式禁用混合入站

- **GIVEN** 用户在 TUN 模式开启禁用混合入站
- **WHEN** 服务构建配置并启动 VPN
- **THEN** mixed 入站和专属规则不存在
- **AND** VPN 不调用系统 HTTP 代理设置
- **AND** 代理端口、认证和绕过列表设置显示为不可用

### Requirement: 桌面备份导入保持核心数据语义

Android 应用 MUST 能导入 Throne 桌面 `.thrbackup`：解析 `THRN` QDataStream 与内嵌 SQLite，忽略图标，尽力导入配置档、分组、路由和可映射设置。sing-box outbound SHOULD 优先还原为原生 Bean，失败时回退 `ConfigBean`。导入完成后 MUST 触发完整重启。

#### Scenario: 导入有效桌面备份

- **GIVEN** 用户选择有效的 `.thrbackup`
- **WHEN** 导入器完成解析
- **THEN** 支持的配置档、分组、路由和设置被映射到 Android 数据模型
- **AND** 不支持的 outbound 以自定义 JSON 配置保留
- **AND** 应用触发完整重启

### Requirement: 启动入口与关键 UI 保持可达

Launcher `MainActivity` MUST 唯一声明静态快捷方式元数据；切换、启用、禁用和扫码四个快捷方式的目标包 MUST 与 `nb4a.properties` 的应用包名一致，目标 Activity MUST 可被 Launcher 启动。扫码界面 MUST 以底部悬浮按钮提供手电筒和图片导入，不依赖会被预览遮挡的工具栏。

#### Scenario: 校验启动快捷方式与扫码入口

- **GIVEN** 包名或扫码 UI 被修改
- **WHEN** 运行对应静态校验
- **THEN** 四个快捷方式、元数据唯一性和导出状态均合法
- **AND** 扫码界面的两个操作可见且已接线

### Requirement: 用户可见错误对话框安全显示

Android 应用在前台页面操作失败且已有可用宿主 Activity 时，MUST 显示包含原始可读错误消息的提示对话框。对话框使用主题包装 Context 时 MUST 正常解析其宿主，MUST NOT 因 Context 类型转换失败而丢失提示；宿主不存在、正在结束或已销毁时 MUST 安全跳过显示，且 MUST NOT 产生新的未处理异常。

#### Scenario: 规则资源更新失败且页面仍有效

- **GIVEN** 用户在规则资源页面发起更新，页面 Activity 仍处于可显示窗口的状态
- **WHEN** 更新操作失败并生成可读错误消息，且对话框 Context 被主题 wrapper 包装
- **THEN** 应用显示含原始错误消息的错误提示对话框
- **AND** 不产生 Context 到 Activity 的类型转换异常

#### Scenario: 显示错误前宿主页面已退出

- **GIVEN** 后台操作失败后，对话框准备返回主线程显示
- **WHEN** 宿主 Activity 不存在、正在结束或已经销毁
- **THEN** 应用安全跳过错误对话框显示
- **AND** 不产生新的未处理异常或无效窗口

#### Scenario: 配置状态错误提示复用安全显示入口

- **GIVEN** 用户点击处于错误状态的配置项，且宿主页面仍有效
- **WHEN** 应用请求显示该配置项的错误详情
- **THEN** 应用显示含该错误详情的提示对话框
- **AND** 主题包装 Context 不影响显示结果
