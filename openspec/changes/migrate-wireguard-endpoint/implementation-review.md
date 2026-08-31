# WireGuard endpoint 迁移实现审查

## 审查范围

本说明对应任务 5.3，汇总最终 CI、真机验收和实现偏差。敏感密钥、完整 peer 标识及完整生成配置不进入本文。

## 最终实现结论

- WireGuard 节点生成顶层官方 endpoint，不再由产品配置生成器触发 legacy WireGuard outbound。
- endpoint 保留主路由、selector/urltest 与受支持链式拓扑的 tag 引用语义。
- 无已有链式 detour 且无正数监听端口的 WireGuard endpoint 绕行到非空 direct outbound；direct 使用 `network_strategy = "default"`，已有链式 detour 与节点 MTU 保持不变。
- endpoint 分区后，空的 `route.final` 显式设为主代理 tag，避免主流量隐式回退 direct；用户已有的非空 final 不被覆盖。

## CI 证据

- 最终 Preview Build：https://github.com/throneproj/ThroneForAndroid/actions/runs/32473153126
- `Native Build (LibCore)`：成功，job https://github.com/throneproj/ThroneForAndroid/actions/runs/32473153126/job/96746216144
- `Build OSS APK`：成功，job https://github.com/throneproj/ThroneForAndroid/actions/runs/32473153126/job/96746214940
- 最终 run 对应提交 `e4ea5ba87923305944d194170e2b3a3a3c1ad2fe`，包含 detour 修复、主路由 final 修复及其回归测试。

## 真机证据摘要

- 用户确认中途报告所述数据面故障已由 detour 方案解决；最终验收已覆盖单节点以及 selector/urltest 或链式场景。
- 验收结果包括 WireGuard 可连接、IPv4 与 DNS 可用、浏览器 HTTPS/较大响应恢复，且不再以中途的 `sendmsg: message too long` 作为未解决结论。
- Wi-Fi/移动网络切换后的重连验收已完成，任务 5.2 已据实际结果勾选。
- 仓库未保留设备型号、Android/API 版本、握手时间戳、切网时间线或最终脱敏日志文件；本文不补造这些字段。

## IPv6 记录

endpoint 生成的 `allowed_ips` 始终包含 IPv4 与 IPv6 全流量前缀。最终验收材料未保留测试服务与设备是否同时具备 IPv6，因此无法有依据地声明 IPv6 成功，也无法将其断言为环境性跳过；本次审查明确记录为“IPv6 真机证据未留存”。

## 中途报告边界

`ROO_WG_REPORT.md` 是修复前的问题收敛与实验方案，不代表最终未决状态。最终设计以“detour 到带 `network_strategy = default` 的非空 direct 已恢复 Android 数据面”为准；未引入 MTU 1280，也未把 GSO 当作已确认根因。
