## 1. libcore 单节点速度测试与依赖基线

- [x] 1.1 在 `libcore/go.mod` 固定 Throne 基线使用的 `github.com/Mahdi-zarei/speedtest-go v1.7.13-0.20260107171856-79c565dfd83a`，记录/校验模块来源、许可证与 checksum，并集中定义服务列表主/回退端点及下载/上传 URL 派生规则。
- [x] 1.2 在 libcore 实现经指定测试 box/outbound 的下载+上传、仅下载、仅上传与简单下载执行器，遵循 5000 ms 默认超时、8 个单节点内部连接和 `http://cachefly.cachefly.net/1mb.test` 简单下载默认值。
- [x] 1.3 增加 gomobile 可绑定的会话 API、采样结果模型和取消入口，结果覆盖速率、字节数、延迟、服务器信息、错误与取消状态，并保证 box、接口 wrapper、fd protect 和网络策略按测试实例隔离。
- [x] 1.4 增加 Go 单元测试/可注入 HTTP 服务器测试，验证下载与上传请求方法/URL、简单下载字节计算、模式裁剪、超时、取消、错误传播及指定 outbound dialer 的使用。
- [x] 1.5 提交本批次并运行 GitHub Actions 的 libcore/AAR 构建及 Go 测试 job；预期 gomobile 绑定成功、依赖校验通过、所有测速测试通过。进入下一批前需回传 workflow run 链接、提交 SHA、相关 job 绿灯截图/日志摘要；本批不要求真机，因为尚无 Android 入口。

## 2. Android 测试设置与缺省迁移语义

- [x] 2.1 集中定义延迟 URL `http://cp.cloudflare.com/`、URL 延迟并发 10、速度模式下载+上传、速度超时 5000 ms、服务发现端点和简单下载 URL；让 `DataStore` 与 preference 默认读取同一来源，消除 Google 204、URL 延迟并发 5 等重复旧值，并禁止将该并发值传给 Throne 下载/上传测速。
- [x] 2.2 在全局设置中加入速度测试模式、超时与简单下载 URL 控件和校验，保持内部服务发现/下载/上传派生端点不对普通用户暴露；将 `connection_test_url` 的基准标题改为 “Latency test URL”。
- [x] 2.3 扩展 Throne 备份设置映射，仅在备份字段存在时导入 `speed_test_mode`、`speed_test_timeout_ms`、`simple_dl_url` 等可映射值，并保持 Android 已持久化设置及不在备份中的字段不被默认值覆盖。
- [x] 2.4 增加 JVM/静态测试，分别覆盖全新配置的全部默认值、预置旧 Google 204/自定义 URL/并发值后的升级读取、preference 与 DataStore 默认一致性，以及备份字段存在/缺失时的导入行为。
- [x] 2.5 提交本批次并运行 GitHub Actions Android unit/lint/build job；预期资源编译、设置测试和备份导入测试全部通过。回传 workflow run、提交 SHA、测试报告；在测试版真机升级安装上确认原有自定义值不变，并提供设置页截图与升级前后导出值作为最低证据。

## 3. “速度测试本组”Android 执行与反馈

