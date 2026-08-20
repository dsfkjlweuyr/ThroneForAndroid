## MODIFIED Requirements

### Requirement: 已知官方内核降级必须显式保留

SSR/Snell 以及 Clash API selector 回调等尚未完成的兼容项 MUST 在主规范中保持可见，且 MUST NOT 被描述为已支持。WireGuard MUST 使用与 `SINGBOX_VERSION` 对应的官方 endpoint registry/API；Android 生成器 MUST NOT 依赖 sing-box 1.13 已移除的 WireGuard outbound。用于诊断用户自定义旧 JSON 的 legacy outbound stub MAY 保留，但 MUST NOT 被 T4A 自身生成的配置触发。

#### Scenario: 评估 WireGuard 节点

- **GIVEN** 当前官方内核支持 WireGuard endpoint 且不支持 WireGuard outbound
- **WHEN** Android 为 WireGuard 节点生成配置
- **THEN** 配置使用已注册的官方 WireGuard endpoint
- **AND** T4A 自身生成的配置不会触发 legacy outbound stub

#### Scenario: 用户提供 legacy WireGuard JSON

- **GIVEN** 用户自定义配置仍包含 WireGuard outbound
- **WHEN** libcore 加载该配置
- **THEN** 系统返回明确的已移除格式诊断
- **AND** 不将该旧格式描述为可运行能力
