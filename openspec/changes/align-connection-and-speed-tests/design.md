## Context

见 `proposal.md` 的动机与范围。当前 Android 只有 URL RTT 与直连 TCP socket 延迟路径，没有真正的吞吐测速；原菜单 `action_connection_tcp_ping` 调用 `pingTest(false)`，`icmpPing` 分支已部分空置，但 Bean 层仍保留 `canTCPing()` 能力判断。延迟默认值分散在 `Constants.kt`、`DataStore.kt` 与 preference XML 中，分别为 Google 204 和并发 5。

Throne 桌面参考基线固定为本地 `C:\repos\Throne` 的提交 `2e7182b9ea99947a409fee30f74df83752ab763c`：

- `SettingsRepo` 默认延迟 URL 为 `http://cp.cloudflare.com/`、URL/国家探测批处理并发为 10、速度测试模式为下载+上传、速度测试超时为 5000 ms、简单下载 URL 为 `http://cachefly.cachefly.net/1mb.test`。
- `TestRunner` 按模式发出下载、上传、简单下载或国家探测请求，并把简单下载 URL、超时传入 core；并发 10 仅用于 URL 延迟/国家探测批处理，不控制完整下载/上传速度测试。
- core 使用 `github.com/Mahdi-zarei/speedtest-go` 的伪版本 `v1.7.13-0.20260107171856-79c565dfd83a`。该库先访问 `https://www.speedtest.net/api/js/servers`，空响应时回退 `https://www.speedtest.net/speedtest-servers-static.php`，再选择最低 HTTP 延迟服务器；下载 URL 由服务器 upload URL 的目录和 `random{size}x{size}.jpg` 生成，上传直接 POST 到服务器 URL。因此 Throne 并不存在四个可持久化的固定“下载/上传 URL”，应同步的是服务发现默认端点、服务器派生规则和可编辑的简单下载 URL，而非虚构配置项。

Android/libcore 构建和真机验证只能交由 GitHub Actions；实现必须按最小批次滚动，并在每批 CI/设备证据确认后继续。

## Goals / Non-Goals

**Goals:**

- 用真正的节点内下载/上传吞吐测速替换旧 TCPing 行为，并保留当前组批处理、进度、取消和结果展示体验。
- 将延迟及速度测试默认值集中为单一来源，并保证 preference 默认值、DataStore 回退、备份导入和执行请求一致。
- 与上述 Throne 基线保持测速模式、超时、服务发现、URL 派生和简单下载默认值一致，同时适配 Android 的 JNI 与生命周期。
- 完整删除直连 TCP/ICMP Ping 专属代码和资源，而不破坏 URL 延迟测试。

**Non-Goals:**

- 不复刻 Throne 桌面完整 gRPC 架构、国家探测 UI、选中/当前单节点动作或 HTML 结果面板。
- 不迁移或强制改写已有用户的自定义设置。
- 不把“速度测试本组”继续实现为 TCP connect RTT，也不提供兼容别名入口。

## Decisions

### 1. 以当前 Throne 提交和其锁定依赖作为行为基线

记录 Throne 提交、速度测试依赖伪版本和上游原始源码 URL；实现时从官方 GitHub 源与模块校验信息引入同一版本，不把 `C:\repos\Throne` 作为构建依赖。这样既可准确同步桌面行为，也符合可复现构建要求。

备选方案是只复制 `cachefly` 默认值并自行设计下载/上传 URL。该方案无法实现“与 Throne 一致”，且会误解其 Ookla 服务发现与服务器 URL 派生模型，因此不采用。

### 2. 在 libcore 提供面向 Android 的流式/轮询式测速会话

libcore 负责创建隔离测试 box、按受测 profile/outbound 拨号、服务发现、下载/上传或简单下载、采样和取消；Android 负责组队列、生命周期、进度对话框/通知和最终结果落地。JNI 边界使用 gomobile 可表达的简单请求参数、结果对象和会话句柄，避免直接暴露 Go channel 或复杂集合。

每个结果至少包含 profile 关联键、下载/上传速率与字节数、延迟、服务器名称/国家、错误和取消标志。速度测试会话必须绑定 context cancel，并复用现有测试 box 的网络策略、接口 wrapper 与 fd protect 语义。

备选方案是在 Kotlin 用 HTTP 客户端直接测速。它会重复 sing-box outbound 配置与插件启动逻辑，容易绕过节点或与正式连接语义不一致，因此不采用。

### 3. 支持四种桌面对齐模式，但 Android 首次默认使用下载+上传

设置模型包含下载+上传、仅下载、仅上传、简单下载，默认模式为下载+上传；默认速度测试超时为 5000 ms，简单下载 URL 为 `http://cachefly.cachefly.net/1mb.test`。完整测速使用上游默认服务发现端点和服务器派生下载/上传 URL；这些默认端点以常量集中定义并允许后续产品需要时暴露配置，但本次 UI 只要求模式、超时与简单下载 URL，避免向用户展示难以正确组合的内部 endpoint。

