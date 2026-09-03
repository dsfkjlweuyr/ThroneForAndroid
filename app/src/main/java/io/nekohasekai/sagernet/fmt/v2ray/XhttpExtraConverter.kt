package io.nekohasekai.sagernet.fmt.v2ray

import org.json.JSONObject

object XhttpExtraConverter {

    fun xrayToSingBox(xrayExtra: String): String {
        if (xrayExtra.isBlank()) return ""
        return try {
            val xray = JSONObject(xrayExtra)
            if (isSingBoxFormat(xray)) return xrayExtra
            val singBox = JSONObject()

            convertField(xray, singBox, "xPaddingBytes", "x_padding_bytes")
            convertField(xray, singBox, "scMaxEachPostBytes", "sc_max_each_post_bytes")
            convertField(xray, singBox, "scMinPostsIntervalMs", "sc_min_posts_interval_ms")
            convertField(xray, singBox, "scMaxBufferedPosts", "sc_max_buffered_posts")
            convertField(xray, singBox, "scStreamUpServerSecs", "sc_stream_up_server_secs")
            convertField(xray, singBox, "noGRPCHeader", "no_grpc_header")
            convertField(xray, singBox, "noSSEHeader", "no_sse_header")
            convertField(xray, singBox, "headers", "headers")
            convertField(xray, singBox, "xPaddingObfsMode", "x_padding_obfs_mode")
            convertField(xray, singBox, "xPaddingKey", "x_padding_key")
            convertField(xray, singBox, "xPaddingHeader", "x_padding_header")
            convertField(xray, singBox, "xPaddingPlacement", "x_padding_placement")
            convertField(xray, singBox, "xPaddingMethod", "x_padding_method")
            convertField(xray, singBox, "uplinkHttpMethod", "uplink_http_method")
            convertField(xray, singBox, "sessionIdPosition", "session_placement")
            convertField(xray, singBox, "sessionIdName", "session_key")
            convertField(xray, singBox, "seqPosition", "seq_placement")
            convertField(xray, singBox, "seqName", "seq_key")
            convertField(xray, singBox, "dataUpPlacement", "uplink_data_placement")
            convertField(xray, singBox, "dataUpName", "uplink_data_key")
            convertField(xray, singBox, "dataUpSplitSize", "uplink_chunk_size")

            if (xray.has("xmux")) {
                val xrayXmux = xray.getJSONObject("xmux")
                val singBoxXmux = JSONObject()
                convertField(xrayXmux, singBoxXmux, "maxConcurrency", "max_concurrency")
                convertField(xrayXmux, singBoxXmux, "maxConnections", "max_connections")
                convertField(xrayXmux, singBoxXmux, "cMaxReuseTimes", "c_max_reuse_times")
                convertField(xrayXmux, singBoxXmux, "hMaxRequestTimes", "h_max_request_times")
                convertField(xrayXmux, singBoxXmux, "hMaxReusableSecs", "h_max_reusable_secs")
                convertField(xrayXmux, singBoxXmux, "hKeepAlivePeriod", "h_keep_alive_period")
                if (singBoxXmux.length() > 0) singBox.put("xmux", singBoxXmux)
            }

            if (xray.has("downloadSettings")) {
                val xrayDown = xray.getJSONObject("downloadSettings")
                val singBoxDown = JSONObject()

                xrayDown.optJSONObject("xhttpSettings")?.let { xhttpSettings ->
                    convertField(xhttpSettings, singBoxDown, "mode", "mode")
                    convertField(xhttpSettings, singBoxDown, "host", "host")
                    convertField(xhttpSettings, singBoxDown, "path", "path")
                }
                convertField(xrayDown, singBoxDown, "address", "server")
                convertField(xrayDown, singBoxDown, "port", "server_port")

                if (xrayDown.has("security")) {
                    val tls = JSONObject().apply { put("enabled", true) }

                    when (xrayDown.getString("security")) {
                        "tls" -> {
                            xrayDown.optJSONObject("tlsSettings")?.let { tlsSettings ->
                                convertField(tlsSettings, tls, "serverName", "server_name")
                                convertField(tlsSettings, tls, "alpn", "alpn")
                                convertField(tlsSettings, tls, "allowInsecure", "insecure")
                                tlsSettings.optString("fingerprint")?.let { fp ->
                                    if (fp.isNotBlank()) {
                                        val utls = JSONObject().apply {
                                            put("enabled", true)
                                            put("fingerprint", fp)
                                        }
                                        tls.put("utls", utls)
                                    }
                                }
                            }
                        }
                        "reality" -> {
                            xrayDown.optJSONObject("realitySettings")?.let { realitySettings ->
                                convertField(realitySettings, tls, "serverName", "server_name")
                                val reality = JSONObject().apply {
                                    put("enabled", true)
                                    convertField(realitySettings, this, "publicKey", "public_key")
                                    convertField(realitySettings, this, "shortId", "short_id")
                                }
                                tls.put("reality", reality)
                                realitySettings.optString("fingerprint")?.let { fp ->
                                    if (fp.isNotBlank()) {
                                        val utls = JSONObject().apply {
                                            put("enabled", true)
                                            put("fingerprint", fp)
                                        }
                                        tls.put("utls", utls)
                                    }
                                }
                            }
                        }
                    }
                    singBoxDown.put("tls", tls)
                }

                xrayDown.optJSONObject("xhttpSettings")?.optJSONObject("extra")?.let { extra ->
                    if (extra.has("xmux")) {
                        val xrayXmux = extra.getJSONObject("xmux")
                        val downXmux = JSONObject()
                        convertField(xrayXmux, downXmux, "maxConcurrency", "max_concurrency")
                        convertField(xrayXmux, downXmux, "maxConnections", "max_connections")
                        convertField(xrayXmux, downXmux, "cMaxReuseTimes", "c_max_reuse_times")
                        convertField(xrayXmux, downXmux, "hMaxRequestTimes", "h_max_request_times")
                        convertField(xrayXmux, downXmux, "hMaxReusableSecs", "h_max_reusable_secs")
                        convertField(xrayXmux, downXmux, "hKeepAlivePeriod", "h_keep_alive_period")
                        if (downXmux.length() > 0) singBoxDown.put("xmux", downXmux)
                    }

                    convertField(extra, singBoxDown, "xPaddingBytes", "x_padding_bytes")
                    convertField(extra, singBoxDown, "scMaxEachPostBytes", "sc_max_each_post_bytes")
                    convertField(extra, singBoxDown, "scMinPostsIntervalMs", "sc_min_posts_interval_ms")
                    convertField(extra, singBoxDown, "scMaxBufferedPosts", "sc_max_buffered_posts")
                    convertField(extra, singBoxDown, "scStreamUpServerSecs", "sc_stream_up_server_secs")
                    convertField(extra, singBoxDown, "noGRPCHeader", "no_grpc_header")
                    convertField(extra, singBoxDown, "noSSEHeader", "no_sse_header")
                    convertField(extra, singBoxDown, "xPaddingObfsMode", "x_padding_obfs_mode")
                    convertField(extra, singBoxDown, "xPaddingKey", "x_padding_key")
                    convertField(extra, singBoxDown, "xPaddingHeader", "x_padding_header")
                    convertField(extra, singBoxDown, "xPaddingPlacement", "x_padding_placement")
                    convertField(extra, singBoxDown, "xPaddingMethod", "x_padding_method")
                    convertField(extra, singBoxDown, "uplinkHttpMethod", "uplink_http_method")
                    convertField(extra, singBoxDown, "sessionIdPosition", "session_placement")
                    convertField(extra, singBoxDown, "sessionIdName", "session_key")
                    convertField(extra, singBoxDown, "seqPosition", "seq_placement")
                    convertField(extra, singBoxDown, "seqName", "seq_key")
                    convertField(extra, singBoxDown, "dataUpPlacement", "uplink_data_placement")
                    convertField(extra, singBoxDown, "dataUpName", "uplink_data_key")
                    convertField(extra, singBoxDown, "dataUpSplitSize", "uplink_chunk_size")
                }

                if (singBoxDown.length() > 0) singBox.put("download", singBoxDown)
            }

            singBox.toString(2).replace("\\/", "/")
        } catch (e: Exception) {
            e.printStackTrace()
            xrayExtra
        }
    }