- [x] 3.1 新建 Android 速度测试会话适配层，将当前组 profile 依次转换为隔离测试配置并调用 libcore；完整吞吐测速按节点串行，单节点期间持续拉取/接收采样，取消时终止当前会话和剩余队列。
- [x] 3.2 实现“速度测试本组”进度 UI/通知，明确当前节点与下载/上传阶段，展示实时吞吐、服务器/延迟、失败和取消；使用独立于 `ProxyEntity.ping/status` 的 profile 关联持久化字段或实体保存成功结果，并在节点卡片右下角现有测速信息区域以主题灰色辅助文字按模式展示最终上下行吞吐。结果在关闭临时 UI、列表重绑/刷新及重新进入页面后仍保留，直到清除测试结果或下一次成功测速原子覆盖；失败/取消不得写入伪造成功值。
- [x] 3.3 将原 TCPing 菜单位置重新接线到速度测试组入口，并增加耗流量提示或明确确认流程；保留 URL 测试互斥状态，防止两类批量测试同时运行。
- [x] 3.4 增加 Android 单元/组件测试，验证节点串行顺序、四种模式参数、采样到 UI 映射、单节点失败后队列策略、取消、Fragment 销毁和通知最小化恢复；覆盖专用速度结果的持久化读写、按模式格式化、节点卡片右下角主题灰色绑定、列表重绑/重新进入恢复、下一次成功结果覆盖、清除测试结果，以及失败/取消不写入成功吞吐值。
- [ ] 3.5 精简组测速 UI 并修正菜单顺序：测速弹窗仅展示当前正在测速的节点及其实时阶段/吞吐，不在弹窗内累积已完成节点详情，队列完成后不再显示结果汇总弹窗；增加按“已完成节点数/总节点数”更新的组队列进度条，最终结果继续仅在各节点卡片现有测速信息区域逐条展示。将“速度测试本组”固定排列在“URL 测试本组”下方。补充 Android 单元/组件测试，覆盖菜单顺序、进度条更新、弹窗切换节点时替换而非追加内容、完成后无汇总弹窗，以及节点卡片结果持久显示不受影响。
- [ ] 3.6 提交本批次并运行 GitHub Actions Android unit/lint/build 与 libcore 集成 job；预期所有模式参数、生命周期、持久化、节点卡片结果、精简测速弹窗、队列进度条和菜单顺序测试通过。随后在至少两个节点的真机组中执行下载+上传、仅上传和简单下载，确认流量经过各节点、节点间不并发、弹窗只显示当前节点、进度条持续更新、完成后无汇总弹窗且取消及时；确认“速度测试本组”位于“URL 测试本组”下方，每个成功结果以灰色辅助文字保留在对应节点卡片右下角，关闭对话框、刷新列表和重新进入页面后仍在，下一次测速可覆盖且“清除测试结果”会移除；回传 run 链接、提交 SHA、录屏/截图、测试服务器日志或节点出口 IP/流量证据和失败日志。

## 4. 移除旧 TCP/ICMP Ping 专属代码

- [ ] 4.1 删除 `pingTest(icmpPing)`、socket/ICMP 分支、旧菜单 handler，以及 `canTCPing()`/`canICMPing()` 基类能力和所有 Bean overrides；清理仅由这些路径使用的 imports、通知状态和辅助代码。
- [ ] 4.2 删除或重命名 `connection_test_tcp_ping`、`connection_test_icmp_ping`、对应 unavailable 文案和遗留 menu ID，保证“速度测试本组”使用独立 speed-test 命名，URL RTT 与正式连接状态栏 URL 测试不受影响。
- [ ] 4.3 增加静态检查，确保生产源码和资源中不存在废弃 Ping 资源/方法/ID 引用，并回归 URL 测试队列、结果清理和删除不可用节点行为。
- [ ] 4.4 提交本批次并运行 GitHub Actions Android unit/lint/build job；预期无资源引用、编译或 URL 测试回归。真机打开当前组菜单并运行 URL 测试与速度测试，确认没有 TCP/ICMP Ping 入口且两个保留入口均可用；回传 run、提交 SHA、菜单截图和两类测试结果。

## 5. 全量 i18n、规范与最终一致性

- [ ] 5.1 将 `connection_test_url_test` 改为可翻译的 “URL test this group”，增加可翻译的 “Speed test this group”，并为每个现有 `values-*` 目录补齐两项；简中使用“URL 测试本组”/“速度测试本组”，繁中提供对应准确翻译，其他语言至少提供可审核的本地化或英文回退。
- [ ] 5.2 将所有语言的 `connection_test_url` 更新为“延迟测试 URL”等义标题，补齐新增速度模式、超时、简单下载、进度、错误、取消与流量提示文本；移除 `translatable=false` 和旧 Ping 文案。
- [ ] 5.3 增加资源静态校验，枚举所有 `values-*` 检查新键齐全、无重复/不可翻译声明、无废弃 Ping 键，并检查 XML/Kotlin/Java/Go/文档不再引用旧默认 URL、并发 5 或旧菜单术语。
- [ ] 5.4 将本变更 delta 同步到 `openspec/specs/android-application/spec.md` 与 `openspec/specs/libcore-integration/spec.md`，更新引用连接/速度测试默认值和入口的 README、调研记录或其他规范文档，并记录 Throne 提交与上游依赖版本。
- [ ] 5.5 提交最终批次，运行 `openspec validate align-connection-and-speed-tests --strict` 以及 GitHub Actions 全量 Android/libcore unit、lint、build job；预期严格规范校验、全语言资源检查及构建全部通过。真机切换英文、简中和一种繁中语言确认菜单、设置、进度和错误文案，无截断/英文硬编码；回传最终 run、提交 SHA、校验输出、三语言截图和最终测速录屏作为完成证据。
