# Throne `.thrbackup` 文件生成机制调研报告

> 调研对象：[throneproj/Throne](https://github.com/throneproj/Throne)（原 Nekoray，Qt 桌面端 sing-box GUI）
> 源码来源：本地克隆 `C:\repos\Throne`（dev 分支）
> 样本验证：`C:\Users\Tony\Downloads\Throne-backup.thrbackup`（Windows Throne 实际导出）

---

## 1. 结论摘要（TL;DR）

`.thrbackup **不是** zip/tar 压缩包，也**没有加密**。
它是 Qt [`QDataStream`](https://doc.qt.io/qt-6/qdatastream.html)（LittleEndian、Qt_6_0 版本）直接序列化出的**自定义二进制归档**，结构为：

```
┌────────────────────────────────────────────────────────────┐
│ magic          : 4 字节 ASCII "THRN"                        │
│ format_version : quint32 小端（当前 = 2）                    │
│ metadata       : QString（quint32 字节长度 + UTF-16LE 内容） │
│                  内容为紧凑 JSON（backup_version/created_at/ │
│                  platform/parts）                            │
│ files          : QMap<QString, QByteArray>                  │
│                  （quint32 条目数 + 若干 [key QString,       │
│                   value QByteArray] 对）                     │
└────────────────────────────────────────────────────────────┘
```

`files` 映射中的内容：

| key | 内容 | 说明 |
|---|---|---|
| `database` | **完整的 SQLite 数据库文件**（magic 为 `SQLite format 3\0`） | 通过 SQLite Online Backup API 生成的全量快照，再按用户勾选**删除未选中的表数据**并 `VACUUM` |
| `icons/<文件名>` | 自定义分组图标的原始字节 | 来自程序运行目录下的 `icons/` 文件夹，逐文件读入 |

---

## 2. 生成入口与流程

入口：基本设置对话框的 **Create Backup** 按钮
→ [`DialogBasicSettings::on_backup_create_clicked()`](../Throne/src/ui/setting/dialog_basic_settings.cpp:540)
（`C:\repos\Throne\src\ui\setting\dialog_basic_settings.cpp` 第 540–641 行）

### 2.1 逐步流程

1. **收集用户勾选**（UI 上 4 个复选框）→ 构造 [`Configs::BackupParts`](../Throne/include/database/Database.h:34)：
   - `profiles` → `profiles`、`groups_order`、`groups` 三张表
   - `routes` → `route_profiles`、`route_rules` 两张表
   - `settings` → `settings` 表
   - `icons` → `icons/` 文件夹（UI 层处理，与数据库无关）
   - 一个都没选则弹警告直接返回（[`any()`](../Throne/include/database/Database.h:41) 校验）。

2. **落盘内存中的设置**：若勾选了 settings，先调用 `Configs::dataManager->settingsRepo->Save()`，保证快照包含尚未写入数据库的最新设置（dialog_basic_settings.cpp:554）。

3. **弹出保存对话框**：默认路径 `QDir::homePath() + "/Throne-backup.thrbackup"`，过滤器 `Throne Backup (*.thrbackup)`（dialog_basic_settings.cpp:556-561）。

4. **生成选择性数据库快照**（只要勾选了 profiles/routes/settings 任意一项）：
   - 临时文件：`QDir::temp()/Thr_backup_tmp.db`
   - 调用 [`Database::backupSelective()`](../Throne/src/database/Database.cpp:229)：
     - 先用 **SQLite Online Backup API**（`SQLite::Backup::executeStep(-1)`）做一份 **WAL 安全的全量快照**（与 [`backupTo()`](../Throne/src/database/Database.cpp:173) 同机制）；
     - 然后对**未被勾选的类别**，把对应表 `DELETE FROM` 清空（表结构保留，数据清空）；
     - 最后 `VACUUM` 收缩文件；
     - `entity_ids`（ID 计数器表）**始终保留**，保证恢复后 ID 不冲突。
   - 快照整体读入内存，作为 `files["database"]`。

5. **收集图标**：若勾选 icons，遍历运行目录 `icons/` 下所有文件，以 `icons/<文件名>` 为 key 读入 `files`。

6. **序列化写出**（dialog_basic_settings.cpp:602-630）：
   ```cpp
   QDataStream stream(&outFile);
   stream.setByteOrder(QDataStream::LittleEndian);
   stream.setVersion(QDataStream::Qt_6_0);

   stream.writeRawData("THRN", 4);        // magic
   stream << BACKUP_FORMAT_VERSION;       // quint32 = 2
   stream << metaJson QString;            // JSON 元数据
   stream << files;                       // QMap<QString,QByteArray>
   ```

### 2.2 元数据 JSON 字段

```json
{
  "backup_version": 2,                          // BACKUP_CONTENT_VERSION，内容语义版本
  "created_at": "Sat Aug 8 15:31:28 2026",      // QDateTime::toString(Qt::TextDate)
  "platform": "winnt",                          // QSysInfo::kernelType()
  "parts": {                                    // 本归档实际包含的部分
    "profiles": true,
    "routes": true,
    "settings": true,
    "icons": true
  }
}
```

常量定义（dialog_basic_settings.cpp:466-475）：

| 常量 | 值 | 含义 |
|---|---|---|
| `BACKUP_FORMAT_VERSION` | 2 | 归档**结构**版本，破坏性格式变更时递增 |
| `BACKUP_CONTENT_VERSION` | 2 | 归档**内容**版本，写入 meta 的 `backup_version` |

版本历史：
- **v1**：全量数据库快照 + 图标，无 `parts` 元数据；恢复时视为"所有部分都存在"。
- **v2**：选择性快照（只保留勾选类别的数据）+ `parts` 元数据。

---

## 3. 恢复（Restore Backup）流程简述

[`DialogBasicSettings::on_backup_restore_clicked()`](../Throne/src/ui/setting/dialog_basic_settings.cpp:643)（第 643 行起）：

1. 读取并校验 magic `THRN`，否则报 "Not a valid Throne backup file"；
2. 校验 `format_version ∈ [1, BACKUP_FORMAT_VERSION]`，过新则拒绝；
3. 解析 meta JSON 与 `files` 映射；
4. [`BackupPartsFromMeta()`](../Throne/src/ui/setting/dialog_basic_settings.cpp:481) 判定可用部分：
   - v2 且有 `parts`：以 meta 为准（且 database/icons 确实存在于 files 中）；
   - v1 兼容路径：profiles/routes/settings 全部视为可用，icons 以是否存在 `icons/` 条目为准；
5. 弹对话框让用户在**可用部分中再勾选**要恢复的部分；
6. 数据库部分走 [`Database::restoreSelective()`](../Throne/src/database/Database.cpp:253)：
   - `ATTACH DATABASE` 挂入备份库；
   - 关外键 → 事务 → 对每张表 `DELETE FROM main.x` + `INSERT INTO main.x SELECT FROM bak.x`（只拷贝两库共有列，具备**跨 schema 版本的列级兼容**）；
   - 修正 `entity_ids` 计数器（取当前、备份数据、备份计数器三者最大值），防止新建条目 ID 冲突；
   - 提交、恢复外键、`DETACH`、WAL checkpoint；
7. 图标写回 `icons/` 目录；完成后**重启 Throne** 生效。

> 已知限制（官方 issue 确认）：**系统代理（System Proxy）和 Tun 模式的开关状态不参与备份**（[throneproj/Throne#1450](https://github.com/throneproj/Throne/issues/1450) 维护者原话："System proxy and Tun mode are not currently configured to be backed up."）。

---

## 4. 实际样本验证

用 Python 按上述布局解析用户真实备份文件
（解析脚本：[`tools/diagnostics/roo_parse_thrbackup.py`](tools/diagnostics/roo_parse_thrbackup.py)，`uv run tools/diagnostics/roo_parse_thrbackup.py <文件>`）：

```
file size: 102734 bytes
magic: b'THRN'                        ✔
format_version: 2                     ✔ v2 归档
metadata QString (147 chars):
{
  "backup_version": 2,
  "created_at": "Sat Aug 8 15:31:28 2026",
  "parts": { "icons": true, "profiles": true, "routes": true, "settings": true },
  "platform": "winnt"
}
files map count: 1
  key='database'  size=102400  head=b'SQLite format 3\x00'  sqlite=True ✔
consumed 102734/102734 bytes; trailing=0   ✔ 无残留字节，布局完全吻合
```

进一步解开内嵌 SQLite 数据库，表结构与源码常量
[`kProfileTables` / `kRouteTables` / `kSettingsTables`](../Throne/src/database/Database.cpp:189) 完全一致：

```
tables: ['entity_ids', 'profiles', 'groups', 'groups_order',
         'route_profiles', 'route_rules', 'settings']
settings rows: 151   profiles rows: 92   groups rows: 2   route_profiles rows: 1
```

> 注：该样本 meta 中 `icons: true`，但归档里没有 `icons/` 条目——因为用户的 `icons/` 目录为空（UI 未设置自定义图标）。恢复端通过 `hasIcons`（实际扫描 files 里的 `icons/` 前缀条目）兜底，行为正确。

---

## 5. QDataStream 序列化细节（自行解析/生成时的关键）

| Qt 类型 | 二进制布局（本归档固定 LittleEndian） |
|---|---|
| `QString` | `quint32` 字节长度 N + N 字节 **UTF-16** 单元（按流字节序，即 UTF-16LE）；`0xFFFFFFFF` 表示 null |
| `QByteArray` | `quint32` 长度 N + N 原始字节；`0xFFFFFFFF` 表示 null |
| `QMap<K,V>` | `quint32` 条目数 + 依 key 排序的 (K, V) 对序列 |
| `quint32` | 4 字节小端 |

注意事项：
- `QString` 的长度字段是**字节数**（= 字符数 × 2，BMP 内），不是字符数；
- meta JSON 是 ASCII/UTF-8 安全的紧凑 JSON，但在线路上被转成 UTF-16LE 存储；
- 无压缩、无校验和、无加密——**备份文件明文包含全部节点信息（含 UUID/密码），需妥善保管**。

---

## 6. 与 Android 端备份格式的对比

本仓库（ThroneForAndroid / NB4A 系）的备份是**完全不同的格式**——纯 JSON 文件
（样本：`throne_backup_2026年8月8日 15_33_26.json`）：

```json
{
  "version": 1,
  "profiles": [ "<base64 编码的 NekoBox profile bean 二进制>", ... ],
  ...
}
```

- 桌面端 `.thrbackup`：QDataStream 二进制容器 + 内嵌 SQLite 快照；
- Android 端 `.json`：JSON 文本 + Base64 封装的逐条配置 bean；

### 6.1 测试设置映射

Android 导入桌面 `.thrbackup` 的 settings 表时，可映射 `speed_test_mode`、`speed_test_timeout_ms` 和 `simple_dl_url`。导入器仅在备份中明确存在且值有效时写入这些字段；缺失字段不会以桌面默认值覆盖 Android 已持久化的自定义设置。

连接与速度测试行为以 Throne 提交 `2e7182b9ea99947a409fee30f74df83752ab763c` 为参考：延迟 URL 默认为 `http://cp.cloudflare.com/`、URL 延迟并发为 10、速度测试模式默认为下载+上传、超时为 5000 ms、简单下载 URL 为 `http://cachefly.cachefly.net/1mb.test`。完整测速通过服务发现选择服务器，不存在需要持久化的固定下载/上传 URL。上游 `speedtest-go` 版本、许可证及 checksum 记录在 [`libcore/DEPENDENCIES.md`](libcore/DEPENDENCIES.md)。

---

## 7. 复现 / 互操作参考

用 Python 完整解析 `.thrbackup` 的最小实现见本目录
[`tools/diagnostics/roo_parse_thrbackup.py`](tools/diagnostics/roo_parse_thrbackup.py)（约 60 行，无第三方依赖；在仓库根目录通过 `uv run tools/diagnostics/roo_parse_thrbackup.py` 执行）。
若要**程序化生成**兼容备份，反向按第 1 节布局写入即可：
`"THRN"` + `<I 2` + QString(meta_json) + QMap{"database": <SQLite 文件字节>}`，
其中内嵌 SQLite 至少需含 `entity_ids / profiles / groups / groups_order / route_profiles / route_rules / settings` 七张表（未备份的类别建空表即可）。

---

## 8. 参考资料

- 源码：`C:\repos\Throne\src\ui\setting\dialog_basic_settings.cpp`（第 466–499、540–799 行）
- 源码：`C:\repos\Throne\src\database\Database.cpp`（第 173–299 行）
- 源码：`C:\repos\Throne\include\database\Database.h`（第 29–42、283–284 行）
- [throneproj/Throne#1450 — Create Backup 不保存设置（确认 System Proxy/Tun 不备份）](https://github.com/throneproj/Throne/issues/1450)
- [throneproj/Throne#1460 — Throne-backup.thrbackup 恢复问题](https://github.com/throneproj/Throne/issues/1460)
- [throneproj/Throne#1355 — Backup 功能请求（WebDAV 等）](https://github.com/throneproj/Throne/issues/1355)
- Qt 文档：[QDataStream 序列化格式](https://doc.qt.io/qt-6/datastreamformat.html)
