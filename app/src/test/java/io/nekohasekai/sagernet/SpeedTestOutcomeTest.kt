package io.nekohasekai.sagernet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedTestOutcomeTest {

    @Test
    fun eachModeExposesOnlyItsExpectedDirections() {
        val downloadUpload = outcome(SpeedTestSettings.MODE_DOWNLOAD_UPLOAD)
        assertEquals(
            listOf(SpeedTestDirection.DOWNLOAD, SpeedTestDirection.UPLOAD),
            downloadUpload.rates().map { it.direction },
        )
        assertEquals(
            listOf(SpeedTestDirection.DOWNLOAD),
            outcome(SpeedTestSettings.MODE_DOWNLOAD).rates().map { it.direction },
        )
        assertEquals(
            listOf(SpeedTestDirection.UPLOAD),
            outcome(SpeedTestSettings.MODE_UPLOAD).rates().map { it.direction },
        )
        assertEquals(
            listOf(SpeedTestDirection.DOWNLOAD),
            outcome(SpeedTestSettings.MODE_SIMPLE_DOWNLOAD).rates().map { it.direction },
        )
    }

    @Test
    fun onlySuccessfulCompletedSnapshotsCanReplacePersistedResults() {
        assertEquals(outcome(SpeedTestSettings.MODE_DOWNLOAD_UPLOAD), completed())
        assertNull(completed(done = false))
        assertNull(completed(cancelled = true))
        assertNull(completed(error = "network failed"))
        assertNull(completed(stage = "upload"))
        assertNull(completed(mode = "invalid"))
    }

    @Test
    fun aLaterSuccessReplacesTheWholePreviousOutcome() {
        var persisted = completed()!!
        persisted = completed(
            mode = SpeedTestSettings.MODE_UPLOAD,
            download = 0,
            upload = 99,
        )!!

        assertEquals(SpeedTestSettings.MODE_UPLOAD, persisted.mode)
        assertEquals(0L, persisted.downloadBitsPerSecond)
        assertEquals(99L, persisted.uploadBitsPerSecond)
        assertEquals(listOf(SpeedTestDirection.UPLOAD), persisted.rates().map { it.direction })
    }

    @Test
    fun completedRatesAreNeverPersistedAsNegativeValues() {
        val completed = completed(download = -1, upload = -2)!!
        assertEquals(0L, completed.downloadBitsPerSecond)
        assertEquals(0L, completed.uploadBitsPerSecond)
    }

    private fun outcome(mode: String) = SpeedTestOutcome(mode, 10, 20)

    private fun completed(
        mode: String = SpeedTestSettings.MODE_DOWNLOAD_UPLOAD,
        stage: String = "complete",
        done: Boolean = true,
        cancelled: Boolean = false,
        error: String = "",
        download: Long = 10,
        upload: Long = 20,
    ) = SpeedTestOutcome.completedOrNull(
        mode = mode,
        stage = stage,
        done = done,
        cancelled = cancelled,
        error = error,
        downloadBitsPerSecond = download,
        uploadBitsPerSecond = upload,
    )
}
