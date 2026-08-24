package io.nekohasekai.sagernet

enum class SpeedTestDirection {
    DOWNLOAD,
    UPLOAD,
}

data class SpeedTestRate(
    val direction: SpeedTestDirection,
    val bitsPerSecond: Long,
)

data class SpeedTestOutcome(
    val mode: String,
    val downloadBitsPerSecond: Long,
    val uploadBitsPerSecond: Long,
) {

    fun rates(): List<SpeedTestRate> = when (mode) {
        SpeedTestSettings.MODE_DOWNLOAD_UPLOAD -> listOf(
            SpeedTestRate(SpeedTestDirection.DOWNLOAD, downloadBitsPerSecond),
            SpeedTestRate(SpeedTestDirection.UPLOAD, uploadBitsPerSecond),
        )

        SpeedTestSettings.MODE_DOWNLOAD,
        SpeedTestSettings.MODE_SIMPLE_DOWNLOAD ->
            listOf(SpeedTestRate(SpeedTestDirection.DOWNLOAD, downloadBitsPerSecond))

        SpeedTestSettings.MODE_UPLOAD ->
            listOf(SpeedTestRate(SpeedTestDirection.UPLOAD, uploadBitsPerSecond))

        else -> emptyList()
    }

    companion object {
        fun completedOrNull(
            mode: String,
            stage: String,
            done: Boolean,
            cancelled: Boolean,
            error: String,
            downloadBitsPerSecond: Long,
            uploadBitsPerSecond: Long,
        ): SpeedTestOutcome? {
            if (!done || cancelled || error.isNotBlank() || stage != "complete") return null
            if (!SpeedTestSettings.isValidMode(mode)) return null
            return SpeedTestOutcome(
                mode = mode,
                downloadBitsPerSecond = downloadBitsPerSecond.coerceAtLeast(0),
                uploadBitsPerSecond = uploadBitsPerSecond.coerceAtLeast(0),
            )
        }
    }
}
