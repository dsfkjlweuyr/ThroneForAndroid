package io.nekohasekai.sagernet.database

import android.database.sqlite.SQLiteDatabase
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.IPv6Mode
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.SpeedTestSettings
import io.nekohasekai.sagernet.TunImplementation
import io.nekohasekai.sagernet.database.preference.PublicDatabase
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.parseSingBoxOutbound
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import moe.matsuri.nb4a.proxy.config.ConfigBean
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * 解析 Throne 电脑版 `.thrbackup`（QDataStream + 内嵌 SQLite），
 * 并尽力映射为 T4A 的分组/节点、路由规则与设置。
 *
 * 忽略自定义图标（icons/ 下文件）。
 */
object ThroneDesktopBackupImporter {

    private const val MAGIC = "THRN"
    private const val MAX_FORMAT_VERSION = 2

    // Throne RouteRule outboundID
    private const val DESKTOP_OUT_PROXY = -1
    private const val DESKTOP_OUT_DIRECT = -2
    private const val DESKTOP_OUT_BLOCK = -3
    private const val DESKTOP_OUT_WARP_BYPASS = -5

    data class ParsedBackup(
        val formatVersion: Int,
        val meta: JSONObject,
        val hasProfiles: Boolean,
        val hasRoutes: Boolean,
        val hasSettings: Boolean,
        internal val dbFile: File,
    )

    class InvalidBackupException(message: String) : Exception(message)

    fun parse(bytes: ByteArray, cacheDir: File): ParsedBackup {
        if (bytes.size < 8) throw InvalidBackupException("file too small")
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magicBytes = ByteArray(4)
        buf.get(magicBytes)
        if (String(magicBytes, Charsets.US_ASCII) != MAGIC) {
            throw InvalidBackupException("not a Throne backup (bad magic)")
        }
        val formatVersion = buf.int
        if (formatVersion < 1 || formatVersion > MAX_FORMAT_VERSION) {
            throw InvalidBackupException("unsupported thrbackup format version: $formatVersion")
        }
        val metaStr = readQString(buf)
            ?: throw InvalidBackupException("missing metadata")
        val meta = try {
            JSONObject(metaStr)
        } catch (e: Exception) {
            throw InvalidBackupException("invalid metadata JSON: ${e.message}")
        }
        val fileCount = buf.int
        if (fileCount < 0 || fileCount > 10000) {
            throw InvalidBackupException("invalid files map count: $fileCount")
        }
        var databaseBytes: ByteArray? = null
        repeat(fileCount) {
            val key = readQString(buf) ?: return@repeat
            val value = readQByteArray(buf) ?: return@repeat
            if (key == "database") databaseBytes = value
            // icons/ entries intentionally ignored
        }
        if (databaseBytes == null) {
            throw InvalidBackupException("backup has no database payload")
        }
        if (databaseBytes!!.size < 16 ||
            !databaseBytes!!.copyOfRange(0, 16).contentEquals(
                "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
            )
        ) {
            throw InvalidBackupException("database payload is not SQLite")
        }

        val parts = meta.optJSONObject("parts")
        val hasProfiles: Boolean
        val hasRoutes: Boolean
        val hasSettings: Boolean
        if (formatVersion >= 2 && parts != null) {
            hasProfiles = parts.optBoolean("profiles", false)
            hasRoutes = parts.optBoolean("routes", false)
            hasSettings = parts.optBoolean("settings", false)
        } else {
            // v1: treat all as present; refine by table counts below
            hasProfiles = true
            hasRoutes = true
            hasSettings = true
        }

        cacheDir.mkdirs()
        val dbFile = File(cacheDir, "throne_import_${System.currentTimeMillis()}.db")
        dbFile.writeBytes(databaseBytes!!)

        // Refine availability by actual row counts (selective backup may leave empty tables)
        val refined = refineAvailability(dbFile, hasProfiles, hasRoutes, hasSettings)
        return ParsedBackup(
            formatVersion = formatVersion,
            meta = meta,
            hasProfiles = refined.first,
            hasRoutes = refined.second,
            hasSettings = refined.third,
            dbFile = dbFile,
        )
    }

