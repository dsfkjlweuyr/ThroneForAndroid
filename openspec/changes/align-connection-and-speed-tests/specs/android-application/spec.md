## MODIFIED Requirements

### Requirement: Android 模块职责分层

`app/` MUST 承载 UI、后台服务、数据库、配置解析和 JNI 平台适配。`io.nekohasekai.sagernet` MUST 保持 SagerNet 核心框架职责，`moe.matsuri.nb4a` MUST 承担 NekoBox/Throne 兼容扩展、附加协议绑定和工具职责。资源 MUST 按 Android 标准目录组织，并维持已支持语言的本地化字符串；测试菜单和设置中的用户可见名称 MUST 使用可翻译资源，不得以不可翻译的英文文案替代。

#### Scenario: 添加 Android 功能

- **GIVEN** 一个新功能需要 UI、持久化或 libcore 平台能力
- **WHEN** 实现该功能
- **THEN** 代码按核心框架与兼容扩展的职责放入对应包
- **AND** 用户可见文本进入资源并同步适用语言

#### Scenario: 显示本组测试入口

- **GIVEN** 用户打开配置列表的测试菜单
- **WHEN** 应用展示 URL 延迟测试与速度测试入口
- **THEN** 当前语言显示“URL 测试本组”和“速度测试本组”的等义本地化名称
- **AND** 不显示硬编码或声明为不可翻译的 “URL Test” 文案

### Requirement: 测速与正式连接保持语义一致且实例隔离

URL 延迟测试 MUST 使用不注册 PlatformLogWriter 的测试实例，从而不创建或共享 `cache.db`。延迟与速度测试 MUST 沿用用户的 IPv6 模式和 outbound `domain_strategy`，并经受测节点对应的默认 outbound 建立连接。URL 延迟测试 MUST 预热后复用连接测量 RTT；速度测试 MUST 按用户选择执行下载+上传、仅下载、仅上传或简单下载，并持续提供每个节点的进度、最终吞吐量、错误和取消状态。成功完成的速度测试结果 MUST 按 profile 持久落地，在节点卡片右下角现有测速信息区域以灰色辅助文字显示；结果 MUST 在进度对话框或通知关闭、列表重绑/刷新及重新进入配置列表后继续可见，直到用户清除测试结果或后续速度测试覆盖该节点结果。吞吐量 MUST 使用专用结果字段或模型，MUST NOT 写入毫秒语义的 `ProxyEntity.ping` 字段。每个 box MUST 拥有独立的平台接口 wrapper，默认接口监视器 MUST 在首拨前同步注册，fd protect 对非 VPN 未运行类故障 MUST fail-fast。配置列表 MUST 提供“URL 测试本组”和“速度测试本组”操作，并 MUST NOT 提供批量直连 TCP Ping 或 ICMP Ping 操作。新安装或尚未持久化对应设置时，延迟测试 URL MUST 默认为 `http://cp.cloudflare.com/`，URL 延迟测试并发数 MUST 默认为 10；Throne 速度测试 MUST 沿用参考版本自己的节点调度和单节点连接并发策略，MUST NOT 使用 URL 延迟并发值 10。下载、上传及简单下载测速默认值 MUST 与采用的 Throne 桌面参考版本一致，并由测试设置和执行路径共同使用。应用升级 MUST 保留用户已持久化的自定义测试 URL、URL 延迟并发数及速度测试设置。

#### Scenario: 并发批量延迟测试

- **GIVEN** 主进程同时测试当前组的多个节点
- **WHEN** 用户执行“URL 测试本组”且测试实例启动并拨号
- **THEN** 各实例不创建共享 cache 文件
- **AND** 各实例拥有正确的网络接口缓存和 protect 结果
- **AND** 测速使用与正式连接一致的协议族与域名策略

#### Scenario: URL 延迟测试使用独立并发配置

- **GIVEN** 主进程同时执行当前组的 URL 延迟测试
- **WHEN** URL 延迟测试工作队列按默认配置启动
- **THEN** 最多 10 个 URL 延迟测试 worker 并发处理节点
- **AND** 该并发值不传递给 Throne 下载/上传速度测试

#### Scenario: 测试菜单不再提供直连 Ping

- **GIVEN** 用户打开当前组的测试菜单
- **WHEN** 菜单完成渲染
- **THEN** 用户可以选择“URL 测试本组”和“速度测试本组”
- **AND** 菜单中不存在批量 TCP Ping 或 ICMP Ping 操作

#### Scenario: 当前组执行下载和上传速度测试

- **GIVEN** 用户选择下载+上传模式且当前组包含多个可测试节点
- **WHEN** 用户执行“速度测试本组”
- **THEN** 每个节点的测速流量经该节点建立
- **AND** 界面持续显示下载与上传进度
- **AND** 每个成功节点的最终下载与上传吞吐量按 profile 保存，并在对应节点卡片右下角以灰色辅助文字显示
- **AND** 关闭进度界面、刷新列表或重新进入配置列表后，该结果仍然可见
- **AND** 用户清除测试结果或该节点完成下一次速度测试时，旧速度结果被清除或覆盖
- **AND** 吞吐量不写入用于 URL RTT 毫秒值的 `ProxyEntity.ping` 字段
- **AND** 用户取消测试时所有进行中的测速任务停止并报告取消状态

#### Scenario: 当前组执行简单下载测试

- **GIVEN** 用户选择简单下载模式并配置了简单下载 URL
- **WHEN** 用户执行“速度测试本组”
- **THEN** 应用经每个受测节点下载该 URL 的响应体并按实际字节与耗时计算下载吞吐量
- **AND** 不执行上传阶段

#### Scenario: 首次使用测试默认配置

- **GIVEN** 用户是新安装用户或尚未保存过对应测试设置
- **WHEN** 应用读取延迟与速度测试配置
- **THEN** 延迟测试 URL 为 `http://cp.cloudflare.com/`
- **AND** URL 延迟测试并发数为 10
- **AND** 速度测试模式、超时、服务发现/下载/上传 URL 与简单下载 URL 和已记录的 Throne 桌面参考版本默认值一致

#### Scenario: 升级后保留自定义配置

- **GIVEN** 用户在升级前已保存自定义延迟 URL、并发数或速度测试 URL
- **WHEN** 应用升级并加载新版本默认配置
- **THEN** 已保存的自定义值保持不变
- **AND** 新默认值仅用于不存在持久化值的设置
