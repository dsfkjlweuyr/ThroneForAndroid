## ADDED Requirements

### Requirement: libcore 提供节点隔离的上下行速度测试

libcore MUST 提供可由 Android 调用的速度测试接口，经指定节点的 outbound 执行下载和上传测试，并返回可区分节点的下载速度、上传速度、传输字节、延迟、服务端信息、错误与取消状态。测速 MUST 遵守调用方提供的模式、超时和 URL 设置，MUST 支持取消，且 MUST NOT 绕过指定 outbound 或与其他测试实例共享可变网络状态。

#### Scenario: 下载上传完整测速

- **GIVEN** Android 为一个或多个节点请求下载+上传测速并提供有效配置
- **WHEN** libcore 启动速度测试
- **THEN** 每个节点的下载与上传流量均经该节点 outbound
- **AND** 返回结果包含下载和上传吞吐量、字节数、延迟及服务端信息

#### Scenario: 测速被取消

- **GIVEN** 一批速度测试正在执行
- **WHEN** Android 发出取消请求或测试上下文结束
- **THEN** libcore 停止尚未完成的网络操作
- **AND** 返回或发布可识别的取消状态，而不是把取消报告为成功

#### Scenario: 自定义速度测试 URL

- **GIVEN** Android 提供了用户自定义的服务发现、下载、上传或简单下载 URL
- **WHEN** libcore 执行对应速度测试阶段
- **THEN** 网络请求使用已提供的 URL
- **AND** 未提供自定义值时使用与选定 Throne 参考版本一致的默认值
