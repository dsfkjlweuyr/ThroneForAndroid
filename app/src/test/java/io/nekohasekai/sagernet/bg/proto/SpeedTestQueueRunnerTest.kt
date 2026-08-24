package io.nekohasekai.sagernet.bg.proto

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedTestQueueRunnerTest {

    @Test
    fun nodesRunSeriallyAndFailureDoesNotStopQueue() = runBlocking {
        val profiles = listOf(1L, 2L, 3L)
        val events = mutableListOf<String>()
        var active = 0
        var maximumActive = 0
        val runner = SpeedTestQueueRunner(
            sessionFactory = { profile ->
                fakeSession(
                    run = { onSample ->
                        active++
                        maximumActive = maxOf(maximumActive, active)
                        events += "start-$profile"
                        try {
                            if (profile == 2L) error("expected failure")
                            snapshot(profile, done = true).also(onSample)
                        } finally {
                            events += "end-$profile"
                            active--
                        }
                    },
                )
            },
            failureSnapshot = { profile, error -> snapshot(profile, true, error.message.orEmpty()) },
        )

        val results = runner.run(profiles) { _, _, _ -> }

        assertEquals(1, maximumActive)
        assertEquals(
            listOf("start-1", "end-1", "start-2", "end-2", "start-3", "end-3"),
            events,
        )
        assertEquals(3, results.size)
        assertTrue(results[1].error.contains("expected failure"))
    }

    @Test
    fun cancelStopsCurrentSessionAndRemainingQueue() = runBlocking {
        val profiles = listOf(1L, 2L)
        var cancelled = false
        var created = 0
        lateinit var runner: SpeedTestQueueRunner<Long>
        runner = SpeedTestQueueRunner(
            sessionFactory = {
                created++
                fakeSession(
                    run = {
                        runner.cancel()
                        throw CancellationException("cancelled")
                    },
                    cancelAction = { cancelled = true },
                )
            },
            failureSnapshot = { profile, error -> snapshot(profile, true, error.message.orEmpty()) },
        )

        try {
            runner.run(profiles) { _, _, _ -> }
        } catch (_: CancellationException) {
        }

        assertTrue(cancelled)
        assertEquals(1, created)
    }

    private fun snapshot(profileId: Long, done: Boolean, error: String = "") = SpeedTestSnapshot(
        profileId = profileId,
        profileName = "profile-$profileId",
        mode = "download_upload",
        stage = if (error.isEmpty()) SpeedTestQueueRunner.STAGE_COMPLETE else SpeedTestQueueRunner.STAGE_ERROR,
        error = error,
        done = done,
    )

    private fun fakeSession(
        run: suspend ((SpeedTestSnapshot) -> Unit) -> SpeedTestSnapshot,
        cancelAction: () -> Unit = {},
    ) = object : SpeedTestNodeSession {
        override suspend fun run(onSample: (SpeedTestSnapshot) -> Unit) = run(onSample)
        override fun cancel() = cancelAction()
        override fun close() = Unit
    }
}
