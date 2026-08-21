# Android WireGuard endpoint 数据面故障调研报告

## 1. 摘要

ThroneForAndroid 将 WireGuard 从 sing-box 1.13 已移除的 legacy outbound 迁移到官方 endpoint 后，首轮 Android 真机 preview 出现稳定故障：

- URLTest 与首页延迟测试成功；
- WireGuard endpoint 能启动并产生隧道内连接；
- Chrome 无法正常打开网页；
- 日志持续出现 `sendmsg: message too long`；
- DNS 大量返回 `NOERROR`，但应用 HTTPS 连接最终超时。

第二轮带脱敏诊断的 preview 已确认：隔离 URLTest 和正式 VPN 实例的 WireGuard endpoint MTU 均为 1390，全局 TUN MTU 为 1500。因此，“endpoint 错误继承 TUN MTU 1500”已被排除。

固定版本源码调研进一步表明，sing-box v1.13.16 的 WireGuard `ClientBind` 每次发送单个数据报，`BatchSize()` 返回 1；本案不能仅凭历史文档直接归因于批量 UDP GSO。当前证据最支持以下机制：WireGuard endpoint 无 detour 时使用 Android 默认 dialer/Network 绑定路径，单个封装后的 UDP 数据报在该路径上返回 `EMSGSIZE`；显式 detour 到一个非空 direct outbound 会切换 dialer 路径，并且这是上游多个 Android 用户已验证的 workaround。

**建议优先验证而非直接宣称永久修复：**

1. 对没有既有链式 detour 的 WireGuard endpoint 设置 `detour = "direct"`；
2. 让现有 direct outbound 至少包含一个 Dial 字段，优先使用 `network_strategy = "default"`；
3. 不覆盖 WireGuard 已有的链式 detour；
4. 在相同设备、网络、服务端和节点上复验 Chrome HTTPS 与较大响应；
5. 若仍出现 `EMSGSIZE`，再以 MTU 1280 做独立对照，不把降 MTU 与 detour 同时引入。

## 2. 范围与版本基线

- 项目：ThroneForAndroid / NB4A。
- 目标内核：`nb4a.properties` 固定的 sing-box v1.13.16。
- WireGuard 模式：顶层 `endpoints` 中的官方 WireGuard endpoint。
- sing-box v1.13.16 锁定依赖：`github.com/sagernet/wireguard-go` 提交 `506b7631853c`。
- 本报告不修改 Go 依赖、不升级 sing-box、不实现自定义 WireGuard 协议栈。
- 调研时未在本地克隆 sing-box，也未运行 Android/Go 构建；固定版本源码通过 GitHub tag/raw/API 查阅。

## 3. 真机证据

### 3.1 第一轮日志 `38100`

来源：`C:/Users/Tony/Downloads/NB4A 3810012430414748450.log`。

观察：

- 全局配置摘要显示 TUN MTU 为 1500；
- WireGuard endpoint 反复报告：`failed to send data packets: ... sendmsg: message too long`；
- Chrome 相关 HTTPS 连接出现 `i/o timeout`；
- DNS 路径存在大量成功响应，FakeIP 映射也正常出现；
- endpoint 可尝试访问远程 DNS，说明 endpoint/tag 并非完全不可用；
- URLTest 小请求可以成功。

这组证据支持“按数据包大小或发送路径触发的数据面失败”，不支持“单纯 DNS 完全失效”或“WireGuard 完全没有握手/连接能力”。

### 3.2 第二轮日志 `27806`

来源：`C:/Users/Tony/Downloads/NB4A 2780620571358584300.log`。

新增脱敏日志：

```text
WireGuardEndpointTrace ... forTest=true endpointMtu=1390 tunMtu=1500 peerAddressFamily=ipv4-or-domain peerPort=51820
WireGuardEndpointTrace ... forTest=false endpointMtu=1390 tunMtu=1500 peerAddressFamily=ipv4-or-domain peerPort=51820
```

随后正式实例持续出现：

```text
endpoint/wireguard[redacted]: peer(redacted) - failed to send data packets:
write udp4 0.0.0.0:<ephemeral>-><redacted>:51820: sendmsg: message too long
```

