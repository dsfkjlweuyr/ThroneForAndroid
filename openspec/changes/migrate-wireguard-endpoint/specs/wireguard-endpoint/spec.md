## Purpose

定义 T4A 在 sing-box 1.13 上存储、导入、生成并运行 WireGuard endpoint 的用户可观察行为，同时保护既有节点数据与链式路由语义。

## ADDED Requirements

### Requirement: WireGuard 节点生成 endpoint 配置

系统 SHALL 将内部 WireGuard 节点生成为顶层 `endpoints` 中 `type = "wireguard"` 的 endpoint，并 MUST NOT 为该节点生成 legacy WireGuard outbound。

#### Scenario: 生成单节点配置

- **GIVEN** 用户选择一个字段有效的 WireGuard 节点
- **WHEN** T4A 生成 sing-box 运行配置
- **THEN** 配置的 `endpoints` 包含带稳定 tag 的 WireGuard endpoint
- **AND** `outbounds` 不包含同一节点的 `type = "wireguard"` legacy outbound

### Requirement: WireGuard 字段完整映射

系统 MUST 将节点本地地址列表、私钥、MTU 和正数监听端口映射到 endpoint，并 MUST 将服务器地址、服务器端口、公钥、非空预共享密钥、正数保活间隔和非空 reserved 映射到首个 peer。系统 SHALL 为该 peer 生成 IPv4 与 IPv6 全流量 `allowed_ips`；可选字段为空或非正数时 MUST 省略相应 JSON 字段。

#### Scenario: 映射完整节点

- **GIVEN** WireGuard 节点包含双栈本地地址、监听端口、密钥、MTU、服务器、保活和 reserved
- **WHEN** 节点转换为 endpoint
- **THEN** endpoint 和首个 peer 保留全部对应值
- **AND** peer 的 `allowed_ips` 同时包含 `0.0.0.0/0` 与 `::/0`

#### Scenario: 省略可选字段

- **GIVEN** 节点的监听端口和保活间隔为零且预共享密钥与 reserved 为空
- **WHEN** 节点转换为 endpoint
- **THEN** 生成 JSON 不包含这些可选字段

### Requirement: Reserved 表示兼容

系统 SHALL 接受 base64 reserved 字符串，并 SHALL 将由三个十进制字节组成的列表表示（含逗号、换行、空格或方括号）转换为等价的单行 base64 字符串。无法识别为三字节列表的非空值 MUST 原样保留，以避免破坏已有配置。

#### Scenario: 转换三字节列表

- **GIVEN** 节点 reserved 为 `[0, 1, 2]`
- **WHEN** 生成 WireGuard peer
- **THEN** peer 的 reserved 为等价 base64 值 `AAEC`

#### Scenario: 保留已有 base64

- **GIVEN** 节点 reserved 已为 `AAEC`
- **WHEN** 生成 WireGuard peer
- **THEN** 该值保持不变

### Requirement: WireGuard 配置导入

系统 SHALL 支持导入 wg-quick 文本配置和 sing-box WireGuard endpoint JSON。wg-quick 中每个具有有效 Endpoint 与 PublicKey 的 peer MUST 形成独立节点并继承 Interface 地址、私钥、MTU 和监听端口；endpoint JSON 导入 MUST 读取首个 peer 及 endpoint 的 tag、地址、私钥、MTU 和监听端口。缺少 peer 的 endpoint JSON MUST NOT 产生 WireGuard 节点。

#### Scenario: 导入多 peer wg-quick 配置

- **GIVEN** 一个 Interface 下包含两个有效 Peer 且地址值含逗号与空格
- **WHEN** 用户导入配置
- **THEN** 系统生成两个 WireGuard 节点
- **AND** 每个节点继承修剪后的 Interface 地址和对应 peer 字段

#### Scenario: 导入 endpoint JSON

- **GIVEN** sing-box JSON 含一个 WireGuard endpoint 和一个 peer
- **WHEN** 订阅或 JSON 导入器解析配置
- **THEN** 系统生成字段等价的 WireGuard 节点

#### Scenario: 拒绝无 peer endpoint

- **GIVEN** WireGuard endpoint JSON 未提供 peers 或 peers 为空
- **WHEN** 导入器解析配置
- **THEN** 该对象不产生节点
- **AND** 导入流程不会因类型转换异常而崩溃

### Requirement: 既有 WireGuard 数据向后兼容

系统 MUST 继续反序列化升级前保存的 WireGuardBean，并 SHALL 对旧版本不存在的监听端口或保活字段使用零值。迁移 MUST NOT 要求用户删除并重新创建既有 WireGuard 节点。

#### Scenario: 打开旧数据库节点

- **GIVEN** 数据库包含迁移前序列化的 WireGuardBean
- **WHEN** 新版本加载并再次保存节点
- **THEN** 原有地址、密钥、MTU、服务器和 reserved 保持不变
- **AND** 缺失的新字段得到兼容默认值

### Requirement: Endpoint 保持代理图引用语义

系统 MUST 保持 WireGuard endpoint 的 tag 可被主路由、selector、urltest 和链式代理引用。若 WireGuard 位于链中，系统 SHALL 使用 sing-box endpoint 支持的引用/绕行语义，且 MUST NOT 重新把它包装成已移除的 WireGuard outbound。

#### Scenario: WireGuard 作为主节点

- **GIVEN** WireGuard 是当前选中的主节点
- **WHEN** 配置生成器建立最终路由目标
- **THEN** 最终目标引用该 endpoint 的 tag
- **AND** 配置可通过 sing-box 配置检查

#### Scenario: WireGuard 参与代理链

- **GIVEN** 一条支持的代理链包含 WireGuard 节点和另一个内部节点
- **WHEN** 配置生成器建立链路关系
- **THEN** 生成配置保持预期跳序与可解析 tag 引用
- **AND** 不出现 legacy WireGuard outbound

