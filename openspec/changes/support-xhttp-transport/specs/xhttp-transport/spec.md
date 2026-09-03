## Purpose

定义 XHTTP (SplitHTTP) 客户端传输协议在 ThroneForAndroid 中的运行契约，涵盖模式协商、流式上传下载、混淆填充及连接复用行为。

## ADDED Requirements

### Requirement: XHTTP 客户端传输模式与协议装配

XHTTP 客户端传输驱动 MUST 支持 `auto`、`packet-up`、`stream-up` 和 `stream-one` 四种工作模式，并遵循与 `sing-box-throne` 一致的请求装配规则。在 `stream-up` 与 `stream-one` 模式下，驱动 MUST 使用 HTTP/2 分块流式传输；在 `packet-up` 模式下，驱动 MUST 将上行数据包装为离散 HTTP 请求。当模式设置为 `auto` 时，驱动 MUST 具备自动检测与模式探测能力。

#### Scenario: 建立 stream-up 传输流
- **GIVEN** VLESS 节点的 XHTTP 模式配置为 `stream-up`
- **WHEN** 客户端拨号发起连接
- **THEN** 客户端通过 HTTP/2 建立全双工或分块长连接流
- **AND** 上行与下行通道均按流式协议交互

#### Scenario: 建立 stream-one 传输流
- **GIVEN** VLESS 节点的 XHTTP 模式配置为 `stream-one`
- **WHEN** 客户端与服务端完成 TLS/Reality 握手并拨号
- **THEN** 客户端使用单 HTTP 请求双向流建立通道，不额外发起独立的下载请求

### Requirement: X-Padding 混淆与流特征保护

XHTTP 传输驱动 MUST 支持 Xray 兼容的 X-Padding 填充规则。当配置提供 `x_padding_bytes` 或相关混淆参数时，驱动 MUST 在 HTTP 头部或数据包载荷中注入指定范围的填充字节，以对齐服务端反审查探测特征。

#### Scenario: 发送带 Padding 的数据包
- **GIVEN** 节点配置指定了 `x_padding_bytes` 范围
- **WHEN** XHTTP 客户端发送握手请求或上行数据包
- **THEN** 请求中包含符合长度区间的伪随机填充
- **AND** 远端服务能够正确去除填充并还原真实载荷

### Requirement: xmux 连接复用管理

XHTTP 传输驱动 MUST 支持 `xmux` 连接复用选项，包括并发连接上限（`max_concurrency`）、最大连接数（`max_connections`）以及请求复用策略。复用管理 MUST 保证同一会话及连接生命周期在网络接口重置或关闭时安全释放，不得产生 goroutine 或文件描述符泄露。

#### Scenario: 并发请求复用底层 HTTP 连接
- **GIVEN** 节点启用了 `xmux` 连接复用且并发数在配置限制内
- **WHEN** 多个应用连接并发经由该节点出站
- **THEN** 底层复用已建立的 HTTP/2 连接
- **AND** 避免为每个出站流重复执行完整的 TLS/Reality 握手