    fun singBoxToXray(singBoxExtra: String): String {
        if (singBoxExtra.isBlank()) return ""
        return try {
            val singBox = JSONObject(singBoxExtra)
            if (isXrayFormat(singBox)) return singBoxExtra
            val xray = JSONObject()

            convertField(singBox, xray, "x_padding_bytes", "xPaddingBytes")
            convertField(singBox, xray, "sc_max_each_post_bytes", "scMaxEachPostBytes")
            convertField(singBox, xray, "sc_min_posts_interval_ms", "scMinPostsIntervalMs")
            convertField(singBox, xray, "sc_max_buffered_posts", "scMaxBufferedPosts")
            convertField(singBox, xray, "sc_stream_up_server_secs", "scStreamUpServerSecs")
            convertField(singBox, xray, "no_grpc_header", "noGRPCHeader")
            convertField(singBox, xray, "no_sse_header", "noSSEHeader")
            convertField(singBox, xray, "headers", "headers")
            convertField(singBox, xray, "x_padding_obfs_mode", "xPaddingObfsMode")
            convertField(singBox, xray, "x_padding_key", "xPaddingKey")
            convertField(singBox, xray, "x_padding_header", "xPaddingHeader")
            convertField(singBox, xray, "x_padding_placement", "xPaddingPlacement")
            convertField(singBox, xray, "x_padding_method", "xPaddingMethod")
            convertField(singBox, xray, "uplink_http_method", "uplinkHttpMethod")
            convertField(singBox, xray, "session_placement", "sessionIdPosition")
            convertField(singBox, xray, "session_key", "sessionIdName")
            convertField(singBox, xray, "seq_placement", "seqPosition")
            convertField(singBox, xray, "seq_key", "seqName")
            convertField(singBox, xray, "uplink_data_placement", "dataUpPlacement")
            convertField(singBox, xray, "uplink_data_key", "dataUpName")
            convertField(singBox, xray, "uplink_chunk_size", "dataUpSplitSize")

            if (singBox.has("xmux")) {
                val singBoxXmux = singBox.getJSONObject("xmux")
                val xrayXmux = JSONObject()
                convertField(singBoxXmux, xrayXmux, "max_concurrency", "maxConcurrency")
                convertField(singBoxXmux, xrayXmux, "max_connections", "maxConnections")
                convertField(singBoxXmux, xrayXmux, "c_max_reuse_times", "cMaxReuseTimes")
                convertField(singBoxXmux, xrayXmux, "h_max_request_times", "hMaxRequestTimes")
                convertField(singBoxXmux, xrayXmux, "h_max_reusable_secs", "hMaxReusableSecs")
                convertField(singBoxXmux, xrayXmux, "h_keep_alive_period", "hKeepAlivePeriod")
                if (xrayXmux.length() > 0) xray.put("xmux", xrayXmux)
            }

            if (singBox.has("download")) {
                val singBoxDown = singBox.getJSONObject("download")
                val xrayDown = JSONObject()

                convertField(singBoxDown, xrayDown, "server", "address")
                convertField(singBoxDown, xrayDown, "server_port", "port")
                xrayDown.put("network", "xhttp")

                if (singBoxDown.has("tls")) {
                    val tls = singBoxDown.getJSONObject("tls")

                    if (tls.has("reality") && tls.getJSONObject("reality").optBoolean("enabled", false)) {
                        xrayDown.put("security", "reality")
                        val reality = tls.getJSONObject("reality")
                        val realitySettings = JSONObject()
                        convertField(tls, realitySettings, "server_name", "serverName")
                        convertField(reality, realitySettings, "public_key", "publicKey")
                        convertField(reality, realitySettings, "short_id", "shortId")
                        if (tls.has("utls")) {
                            val utls = tls.getJSONObject("utls")
                            convertField(utls, realitySettings, "fingerprint", "fingerprint")
                        }
                        xrayDown.put("realitySettings", realitySettings)
                    } else {
                        xrayDown.put("security", "tls")
                        val tlsSettings = JSONObject()
                        convertField(tls, tlsSettings, "server_name", "serverName")
                        convertField(tls, tlsSettings, "alpn", "alpn")
                        convertField(tls, tlsSettings, "insecure", "allowInsecure")
                        if (tls.has("utls")) {
                            val utls = tls.getJSONObject("utls")
                            convertField(utls, tlsSettings, "fingerprint", "fingerprint")
                        }
                        xrayDown.put("tlsSettings", tlsSettings)
                    }
                }

                val xhttpSettings = JSONObject()
                convertField(singBoxDown, xhttpSettings, "mode", "mode")
                convertField(singBoxDown, xhttpSettings, "host", "host")
                convertField(singBoxDown, xhttpSettings, "path", "path")

                val xhttpExtra = JSONObject()
                convertField(singBoxDown, xhttpExtra, "x_padding_bytes", "xPaddingBytes")
                convertField(singBoxDown, xhttpExtra, "sc_max_each_post_bytes", "scMaxEachPostBytes")
                convertField(singBoxDown, xhttpExtra, "sc_min_posts_interval_ms", "scMinPostsIntervalMs")
                convertField(singBoxDown, xhttpExtra, "sc_max_buffered_posts", "scMaxBufferedPosts")
                convertField(singBoxDown, xhttpExtra, "sc_stream_up_server_secs", "scStreamUpServerSecs")
                convertField(singBoxDown, xhttpExtra, "no_grpc_header", "noGRPCHeader")
                convertField(singBoxDown, xhttpExtra, "no_sse_header", "noSSEHeader")
                convertField(singBoxDown, xhttpExtra, "x_padding_obfs_mode", "xPaddingObfsMode")
                convertField(singBoxDown, xhttpExtra, "x_padding_key", "xPaddingKey")
                convertField(singBoxDown, xhttpExtra, "x_padding_header", "xPaddingHeader")
                convertField(singBoxDown, xhttpExtra, "x_padding_placement", "xPaddingPlacement")
                convertField(singBoxDown, xhttpExtra, "x_padding_method", "xPaddingMethod")
                convertField(singBoxDown, xhttpExtra, "uplink_http_method", "uplinkHttpMethod")
                convertField(singBoxDown, xhttpExtra, "session_placement", "sessionIdPosition")
                convertField(singBoxDown, xhttpExtra, "session_key", "sessionIdName")
                convertField(singBoxDown, xhttpExtra, "seq_placement", "seqPosition")
                convertField(singBoxDown, xhttpExtra, "seq_key", "seqName")
                convertField(singBoxDown, xhttpExtra, "uplink_data_placement", "dataUpPlacement")
                convertField(singBoxDown, xhttpExtra, "uplink_data_key", "dataUpName")
                convertField(singBoxDown, xhttpExtra, "uplink_chunk_size", "dataUpSplitSize")

                if (singBoxDown.has("xmux")) {
                    val singBoxDownXmux = singBoxDown.getJSONObject("xmux")
                    val xrayDownXmux = JSONObject()
                    convertField(singBoxDownXmux, xrayDownXmux, "max_concurrency", "maxConcurrency")
                    convertField(singBoxDownXmux, xrayDownXmux, "max_connections", "maxConnections")
                    convertField(singBoxDownXmux, xrayDownXmux, "c_max_reuse_times", "cMaxReuseTimes")
                    convertField(singBoxDownXmux, xrayDownXmux, "h_max_request_times", "hMaxRequestTimes")
                    convertField(singBoxDownXmux, xrayDownXmux, "h_max_reusable_secs", "hMaxReusableSecs")
                    convertField(singBoxDownXmux, xrayDownXmux, "h_keep_alive_period", "hKeepAlivePeriod")
                    if (xrayDownXmux.length() > 0) xhttpExtra.put("xmux", xrayDownXmux)
                }

                if (xhttpExtra.length() > 0) xhttpSettings.put("extra", xhttpExtra)
                xrayDown.put("xhttpSettings", xhttpSettings)

                if (xrayDown.length() > 0) xray.put("downloadSettings", xrayDown)
            }

            xray.toString(2).replace("\\/", "/")
        } catch (e: Exception) {
            e.printStackTrace()
            singBoxExtra
        }
    }