同时：

- 隔离 URLTest 返回成功延迟；
- 正式 VPN 实例的 Chrome HTTPS 流量超时；
- `endpointMtu=1390` 在两类实例中一致。

由此排除“正式实例生成了不同 MTU”及“builder 把全局 1500 直接写入 endpoint”的假设。

## 4. 七类候选根因及收敛

| 候选 | 当前判断 | 证据与理由 |
| --- | --- | --- |
| DNS 劫持/FakeIP 完全失败 | 低 | 日志有大量 `NOERROR`、FakeIP 命中与域名恢复；个别 `SERVFAIL` 更像次生或域名特定故障。 |
| Chrome 被规则错误地送往 direct | 中低 | 日志确有 `outbound/direct[direct]`，但这部分既可能是绕过规则，也可能是 endpoint 底层拨号；无法解释 endpoint 自身持续 `EMSGSIZE`。 |
| 密钥、peer 或握手错误 | 低 | URLTest 成功且 endpoint 能建立隧道内连接；若密钥完全错误，不应稳定通过同节点测速。 |
| endpoint tag/selector 引用失效 | 低 | 正式日志明确进入目标 WireGuard endpoint，说明 tag 至少可解析和调用。 |
| endpoint MTU 直接误配为 TUN MTU 1500 | 已排除 | 两类实例均明确记录 endpoint MTU 1390。 |
| 真实路径 PMTU 小于封装后报文，单报文触发 `EMSGSIZE` | 高 | 错误来自底层 UDP 单报文写；1390 加 WireGuard/UDP/IP 外层开销后约为 1450 字节，仍可能超过特定 Android 绑定路径的有效 PMTU。 |
| Android 默认 dialer/Network 绑定路径异常，detour 改变路径后恢复 | 高 | 上游 #3390/#3535 多设备验证 `detour=direct` workaround；固定版本源码确认 detour 会切换为 `DetourDialer`。 |

保留但不作为首要结论的可能性：IPv6 优选黑洞、VPN socket protect/路由回环、厂商 Android 网络栈差异。现有 peer 外层为 IPv4，且错误是明确的 IPv4 UDP `EMSGSIZE`，故这些解释优先级较低。

## 5. T4A 代码路径

### 5.1 Endpoint 构建

`app/src/main/java/io/nekohasekai/sagernet/fmt/wireguard/WireGuardFmt.kt` 的 `buildSingBoxEndpointWireGuardBean()`：

- 透传正数节点 MTU；
- 生成双栈 `allowed_ips`；
- 当前不主动设置单节点 endpoint 的 `detour`。

### 5.2 拓扑与 direct outbound

`app/src/main/java/io/nekohasekai/sagernet/fmt/ConfigBuilder.kt`：

- WireGuard 与其他节点一起参与 tag/链构建；
- 已有链式场景通过通用 `detourTo()` 设置下一跳；
- 默认 `direct`/`bypass` outbound 当前只有 `type` 与 `tag`，没有 Dial 字段；
- 新增的 `WireGuardEndpointTrace` 只记录 profile ID、测试模式、endpoint/TUN MTU、peer 地址族和端口，不记录私钥、公钥、PSK 或完整配置。

因此，修复不能在纯 WireGuard builder 中无条件写死 `detour=direct`：builder 不知道该 endpoint 后续是否已经处于代理链中。更安全的落点是配置拓扑构建完成后，仅为“仍无 detour 的 WireGuard endpoint”补 direct detour。

## 6. sing-box v1.13.16 固定版本源码结论

### 6.1 Endpoint dialer 选择

官方 tag `v1.13.16` 的 `protocol/wireguard/endpoint.go`：

- 使用 endpoint 的通用 Dialer options 调用 `dialer.NewWithOptions()`；
- `detour` 非空时选择 `DetourDialer`；
- `listen_port` 与 `detour` 明确冲突；
- endpoint MTU 为 0 时，transport 层默认使用 1408。

