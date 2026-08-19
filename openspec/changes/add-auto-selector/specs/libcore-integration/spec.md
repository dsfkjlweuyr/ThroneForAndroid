## MODIFIED Requirements

### Requirement: libcore 仅接入官方 sing-box

libcore MUST 以 `SagerNet/sing-box` 官方源码作为上游基线，版本由 `SINGBOX_VERSION` 指定。仓库 MAY 在校验官方 tag 后应用仓库内维护、可审计且基线绑定的功能补丁，但代码和构建依赖 MUST NOT 直接引用 starifly、Throneproj 或其他 sing-box fork，也 MUST NOT 引用 libneko、nekoutils、旧 boxapi 或 fork conntrack。`libcore/go.mod` 中的本地 replace MAY 指向应用补丁后的构建工作树，但不得成为另一个版本来源。

#### Scenario: 获取内核源码

- **GIVEN** CI 开始构建 libcore
- **WHEN** 源码获取脚本处理 `SINGBOX_VERSION`
- **THEN** remote 被校正为官方仓库
- **AND** HEAD 在应用仓库内补丁前与指定 tag 的 commit 完全一致
- **AND** 静态依赖检查不存在 fork 残留

#### Scenario: 获取并叠加自动选择器内核能力

- **GIVEN** CI 开始构建包含 auto-selector 的 libcore
- **WHEN** 源码获取脚本处理 `SINGBOX_VERSION`
- **THEN** remote 被校正为官方仓库且 HEAD 先与指定 tag 的 commit 完全一致
- **AND** 仓库内声明的补丁按固定顺序干净应用
- **AND** Go 模块依赖中不存在 fork 仓库引用

## ADDED Requirements

### Requirement: 自动选择器补丁必须保持官方注册与生命周期契约

auto-selector 补丁 MUST 通过目标官方版本的 outbound registry 注册独立类型，并 MUST 实现官方 outbound group、连接、数据包连接、URL 测试、启动和关闭生命周期所要求的契约。补丁 MUST NOT 替换全局路由器、平台接口或手动 selector 实现；补丁应用失败、注册缺失或配置 schema 不匹配 MUST 使构建或测试失败。

#### Scenario: 构建包含 auto-selector 的配置

- **GIVEN** 配置包含合法的 `auto-selector` outbound
- **WHEN** 官方 registry 创建并启动 box
- **THEN** outbound 被识别并完成成员解析与生命周期启动
- **AND** 手动 `selector` 与 `urltest` 的既有注册行为不受影响

### Requirement: libcore 暴露幂等状态与明确控制结果

libcore MUST 为当前 box 暴露自动选择器状态查询、立即复检、设置成员偏好和清除偏好操作。状态查询 MUST 幂等且不得重置统计；控制操作 MUST 验证组与成员存在，并 MUST 以可判别结果返回成功、无运行实例、组不存在、成员不存在或内部错误。Android 回调 MUST 在实际选择变化时携带自动选择器 tag 与成员 tag。

#### Scenario: Android 固定不存在的成员

- **GIVEN** 自动选择器正在运行但目标 tag 不属于其运行成员
- **WHEN** Android 请求设置该成员为偏好
- **THEN** libcore 返回成员不存在的失败结果
- **AND** 当前选择与持久偏好不被改变

#### Scenario: 多个消费者轮询状态

- **GIVEN** 服务状态页和后台监视器同时查询自动选择器
- **WHEN** 两者重复读取状态
- **THEN** 获得一致的快照语义
- **AND** 任一读取均不清空成员健康或流量计数

