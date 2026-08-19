## 1. 官方 sing-box 补丁叠加基础设施

- [ ] 1.1 建立按官方基线组织的 `patches/sing-box` series 目录与来源说明，记录 Throne 参考 commit、许可证归属、应用顺序和禁止把参考仓库作为构建输入的约束。
- [ ] 1.2 从用户提供的 `C:\repos\sing-box-throne` 提取 auto-selector 最小差异，移植类型常量、options、registry 接线和 `protocol/group/autoselector*.go` 到 v1.13.16 可重放补丁。
- [ ] 1.3 移植并适配 auto-selector 健康采样、分层调度、watch、warm restore、断网暂停、排名迟滞、拨号重试、连接中断、固定偏好和两种均衡模式的 Go 单元测试。
- [ ] 1.4 修改官方源码获取脚本，在验证 `SINGBOX_VERSION` 对应干净 tag 后按 series 执行补丁预检和确定性应用，任何漂移、部分应用或残留修改均失败。
- [ ] 1.5 更新 libcore CI 缓存键，使其同时包含 `SINGBOX_VERSION` 和补丁序列/内容 hash，并在 CI 中执行补丁应用检查及 auto-selector Go 测试。
- [ ] 1.6 增加仓库静态校验，验证 series 完整性、补丁路径和基线声明，并拒绝 Go 依赖或构建脚本中出现 Throneproj/starifly 等 fork URL。

## 2. 分组策略数据模型与迁移

- [ ] 2.1 新增 SINGLE、MANUAL、AUTO 可判别分组策略及带版本的 `AutoSelectorConfig` 默认模型，覆盖候选、健康、切换、均衡、endpoint、固定成员、排名池和紧凑 warm 状态字段。
- [ ] 2.2 升级 Room 数据库并编写 migration，将现有 `isSelector=true` 无损映射为 MANUAL、其他分组映射为 SINGLE，确保没有分组被自动启用 AUTO。
- [ ] 2.3 更新 `ProxyGroup` 序列化、应用备份/恢复、订阅分享/导入和 Throne 桌面备份导入，对新字段执行版本兼容和安全默认处理。
- [ ] 2.4 为旧库迁移、设置默认值、序列化往返、未知版本降级和既有 manual selector 保留编写 JVM/Room 测试。

## 3. 自动候选规划与启动前排名

- [ ] 3.1 实现纯 Kotlin `AutoSelectorPlanner` 及结果模型，输出候选、按原因跳过统计、排名池、构建集、补测需求、错误和运行快照摘要。
- [ ] 3.2 实现名称正则、国家代码、元类型、完整配置、代理链/外部实现及构建兼容性资格检查；非法正则与空候选必须返回可展示错误。
- [ ] 3.3 为 URL 测试结果补充或复用可靠时间戳，实现可信窗口、新鲜失败排除、成功/未测/失败排序、持久排名先验和 poolCap/buildLimit 归一化。
- [ ] 3.4 接入现有批量 URL 测试流程：仅在候选截断需要且入选边界仍有未测/陈旧成员时阻塞启动补测，完成后重新排名并持久化池。
- [ ] 3.5 编写 planner 单元测试，覆盖新成员加入、过滤、过期结果、旧失败恢复、无需重复测试、截断、稳定排序、无候选及 pinned 成员失效。

## 4. 配置生成与运行快照

- [ ] 4.1 重构 `ConfigBuilder` 的分组成员链构建，使 MANUAL 与 AUTO 复用成员链和 ID/tag 映射，同时保持 SINGLE、测速和导出行为不变。
- [ ] 4.2 为 AUTO 发射 tag 为 `proxy` 的 `auto-selector` JSON，完整映射有序成员、探测周期、watch、采样、阈值、重试、连接中断、均衡、pinned 和有效 warm entries。
- [ ] 4.3 扩展 `ConfigBuildResult`/`ProxyInstance` 保存 auto-selector 策略、成员 ID/tag、配置指纹、排名池与构建时间，作为状态解析和快照失效判断的唯一映射。
- [ ] 4.4 阻止 auto-selector 作为链成员或额外路由目标，逐成员处理可诊断构建失败，并在最终无成员时拒绝启动。
- [ ] 4.5 增加配置 fixture/单元测试，校验 manual/auto 互斥、字段边界、稳定 tag、完整代理链、warm/pinned 映射及 forTest/forExport 不扩组。

## 5. libcore 状态与控制接口

