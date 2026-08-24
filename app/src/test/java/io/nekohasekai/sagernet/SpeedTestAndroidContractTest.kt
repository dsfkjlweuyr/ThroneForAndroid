package io.nekohasekai.sagernet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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
        val strings = source("main/res/values/strings.xml")
        assertTrue(menu.contains("android:id=\"@+id/action_connection_tcp_ping\""))
        assertTrue(menu.contains("android:title=\"@string/speed_test_group\""))
        assertTrue(strings.contains("name=\"speed_test_confirm_message\""))
    }

    private fun source(relativePath: String): String = File("src/$relativePath").readText()
}
