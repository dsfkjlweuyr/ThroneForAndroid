## Context

参见 `proposal.md` 的动机。T4A 当前把 `ProxyGroup.isSelector` 解释为手动 selector：配置生成器把整组节点构建为官方 sing-box `selector`，服务可原地调用 `SelectOutbound`。节点 URL 测试结果保存在 `ProxyEntity`，但没有自动候选规划、持续健康决策或后备池。

桌面 Throne 的客户端层负责从分组解析、过滤和预排名候选，只把前 N 个节点发给 `auto-selector` outbound；其 sing-box 补丁负责运行时分层探测、健康评分、故障转移、固定偏好与负载均衡。参考补丁位于用户明确提供的 `C:\repos\sing-box-throne`，目标实现文件主要是 `constant/proxy.go`、`option/group.go`、`protocol/group/autoselector*.go` 与 registry 接线。

T4A 的约束是继续以 `SagerNet/sing-box` 官方 `SINGBOX_VERSION=v1.13.16` 为基线，不将 Throne fork 设为 Go 依赖。项目本地不执行 Go/Android 编译，因此补丁应用、Go 单测、AAR 和 APK 验证由 CI 完成。

## Goals / Non-Goals

**Goals:**

- 复制 Throne auto-selector 的用户可见策略和内核算法，而不是用官方 `urltest` 做功能缩水的近似替代。
- 保持官方 sing-box 为可验证基线，并把差异固化为 T4A 仓库内的最小补丁叠加层。
- 复用 T4A 现有分组、URL 测试、代理链、通知、Binder 和流量统计架构，避免引入第二套配置数据库。
- 对大订阅组设置明确池上限和构建上限，避免把全部节点常驻进一个 Android box。
- 保证 manual selector 原行为与既有数据库用户的无损迁移。

**Non-Goals:**

- 不把 `C:\repos\Throne` 或 `C:\repos\sing-box-throne` 纳入构建输入、submodule 或远程依赖。
- 不支持把 auto-selector 作为链节点或嵌套在另一个 selector 中。
- 首版不承诺多组 auto-selector 同时运行；T4A 正式服务仍只有当前主配置的一个 `proxy` 自动组。
- 不实现跨设备同步自动选择器健康历史，也不把运行采样写入 Room 的逐条历史表。
- 不修复 manual selector 已记录但与本功能无关的所有快照校验缺口；只避免新模式复用错误控制语义。

## Decisions

### 1. 使用仓库内补丁叠加官方源码

新增 `patches/sing-box/<baseline>/series` 和按顺序编号的补丁文件。源码获取脚本先强制检出并验证官方 tag，再执行 `git apply --check` 与 `git am`/`git apply` 的确定性流程。补丁内容从参考 fork移植并按官方 v1.13.16 API 调整，提交后不再读取参考路径。

选择原因：这满足用户要求的“官方上游 + 自有叠加层”，也保留升级时清晰的冲突边界。直接将 `go.mod` replace 到 Throneproj fork 会违反单一官方基线；在 libcore 外部重新实现健康算法则无法复用 outbound 的拨号失败信号、连接中断器与 URLTestGroup 生命周期。

CI 缓存键加入 `SINGBOX_VERSION` 与补丁目录 hash。增加静态脚本验证 series 文件、补丁存在性、禁止 fork URL，并在 CI 中执行补丁应用及 auto-selector Go 测试。

### 2. 分组策略使用枚举，详细设置使用独立可序列化配置

将分组选择方式建模为 `ProxyGroup.selectionMode`：`SINGLE=0`、`MANUAL=1`、`AUTO=2`。数据库迁移将现有 `isSelector=true` 映射为 `MANUAL`，其他映射为 `SINGLE`；保留或删除旧列取决于 Room 迁移最小风险，但业务读取以枚举为准。

auto 参数放入 `AutoSelectorConfig`，作为 `ProxyGroup` 的 nullable blob/转换字段或平铺列持久化。倾向 blob 是参数多且仍可能跟随内核演进；它必须有显式序列化版本与默认值，备份/恢复使用同一模型。固定成员保存稳定的 `ProxyEntity.id`，构建时映射为运行 tag。