    fun clashToSingBox(xhttpOpts: Map<String, Any?>): String {
        val singBox = JSONObject()
        copyClashBaseOptions(xhttpOpts, singBox)
        copyClashXmux(xhttpOpts["reuse-settings"], singBox)

        (xhttpOpts["download-settings"] as? Map<*, *>)?.let { downloadSettings ->
            val download = JSONObject()
            copyClashBaseOptions(downloadSettings, download)
            copyClashField(downloadSettings, download, "mode", "mode")
            copyClashField(downloadSettings, download, "host", "host")
            copyClashField(downloadSettings, download, "path", "path")
            copyClashXmux(downloadSettings["reuse-settings"], download)
            copyClashField(downloadSettings, download, "server", "server")
            copyClashField(downloadSettings, download, "port", "server_port")

            val realityOptions = downloadSettings["reality-opts"] as? Map<*, *>
            if (downloadSettings["tls"]?.toString() == "true" || realityOptions != null) {
                val tls = JSONObject().apply { put("enabled", true) }
                copyClashField(downloadSettings, tls, "servername", "server_name")
                copyClashField(downloadSettings, tls, "alpn", "alpn")
                copyClashField(downloadSettings, tls, "skip-cert-verify", "insecure")
                downloadSettings["client-fingerprint"]?.let { fingerprint ->
                    tls.put("utls", JSONObject().apply {
                        put("enabled", true)
                        put("fingerprint", fingerprint)
                    })
                }
                realityOptions?.let { reality ->
                    tls.put("reality", JSONObject().apply {
                        put("enabled", true)
                        copyClashField(reality, this, "public-key", "public_key")
                        copyClashField(reality, this, "short-id", "short_id")
                    })
                }
                download.put("tls", tls)
            }

            if (download.length() > 0) singBox.put("download", download)
        }

        return if (singBox.length() > 0) singBox.toString(2).replace("\\/", "/") else ""
    }

