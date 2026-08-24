package io.nekohasekai.sagernet.bg.proto

import android.os.SystemClock
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.bg.GuardedProcessPool
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.fmt.buildConfig
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.tryResume
import io.nekohasekai.sagernet.ktx.tryResumeWithException
import kotlinx.coroutines.delay
import libcore.Libcore
import moe.matsuri.nb4a.net.LocalResolverImpl
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.suspendCoroutine

class TestInstance(profile: ProxyEntity, val link: String, private val timeout: Int) :
    BoxInstance(profile) {

    private val traceId = traceSequence.incrementAndGet()
    private val traceName = profile.displayName()

    private fun trace(stage: String, message: String) {
        Logs.d("URLTestTrace ktId=$traceId profileId=${profile.id} profile=$traceName stage=$stage $message")
    }

    suspend fun doTest(): Int {
        return suspendCoroutine { c ->
            val totalStarted = SystemClock.elapsedRealtime()
            processes = GuardedProcessPool {
                Logs.w("URLTestTrace ktId=$traceId profileId=${profile.id} profile=$traceName stage=plugin-exit elapsed=${SystemClock.elapsedRealtime() - totalStarted}ms error=${it.readableMessage}")
                c.tryResumeWithException(it)
            }
            runOnDefaultDispatcher {
                var stage = "init"
                try {
                    trace(
                        "begin",
                        "mode=isolated-box currentProfile=${DataStore.currentProfile} " +
                                "isCurrent=${profile.id == DataStore.currentProfile} " +
                                "serviceState=${DataStore.serviceState} network=${SagerNet.underlyingNetwork} " +
                                "link=$link timeout=${timeout}ms thread=${Thread.currentThread().name}"
                    )
                    var started = SystemClock.elapsedRealtime()
                    init()
                    trace("init", "ok elapsed=${SystemClock.elapsedRealtime() - started}ms")

                    stage = "launch"
                    started = SystemClock.elapsedRealtime()
                    launch()
                    trace(
                        "launch",
                        "ok elapsed=${SystemClock.elapsedRealtime() - started}ms plugins=${processes.processCount}"
                    )

                    if (processes.processCount > 0) {
                        stage = "plugin-wait"
                        started = SystemClock.elapsedRealtime()
                        delay(500)
                        trace(
                            "plugin-wait",
                            "ok elapsed=${SystemClock.elapsedRealtime() - started}ms plugins=${processes.processCount}"
                        )
                    }

                    stage = "core-urltest"
                    started = SystemClock.elapsedRealtime()
                    trace("core-urltest", "begin")
                    val latency = Libcore.urlTest(box, link, timeout)
                    trace(
                        "core-urltest",
                        "ok elapsed=${SystemClock.elapsedRealtime() - started}ms latency=${latency}ms"
                    )
                    c.tryResume(latency)
                } catch (e: Exception) {
                    Logs.w(
                        "URLTestTrace ktId=$traceId profileId=${profile.id} profile=$traceName " +
                                "stage=$stage failed totalElapsed=${SystemClock.elapsedRealtime() - totalStarted}ms " +
                                "error=${e.readableMessage}"
                    )
                    c.tryResumeWithException(e)
                } finally {
                    val closeStarted = SystemClock.elapsedRealtime()
                    runCatching { close() }
                        .onFailure {
                            Logs.w(
                                "URLTestTrace ktId=$traceId profileId=${profile.id} profile=$traceName " +
                                        "stage=close failed error=${it.readableMessage}"
                            )
                        }
                    trace(
                        "close",
                        "done elapsed=${SystemClock.elapsedRealtime() - closeStarted}ms " +
                                "totalElapsed=${SystemClock.elapsedRealtime() - totalStarted}ms"
                    )
                }
            }
        }
    }

    protected override fun buildConfig() {
        val started = SystemClock.elapsedRealtime()
        config = buildConfig(profile, true)
        trace(
            "build-config",
            "ok elapsed=${SystemClock.elapsedRealtime() - started}ms " +
                    "externalChains=${config.externalIndex.size}"
        )
    }

    override suspend fun loadConfig() {
        // don't call destroyAllJsi here
        if (BuildConfig.DEBUG) Logs.d(config.config)
        // 测速实例用 NewTestSingBoxInstance：不注册 PlatformLogWriter，
        // 官方内核不再强制创建 CacheFile/ClashServer（见 libcore/box.go 批注）。
        val started = SystemClock.elapsedRealtime()
        box = Libcore.newTestSingBoxInstance(config.config, LocalResolverImpl)
        trace("create-box", "ok elapsed=${SystemClock.elapsedRealtime() - started}ms")
    }

    private companion object {
        private val traceSequence = AtomicLong()
    }

}