不继续叠加 `isAutoSelector` 布尔值，因为两个布尔值能形成无效的双开状态；也不创建虚拟 `ProxyEntity`，因为 Android 当前设置、启动与订阅生命周期天然以分组为入口，虚拟节点会污染订阅成员和节点列表。

### 3. Android 规划器负责候选资格、初始排名和运行快照

新增纯 Kotlin `AutoSelectorPlanner`，输入分组、设置、当前节点实体和时间，输出：合格候选、按原因跳过计数、完整排名池、实际构建成员、是否需要补测、错误及用于失效检测的快照摘要。

资格规则复用 `buildChain` 可支持范围，提前排除 chain、auto 元类型、不可嵌入的 full config 和会导致整个配置失败的成员。名称过滤使用忽略大小写的 Kotlin Regex；国家过滤使用已有 IP 测试国家字段。URL 测试新鲜度依赖每个节点的结果时间；如果现有模型缺少可靠时间戳，则添加持久字段并在迁移时把旧结果视为过期。

排名为：新鲜成功（延迟升序）→ 未测试/过期 → 新鲜失败。已持久化池中仍合格的成员保持先验顺序，新成员按有效延迟追加；执行新一轮补测后则纯按新结果重排。只有合格数超过 buildLimit 且 build 集合仍含未测项时阻塞启动执行批量 URL 测试。

运行快照记录成员 ID/tag 与配置指纹。订阅更新返回的新增、删除和替换集合与快照相交时触发完整重建；仅新增节点不立即打断当前连接，留到正常重启或池耗尽时纳入，除非当前实现能低成本辨认它应进入 buildLimit 前列。

### 4. 配置生成器发射一个 `proxy` auto-selector 与成员完整链

重构当前 selector 分支为共享的“构建分组成员链”路径。MANUAL 发射官方 `selector`；AUTO 使用 planner 的 build 列表并发射补丁新增的 `auto-selector`。路由仍指向 `proxy`，每个成员保留稳定、唯一且可逆映射的 tag，`ConfigBuildResult` 扩展 auto-selector snapshot 和 tag→ID 映射。

AUTO JSON 包括 ordered outbounds、URL、connectivity URL、interval、bench interval、watch interval、active size、sampling、tolerance、max RTT、expected、dial retries、interrupt、balance 配置、pinned tag 和 warm entries。warm entry 来源为最近一次运行状态持久化的紧凑健康快照，超过有效期不发射。

正式配置才启用 auto-selector；`forTest` 与 `forExport` 保持单节点行为。额外路由节点不允许引用 auto 组，避免生命周期与流量归属歧义。

### 5. 内核补丁按 Throne 算法完整移植，但缩小对外表面

移植以下核心：类型/options/registry、member health 环形采样、active/bench/watch 调度、warm restore、connectivity 判定、暂停/恢复、排名与迟滞、TCP/UDP 选择、拨号内联重试、interrupt、balance rotate/connection、状态快照和 `SelectOutbound` 固定语义。

保留上游补丁中的核心单元测试，针对 v1.13.16 API 调整。Android 不需要桌面 RPC server，因此不移植 Throne 的 protobuf/RPC 层；libcore 直接在当前 `BoxInstance` 中发现 `group.AutoSelectorGroup` 并以 gomobile 友好的 JSON 或简单 DTO 暴露。

选择 JSON 状态快照而非大量 gomobile 嵌套集合类型：当前 Go mobile 边界对复杂泛型/切片对象支持有限，版本化 JSON 可降低 JNI 接口数量。控制方法返回结构化 JSON/错误码，避免仅用 bool 丢失原因。

### 6. 单一后台 monitor 串行轮询并负责持久化、耗尽防抖和状态分发

主服务在 auto 模式启动后创建 `AutoSelectorMonitor`，按固定低频（例如 1 秒 UI 可见、后台 3–5 秒）查询幂等状态。monitor 将 tag 解析为实体 ID，更新内存 StateFlow/Binder 回调、通知标题和 `TrafficLooper` 当前归属，并批量、节流地写入紧凑 warm health 与 pinned ID。

