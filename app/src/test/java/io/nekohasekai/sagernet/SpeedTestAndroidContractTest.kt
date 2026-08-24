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
        assertTrue(source.contains("confirmSpeedTest()"))
        assertTrue(source.contains("sessionFactory = ::AndroidSpeedTestSession"))
        assertTrue(source.contains("speedTestRunner?.cancel()"))
        assertTrue(source.contains("speedTestJob?.cancel()"))
        assertTrue(source.contains("formatSpeedTestSnapshot(sample)"))
        assertTrue(source.contains("ConnectionTestNotification("))
        assertTrue(source.contains("if (speedTestHidden && speedTestJob != null)"))
        assertTrue(source.contains("speedTestDialog?.show()"))
        assertFalse(source.substringAfter("private fun speedTest()").substringBefore("fun pingTest").contains(".ping ="))
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