    private fun isSingBoxFormat(json: JSONObject): Boolean {
        return SING_BOX_KEYS.any(json::has) ||
                json.optJSONObject("xmux")?.let { xmux -> SING_BOX_XMUX_KEYS.any(xmux::has) } == true
    }

    private fun isXrayFormat(json: JSONObject): Boolean {
        return XRAY_KEYS.any(json::has) ||
                json.optJSONObject("xmux")?.let { xmux -> XRAY_XMUX_KEYS.any(xmux::has) } == true
    }

    private fun convertField(from: JSONObject, to: JSONObject, fromKey: String, toKey: String) {
        if (from.has(fromKey)) {
            to.put(toKey, from.get(fromKey))
        }
    }

    private fun copyClashBaseOptions(from: Map<*, *>, to: JSONObject) {
        CLASH_BASE_KEYS.forEach { (clashKey, singBoxKey) ->
            copyClashField(from, to, clashKey, singBoxKey)
        }
    }

    private fun copyClashXmux(value: Any?, to: JSONObject) {
        (value as? Map<*, *>)?.let { reuseSettings ->
            val xmux = JSONObject()
            CLASH_XMUX_KEYS.forEach { (clashKey, singBoxKey) ->
                copyClashField(reuseSettings, xmux, clashKey, singBoxKey)
            }
            if (xmux.length() > 0) to.put("xmux", xmux)
        }
    }