“下载+上传 URL 等默认值”在代码和文档中明确拆分为：服务列表主/回退 URL、服务端返回的 upload URL、下载文件派生规则、简单下载 URL。Android 不创建 Throne 不存在的固定 upload URL preference。

### 4. 组测速串行处理节点，单节点内部使用 Throne 上游的连接并发

吞吐测试会占满链路；当前组节点默认串行测速，单节点内部遵循上游 `MaxConnections=8` 和下载/上传采样策略。这样避免多个节点相互争抢带宽而得到不可比较结果。原 `connectionTestConcurrent=10` 明确只用于 URL 延迟测试；Throne 参考实现中的同值还用于国家批量探测，但 Android 本次不新增国家探测。该值不用于同时跑 10 个完整吞吐测试。

Android UI 在每个节点期间轮询或接收采样结果，取消时同时终止当前 libcore 会话和未开始队列。备选的组内十并发虽然更快，但结果失真且与 Throne “完整测速逐节点、仅国家探测批量”行为不一致。

### 5. 菜单 ID 可复用，语义实现必须替换；废弃能力接口全部删除

为减少资源迁移面，可将原 TCPing menu item 改名并重新接线到 `speedTestGroup()`，但删除 `pingTest()`、`canTCPing()`/`canICMPing()` 及其 overrides、socket/ICMP 专属 imports、不可用错误资源和无引用 ID。URL 入口使用可翻译 `connection_test_url_test`，英文基准为 “URL test this group”；速度入口使用独立、可翻译的速度测试资源，不再让资源名暗示 TCP Ping。

所有 Android `values-*` 目录必须显式补齐这两个菜单文案；设置标题 `connection_test_url` 改为各语言的“Latency test URL”等义文本。若现有翻译无法可靠人工生成，允许先采用英文回退文本，但不得继续声明 `translatable=false`，且简中/繁中必须提供准确本地化。

### 6. 默认值仅影响缺省读取，不执行覆盖迁移

延迟 URL、URL 延迟并发、模式、测速超时、服务端点和简单下载 URL放入集中常量；DataStore getter 与 XML default 引用同一资源/常量可表达来源。SharedPreferences 中存在键时原值优先。URL 延迟并发与 Throne 速度测试的节点调度/单节点内部连接数使用不同配置，不互相复用。Throne 备份导入继续只导入备份明确携带的值；补充速度测试字段映射时也不得用桌面默认值覆盖 Android 已有自定义值，导入策略需沿用现有“字段存在才写入”。

## Risks / Trade-offs

- [速度测试库增加 AAR 体积、gomobile 兼容或依赖冲突] → 固定 Throne 已验证的伪版本，先完成 libcore CI 编译门，再接 Android UI；检查依赖许可证和模块校验。
- [Ookla 服务 API 或服务端 URL在部分网络不可达] → 保留主/回退列表端点、明确错误展示，并提供简单下载模式；不静默回退到直连测试。
- [速度测试消耗大量流量] → 入口文案与确认/进度 UI明确为速度测试，节点串行，取消必须及时生效；保留可配置模式和超时。
- [全量 i18n 容易漏项或出现重复资源] → 增加静态脚本/测试枚举所有 `values-*` 并校验新键存在、可翻译和无废弃键引用。
- [旧菜单 ID 复用造成语义混淆] → 允许首批为降低 XML 迁移而复用 ID，但 Kotlin 方法、字符串键、日志和测试必须使用 speed-test 术语；后续可在同批安全重命名 ID。
- [速度结果与现有 `ProxyEntity.ping/status` 模型不匹配] → RTT 继续使用现有字段；下载/上传结果采用专用展示/状态模型，不把吞吐量压入毫秒字段。是否持久化吞吐字段依据现有数据库能力在实现批次内决定，但不可改变本规格可观察行为。

## Migration Plan

1. 固定参考提交和上游依赖，先在 libcore 实现最小单节点完整/简单测速、采样和取消，CI 验证 gomobile/AAR 构建与单元测试。
2. 增加 Android 设置与默认值，但使用“键不存在才回退”的读取方式；补充备份导入字段存在性映射，验证升级用户值不变。
3. 接入当前组串行速度测试 UI，再移除旧 TCP/ICMP Ping 路径和资源；真机验证节点流量确实经过代理、进度/取消可用。
4. 最后完成菜单、设置标题、全量 i18n、规范和文档静态检查。

回滚时可撤回新速度测试菜单和 libcore API，同时恢复到变更前版本；持久化的新设置键保持无害并可被旧版本忽略。不得以恢复旧 TCP/ICMP Ping 作为运行时降级路径。