内核状态中的“所有成员失效”必须持续达到保护窗口（建议 15 秒）且 Android 确认存在网络，才发送一次重建请求；重建有冷却（建议 60 秒）和单飞锁。monitor 本身不直接递归重启服务，而是投递到现有 service lifecycle，防止线程与 box 交叉关闭。

手动固定由状态页发控制请求；成功后再持久化 pinned ID。清除偏好发送空 member。应用重启时配置将 pinned tag重新注入内核。

### 7. 状态 UI 采用分组设置 + 运行时详情两层

分组设置页展示模式选择。AUTO 基础项为名称过滤、是否均衡；高级页/对话框承载成员规模、可信窗口、健康、切换、均衡和 endpoint 参数，并实时显示 planner 摘要（总数、合格、将构建、跳过原因、是否需补测）。

运行时详情使用 Activity/Dialog + RecyclerView，展示 headline、当前/固定成员、工作数、阶段、切换原因和可排序成员表；提供“立即复检”“固定此成员”“恢复自动”。服务未运行时入口隐藏或显示最近快照但禁用控制。所有文案进入默认与 `zh-rCN` resources。

### 8. 流量统计以运行实际为准并避免重复计数

成员链继续产生自身 tag 统计。auto 组的总量按当前实际成员映射到 auto 分组/启动节点，同时成员实体可保留自身统计；同一实体因链或 group 映射出现多次时先按 ID 去重。rotate 模式可精确按当前 tag 切换；connection 模式无法仅凭 selector 当前值精确拆分每条连接，因此 UI 标记成员分摊为近似，但组总量必须准确。

## Risks / Trade-offs

- **[补丁与官方升级冲突]** → 补丁绑定基线、CI 先 `--check`、保留上游单测；升级必须显式 rebase，而不是模糊应用。
- **[Android 同时维护候选排名、内核维护实时排名，出现双重真相]** → Android 排名只决定装入哪些成员及冷启动先验；运行期间选择与健康以内核状态为唯一真相。
- **[大组启动测速耗时和耗电]** → 复用新鲜结果、仅在 buildLimit 截断需要时补测、限制并发、池和 build 上限，运行时采用 active/bench 分层。
- **[坏成员导致整个 config 构建失败]** → planner 先执行资格检查；构建成员逐个捕获可诊断失败并按明确策略跳过，若结果为空则整体失败而非生成半空配置。
- **[Room blob 设置难以 SQL 查询]** → auto 参数不参与列表查询，blob 降低 schema 膨胀；模式枚举保留为独立列供筛选与迁移。
- **[状态轮询耗电]** → 查询仅内存快照且频率随前后台降低；无 auto 运行实例时完全停止。
- **[connection balance 的成员流量不精确]** → 组总量作为可靠指标，成员值标注近似；默认关闭均衡，默认采用单一 sticky 选择。
- **[本地断网误判为全部节点死亡]** → 内核结合平台网络状态、错误分类和可选直连 endpoint，Android 重建前再做网络门控和持续窗口。
- **[补丁复制引入许可证或归属遗漏]** → 保留 sing-box 与 Throne 补丁文件头/许可证要求，在 AUTHORS 或补丁说明记录来源 commit；不复制桌面 UI/RPC 无关代码。

## Migration Plan

1. 先提交补丁基础设施、补丁 series、静态检查和 Go 单测，不改变 Android 默认配置。
2. 增加 Room migration：现有 `isSelector=true` 映射 MANUAL，其余 SINGLE；新增 auto 配置默认值但不自动启用。
3. 增加 planner、配置 JSON 与 libcore 状态/控制 API，通过 fixture 和 Kotlin 单测验证候选、排名、映射与配置字段。
4. 接入服务 monitor、订阅变更重建、通知/统计和运行 UI；功能仍只对显式 AUTO 分组生效。
5. CI 构建 patched libcore 与 APK；真机依次验证冷启动、热切换、飞行模式、节点故障、订阅更新、固定恢复、rotate/connection 和 300 成员压力。
6. 验证稳定后同步主规范并发布。回滚时可将受影响分组模式迁回 SINGLE/MANUAL；旧 APK 忽略或无法识别新列时必须通过兼容 migration/导出版本防止降级崩溃。构建级回滚可移除补丁应用与 auto 注册，但须先禁止 AUTO 配置生成。

