## 1. 锁定官方契约与纯格式转换

- [x] 1.1 对照 `nb4a.properties` 的 sing-box v1.13.16 官方源码/JSON schema，记录 WireGuard endpoint、peer、根 `endpoints` 和 endpoint registry 的准确字段与类型；同时核对 T4A WireGuardBean 的 Kryo 版本，只有缺失字段时才采用追加字段的兼容升级。
- [x] 1.2 在 `SingBoxOptions.java` 增加根 endpoints、Endpoint 基类及 v1.13.16 WireGuard endpoint/peer options，并在 WireGuard 格式模块实现 endpoint builder、可选字段省略和 reserved 三字节列表/base64 兼容转换；保留 legacy outbound options 但移除产品 builder 对它的调用。
- [x] 1.3 增加 JVM 单元测试，覆盖完整字段、双栈 allowed_ips、零值/空值省略、reserved 两种表示以及 Bean 旧版本反序列化（如本批修改 Bean）；fixture 必须使用无生产价值的测试密钥且失败输出不得泄露完整私钥/PSK。
- [x] 1.4 提交本批至 GitHub Actions `CI / Build OSS APK`（其依赖 `CI / Native Build (LibCore)`）；预期两个 job 均成功且目标 WireGuard 单测通过，回传 Actions run URL、失败/成功测试摘要和 APK 编译成功记录。收到该证据前不得开始第 2 批。

## 2. 单节点 endpoint 配置拓扑

- [x] 2.1 在根配置和序列化模型中接入 `endpoints`，实现 endpoint 类型白名单与构建后分区，使一个 WireGuard 主节点进入 endpoints、保留原 tag，并从 outbounds 消失；明确自动配置与用户自定义 endpoints 的 merge 顺序和同 tag 行为。
- [x] 2.2 增加配置构建测试，断言单 WireGuard 主节点的最终 JSON、主路由 tag、自定义 endpoint 共存结果，以及 legacy WireGuard outbound/stub 不会被产品生成配置触发；加入目标版本可执行的 libcore 配置检查入口或测试。
- [x] 2.3 提交本批至 GitHub Actions `CI / Native Build (LibCore)` 与 `CI / Build OSS APK`；预期生成的单节点 JSON通过 libcore 配置检查、两个 job 成功且不出现 WireGuard outbound removed 错误，回传 run URL、脱敏生成结构与配置检查日志。收到证据前不得开始第 3 批。

## 3. Selector、URLTest 与链式引用

- [x] 3.1 调整 selector/urltest 及代理链构建，使 WireGuard endpoint tag 可作为主目标和组成员，并按 sing-box endpoint 支持的 detour/引用方向维持 T4A 既有跳序；不以 legacy outbound 包装 endpoint。
- [x] 3.2 为 WireGuard 作为 selector 成员、urltest 成员、链首/链尾等实际支持位置分别添加拓扑测试；每个测试断言 tag 可解析、跳序正确、endpoint/outbound 分区正确，并对不受官方 endpoint 语义支持的位置返回明确诊断而非生成无效配置。
- [ ] 3.3 提交本批至 GitHub Actions `CI / Native Build (LibCore)` 与 `CI / Build OSS APK`；预期每个拓扑 fixture 均通过 libcore 配置检查且构建成功，回传 run URL、各场景检查矩阵和脱敏错误（若存在明确不支持场景）。收到证据前不得开始第 4 批。

## 4. wg-quick 与 endpoint JSON 导入

- [ ] 4.1 将 wg-quick 解析收敛到 WireGuard 格式模块并让 RawUpdater 复用，修剪多地址，按有效 peer 拆分节点，并映射 Interface 的私钥、MTU、监听端口及 Peer 的 Endpoint、公钥、PSK、保活和 reserved。
- [ ] 4.2 扩展 sing-box JSON 导入器遍历根 endpoints，并安全解析 WireGuard endpoint 首个 peer；数字兼容 JSON 数字/字符串，无 peer、错误 peer 类型或缺失必要字段时跳过对象而不崩溃。
- [ ] 4.3 增加 wg-quick 多 peer、IPv4/IPv6 Endpoint、endpoint JSON 往返、无 peer/错误类型及旧数据库节点重存测试，并确认编辑界面能无损读取和保存迁移字段。
- [ ] 4.4 提交本批至 GitHub Actions `CI / Build OSS APK`（连同依赖的 `CI / Native Build (LibCore)`）；预期所有导入与兼容测试、Android 编译成功，回传 run URL、测试数/结果和不含密钥的失败摘要。收到证据前不得开始第 5 批。

## 5. 真机可运行性与规范收尾

- [ ] 5.1 使用专用测试 WireGuard 服务在 Android 真机验证单节点：导入或创建节点、启动 VPN、确认握手、IPv4 流量与 DNS；服务和设备均支持 IPv6 时同时验证 IPv6，否则记录环境性跳过。证据为设备/API 版本、脱敏握手时间、访问结果和无 legacy stub 的日志片段。
- [ ] 5.2 在真机验证一个 CI 已确认有效的 selector/urltest 或链式场景，并执行 Wi-Fi/移动网络切换后的重连；预期目标选择/跳序正确、切网后恢复流量且密钥不出现在日志，回传脱敏拓扑、切换时间线和连通性结果。
- [ ] 5.3 根据最终实现同步本 change 的 delta spec/设计偏差，运行 `openspec validate migrate-wireguard-endpoint --strict`；预期零错误，并将 CI run URL、真机证据摘要及任何有理由的 IPv6 跳过记录附入实现审查说明。
