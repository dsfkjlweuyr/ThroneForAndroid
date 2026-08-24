package io.nekohasekai.sagernet

import io.nekohasekai.sagernet.ktx.PreferenceProxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class TestSettingsContractTest {

    @Test
    fun freshInstallDefaultsMatchThroneBaseline() {
        val defaults = defaultResources()

        assertEquals("http://cp.cloudflare.com/", defaults["default_connection_test_url"])
        assertEquals("10", defaults["default_connection_test_concurrent"])
        assertEquals("download_upload", defaults["default_speed_test_mode"])
        assertEquals("5000", defaults["default_speed_test_timeout_ms"])
        assertEquals(
            "https://www.speedtest.net/api/js/servers",
            defaults["default_speed_test_server_list_url"],
        )
        assertEquals(
            "https://www.speedtest.net/speedtest-servers-static.php",
            defaults["default_speed_test_fallback_server_list_url"],
        )
        assertEquals(
            "http://cachefly.cachefly.net/1mb.test",
            defaults["default_simple_download_url"],
        )
    }

    @Test
    fun persistedUpgradeValuesWinOverNewDefaults() {
        val values = mutableMapOf<String, Any>(
            Key.CONNECTION_TEST_URL to "http://www.gstatic.com/generate_204",
            Key.CONNECTION_TEST_CONCURRENT to 5,
        )
        val url = proxy(values, Key.CONNECTION_TEST_URL, "http://cp.cloudflare.com/")
        val concurrent = proxy(values, Key.CONNECTION_TEST_CONCURRENT, 10)

        assertEquals("http://www.gstatic.com/generate_204", url.getter(url.name, url.defaultValue()))
        assertEquals(5, concurrent.getter(concurrent.name, concurrent.defaultValue()))

        values[Key.CONNECTION_TEST_URL] = "https://example.com/custom-latency"
        values[Key.CONNECTION_TEST_CONCURRENT] = 3
        assertEquals("https://example.com/custom-latency", url.getter(url.name, url.defaultValue()))
        assertEquals(3, concurrent.getter(concurrent.name, concurrent.defaultValue()))
    }

    @Test
    fun preferenceAndDataStoreUseTheSameDefaultResources() {
        val preferenceXml = sourceFile("src/main/res/xml/global_preferences.xml").readText()
        val dataStore = sourceFile(
            "src/main/java/io/nekohasekai/sagernet/database/DataStore.kt",
        ).readText()

        mapOf(
            "connectionTestURL" to "default_connection_test_url",
            "speedTestMode" to "default_speed_test_mode",
            "speedTestTimeoutMs" to "default_speed_test_timeout_ms",
            "simpleDownloadURL" to "default_simple_download_url",
        ).forEach { (key, resource) ->
            val preference = preferenceXml.substringAfter("app:key=\"$key\"")
            assertTrue("preference $key must exist", preference != preferenceXml)
            assertTrue(
                "preference $key must use $resource",
                preferenceXml.contains("app:defaultValue=\"@string/$resource\"") &&
                    dataStore.contains("R.string.$resource"),
            )
        }

        assertFalse(preferenceXml.contains("speedTestServerListURL"))
        assertFalse(preferenceXml.contains("speedTestFallbackServerListURL"))
    }

    @Test
    fun desktopBackupOnlyUpdatesFieldsThatArePresentAndValid() {
        val existing = mapOf(
            Key.SPEED_TEST_MODE to SpeedTestSettings.MODE_UPLOAD,
            Key.SPEED_TEST_TIMEOUT_MS to "9000",
            Key.SIMPLE_DOWNLOAD_URL to "https://example.com/existing.bin",
        )

        assertEquals(existing, existing + SpeedTestSettings.desktopBackupUpdates(emptyMap()))
        assertEquals(
            existing,
            existing + SpeedTestSettings.desktopBackupUpdates(
                mapOf(
                    "speed_test_mode" to "invalid",
                    "speed_test_timeout_ms" to "0",
                    "simple_dl_url" to "file:///tmp/test.bin",
                ),
            ),
        )

        val imported = existing + SpeedTestSettings.desktopBackupUpdates(
            mapOf(
                "speed_test_mode" to SpeedTestSettings.MODE_SIMPLE_DOWNLOAD,
                "speed_test_timeout_ms" to "5000",
                "simple_dl_url" to " https://example.com/imported.bin ",
            ),
        )
        assertEquals(SpeedTestSettings.MODE_SIMPLE_DOWNLOAD, imported[Key.SPEED_TEST_MODE])
        assertEquals("5000", imported[Key.SPEED_TEST_TIMEOUT_MS])
        assertEquals("https://example.com/imported.bin", imported[Key.SIMPLE_DOWNLOAD_URL])
    }

    @Test
    fun urlLatencyConcurrencyIsNotASpeedTestSetting() {
        val settingsSource = sourceFile(
            "src/main/java/io/nekohasekai/sagernet/TestSettings.kt",
        ).readText()
        assertFalse(settingsSource.contains("CONNECTION_TEST_CONCURRENT"))
        assertFalse(settingsSource.contains("connectionTestConcurrent"))
    }

    private fun defaultResources(): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(sourceFile("src/main/res/values/test_settings_defaults.xml"))
        val strings = document.getElementsByTagName("string")
        return buildMap {
            for (index in 0 until strings.length) {
                val node = strings.item(index)
                put(node.attributes.getNamedItem("name").nodeValue, node.textContent.trim())
            }
        }
    }

    private fun <T : Any> proxy(
        values: MutableMap<String, Any>,
        key: String,
        default: T,
    ) = PreferenceProxy(
        name = key,
        defaultValue = { default },
        getter = { name, fallback -> values[name] as? T ?: fallback },
        setter = { name, value -> values[name] = value },
    )

    private fun sourceFile(relative: String): File {
        return sequenceOf(File(relative), File("app", relative))
            .firstOrNull(File::isFile)
            ?: error("Cannot locate project file: $relative")
    }
}
