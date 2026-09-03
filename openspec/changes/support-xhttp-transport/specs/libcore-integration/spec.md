## MODIFIED Requirements

### Requirement: 自定义协议扩展不得依赖 fork

Juicity outbound、覆盖版 HTTP outbound 以及扩展版 VLESS outbound（含 XHTTP 客户端传输驱动）MAY 由 libcore 自定义注册，但 MUST 基于官方 registry/API，不得修改或分叉官方 sing-box 源码。HTTP TLS 在用户未显式配置 ALPN 时 MUST 提供 `h2` 与 `http/1.1`，并按协商结果使用 HTTP/2 CONNECT 或 HTTP/1.1 CONNECT。VLESS outbound 在配置 XHTTP 传输时 MUST 能正确解析并建立连接，且在常规 TCP/WS/gRPC 等标准传输下保持与官方 VLESS 语义完全一致。

#### Scenario: 连接 h2-only HTTPS 代理
- **GIVEN** HTTPS 代理仅接受 HTTP/2 且用户未显式指定 ALPN
- **WHEN** HTTP outbound 完成 TLS 握手
- **THEN** ALPN 可协商为 `h2`
- **AND** outbound 使用 HTTP/2 CONNECT 建立代理流

#### Scenario: 连接 VLESS XHTTP 代理
- **GIVEN** 配置了 `transport.type = "xhttp"` 的 VLESS 节点
- **WHEN** libcore 初始化 box 实例并拨号连接
- **THEN** box 成功解析并注册出站驱动，不报 `unknown transport type: xhttp`
- **AND** 拨号流量经由 XHTTP 传输层成功发出
