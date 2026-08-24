package io.nekohasekai.sagernet.bg.proto

import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.bg.GuardedProcessPool
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.fmt.buildConfig
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.readableMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import libcore.Libcore
import libcore.SpeedTestResult
import libcore.SpeedTestSession
import moe.matsuri.nb4a.net.LocalResolverImpl
import kotlin.coroutines.coroutineContext

data class SpeedTestSnapshot(
    val profileId: Long,
    val profileName: String,
    val mode: String,
    val stage: String,
    val downloadBitsPerSecond: Long = 0,
    val uploadBitsPerSecond: Long = 0,
    val downloadBytes: Long = 0,
    val uploadBytes: Long = 0,
    val latencyMs: Long = 0,
    val serverName: String = "",
    val serverCountry: String = "",
    val error: String = "",
    val cancelled: Boolean = false,
    val done: Boolean = false,
)

interface SpeedTestNodeSession : AutoCloseable {
    suspend fun run(onSample: (SpeedTestSnapshot) -> Unit): SpeedTestSnapshot
    fun cancel()
}

class SpeedTestQueueRunner<T>(
    private val sessionFactory: (T) -> SpeedTestNodeSession,
    private val failureSnapshot: (T, Exception) -> SpeedTestSnapshot,
) {
    @Volatile
    private var currentSession: SpeedTestNodeSession? = null

    suspend fun run(
        profiles: List<T>,
        onSample: (index: Int, total: Int, sample: SpeedTestSnapshot) -> Unit,
    ): List<SpeedTestSnapshot> {
        val results = ArrayList<SpeedTestSnapshot>(profiles.size)
        profiles.forEachIndexed { index, profile ->
            coroutineContext.ensureActive()
            val session = sessionFactory(profile)
            currentSession = session
            try {
                results += session.run { onSample(index, profiles.size, it) }
            } catch (e: CancellationException) {
                session.cancel()
                throw e
            } catch (e: Exception) {
                val failed = failureSnapshot(profile, e)
                onSample(index, profiles.size, failed)
                results += failed
            } finally {
                runCatching { session.close() }.onFailure { Logs.w(it) }
                currentSession = null
            }
        }
        return results
    }

    fun cancel() {
        currentSession?.cancel()
    }

    companion object {
        const val STAGE_PENDING = "pending"
        const val STAGE_DISCOVERY = "discovery"
        const val STAGE_LATENCY = "latency"
        const val STAGE_DOWNLOAD = "download"
        const val STAGE_UPLOAD = "upload"
        const val STAGE_COMPLETE = "complete"
        const val STAGE_CANCELLED = "cancelled"
        const val STAGE_ERROR = "error"
    }
}

class AndroidSpeedTestSession(private val profile: ProxyEntity) : BoxInstance(profile), SpeedTestNodeSession {
    @Volatile
    private var nativeSession: SpeedTestSession? = null

    override fun buildConfig() {
        config = buildConfig(profile, true)
    }

    override suspend fun loadConfig() = Unit

    override suspend fun run(onSample: (SpeedTestSnapshot) -> Unit): SpeedTestSnapshot {
        processes = GuardedProcessPool { error ->
            Logs.w("SpeedTest plugin failed for ${profile.displayName()}: ${error.readableMessage}")
            nativeSession?.cancel()
        }
        init()
        launchExternal()
        if (processes.processCount > 0) delay(500)
        if (BuildConfig.DEBUG) Logs.d(config.config)

        val session = Libcore.newSpeedTestSession(
            profile.id.toString(),
            config.config,
            LocalResolverImpl,
            DataStore.speedTestMode,
            DataStore.speedTestTimeoutMs,
            DataStore.speedTestServerListURL,
            DataStore.speedTestFallbackServerListURL,
            DataStore.simpleDownloadURL,
        )
        nativeSession = session
        try {
            session.start()
            while (true) {
                coroutineContext.ensureActive()
                val snapshot = session.result.toSnapshot()
                onSample(snapshot)
                if (snapshot.done) return snapshot
                delay(SAMPLE_INTERVAL_MS)
            }
        } catch (e: CancellationException) {
            session.cancel()
            throw e
        }
    }

    override fun cancel() {
        nativeSession?.cancel()
    }

    override fun close() {
        runCatching { nativeSession?.close() }.onFailure { Logs.w(it) }
        nativeSession = null
        super.close()
    }

    private fun SpeedTestResult.toSnapshot() = SpeedTestSnapshot(
        profileId = profile.id,
        profileName = profile.displayName(),
        mode = mode,
        stage = stage,
        downloadBitsPerSecond = downloadBitsPerSecond,
        uploadBitsPerSecond = uploadBitsPerSecond,
        downloadBytes = downloadBytes,
        uploadBytes = uploadBytes,
        latencyMs = latencyMs,
        serverName = serverName.orEmpty(),
        serverCountry = serverCountry.orEmpty(),
        error = error.orEmpty(),
        cancelled = cancelled,
        done = done,
    )

    private companion object {
        const val SAMPLE_INTERVAL_MS = 100L
    }
}
