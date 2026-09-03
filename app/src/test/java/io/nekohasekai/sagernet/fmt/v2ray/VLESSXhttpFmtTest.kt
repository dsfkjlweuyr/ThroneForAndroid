package io.nekohasekai.sagernet.fmt.v2ray

import io.nekohasekai.sagernet.group.RawUpdater
import io.nekohasekai.sagernet.fmt.KryoConverters
import kotlinx.coroutines.runBlocking
import moe.matsuri.nb4a.utils.JavaUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VLESSXhttpFmtTest {

    @Test
    fun parseVlessXhttpUrlPreservesCoreExtraFields() {
        val bean = parseV2Ray(
            "vless://00000000-0000-0000-0000-000000000001@example.com:443" +
                    "?encryption=none&type=xhttp&security=tls&host=cdn.example.com" +
                    "&path=%2Fapi%2Fv1&mode=stream-up&extra=" +
                    "%7B%22xPaddingBytes%22%3A%22100-200%22%2C%22noSSEHeader%22%3Atrue%2C" +
                    "%22scMaxBufferedPosts%22%3A12%2C%22scStreamUpServerSecs%22%3A%2220-40%22%2C" +
                    "%22xmux%22%3A%7B%22maxConcurrency%22%3A%2216-32%22%7D%2C" +
                    "%22downloadSettings%22%3A%7B%22address%22%3A%22download.example.com%22%2C" +
                    "%22port%22%3A8443%2C%22xhttpSettings%22%3A%7B%22mode%22%3A%22packet-up%22%2C" +
                    "%22path%22%3A%22%2Fdown%22%7D%7D%7D#xhttp-url"
        )

        assertTrue(bean.isVLESS)
        assertEquals("xhttp", bean.type)
        assertEquals("cdn.example.com", bean.host)
        assertEquals("/api/v1", bean.path)
        assertEquals("stream-up", bean.xhttpMode)

        val extra = JavaUtil.gson.fromJson(bean.xhttpExtra, com.google.gson.JsonObject::class.java)
        assertEquals("100-200", extra["x_padding_bytes"].asString)
        assertTrue(extra["no_sse_header"].asBoolean)
        assertEquals(12, extra["sc_max_buffered_posts"].asInt)
        assertEquals("20-40", extra["sc_stream_up_server_secs"].asString)
        assertEquals("16-32", extra.getAsJsonObject("xmux")["max_concurrency"].asString)
        assertEquals("download.example.com", extra.getAsJsonObject("download")["server"].asString)
        assertEquals(8443, extra.getAsJsonObject("download")["server_port"].asInt)

        bean.initializeDefaultValues()
        val restored = KryoConverters.deserialize(
            VMessBean(),
            KryoConverters.serialize(bean)
        )
        assertEquals("xhttp", restored.type)
        assertEquals("cdn.example.com", restored.host)
        assertEquals("/api/v1", restored.path)
        assertEquals("stream-up", restored.xhttpMode)
        assertEquals(bean.xhttpExtra, restored.xhttpExtra)
    }

    @Test
    fun invalidUrlModeFallsBackToAutoBeforeConfigGeneration() {
        val bean = parseV2Ray(
            "vless://00000000-0000-0000-0000-000000000003@example.com:443" +
                    "?encryption=none&type=xhttp&mode=invalid"
        )
        bean.initializeDefaultValues()

        assertEquals("auto", bean.xhttpMode)
        assertEquals("auto", buildSingBoxOutboundStreamSettings(bean)!!.let {
            JavaUtil.gson.toJsonTree(it).asJsonObject["mode"].asString
        })
    }

    @Test
    fun clashImportPreservesXhttpXmuxAndDownloadSettings() = runBlocking {
        val proxies = RawUpdater.parseRaw(
            """
                proxies:
                  - name: xhttp-clash
                    type: vless
                    server: example.com
                    port: 443
                    uuid: 00000000-0000-0000-0000-000000000002
                    network: xhttp
                    tls: true
                    xhttp-opts:
                      mode: packet-up
                      host: upload.example.com
                      path: /upload
                      x-padding-bytes: 200-300
                      no-sse-header: true
                      sc-max-buffered-posts: 10
                      sc-stream-up-server-secs: 30-60
                      reuse-settings:
                        max-connections: 2
                      download-settings:
                        host: download.example.com
                        path: /download
                        server: alt.example.com
                        port: 8443
                        tls: true
                        servername: tls.example.com
                        client-fingerprint: chrome
                        reuse-settings:
                          max-concurrency: 8-16
            """.trimIndent()
        )

        val bean = proxies!!.single() as StandardV2RayBean
        assertEquals("xhttp", bean.type)
        assertEquals("packet-up", bean.xhttpMode)
        assertEquals("upload.example.com", bean.host)
        assertEquals("/upload", bean.path)

        val extra = JavaUtil.gson.fromJson(bean.xhttpExtra, com.google.gson.JsonObject::class.java)
        assertEquals("200-300", extra["x_padding_bytes"].asString)
        assertTrue(extra["no_sse_header"].asBoolean)
        assertEquals(10, extra["sc_max_buffered_posts"].asInt)
        assertEquals("30-60", extra["sc_stream_up_server_secs"].asString)
        assertEquals(2, extra.getAsJsonObject("xmux")["max_connections"].asInt)

        val download = extra.getAsJsonObject("download")
        assertEquals("download.example.com", download["host"].asString)
        assertEquals("/download", download["path"].asString)
        assertEquals("alt.example.com", download["server"].asString)
        assertEquals(8443, download["server_port"].asInt)
        assertEquals("8-16", download.getAsJsonObject("xmux")["max_concurrency"].asString)
        assertEquals("tls.example.com", download.getAsJsonObject("tls")["server_name"].asString)
        assertEquals(
            "chrome",
            download.getAsJsonObject("tls").getAsJsonObject("utls")["fingerprint"].asString
        )
    }

    @Test
    fun singBoxTransportJsonEmitsNormalizedXhttpContract() {
        val bean = VMessBean().apply {
            alterId = -1
            type = "xhttp"
            host = "cdn.example.com"
            path = "/transport"
            xhttpMode = "stream-one"
            xhttpExtra = """
                {
                  "x_padding_bytes": "100-1000",
                  "no_sse_header": false,
                  "sc_max_buffered_posts": 30,
                  "sc_stream_up_server_secs": "20-80",
                  "xmux": {"max_concurrency": "16-32"},
                  "download": {"server": "download.example.com", "server_port": 8443}
                }
            """.trimIndent()
            initializeDefaultValues()
        }

        val transport = JavaUtil.gson.toJsonTree(buildSingBoxOutboundStreamSettings(bean)).asJsonObject

        assertEquals("xhttp", transport["type"].asString)
        assertEquals("stream-one", transport["mode"].asString)
        assertEquals("cdn.example.com", transport["host"].asString)
        assertEquals("/transport", transport["path"].asString)
        assertEquals("100-1000", transport["x_padding_bytes"].asString)
        assertFalse(transport["no_sse_header"].asBoolean)
        assertEquals(30, transport["sc_max_buffered_posts"].asInt)
        assertEquals("20-80", transport["sc_stream_up_server_secs"].asString)
        assertEquals("16-32", transport.getAsJsonObject("xmux")["max_concurrency"].asString)
        assertEquals("download.example.com", transport.getAsJsonObject("download")["server"].asString)
    }
}
