package io.nekohasekai.sagernet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class SpeedTestAndroidContractTest {

    @Test
    fun nativeSessionReceivesAllFourModeSettingsAndIsPolled() {
        val source = source("main/java/io/nekohasekai/sagernet/bg/proto/SpeedTestRunner.kt")
        val settings = source("main/java/io/nekohasekai/sagernet/TestSettings.kt")
        assertTrue(source.contains("Libcore.newSpeedTestSession("))
        assertTrue(source.contains("DataStore.speedTestMode"))
        assertTrue(source.contains("DataStore.speedTestTimeoutMs"))
        assertTrue(source.contains("DataStore.speedTestServerListURL"))
        assertTrue(source.contains("DataStore.speedTestFallbackServerListURL"))
        assertTrue(source.contains("DataStore.simpleDownloadURL"))
        assertTrue(source.contains("session.result.toSnapshot()"))
        assertTrue(source.contains("delay(SAMPLE_INTERVAL_MS)"))
        listOf("download_upload", "download", "upload", "simple_download").forEach { mode ->
            assertTrue("missing speed-test mode $mode", settings.contains("\"$mode\""))
        }
    }

    @Test
    fun uiUsesDedicatedResultsAndLifecycleCancellation() {
        val source = source("main/java/io/nekohasekai/sagernet/ui/ConfigurationFragment.kt")
        val lifecycle = source
            .substringAfter("override fun onDestroy()")
            .substringBefore("override fun onKeyDown")
        val speedTest = source
            .substringAfter("private fun speedTest()")
            .substringBefore("private fun formatSpeedTestSnapshot")
        val formatter = source
            .substringAfter("private fun formatSpeedTestSnapshot")
            .substringBefore("inner class TestDialog")
        assertTrue(source.contains("confirmSpeedTest()"))
        assertTrue(source.contains("sessionFactory = ::AndroidSpeedTestSession"))
        assertTrue(lifecycle.contains("speedTestRunner?.cancel()"))
        assertTrue(lifecycle.contains("speedTestJob?.cancel()"))
        assertTrue(lifecycle.contains("if (speedTestHidden && speedTestJob != null)"))
        assertTrue(lifecycle.contains("speedTestDialog?.show()"))
        assertTrue(speedTest.contains("formatSpeedTestSnapshot(sample)"))
        assertTrue(speedTest.contains("ConnectionTestNotification("))
        assertTrue(speedTest.contains("SpeedTestOutcome.completedOrNull("))
        assertTrue(speedTest.contains("updateSpeedTestResult("))
        assertTrue(speedTest.contains("completedSpeedTestCount(index, total, sample.done)"))
        assertTrue(speedTest.contains("binding.progressLinear.setProgressCompat(completed, true)"))
        assertTrue(speedTest.contains("dialog.dismiss()"))
        assertFalse(speedTest.contains("speed_test_finished_summary"))
        assertFalse(speedTest.contains("results.forEach"))
        listOf(
            "sample.downloadBitsPerSecond",
            "sample.uploadBitsPerSecond",
            "sample.cancelled",
            "sample.error",
        ).forEach { assertTrue("missing UI/result mapping for $it", speedTest.contains(it)) }
        listOf(
            "STAGE_DISCOVERY",
            "STAGE_LATENCY",
            "STAGE_DOWNLOAD",
            "STAGE_UPLOAD",
            "STAGE_COMPLETE",
            "STAGE_CANCELLED",
            "STAGE_ERROR",
            "snapshot.serverName",
            "snapshot.latencyMs",
        ).forEach { assertTrue("missing snapshot rendering for $it", formatter.contains(it)) }
        assertTrue(source.contains("android.R.attr.textColorSecondary"))
        assertTrue(source.contains("speedTestResultText(proxyEntity)"))
        assertTrue(source.contains("SpeedTestDirection.DOWNLOAD -> \"↓\""))
        assertTrue(source.contains("SpeedTestDirection.UPLOAD -> \"↑\""))
        assertFalse(speedTest.contains(".ping ="))
    }

    @Test
    fun dedicatedResultsArePersistedClearedAndMigrated() {
        val entity = source("main/java/io/nekohasekai/sagernet/database/ProxyEntity.kt")
        val database = source("main/java/io/nekohasekai/sagernet/database/SagerDatabase.kt")
        assertTrue(entity.contains("speedTestMode: String"))
        assertTrue(entity.contains("speedTestDownloadBitsPerSecond: Long"))
        assertTrue(entity.contains("speedTestUploadBitsPerSecond: Long"))
        assertTrue(entity.contains("@ColumnInfo(defaultValue = \"''\")"))
        assertTrue(entity.contains("@ColumnInfo(defaultValue = \"0\")"))
        assertTrue(entity.contains("output.writeInt(1)"))
        assertTrue(entity.contains("if (version >= 1)"))
        assertTrue(entity.contains("fun updateSpeedTestResult("))
        assertTrue(entity.contains("fun clearTestResults(groupId: Long)"))
        assertTrue(entity.contains("speedTestMode = ''"))
        assertTrue(entity.contains("speedTestDownloadBitsPerSecond = 0"))
        assertTrue(entity.contains("speedTestUploadBitsPerSecond = 0"))
        assertTrue(database.contains("version = 9"))
        assertTrue(database.contains("AutoMigration(from = 8, to = 9)"))
    }

    @Test
    fun menuIsRewiredWithDataUsageConfirmation() {
        val menu = source("main/res/menu/add_profile_menu.xml")
        val layout = source("main/res/layout/layout_progress_list.xml")
        val strings = source("main/res/values/strings.xml")
        val urlTestPosition = menu.indexOf("android:id=\"@+id/action_connection_url_test\"")
        val speedTestPosition = menu.indexOf("android:id=\"@+id/action_speed_test_group\"")
        assertTrue(menu.contains("android:id=\"@+id/action_speed_test_group\""))
        assertTrue(menu.contains("android:title=\"@string/speed_test_group\""))
        assertTrue(urlTestPosition >= 0)
        assertTrue(speedTestPosition > urlTestPosition)
        assertTrue(layout.contains("android:id=\"@+id/progress_linear\""))
        assertTrue(layout.contains("android:visibility=\"gone\""))
        assertTrue(strings.contains("name=\"speed_test_confirm_message\""))
    }

    @Test
    fun legacyDirectPingCodeResourcesAndMenuIdsAreAbsent() {
        val deprecatedSymbols = listOf(
            "pingTest(",
            "canTCPing(",
            "canICMPing(",
            "action_connection_tcp_ping",
            "action_connection_icmp_ping",
            "connection_test_tcp_ping",
            "connection_test_icmp_ping",
        )
        val productionFiles = File("src/main").walkTopDown()
            .filter { it.isFile && it.extension in setOf("java", "kt", "xml") }
            .toList()

        deprecatedSymbols.forEach { symbol ->
            assertFalse(
                "deprecated direct Ping symbol remains in production: $symbol",
                productionFiles.any { it.readText().contains(symbol) },
            )
        }
    }

    @Test
    fun everyLocaleDefinesTranslatableConnectionAndSpeedTestStrings() {
        val requiredKeys = setOf(
            "connection_test_url",
            "connection_test_url_test",
            "speed_test_settings",
            "speed_test_mode",
            "speed_test_mode_download_upload",
            "speed_test_mode_download",
            "speed_test_mode_upload",
            "speed_test_mode_simple_download",
            "speed_test_timeout_ms",
            "speed_test_timeout_invalid",
            "simple_download_url",
            "speed_test_url_invalid",
            "speed_test_group",
            "speed_test_confirm_title",
            "speed_test_confirm_message",
            "speed_test_stage_pending",
            "speed_test_stage_discovery",
            "speed_test_stage_latency",
            "speed_test_stage_download",
            "speed_test_stage_upload",
            "speed_test_stage_complete",
            "speed_test_stage_cancelled",
            "speed_test_stage_error",
            "speed_test_latency_format",
            "speed_test_server_format",
            "speed_test_download_format",
            "speed_test_upload_format",
            "speed_test_rate_mbps",
        )
        val stringFiles = File("src/main/res").listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values") }
            .map { File(it, "strings.xml") }
            .filter(File::isFile)

        assertTrue("no localized strings.xml files found", stringFiles.isNotEmpty())
        stringFiles.forEach { stringsFile ->
            val strings = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(stringsFile)
                .getElementsByTagName("string")
            val resources = (0 until strings.length).map { strings.item(it).attributes }
            val names = resources.map { it.getNamedItem("name").nodeValue }

            assertEquals(
                "duplicate string keys in ${stringsFile.parentFile.name}",
                names.toSet().size,
                names.size,
            )
            assertTrue(
                "missing connection/speed-test strings in ${stringsFile.parentFile.name}: " +
                    (requiredKeys - names.toSet()).sorted(),
                names.containsAll(requiredKeys),
            )
            resources.filter { it.getNamedItem("name").nodeValue in requiredKeys }.forEach { attributes ->
                assertFalse(
                    "connection/speed-test string is not translatable in ${stringsFile.parentFile.name}: " +
                        attributes.getNamedItem("name").nodeValue,
                    attributes.getNamedItem("translatable")?.nodeValue == "false",
                )
            }
        }
    }

    @Test
    fun baselineAndChineseMenuTranslationsUseCurrentTerminology() {
        val expected = mapOf(
            "values" to Triple("Latency test URL", "URL test this group", "Speed test this group"),
            "values-zh-rCN" to Triple("延迟测试 URL", "URL 测试本组", "速度测试本组"),
            "values-zh-rHK" to Triple("延遲測試 URL", "URL 測試本組", "速度測試本組"),
            "values-zh-rTW" to Triple("延遲測試 URL", "URL 測試本組", "速度測試本組"),
        )

        expected.forEach { (directory, translations) ->
            val strings = source("main/res/$directory/strings.xml")
            assertTrue(strings.contains(">${translations.first}</string>"))
            assertTrue(strings.contains(">${translations.second}</string>"))
            assertTrue(strings.contains(">${translations.third}</string>"))
        }
    }

    @Test
    fun productionAndCurrentDocumentationDoNotUseLegacyTestDefaultsOrTerms() {
        val forbidden = listOf(
            "http://www.gstatic.com/generate_204",
            "URL Test",
            "connection_test_tcp_ping",
            "connection_test_icmp_ping",
            "action_connection_tcp_ping",
            "action_connection_icmp_ping",
        )
        val checkedFiles = buildList {
            addAll(File("src/main").walkTopDown().filter(File::isFile).toList())
            add(File("../README.md"))
            add(File("../THR_FILE_RESEARCH.md"))
            add(File("../openspec/specs/android-application/spec.md"))
            add(File("../openspec/specs/libcore-integration/spec.md"))
        }.filter(File::isFile)

        forbidden.forEach { term ->
            assertFalse(
                "legacy connection/speed-test term remains in ${checkedFiles.firstOrNull { it.readText().contains(term) }}: $term",
                checkedFiles.any { it.readText().contains(term) },
            )
        }
        assertFalse(
            "legacy URL-test concurrency default remains in production resources",
            File("src/main/res").walkTopDown()
                .filter { it.isFile && it.extension == "xml" }
                .any { it.readText().contains(">5</integer>") && it.readText().contains("connection_test") },
        )
    }

    @Test
    fun urlTestQueueResultClearingAndUnavailableDeletionRemainWired() {
        val fragment = source("main/java/io/nekohasekai/sagernet/ui/ConfigurationFragment.kt")
        val urlTest = fragment
            .substringAfter("fun urlTest()")
            .substringBefore("inner class GroupPagerAdapter")
        val actions = fragment
            .substringAfter("R.id.action_connection_test_clear_results")
            .substringBefore("R.id.action_remove_duplicate")

        assertTrue(urlTest.contains("repeat(DataStore.connectionTestConcurrent)"))
        assertTrue(urlTest.contains("val urlTest = UrlTest()"))
        assertTrue(urlTest.contains("profile.ping = result"))
        assertTrue(urlTest.contains("ProfileManager.updateProfile(it)"))
        assertTrue(urlTest.contains("GroupManager.postReload(DataStore.currentGroupId())"))
        assertTrue(urlTest.contains("DataStore.runningTest = false"))

        assertTrue(actions.contains("SagerDatabase.proxyDao.clearTestResults(DataStore.currentGroupId())"))
        assertTrue(actions.contains("getCurrentGroupFragment()?.adapter?.clearTestResults()"))
        assertTrue(actions.contains("profile.status != 0 && profile.status != 1"))
        assertTrue(actions.contains("ProfileManager.deleteProfile2("))
    }

    private fun source(relativePath: String): String = File("src/$relativePath").readText()
}