这意味着任何自动 direct detour 都必须对正数 `listen_port` 做明确处理，不能生成内核必然拒绝的配置。

### 6.2 ClientBind 发送路径

官方 tag `v1.13.16` 的 `transport/wireguard/client_bind.go`：

- 无 detour 时，底层由默认 dialer 创建 UDP PacketConn；
- `ClientBind.Send()` 遍历待发送 buffer，并逐个调用 `WriteToUDPAddrPort()`；
- 一旦写失败，关闭 PacketConn 并原样返回错误；
- `ClientBind.BatchSize()` 固定返回 1。

wireguard-go 的 sender 接收到该错误后记录 `Failed to send data packets`。因此当前日志中的 `message too long` 是底层 UDP 写错误，不是配置检查错误，也不是 Android UI 层合成错误。

### 6.3 为什么 direct 不能是“空 outbound”

官方 tag `v1.13.16` 的 `common/dialer/detour.go` 会拒绝 detour 到 `IsEmpty() == true` 的 direct outbound，错误为“detour to an empty direct outbound makes no sense”。

`protocol/direct/outbound.go` 通过 Dialer options 是否为空计算 `IsEmpty()`。因此上游 workaround 同时要求：

```json
{
  "type": "wireguard",
  "detour": "direct"
}
```

以及：

```json
{
  "type": "direct",
  "tag": "direct",
  "network_strategy": "default"
}
```

第二项不是性能调优装饰，而是使 direct outbound 成为合法 detour 目标所需的非空 Dial 配置。

## 7. 上游资料

### 7.1 直接相关