    fun import(
        parsed: ParsedBackup,
        importProfiles: Boolean,
        importRules: Boolean,
        importSettings: Boolean,
    ) {
        val db = SQLiteDatabase.openDatabase(
            parsed.dbFile.path,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        )
        try {
            val settingsMap = readSettings(db)
            if (importProfiles && parsed.hasProfiles) {
                importGroupsAndProfiles(db, settingsMap)
            }
            if (importRules && parsed.hasRoutes) {
                importRoutes(db, settingsMap)
            }
            if (importSettings && parsed.hasSettings) {
                applySettings(settingsMap)
            }
        } finally {
            db.close()
            parsed.dbFile.delete()
        }
    }

    // region QDataStream

    private fun readQString(buf: ByteBuffer): String? {
        if (buf.remaining() < 4) throw InvalidBackupException("truncated QString length")
        val n = buf.int
        if (n == -1) return null // 0xFFFFFFFF
        if (n < 0 || n > buf.remaining()) throw InvalidBackupException("bad QString length $n")
        if (n == 0) return ""
        val bytes = ByteArray(n)
        buf.get(bytes)
        return try {
            StandardCharsets.UTF_16LE.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            String(bytes, Charsets.UTF_16LE)
        }
    }

    private fun readQByteArray(buf: ByteBuffer): ByteArray? {
        if (buf.remaining() < 4) throw InvalidBackupException("truncated QByteArray length")
        val n = buf.int
        if (n == -1) return null
        if (n < 0 || n > buf.remaining()) throw InvalidBackupException("bad QByteArray length $n")
        val bytes = ByteArray(n)
        buf.get(bytes)
        return bytes
    }

    // endregion

    private fun refineAvailability(
        dbFile: File,
        hasProfiles: Boolean,
        hasRoutes: Boolean,
        hasSettings: Boolean,
    ): Triple<Boolean, Boolean, Boolean> {
        val db = SQLiteDatabase.openDatabase(
            dbFile.path, null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        )
        try {
            fun count(table: String): Int = try {
                db.rawQuery("SELECT COUNT(*) FROM $table", null).use { c ->
                    if (c.moveToFirst()) c.getInt(0) else 0
                }
            } catch (_: Exception) {
                0
            }
            val p = hasProfiles && (count("profiles") > 0 || count("groups") > 0)
            val r = hasRoutes && (count("route_rules") > 0 || count("route_profiles") > 0)
            val s = hasSettings && count("settings") > 0
            return Triple(p, r, s)
        } finally {
            db.close()
        }
    }