    private fun copyClashField(from: Map<*, *>, to: JSONObject, fromKey: String, toKey: String) {
        if (from.containsKey(fromKey) && from[fromKey] != null) {
            to.put(toKey, JSONObject.wrap(from[fromKey]))
        }
    }

    private val CLASH_BASE_KEYS = mapOf(
        "headers" to "headers",
        "x-padding-bytes" to "x_padding_bytes",
        "no-grpc-header" to "no_grpc_header",
        "no-sse-header" to "no_sse_header",
        "sc-max-each-post-bytes" to "sc_max_each_post_bytes",
        "sc-min-posts-interval-ms" to "sc_min_posts_interval_ms",
        "sc-max-buffered-posts" to "sc_max_buffered_posts",
        "sc-stream-up-server-secs" to "sc_stream_up_server_secs",
        "x-padding-obfs-mode" to "x_padding_obfs_mode",
        "x-padding-key" to "x_padding_key",
        "x-padding-header" to "x_padding_header",
        "x-padding-placement" to "x_padding_placement",
        "x-padding-method" to "x_padding_method",
        "uplink-http-method" to "uplink_http_method",
        "session-placement" to "session_placement",
        "session-key" to "session_key",
        "seq-placement" to "seq_placement",
        "seq-key" to "seq_key",
        "uplink-data-placement" to "uplink_data_placement",
        "uplink-data-key" to "uplink_data_key",
        "uplink-chunk-size" to "uplink_chunk_size"
    )