1. [SagerNet/sing-box #3390 — Low WireGuard throughput on 3G/4G networks](https://github.com/SagerNet/sing-box/issues/3390)
   - Android 上 WireGuard 直接走移动网络时吞吐异常；
   - 多名用户确认 endpoint 添加 `detour: direct` 后恢复；
   - 由于空 direct 会被拒绝，用户同时设置 `network_strategy: default`；
   - 截至调研时 issue 仍开放并归入 `1.13 Next`，未看到可直接映射到 v1.13.16 的完成修复说明。

2. [SagerNet/sing-box #3535 — Android phones using Sing-box to set up Wireguard experience slow network speeds](https://github.com/SagerNet/sing-box/issues/3535)
   - 使用 1.13 alpha 的 WireGuard endpoint；
   - 握手/443 可达但极慢；
   - 作者确认 #3390 方案至少使其恢复正常使用；
   - 被关闭为 #3390 的重复问题。

### 7.2 相关但不能直接等同

3. [SagerNet/sing-box #1783 — Android 切网时 WireGuard 卡死](https://github.com/SagerNet/sing-box/issues/1783)
   - 旧 WireGuard outbound 的历史问题；
   - 曾通过启用 GSO缓解，且早期 GSO 与 detour 有冲突；
   - 报告称在 1.10.0-alpha.7 修复；
   - 与本案 endpoint 单报文 `EMSGSIZE` 的版本和症状不同，只能作为 Android WireGuard 发送路径历史风险参考。

4. [SagerNet/sing-box #4157 — endpoint 握手成功但无回程流量](https://github.com/SagerNet/sing-box/issues/4157)
   - Android 1.13 endpoint 的另一类零流量报告；
   - 报告者称 MTU 1280/1408/1420 均无效；
   - 其日志没有本案明确的 `EMSGSIZE`，且 issue 被标记为 spam/not planned；
   - 不应拿它证明本案是 wireguard-go 协议互操作问题。

5. [sing-box WireGuard endpoint 官方文档](https://sing-box.sagernet.org/configuration/endpoint/wireguard/)
   - endpoint 默认 MTU 为 1408；
   - WireGuard endpoint 支持通用 Dial fields；
   - 目标项目的准确契约仍以 v1.13.16 tag 源码为准。

## 8. GSO 结论边界

sing-box 文档说明 WireGuard 从 1.11 起在可用时自动使用 GSO；历史 issue 也存在 GSO 相关故障与修复。然而，本案固定版本的 WireGuard endpoint 使用自定义 `ClientBind`，其 batch size 为 1，发送实现逐包调用 `WriteToUDPAddrPort()`。

因此本报告不采用“GSO 已确认是根因”的表述。更准确的说法是：

- 错误发生在 WireGuard 用户态发送路径；
- 直接拨号与 detour 拨号会选择不同 dialer 路径；
- Android socket/Network 绑定、有效 PMTU、UDP 分片策略或底层 offload 能力仍可能参与；
- 只有 detour/MTU 单变量对照或更底层的 socket/packet capture 才能继续区分。

## 9. 推荐验证矩阵

每组都使用相同设备、Android/API、Wi-Fi、WireGuard 服务端、节点、Chrome URL 和测试时长；一次只改变一个变量。

| 组 | Endpoint MTU | Endpoint detour | Direct Dial 字段 | 目的 | 预期判定 |
| --- | ---: | --- | --- | --- | --- |
| A 基线 | 1390 | 无 | 无 | 复现当前问题 | URLTest 成功，Chrome 失败，出现 `EMSGSIZE`。 |
| B 首选修复 | 1390 | `direct` | `network_strategy=default` | 验证上游 workaround | Chrome 成功且 `EMSGSIZE` 消失，支持 dialer 路径假设。 |
| C MTU 对照 | 1280 | 无 | 无 | 验证真实 PMTU 假设 | 若成功，说明 MTU/PMTU 是主要因素；若仍失败，降低该假设。 |
| D 组合（仅必要时） | 1280 | `direct` | `network_strategy=default` | 判断两因素交互 | 只在 B、C 结果明确后执行，不能作为首个修复。 |

每组最小证据：

- `WireGuardEndpointTrace` 一行；
- 首个 WireGuard 错误或 30 秒无错误说明；
- Chrome HTTPS 页面是否完整加载；
- 一个大于 URLTest 204 响应的下载/页面结果；
- DNS 解析结果；
- 是否完成握手；
- 不含私钥、完整公钥、PSK、完整生成配置。

## 10. 推荐实现约束

若 B 组确认有效，最小实现应满足：

1. 只为尚未具有 detour 的 WireGuard endpoint 补 `direct`；
2. 不覆盖 selector/urltest 或代理链已建立的 detour；
3. `listen_port > 0` 时不得同时添加 detour，应给出明确诊断或保留当前路径并进入专项验证；
4. direct outbound 增加最小、固定版本支持的非空 Dial 字段；
5. 保持 legacy WireGuard outbound 不被产品 builder 调用；
6. 增加最终 JSON/拓扑测试，覆盖单节点、selector/urltest、链首/链尾、已有 detour、listen_port 冲突；
7. 由 CI 做 libcore 配置检查与 Android 编译；
8. 真机必须验证 Chrome 或等价真实应用流量，不能用 URLTest 成功代替。

## 11. 风险

- `network_strategy=default` 会改变 direct outbound 的 Android 网络选择/绑定行为，需回归普通协议与 Wi-Fi/移动网络切换。
- WireGuard `listen_port` 与 detour 在 v1.13.16 明确冲突；自动补 detour 不能破坏监听端口语义。
- 两个 WireGuard endpoint 串联、endpoint 地址为域名等场景有独立上游历史问题，不能由单节点 workaround 推断全部安全。
- #3390 是社区验证 workaround，不是已确认合入 v1.13.16 的上游根治补丁。
- MTU 1280 可能牺牲吞吐，只应作为对照或回退，不应与 detour 同批盲目引入。

## 12. 当前结论

当前最高置信度结论不是“MTU 1390 一定错误”，也不是“GSO 一定损坏”，而是：

> Android 上 sing-box v1.13.16 WireGuard endpoint 的无 detour 底层 UDP 发送路径，在该设备/网络上对单个封装数据报返回 `EMSGSIZE`；小流量测试不足以暴露该问题。显式绕行非空 direct outbound 是最有上游复现支持、且可通过单变量真机实验验证的首选修复方向。

在 B 组真机结果返回前，不应把 5.0.1 标记完成，也不应推进既定 5.1/5.2 真机验收。
