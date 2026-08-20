## Context

参见 `proposal.md` 的动机与范围。T4A 已具有 WireGuardBean、编辑 Activity、wg-quick 导入、legacy outbound options 以及 libcore 的官方 WireGuard endpoint 注册，但这些部分处于“Android 生成 outbound、Go 仅接受 endpoint”的断裂状态。目标内核版本由 `nb4a.properties` 固定为 sing-box v1.13.16。

husi 的现行方案提供了可移植的最小闭环：

1. WireGuardBean 以版本化 Kryo 格式持久化 `listenPort` 与 `persistentKeepaliveInterval`；
2. `buildSingBoxEndpointWireGuardBean` 生成一个 endpoint 和一个 peer；
3. 所有节点先进入统一的 tag/链构建流程，再按协议类型从 `outbounds` 分区到顶层 `endpoints`；
4. JSON 导入器从 `endpoints` 反向构造 WireGuardBean；
5. libcore 只在 endpoint registry 注册官方 WireGuard 实现。

T4A 与 husi 的配置对象技术不同（Java options + Gson hack map 对比 Kotlin serialization/map），因此移植应采用其结构与语义，而不是逐文件复制。实施期不能在本地构建 Go/Android；每一批次必须由 GitHub Actions 或真机结果闭环后再继续。

## Goals / Non-Goals

**Goals:**

- 在 T4A 现有配置构建架构内引入顶层 endpoint 表达，并只把 endpoint 类型从 outbound 集合中分离。
- 使用 sing-box v1.13.16 官方 WireGuard endpoint/peer 字段契约，保持既有 UI 与数据库数据可用。
- 覆盖节点转换、两类导入、配置拓扑和 libcore 加载的可验证路径。
- 使 WireGuard 的 tag 在主节点、selector/urltest 与支持的链式场景中保持既有引用语义。

**Non-Goals:**

- 不实现自定义 WireGuard 协议栈、内核模式 WireGuard 或另一个 Go 依赖。
- 不扩展为任意多 peer 的单一 Android 节点；沿用每个 T4A 节点对应一个 peer，wg-quick 多 peer 拆为多个节点。
- 不在本变更中重构所有协议的 options 生成方式，也不移除 legacy stub 对用户自定义旧 JSON 的诊断。
- 不借此变更提升 sing-box 版本、重做 WireGuard 编辑 UI 或解决 SSR/Snell 等其他已知降级。

## Decisions

### 1. 采用“统一构建后分区 endpoints”，而非为 WireGuard 建立旁路

在根 options 增加 `endpoints`，WireGuard builder 返回 endpoint options；配置构建器继续为所有节点分配 tag、建立 selector/urltest/chain 引用，完成后根据集中式 `isEndpoint(type)` 判定把 endpoint 从 `outbounds` 移到 `endpoints`。

选择原因：这与 husi 的 `partitionEndpoints()` 相同，能最大限度复用 T4A 当前围绕 `currentOutbound` 的 tag、流量映射与链构建流程，未来也可承载其他官方 endpoint。分区必须发生在自定义配置 merge 之前或采用明确的 merge 规则，以免用户配置中的 endpoint 被覆盖。

备选方案是在遇到 WireGuard 时立即写入独立 endpoint 集合。该方案类型更严格，但会迫使链构建的多个分支同时理解两个集合，扩大首轮迁移范围并增加 tag 漏配风险，因此不采用。

### 2. 新增 endpoint options，保留 legacy outbound options 仅作兼容读取/诊断

在 T4A 的 `SingBoxOptions` 中按 v1.13.16 官方定义加入根 `endpoints`、Endpoint 基类、WireGuardEndpointOptions 与对应 peer 字段；WireGuard 格式 builder 改为返回 endpoint options。legacy Outbound_WireGuardOptions 暂不删除，以减少对旧 JSON 解析和诊断路径的无关扰动，但产品生成器不再调用它。

选择原因：libcore 已注册 `wireguard.RegisterEndpoint` 并显式用 stub 拒绝 legacy outbound；让 Android 输出与这一边界对齐即可，无需修改协议运行时。备选的“恢复/维护自定义 outbound”会逆上游 1.13 API、增加安全和维护成本，故拒绝。

### 3. 保持 WireGuardBean 的兼容序列化，按需增量补字段

先核对 T4A 现有 Bean 已存字段和 Kryo 版本。若已有 `listenPort` 与 `persistentKeepaliveInterval`，保持二进制布局不变；若缺少，则只追加字段、递增 Bean 自身序列化版本，并对旧版本读取零值。字段命名保持 T4A 现状，转换边界负责映射官方 snake_case JSON。

选择原因：数据库兼容是用户可观察契约。替换 Bean 或重排字段会让已有节点不可读。备选的数据表迁移没有必要，因为该项目已经使用 Bean 内版本化序列化。

