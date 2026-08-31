## 1. 修复共享链成员的最终标签解析

- [x] 1.1 在链边写入前解析并复用 `globalOutbounds` 中共享成员的最终标签，确保已先生成的成员不会留下 `g-<id>` 等悬空 detour，同时保持主链标签、`profileTagMap`、`trafficMap` 和可读名称语义不变。
- [x] 1.2 增加 Android JVM 回归测试，覆盖“普通主节点 + 独立规则引用共享节点 + 另一规则引用包含该节点的链”以及“链本身为主节点”两种构建顺序，并断言最终 endpoint/outbound、detour、selector 与 route final 引用全部可解析。
- [x] 1.3 将“链式配置引用必须解析到最终出站标签”同步到主 `android-application` 规范，运行不依赖 Android SDK/Go 的 OpenSpec 严格校验与仓库静态检查，并提交仅包含批次 1 的可审查改动。

## 2. 批次 1 CI 与真机反馈门

- [x] 2.1 触发仓库适用的 Android CI workflow/unit-test job，要求新增配置构建测试及既有 `ConfigBuilderWireGuardTest` 等相关 JVM 测试通过；回传 workflow run URL、job 名称和失败时的完整测试名/堆栈。在结果确认前不得开始批次 3。
- [x] 2.2 在真机导入原复现数据，选择普通节点 2913，并保持路由规则同时引用共享节点 3045 与链 3046；预期 `ChainTopologyTrace unresolved=[]`、生成配置中链 detour 指向实际存在标签、服务成功连接。回传相关拓扑日志、启动成功日志及无 CrashHandler 的证据。
- [x] 2.3 以链 3046 作为主节点再验证一次正常连接与 URL Test，确认批次 1 没有回归原本可用的链优先构建路径；回传 `ChainTopologyTrace`、`BoxLifecycleTrace stage=start success` 和测试结果。

## 3. 修复启动失败后的幂等清理

- [x] 3.1 在 libcore 中明确 box 的启动失败/关闭状态转换，使底层启动回滚后的首次清理与后续重复关闭保持幂等；仅归一化可识别的已关闭结果，未知关闭错误继续进入受控错误边界并保留生命周期日志。
- [x] 3.2 在 Android 服务失败清理路径建立最终错误边界，确保原始启动错误用于服务失败提示，清理异常被单独记录且资源引用、通知、receiver 和服务状态仍收敛到停止，不再逃逸至默认崩溃处理器。
- [x] 3.3 增加 Go 生命周期测试与适用的 Android/JVM 契约测试，覆盖部分启动失败后关闭、重复关闭、未知关闭错误可诊断，以及原始启动错误不被 `file already closed` 覆盖。
- [x] 3.4 将失败清理与幂等关闭要求分别同步到主 `android-application`、`libcore-integration` 规范，运行不依赖本地 Android SDK/Go 的 OpenSpec 严格校验与静态检查，并提交仅包含批次 3 的可审查改动。

## 4. 批次 2 CI 与真机反馈门

- [x] 4.1 触发适用的 libcore 构建/Go test job 和 Android unit-test/build job；预期 box 生命周期测试、Android 清理契约测试和完整构建通过。回传 workflow run URL、job 名称、目标 `SINGBOX_VERSION` 以及失败时的完整测试/构建日志。
- [x] 4.2 真机使用可控的无效 outbound 依赖或测试配置触发 sing-box 部分启动失败；预期用户收到原始依赖错误，`ServiceStopTrace` 收敛到 stopped，Go/Android close 日志显示安全清理，且不存在 `CrashHandler`、Phoenix 重生或 `:bg` Fatal。回传从 Start failed 到 stopped 的连续日志。
- [x] 4.3 真机正常启动并停止有效的普通节点与链式节点各一次，再快速重复停止/启动；预期首次关闭成功、重复请求被幂等处理、后续仍可连接。回传对应实例 ID 的完整 `ServiceLifecycleTrace`、`VpnLifecycleTrace` 和 `BoxLifecycleTrace`。

