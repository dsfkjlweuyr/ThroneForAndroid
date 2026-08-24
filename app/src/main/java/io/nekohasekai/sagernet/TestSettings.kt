package io.nekohasekai.sagernet

import java.net.URI

/**
 * Android-facing speed-test setting values shared by preferences, backup import,
 * and the libcore adapter. The mode strings intentionally match libcore's
 * gomobile API.
 */
object SpeedTestSettings {

    const val MODE_DOWNLOAD_UPLOAD = "download_upload"
    const val MODE_DOWNLOAD = "download"
    const val MODE_UPLOAD = "upload"
    const val MODE_SIMPLE_DOWNLOAD = "simple_download"

    val modes = setOf(
        MODE_DOWNLOAD_UPLOAD,
        MODE_DOWNLOAD,
        MODE_UPLOAD,
        MODE_SIMPLE_DOWNLOAD,
    )

    fun isValidMode(value: String): Boolean = value in modes

    fun isValidTimeout(value: String): Boolean = value.toIntOrNull()?.let { it > 0 } == true

    fun isValidHttpUrl(value: String): Boolean = runCatching {
        val uri = URI(value.trim())
        (uri.scheme.equals("http", ignoreCase = true) ||
            uri.scheme.equals("https", ignoreCase = true)) && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    /**
     * Builds only the writes explicitly represented by a valid desktop backup
     * field. A missing or invalid field never supplies a default and therefore
     * cannot overwrite an Android value already persisted under the target key.
     */
    internal fun desktopBackupUpdates(settings: Map<String, String>): Map<String, String> = buildMap {
        settings["speed_test_mode"]
            ?.takeIf(::isValidMode)
            ?.let { put(Key.SPEED_TEST_MODE, it) }
        settings["speed_test_timeout_ms"]
            ?.takeIf(::isValidTimeout)
            ?.let { put(Key.SPEED_TEST_TIMEOUT_MS, it) }
        settings["simple_dl_url"]
            ?.trim()
            ?.takeIf(::isValidHttpUrl)
            ?.let { put(Key.SIMPLE_DOWNLOAD_URL, it) }
    }
}