### 4. Reserved 转换集中在 WireGuard 格式层

提供纯函数把恰好三个十进制字节的常见列表形式转换为 base64；其他字符串原样返回。builder 与导入器共用该边界，UI 继续接受用户熟悉的两种表示。

选择原因：这是 husi 已验证的兼容行为，也避免 UI、订阅解析器和配置生成器各自实现不一致转换。严格拒绝未知字符串可能破坏旧数据，故采用保守透传。

### 5. 导入职责按格式归位

将 wg-quick 文本解析收敛到 WireGuard 格式模块（RawUpdater 仅负责识别与调用），并扩展 sing-box JSON 导入入口，使其遍历根 `endpoints`，对 WireGuard 调用 endpoint parser。解析使用安全类型检查：无 peer 返回 null；首个 peer 映射为单节点；数字同时接受 JSON 数字和字符串表示。

选择原因：格式模块可直接单元测试并与 builder 保持双向字段一致。保留复杂解析在 RawUpdater 会继续形成无法独立测试的大类。多 peer endpoint 仍只读首个 peer，以匹配 T4A 单节点模型；wg-quick 多 peer 则维持拆分行为。

### 6. 以配置检查和真机握手分层验证

单元测试验证字段/省略/导入/分区；CI 生成至少单节点、selector/urltest、支持链式三类配置并调用项目已有的 libcore 配置检查或启动测试；真机最终验证实际 WireGuard 握手、DNS 与流量。敏感密钥只使用测试 fixture 或 CI secret，日志证据必须脱敏。

选择原因：JSON 结构正确不等于 Android VPN 生命周期中可联网，而直接依赖真机也难定位字段错误。分层门禁可以在每个最小批次快速失败。

## Risks / Trade-offs

- [T4A 的链算法假定所有节点均为 outbound，endpoint 的 detour/引用方向可能与 legacy outbound 不完全等价] → 第一批先锁定官方 v1.13.16 schema 和最小 builder；拓扑批次为每种支持位置保存生成 JSON，并由配置检查门禁确认后再进入真机。
- [按 `type` 分区可能误把用户自定义对象移动到 endpoints] → `isEndpoint` 使用明确白名单，仅包含目标版本确认的 endpoint 类型；未知类型不自动移动。
- [自定义配置 merge 可能覆盖自动生成的 endpoints] → 明确 merge 顺序并添加同时存在用户 endpoints 与自动 WireGuard endpoint 的测试，按 tag 处理冲突或沿用项目现有覆盖规则。
- [WireGuard 私钥、PSK 等可能进入日志或测试产物] → fixture 使用无生产价值的占位数据，日志与 CI artifact 不输出完整配置中的密钥。
- [husi 当前分支可能面向不同 sing-box 提交] → husi 只用于架构参照；实施前以 `SINGBOX_VERSION=v1.13.16` 对应官方源码/JSON schema 校验字段和 registry API。
- [保留 legacy options 会造成“仍支持 outbound”的误解] → 生成器无引用、测试断言 outbounds 不含 WireGuard，注释注明仅为旧输入诊断；后续可独立清理。

## Migration Plan

1. 固化 v1.13.16 官方 endpoint 契约与测试 fixture，加入 options/纯 builder/解析单元测试；GitHub Actions 编译并运行目标单测，通过后才继续。
2. 接入根 endpoints 与构建后分区，先覆盖单节点输出；CI 执行配置序列化和 libcore 配置检查，确认不触发 legacy stub。
3. 接入 selector/urltest 与支持的链式拓扑；对每类拓扑执行 CI 配置检查，失败则回滚该批配置构建改动而不继续累积。
4. 收敛 wg-quick 与 endpoint JSON 导入并验证旧 Bean 读取；运行导入/数据库测试。
5. 在测试 WireGuard 服务上进行真机连接：启动 VPN、完成握手、访问 IPv4/IPv6（服务具备时）、解析 DNS、切换网络并重连；保存脱敏日志和成功流量证据。

回滚时可恢复 Android 的 builder/分区改动；这会回到明确的“WireGuard 已知不可运行”状态，但不会破坏数据库，因为 Bean 的兼容读取布局不回退。若已追加 Bean 字段，旧代码通常可读取既有前缀；发布前仍需以旧版本读取新保存数据的测试确认降级行为。

## Open Questions

- T4A 现有 CI 中最适合执行最终 sing-box 配置检查的 workflow/job 名称需在实施时从 `.github/workflows` 选择；这不改变测试门禁内容。
- 真机 WireGuard 测试端是否具备 IPv6 决定 IPv6 流量是强制证据还是记录为环境性跳过；endpoint 始终生成双栈 `allowed_ips`。
