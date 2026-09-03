## ADDED Requirements

### Requirement: VLESS XHTTP 配置生成与导入兼容

Android 配置生成器与解析器 MUST 保持与 Throne 电脑版/sing-box-throne 一致的 VLESS XHTTP 配置规范。在导入 V2Ray 格式链接（`type=xhttp`）、Clash meta 订阅（`xhttp-opts`）以及手动编辑时，MUST 正确归一化并持久化 `mode`、`host`、`path` 以及 `extra`（含 `xmux`、`x_padding_bytes`、`sc_max_each_post_bytes` 等参数），并在生成 sing-box outbound 配置时输出匹配 libcore 扩展 VLESS outbound 的 transport 结构。

#### Scenario: 导入带 extra 的 VLESS XHTTP 节点
- **GIVEN** 用户导入包含 `type=xhttp` 和 `extra` 参数的 VLESS 分享链接或 Clash 配置
- **WHEN** 解析器处理该节点
- **THEN** 节点的传输协议被识别为 `xhttp`
- **AND** 模式、路径及 extra 结构被正确反序列化并保存，不丢失 XHTTP 专有配置

#### Scenario: 为 XHTTP 节点生成出站配置
- **GIVEN** 当前选中的代理节点为 VLESS XHTTP 节点
- **WHEN** 应用生成 sing-box 配置
- **THEN** 生成的 outbound 中 `transport` 的 `type` 为 `"xhttp"`
- **AND** 包含归一化后的 `mode`、`host`、`path` 及有效 extra 字段