    private val CLASH_XMUX_KEYS = mapOf(
        "max-concurrency" to "max_concurrency",
        "max-connections" to "max_connections",
        "c-max-reuse-times" to "c_max_reuse_times",
        "h-max-request-times" to "h_max_request_times",
        "h-max-reusable-secs" to "h_max_reusable_secs",
        "h-keep-alive-period" to "h_keep_alive_period"
    )

    private val SING_BOX_KEYS = setOf(
        "x_padding_bytes", "sc_max_each_post_bytes", "sc_min_posts_interval_ms",
        "sc_max_buffered_posts", "sc_stream_up_server_secs", "no_grpc_header",
        "no_sse_header", "headers", "x_padding_obfs_mode", "x_padding_key",
        "x_padding_header", "x_padding_placement", "x_padding_method",
        "uplink_http_method", "session_placement", "session_key", "seq_placement",
        "seq_key", "uplink_data_placement", "uplink_data_key", "uplink_chunk_size",
        "download"
    )

    private val XRAY_KEYS = setOf(
        "xPaddingBytes", "scMaxEachPostBytes", "scMinPostsIntervalMs",
        "scMaxBufferedPosts", "scStreamUpServerSecs", "noGRPCHeader",
        "noSSEHeader", "headers", "xPaddingObfsMode", "xPaddingKey",
        "xPaddingHeader", "xPaddingPlacement", "xPaddingMethod", "uplinkHttpMethod",
        "sessionIdPosition", "sessionIdName", "seqPosition", "seqName",
        "dataUpPlacement", "dataUpName", "dataUpSplitSize", "downloadSettings"
    )

    private val SING_BOX_XMUX_KEYS = setOf(
        "max_concurrency", "max_connections", "c_max_reuse_times",
        "h_max_request_times", "h_max_reusable_secs", "h_keep_alive_period"
    )

    private val XRAY_XMUX_KEYS = setOf(
        "maxConcurrency", "maxConnections", "cMaxReuseTimes",
        "hMaxRequestTimes", "hMaxReusableSecs", "hKeepAlivePeriod"
    )
}