- [ ] 5.1 在 `BoxInstance` 启动时发现当前 `AutoSelectorGroup`，并确保关闭 box 后引用、轮询和控制安全失效。
- [ ] 5.2 定义版本化、gomobile 友好的自动选择器状态 JSON，覆盖组阶段、当前/固定成员、健康计数、耗尽时间、切换原因和成员健康明细。
- [ ] 5.3 暴露幂等状态查询、立即复检、设置 pinned 和清除 pinned 方法，以稳定错误码区分未运行、组不存在、成员不存在和内部错误。
- [ ] 5.4 扩展 Android 平台回调，在内核实际选择变化时传递 auto-selector tag、成员 tag 与原因，同时不改变 manual selector 回调语义。
- [ ] 5.5 增加 libcore 单元/集成测试，覆盖重复状态读取不清零、无效控制无副作用、固定失败回退恢复、选择回调及关闭竞态。

## 6. Android 服务监视、重建与状态同步

- [ ] 6.1 实现服务级 `AutoSelectorMonitor`，仅在 AUTO 运行实例存在时串行轮询状态，并按前后台状态调节频率、解析 tag→实体映射和发布内存状态。
- [ ] 6.2 接入服务通知、Binder/主界面状态和 `TrafficLooper`，在自动切换时更新当前显示并对同一统计批次中的成员/链映射按实体 ID 去重。
- [ ] 6.3 节流持久化 pinned、排名与紧凑 warm health；确保状态轮询不产生高频 Room 写入，connection balance 的成员流量按近似语义标记。
- [ ] 6.4 在订阅更新中识别删除、替换或配置改变的当前构建成员，使用运行快照指纹触发单飞完整重建；仅新增后备成员不得无条件打断当前连接。
- [ ] 6.5 实现池耗尽保护窗口、Android 网络门控、重建冷却和下一批后备排名选择，避免断网误重建及无限快速重启。
- [ ] 6.6 增加服务与 monitor 测试，覆盖正常切换、飞行模式暂停、恢复、订阅替换、连续耗尽、冷却、防并发重建和服务关闭竞态。

## 7. 分组设置与运行状态 UI

- [ ] 7.1 将分组设置的 selector 开关升级为 SINGLE/MANUAL/AUTO 模式选择，按模式显示相应说明并确保切换保存时互斥。
- [ ] 7.2 实现 auto 基础和高级设置控件，覆盖名称/国家过滤、池/构建/可信窗口、健康周期、采样、阈值、重试、中断、均衡和测试端点，并执行交叉字段归一化。
- [ ] 7.3 在设置页实时展示 planner 摘要，包括分组总数、合格数、将运行数、跳过原因和是否需要启动前补测。
- [ ] 7.4 实现自动选择器运行详情 Activity/Dialog 和成员列表，展示阶段、当前/固定成员、健康统计、切换原因、延迟、抖动、样本和错误状态。
- [ ] 7.5 接入立即复检、固定所选成员与恢复自动操作；只有 libcore 返回成功后才更新持久偏好，失败时显示明确原因。
- [ ] 7.6 补充默认与简体中文字符串、无障碍标签、深色主题和窄屏布局，并为模式可见性、字段校验和控制结果增加 UI 测试。

## 8. 规范、静态校验与交付验证

- [ ] 8.1 将本 change 的 delta 同步到 `auto-selector`、`proxy-group-selection`、`libcore-integration` 和 `repository-governance` 主规范，并更新与用户可见行为相关的 README/帮助说明。
- [ ] 8.2 运行 `openspec validate add-auto-selector --strict`、仓库治理检查、补丁 series 静态检查及所有不依赖 Go/Android SDK 的本地测试，修复全部失败。
- [ ] 8.3 通过 GitHub Actions 验证补丁可应用、Go 单测、gomobile AAR、Room schema、Android lint/test 和全量 APK 构建，并保存失败日志作为修复依据。
- [ ] 8.4 在真机验证 SINGLE 与 MANUAL 无回归，以及 AUTO 的冷启动排名、即时故障转移、固定失败/恢复、飞行模式、订阅替换、rotate/connection 和服务重启 warm restore。
- [ ] 8.5 使用接近 buildLimit 的大组执行启动时间、内存、探测流量、电量与状态 UI 压力测试，记录可接受基线并据结果调整默认值而不突破规范边界。
- [ ] 8.6 完成发布前迁移/回滚演练，确认旧用户不被自动启用 AUTO、备份可恢复、禁用 AUTO 后可安全移除补丁生成路径，并归档 OpenSpec change。