    private fun readSettings(db: SQLiteDatabase): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        try {
            db.rawQuery("SELECT key, value FROM settings", null).use { c ->
                val ki = c.getColumnIndex("key")
                val vi = c.getColumnIndex("value")
                while (c.moveToNext()) {
                    val k = c.getString(ki) ?: continue
                    map[k] = c.getString(vi) ?: ""
                }
            }
        } catch (e: Exception) {
            Logs.w(e)
        }
        return map
    }

    // region profiles

    private fun importGroupsAndProfiles(db: SQLiteDatabase, settings: Map<String, String>) {
        data class DeskGroup(
            val id: Long,
            val name: String,
            val url: String,
            val info: String,
            val subLastUpdate: Long,
            val skipAutoUpdate: Boolean,
            val frontProxyId: Long,
            val landingProxyId: Long,
            val profilesJson: String,
            val order: Long,
        )

        data class DeskProfile(
            val id: Long,
            val type: String,
            val name: String,
            val gid: Long,
            val outboundJson: String,
            val trafficUp: Long,
            val trafficDl: Long,
            val latency: Int,
        )

        val orderMap = HashMap<Long, Long>()
        try {
            db.rawQuery("SELECT group_id, display_order FROM groups_order", null).use { c ->
                val gi = c.getColumnIndex("group_id")
                val oi = c.getColumnIndex("display_order")
                while (c.moveToNext()) {
                    orderMap[c.getLong(gi)] = c.getLong(oi)
                }
            }
        } catch (_: Exception) {
        }

        val deskGroups = ArrayList<DeskGroup>()
        db.rawQuery("SELECT * FROM groups", null).use { c ->
            fun idx(n: String) = c.getColumnIndex(n)
            val iId = idx("id")
            val iName = idx("name")
            val iUrl = idx("url")
            val iInfo = idx("info")
            val iSub = idx("sub_last_update")
            val iSkip = idx("skip_auto_update")
            val iFront = idx("front_proxy_id")
            val iLand = idx("landing_proxy_id")
            val iProfiles = idx("profiles_json")
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                deskGroups.add(
                    DeskGroup(
                        id = id,
                        name = c.getString(iName) ?: "",
                        url = if (iUrl >= 0) c.getString(iUrl) ?: "" else "",
                        info = if (iInfo >= 0) c.getString(iInfo) ?: "" else "",
                        subLastUpdate = if (iSub >= 0) c.getLong(iSub) else 0L,
                        skipAutoUpdate = iSkip >= 0 && c.getInt(iSkip) != 0,
                        frontProxyId = if (iFront >= 0) c.getLong(iFront) else -1L,
                        landingProxyId = if (iLand >= 0) c.getLong(iLand) else -1L,
                        profilesJson = if (iProfiles >= 0) c.getString(iProfiles) ?: "[]" else "[]",
                        order = orderMap[id] ?: id,
                    )
                )
            }
        }
        deskGroups.sortBy { it.order }

        val deskProfiles = ArrayList<DeskProfile>()
        db.rawQuery("SELECT * FROM profiles", null).use { c ->
            fun idx(n: String) = c.getColumnIndex(n)
            val iId = idx("id")
            val iType = idx("type")
            val iName = idx("name")
            val iGid = idx("gid")
            val iOut = idx("outbound_json")
            val iUp = idx("traffic_up")
            val iDl = idx("traffic_dl")
            val iLat = idx("latency")
            while (c.moveToNext()) {
                deskProfiles.add(
                    DeskProfile(
                        id = c.getLong(iId),
                        type = c.getString(iType) ?: "",
                        name = c.getString(iName) ?: "",
                        gid = c.getLong(iGid),
                        outboundJson = if (iOut >= 0) c.getString(iOut) ?: "" else "",
                        trafficUp = if (iUp >= 0) c.getLong(iUp) else 0L,
                        trafficDl = if (iDl >= 0) c.getLong(iDl) else 0L,
                        latency = if (iLat >= 0) c.getInt(iLat) else 0,
                    )
                )
            }
        }

        val subAutoUpdateDelay = settings["sub_auto_update"]?.toIntOrNull()?.takeIf { it > 0 } ?: 1440

        // Build per-group profile order from profiles_json
        val orderInGroup = HashMap<Long, Long>() // profileId -> userOrder
        for (g in deskGroups) {
            val arr = try {
                JSONArray(g.profilesJson)
            } catch (_: Exception) {
                JSONArray()
            }
            var ord = 1L
            for (i in 0 until arr.length()) {
                val pid = arr.optLong(i, -1L)
                if (pid > 0) {
                    orderInGroup[pid] = ord++
                }
            }
        }

        val groups = ArrayList<ProxyGroup>()
        // Always keep a local ungrouped bucket (id=1) so T4A assumptions hold
        groups.add(
            ProxyGroup(
                id = 1L,
                userOrder = 0L,
                ungrouped = true,
                name = "Ungrouped",
                type = GroupType.BASIC,
            )
        )

        for (g in deskGroups) {
            val isSub = g.url.isNotBlank()
            val pg = ProxyGroup(
                id = g.id,
                userOrder = g.order,
                ungrouped = false,
                name = g.name.ifBlank { "Group ${g.id}" },
                type = if (isSub) GroupType.SUBSCRIPTION else GroupType.BASIC,
                frontProxy = g.frontProxyId.takeIf { it > 0 } ?: -1L,
                landingProxy = g.landingProxyId.takeIf { it > 0 } ?: -1L,
            )
            if (isSub) {
                val sub = SubscriptionBean().applyDefaultValues()
                sub.link = g.url
                sub.subscriptionUserinfo = g.info
                sub.autoUpdate = !g.skipAutoUpdate
                sub.autoUpdateDelay = subAutoUpdateDelay
                // desktop stores unix seconds; T4A lastUpdated is Int seconds
                sub.lastUpdated = g.subLastUpdate
                    .coerceIn(0L, Int.MAX_VALUE.toLong())
                    .toInt()
                pg.subscription = sub
            }
            groups.add(pg)
        }

        val proxies = ArrayList<ProxyEntity>()
        var fallbackOrder = 10_000L
        for (p in deskProfiles) {
            val bean = convertOutbound(p.outboundJson, p.name, p.type)
            val entity = ProxyEntity(
                id = p.id,
                groupId = if (p.gid > 0) p.gid else 1L,
                userOrder = orderInGroup[p.id] ?: fallbackOrder++,
                tx = p.trafficUp,
                rx = p.trafficDl,
                ping = p.latency,
            ).putBean(bean)
            proxies.add(entity)
        }

        SagerDatabase.proxyDao.reset()
        SagerDatabase.groupDao.reset()
        SagerDatabase.groupDao.insert(groups)
        if (proxies.isNotEmpty()) {
            SagerDatabase.proxyDao.insert(proxies)
        }

        // selected group / profile from desktop settings (IDs preserved)
        settings["current_group"]?.toLongOrNull()?.takeIf { it > 0 }?.let {
            if (groups.any { g -> g.id == it }) {
                DataStore.selectedGroup = it
            }
        }
        settings["remember_id"]?.toLongOrNull()?.takeIf { it > 0 }?.let { rid ->
            if (proxies.any { it.id == rid }) {
                DataStore.selectedProxy = rid
                DataStore.currentProfile = rid
            }
        }
    }

    private fun convertOutbound(outboundJson: String, name: String, typeHint: String): AbstractBean {
        if (outboundJson.isBlank()) {
            return ConfigBean().apply {
                this.name = name
                this.type = 1
                config = """{"type":"$typeHint","tag":${JSONObject.quote(name)}}"""
                initializeDefaultValues()
            }
        }
        val json = try {
            JSONObject(outboundJson)
        } catch (_: Exception) {
            return ConfigBean().apply {
                this.name = name
                this.type = 1
                config = outboundJson
                initializeDefaultValues()
            }
        }
        if (!json.has("tag") && name.isNotBlank()) {
            json.put("tag", name)
        }
        val parsed = runCatching { parseSingBoxOutbound(json) }.getOrNull()
        if (parsed != null) {
            if (parsed.name.isNullOrBlank()) parsed.name = name
            return parsed
        }
        return ConfigBean().apply {
            this.name = name.ifBlank { json.optString("tag") }
            this.type = 1 // outbound
            config = outboundJson
            initializeDefaultValues()
        }
    }

    // endregion

    // region routes

    private fun importRoutes(db: SQLiteDatabase, settings: Map<String, String>) {
        val currentRouteId = settings["current_route_id"]?.toLongOrNull()
        val routeProfileIds = ArrayList<Long>()
        try {
            db.rawQuery("SELECT id FROM route_profiles ORDER BY id", null).use { c ->
                val i = c.getColumnIndex("id")
                while (c.moveToNext()) routeProfileIds.add(c.getLong(i))
            }
        } catch (e: Exception) {
            Logs.w(e)
        }
        if (routeProfileIds.isEmpty()) {
            SagerDatabase.rulesDao.reset()
            return
        }
        val targetIds = if (currentRouteId != null && routeProfileIds.contains(currentRouteId)) {
            listOf(currentRouteId)
        } else {
            routeProfileIds
        }

        val rules = ArrayList<RuleEntity>()
        var userOrder = 1L
        for (rpId in targetIds) {
            db.rawQuery(
                "SELECT * FROM route_rules WHERE route_profile_id = ? ORDER BY rule_order",
                arrayOf(rpId.toString())
            ).use { c ->
                fun idx(n: String) = c.getColumnIndex(n)
                val iName = idx("name")
                val iNetwork = idx("network")
                val iProtocol = idx("protocol")
                val iDomain = idx("domain_json")
                val iDomSuf = idx("domain_suffix_json")
                val iDomKey = idx("domain_keyword_json")
                val iDomRe = idx("domain_regex_json")
                val iSrcIp = idx("source_ip_cidr_json")
                val iSrcPriv = idx("source_ip_is_private")
                val iIp = idx("ip_cidr_json")
                val iIpPriv = idx("ip_is_private")
                val iSrcPort = idx("source_port_json")
                val iSrcPortR = idx("source_port_range_json")
                val iPort = idx("port_json")
                val iPortR = idx("port_range_json")
                val iRuleSet = idx("rule_set_json")
                val iOutbound = idx("outbound_id")
                val iAction = idx("action")
                while (c.moveToNext()) {
                    val action = (if (iAction >= 0) c.getString(iAction) else null) ?: "route"
                    // T4A ConfigBuilder already injects hijack-dns / sniff plumbing
                    if (action == "hijack-dns" || action == "sniff" || action == "resolve") continue

                    val domainParts = ArrayList<String>()
                    parseStringList(if (iDomain >= 0) c.getString(iDomain) else null).forEach {
                        domainParts.add(if (it.startsWith("full:")) it else "full:$it")
                    }
                    parseStringList(if (iDomSuf >= 0) c.getString(iDomSuf) else null).forEach {
                        domainParts.add(
                            when {
                                it.startsWith("domain:") || it.startsWith("full:") ||
                                    it.startsWith("geosite:") || it.startsWith("geosite-") -> it
                                else -> "domain:$it"
                            }
                        )
                    }
                    parseStringList(if (iDomKey >= 0) c.getString(iDomKey) else null).forEach {
                        domainParts.add(if (it.startsWith("keyword:")) it else "keyword:$it")
                    }
                    parseStringList(if (iDomRe >= 0) c.getString(iDomRe) else null).forEach {
                        domainParts.add(if (it.startsWith("regexp:")) it else "regexp:$it")
                    }

                    val ipParts = ArrayList<String>()
                    parseStringList(if (iIp >= 0) c.getString(iIp) else null).forEach { ipParts.add(it) }
                    if (iIpPriv >= 0 && c.getInt(iIpPriv) != 0) {
                        ipParts.add("geoip:private")
                    }
                    val srcParts = ArrayList<String>()
                    parseStringList(if (iSrcIp >= 0) c.getString(iSrcIp) else null).forEach { srcParts.add(it) }
                    if (iSrcPriv >= 0 && c.getInt(iSrcPriv) != 0) {
                        // no dedicated field; approximate via source list note — skip private flag
                    }

                    val remoteRulesets = ArrayList<String>()
                    parseStringList(if (iRuleSet >= 0) c.getString(iRuleSet) else null).forEach { rs ->
                        when {
                            rs.startsWith("http://") || rs.startsWith("https://") -> remoteRulesets.add(rs)
                            rs.startsWith("geoip:") || rs.startsWith("geoip-") -> ipParts.add(rs)
                            rs.startsWith("geosite:") || rs.startsWith("geosite-") -> domainParts.add(rs)
                            else -> {
                                // unknown token: keep as domain ruleset-ish
                                domainParts.add(rs)
                            }
                        }
                    }

                    val ports = ArrayList<String>()
                    parseStringList(if (iPort >= 0) c.getString(iPort) else null).forEach { ports.add(it) }
                    parseStringList(if (iPortR >= 0) c.getString(iPortR) else null).forEach {
                        // desktop ranges often "1000:2000"; T4A uses same colon form in port field
                        ports.add(it)
                    }
                    val srcPorts = ArrayList<String>()
                    parseStringList(if (iSrcPort >= 0) c.getString(iSrcPort) else null).forEach { srcPorts.add(it) }
                    parseStringList(if (iSrcPortR >= 0) c.getString(iSrcPortR) else null).forEach { srcPorts.add(it) }

                    val network = if (iNetwork >= 0) c.getString(iNetwork) ?: "" else ""
                    val protocol = if (iProtocol >= 0) c.getString(iProtocol) ?: "" else ""
                    // skip pure dns protocol rules (usually paired with hijack-dns)
                    if (protocol.equals("dns", ignoreCase = true) && domainParts.isEmpty() && ipParts.isEmpty()) {
                        continue
                    }

                    val outboundId = if (iOutbound >= 0) c.getInt(iOutbound) else DESKTOP_OUT_DIRECT
                    val outbound = when (action) {
                        "reject" -> -2L
                        else -> mapOutboundId(outboundId)
                    }

                    // Skip empty rules that would match everything to proxy/direct unintentionally
                    val hasMatch = domainParts.isNotEmpty() || ipParts.isNotEmpty() ||
                        ports.isNotEmpty() || srcPorts.isNotEmpty() || srcParts.isNotEmpty() ||
                        network.isNotBlank() || protocol.isNotBlank() || remoteRulesets.isNotEmpty()
                    if (!hasMatch) continue

                    rules.add(
                        RuleEntity(
                            id = userOrder,
                            name = (if (iName >= 0) c.getString(iName) else null)
                                ?.takeIf { it.isNotBlank() } ?: "Rule $userOrder",
                            userOrder = userOrder,
                            enabled = true,
                            domains = domainParts.joinToString("\n"),
                            ip = ipParts.joinToString("\n"),
                            port = ports.joinToString(","),
                            sourcePort = srcPorts.joinToString(","),
                            network = network,
                            source = srcParts.joinToString("\n"),
                            protocol = protocol,
                            ruleset = remoteRulesets.joinToString("\n"),
                            outbound = outbound,
                        )
                    )
                    userOrder++
                }
            }
        }

        SagerDatabase.rulesDao.reset()
        if (rules.isNotEmpty()) {
            SagerDatabase.rulesDao.insert(rules)
        }
    }

    private fun mapOutboundId(desktopId: Int): Long = when (desktopId) {
        DESKTOP_OUT_PROXY -> 0L
        DESKTOP_OUT_DIRECT, DESKTOP_OUT_WARP_BYPASS -> -1L
        DESKTOP_OUT_BLOCK -> -2L
        else -> if (desktopId > 0) desktopId.toLong() else 0L
    }

    private fun parseStringList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i, "")
                    if (s.isNotBlank()) add(s)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // endregion

    // region settings

    private fun applySettings(s: Map<String, String>) {
        val store = DataStore.configurationStore

        fun putBool(key: String, value: Boolean) = store.putBoolean(key, value)
        fun putStr(key: String, value: String) = store.putString(key, value)
        fun putIntStr(key: String, value: Int) = store.putString(key, value.toString())
        fun putInt(key: String, value: Int) = store.putInt(key, value)

        s["remote_dns"]?.takeIf { it.isNotBlank() }?.let { putStr(Key.REMOTE_DNS, normalizeDns(it)) }
        s["direct_dns"]?.takeIf { it.isNotBlank() }?.let { putStr(Key.DIRECT_DNS, normalizeDns(it)) }
        s["enable_dns_routing"]?.let { putBool(Key.ENABLE_DNS_ROUTING, it.toBooleanStrictOrNull() ?: return@let) }
        s["fakedns"]?.let { putBool(Key.ENABLE_FAKEDNS, it.toBooleanStrictOrNull() ?: return@let) }

        s["inbound_socks_port"]?.toIntOrNull()?.takeIf { it in 1..65535 }?.let {
            putStr(Key.MIXED_PORT, it.toString())
        }
        s["disable_mixed_inbound"]?.let {
            putBool(Key.DISABLE_MIXED_INBOUND, it.toBooleanStrictOrNull() ?: return@let)
        }
        val inboundAuth = s["inbound_auth"]?.toBooleanStrictOrNull() == true
        if (inboundAuth) {
            s["inbound_user"]?.let { putStr(Key.MIXED_USERNAME, it) }
            s["inbound_pass"]?.let { putStr(Key.MIXED_PASSWORD, it) }
        } else {
            // desktop auth off → clear credentials so mixed stays open on loopback
            putStr(Key.MIXED_USERNAME, "")
            putStr(Key.MIXED_PASSWORD, "")
        }
        s["inbound_address"]?.let { addr ->
            // non-loopback listen ≈ allow LAN access
            val allow = addr.isNotBlank() && addr != "127.0.0.1" && addr != "::1"
            putBool(Key.ALLOW_ACCESS, allow)
        }

        s["log_level"]?.let { putIntStr(Key.LOG_LEVEL, mapLogLevel(it)) }
        s["vpn_mtu"]?.toIntOrNull()?.takeIf { it >= 1000 }?.let { putIntStr(Key.MTU, it) }
        s["vpn_strict_route"]?.let {
            putBool(Key.STRICT_ROUTE, it.toBooleanStrictOrNull() ?: return@let)
        }
        s["vpn_ipv6"]?.let {
            val on = it.toBooleanStrictOrNull() ?: return@let
            putIntStr(Key.IPV6_MODE, if (on) IPv6Mode.ENABLE else IPv6Mode.DISABLE)
        }
        s["vpn_impl"]?.let {
            val impl = when (it.lowercase()) {
                "system" -> TunImplementation.SYSTEM
                "gvisor" -> TunImplementation.GVISOR
                "mixed" -> TunImplementation.MIXED
                else -> return@let
            }
            putIntStr(Key.TUN_IMPLEMENTATION, impl)
        }
        // Tun mode on desktop ≈ VPN service mode; system proxy ≈ proxy mode
        // Official note: system proxy / tun switch may not be in backup; still map if present
        s["tun_mode_enabled"]?.toBooleanStrictOrNull()?.let { tunOn ->
            if (tunOn) putStr(Key.SERVICE_MODE, Key.MODE_VPN)
        }
        s["system_proxy_enabled"]?.toBooleanStrictOrNull()?.let { spOn ->
            if (spOn) putStr(Key.SERVICE_MODE, Key.MODE_PROXY)
        }

        s["test_url"]?.takeIf { it.isNotBlank() }?.let { putStr(Key.CONNECTION_TEST_URL, it) }
        s["url_test_timeout_ms"]?.toIntOrNull()?.let { putInt(Key.CONNECTION_TEST_TIMEOUT, it) }
        s["test_concurrent"]?.toIntOrNull()?.let { putInt(Key.CONNECTION_TEST_CONCURRENT, it) }
        SpeedTestSettings.desktopBackupUpdates(s).forEach { (key, value) ->
            putStr(key, value)
        }

        s["skip_cert"]?.toBooleanStrictOrNull()?.let { putBool(Key.GLOBAL_ALLOW_INSECURE, it) }
            ?: s["net_insecure"]?.toBooleanStrictOrNull()?.let { putBool(Key.GLOBAL_ALLOW_INSECURE, it) }

        s["fragment_default_on"]?.toBooleanStrictOrNull()?.let { putBool(Key.ENABLE_TLS_FRAGMENT, it) }
        s["fragment_size"]?.takeIf { it.isNotBlank() }?.let { putStr(Key.FRAGMENT_LENGTH, it) }
        s["fragment_sleep"]?.takeIf { it.isNotBlank() }?.let { putStr(Key.FRAGMENT_INTERVAL, it) }

        s["sniffing_mode"]?.toIntOrNull()?.let { putIntStr(Key.TRAFFIC_SNIFFING, it) }

        // disable_private_range_bypass=true means do NOT bypass LAN → bypassLanInCore=false
        s["disable_private_range_bypass"]?.toBooleanStrictOrNull()?.let { disabled ->
            putBool(Key.BYPASS_LAN_IN_CORE, !disabled)
            putBool(Key.BYPASS_LAN, !disabled)
        }

        s["core_box_clash_api"]?.let { v ->
            val enabled = when {
                v.equals("true", true) -> true
                v.equals("false", true) -> false
                else -> (v.toIntOrNull() ?: 0) != 0
            }
            putBool(Key.ENABLE_CLASH_API, enabled)
        }

        // domain strategies (T4A uses dedicated keys read by SingBoxOptionsUtil)
        s["outbound_domain_strategy"]?.let {
            putStr("domain_strategy_for_server", it)
        }
        s["domain_strategy"]?.let {
            // general fallback
            if (store.getString("domain_strategy_for_server") == null) {
                putStr("domain_strategy_for_server", it)
            }
        }
        s["remote_dns_strategy"]?.let { putStr("domain_strategy_for_remote", it) }
        s["direct_dns_strategy"]?.let { putStr("domain_strategy_for_direct", it) }

        // geo assets: desktop often points at .dat; only map if looks usable or leave default
        s["xray_geosite_url"]?.takeIf { it.contains("geosite") }?.let {
            // Prefer keeping T4A default .db URLs; map only custom non-empty
            if (!it.contains("Loyalsoldier") && it.isNotBlank()) {
                putStr(Key.RULES_GEOSITE_URL, it)
            }
        }
        s["xray_geoip_url"]?.takeIf { it.contains("geoip") }?.let {
            if (!it.contains("Loyalsoldier") && it.isNotBlank()) {
                putStr(Key.RULES_GEOIP_URL, it)
            }
        }

        s["disable_traffic_stats"]?.toBooleanStrictOrNull()?.let {
            putBool(Key.PROFILE_TRAFFIC_STATISTICS, !it)
        }

        // touch PublicDatabase so Room flush is consistent with other backup path
        PublicDatabase.kvPairDao.get(Key.REMOTE_DNS)
    }

    private fun normalizeDns(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return t
        // bare IP → leave as-is (T4A accepts udp/tcp/https/local/etc.)
        return t
    }

    private fun mapLogLevel(desktop: String): Int = when (desktop.lowercase()) {
        "panic", "fatal" -> 0
        "error", "warn", "warning" -> 1
        "info" -> 2
        "debug" -> 3
        "trace" -> 4
        else -> desktop.toIntOrNull()?.coerceIn(0, 4) ?: 1
    }

    // endregion
}
