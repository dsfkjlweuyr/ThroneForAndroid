## Why

Android 端现有连接测试菜单混用了不可翻译的 “URL Test”、遗留的直连 TCP/ICMP Ping 与速度测试入口，且延迟测试默认值已经落后于 Throne 桌面端。统一测试分组、术语和默认配置，可以减少用户误解，并让 Android 与 Throne 的测试行为和备份设置映射保持一致。

## What Changes

- 将批量 URL 延迟测试入口从不可翻译的 “URL Test” 改为可本地化的“URL 测试本组”，并补齐项目支持语言的 i18n 资源。
- **BREAKING**：移除 `connection_test_tcp_ping`、`connection_test_icmp_ping` 对应的批量直连 TCP/ICMP Ping 菜单、执行路径、能力判断和仅供这些路径使用的资源；不再向用户提供这两种连接测试。
- 以原 `TCPing` 菜单位置提供“速度测试本组”，但不保留旧 TCP 建连延迟实现；新增与 Throne 桌面端一致的当前组下载+上传速度测试，并支持简单下载模式。
- 将设置项 `connection_test_url` 的显示名称调整为“延迟测试 URL”，新安装/未设置用户的默认地址从 `http://www.gstatic.com/generate_204` 改为 `http://cp.cloudflare.com/`，默认并发数从 5 改为 10。
- 以 Throne 桌面端当前设置模型为参照，同步 Android 端速度测试模式、超时、服务发现/下载/上传 URL 及简单下载 URL 等默认值与其集中定义、持久化和消费方式；已持久化的用户自定义值不被默认值升级覆盖。
- 更新 Android 应用能力规范、相关说明和回归检查，确保 UI、i18n、默认值、备份导入及测试执行路径保持一致。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `android-application`: 明确测试菜单分组与本地化要求、移除直连 TCP/ICMP Ping、对齐延迟及上下行测速默认配置，并规定默认值升级不得覆盖已有用户设置。
- `libcore-integration`: 新增经指定 outbound 执行、可报告进度并可取消的下载/上传速度测试桥接，且明确测速 URL 和实例隔离契约。

## Impact

- Android UI 与资源：配置列表菜单、测试操作处理、全量 `values-*` 字符串和全局设置页面。
- Android 配置与执行：`Constants`、`DataStore`、URL 测试偏好、新增速度测试请求/结果与执行 UI、备份导入映射，以及与废弃 Ping 路径相关的 Bean 能力接口。
- 规范与文档：`openspec/specs/android-application/spec.md` 及引用旧术语、旧默认值或旧测试入口的其他文档/检查。
- 参考实现：调研 `C:\repos\Throne` 中 `SettingsRepo`、基本设置页面、`TestRunner` 与速度测试内核的当前实现；这是产品同源参考。该范围会新增 Android/libcore 的速度测试桥接及上游速度测试库依赖，可能影响 libcore 构建，但不改变开发工具目录布局；具体参考版本与依赖版本在设计阶段记录。
