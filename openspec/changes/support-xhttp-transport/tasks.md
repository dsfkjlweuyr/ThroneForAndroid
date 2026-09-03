## 1. 移植并接入 XHTTP 客户端传输驱动 (libcore)

- [x] 1.1 从 `sing-box-throne` (`origin/xhttp`) 移植精简版 Xray buffer 工具集至 `libcore/protocol/vless/internal/`（包含 `xray/buf`, `xray/pipe`, `xray/bytespool`, `xray/signal`）
- [x] 1.2 移植 XHTTP 核心传输驱动至 `libcore/protocol/vless/xhttp`（包含 `client.go`, `conn.go`, `dialer.go`, `http.go`, `mux.go`, `upload_queue.go`, `writer.go`），并定义 `XHTTPOptions`
- [x] 1.3 编写静态依赖与符号审计脚本 `tools/diagnostics/audit_xhttp_libcore.py`，使用 `uv run` 执行，验证依赖与官方 sing-box v1.13.16 纯净兼容
- [x] 1.4 批次一验证门禁（CI 构建）：提交变更并触发 GitHub Actions CI 构建（`.github/workflows/ci.yml`），验证 libcore Go 模块编译与 AAR 打包无符号错误、无未导出的依赖错误

## 2. 实现并注册扩展版 VLESS Outbound (libcore)

- [x] 2.1 在 `libcore/protocol/vless` 中实现自定义 `VLESSOutboundOptions` 与 `NewOutbound`，当 `transport.type == "xhttp"` 时接入 XHTTP 传输驱动，其他传输类型保持官方 `v2ray.NewClientTransport` 委托
- [x] 2.2 在 `libcore/box_include.go` 的 `nekoboxAndroidOutboundRegistry` 中注册自定义 VLESS outbound，覆盖官方默认注册
- [x] 2.3 编写 `libcore` 单元测试，验证包含 XHTTP 传输配置的 VLESS 出站能够成功反序列化并构造实例
- [ ] 2.4 批次二验证门禁（CI 构建与内核测试）：提交变更并触发 GitHub Actions CI 构建，验证 `box.New` 在包含 xhttp 节点配置时成功创建实例，不再报 `unknown transport type: xhttp`，且标准 VLESS 节点行为不倒退

## 3. Android 端配置与数据映射审计对齐 (app)

- [ ] 3.1 审计并修正 `StandardV2RayBean.java`、`V2RayFmt.kt`、`XhttpExtraConverter.kt` 中与 `sing-box-throne` 的字段映射（确保 `mode`, `path`, `host`, `x_padding_bytes`, `xmux`, `download` 准确传递）
- [ ] 3.2 在 `app/src/test/java/io/nekohasekai/sagernet/fmt/` 中增加针对 VLESS XHTTP URL 解析、Clash 导入和 sing-box JSON 导出的单元测试覆盖
- [ ] 3.3 运行 Gradle 单元测试验证配置构建契约全部通过
- [ ] 3.4 批次三验证门禁（CI/Preview 构建）：提交变更并触发 GitHub Actions Preview 工作流，验证 Android APK 编译打包成功并产出测试包

## 4. 端到端真机验证与规范归档

- [ ] 4.1 在 Android 真机上安装 Preview APK，配置 XHTTP 节点（涵盖 auto, stream-up, packet-up, stream-one），执行节点测速与正式连接
- [ ] 4.2 验证 Wi-Fi 与移动网络热切换时连接池与 xmux 自动重置，确认无 fd 泄露与连接挂起
- [ ] 4.3 收集真机测试日志与连通性证据，确认满足验收标准
