package io.nekohasekai.sagernet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ServiceFailureCleanupContractTest {

    @Test
    fun serviceStopPreservesOriginalFailureAndContainsCleanupErrors() {
        val source = source("main/java/io/nekohasekai/sagernet/bg/BaseService.kt")
        val stopRunner = source
            .substringAfter("fun stopRunner(restart: Boolean = false, msg: String? = null)")
            .substringBefore("fun persistStats()")

        assertTrue(stopRunner.contains("val originalMessage = msg"))
        assertTrue(stopRunner.contains("data.changeState(State.Stopped, originalMessage)"))
        assertTrue(stopRunner.contains("stage=cleanup failed"))
        assertTrue(stopRunner.contains("originalMessagePreserved="))
        assertTrue(stopRunner.contains("stage=stopped"))
        assertTrue(stopRunner.contains("data.notification = null"))
        assertTrue(stopRunner.contains("data.closeReceiverRegistered = false"))
        assertTrue(stopRunner.contains("data.proxy = null"))
        assertTrue(stopRunner.contains("process-cleanup-boundary"))
        assertFalse(stopRunner.contains("throw cleanupError"))
    }

    @Test
    fun closeLayersFinishRemainingResourcesBeforeRethrowingToServiceBoundary() {
        val service = source("main/java/io/nekohasekai/sagernet/bg/BaseService.kt")
        val killProcesses = service
            .substringAfter("suspend fun killProcesses(): Throwable?")
            .substringBefore("fun stopRunner(")
        val box = source("main/java/io/nekohasekai/sagernet/bg/proto/BoxInstance.kt")
        val proxy = source("main/java/io/nekohasekai/sagernet/bg/proto/ProxyInstance.kt")
        val vpn = source("main/java/io/nekohasekai/sagernet/bg/VpnService.kt")

        assertTrue(killProcesses.contains("recordCleanupFailure(\"proxy-close\", error)"))
        assertTrue(killProcesses.contains("recordCleanupFailure(\"wake-lock-release\", error)"))
        assertTrue(killProcesses.contains("recordCleanupFailure(\"network-listener-stop\", error)"))
        assertTrue(killProcesses.contains("return cleanupError"))
        assertFalse(killProcesses.contains("throw error"))

        assertTrue(box.contains("recordCloseError(error)"))
        assertTrue(box.contains("stage=close-processes failed"))
        assertTrue(box.contains("closeError?.let { throw it }"))
        assertTrue(proxy.contains("looper?.stop()"))
        assertTrue(proxy.indexOf("looper?.stop()") < proxy.indexOf("closeError?.let { throw it }"))
        assertTrue(vpn.contains("override suspend fun killProcesses(): Throwable?"))
        assertTrue(vpn.contains("super.killProcesses()?.let"))
        assertTrue(vpn.contains("return cleanupError"))
        assertFalse(vpn.contains("throw error"))
    }

    @Test
    fun goWrapperUsesTypedClosedErrorAndReturnsUnknownCloseFailures() {
        val source = File("../libcore/box.go").readText()
        val close = source
            .substringAfter("func (b *BoxInstance) Close() (err error)")
            .substringBefore("func (b *BoxInstance) Sleep()")

        assertTrue(source.contains("boxStateStartFailed"))
        assertTrue(close.contains("errors.Is(err, os.ErrClosed)"))
        assertTrue(close.contains("normalized already-closed"))
        assertTrue(close.contains("return err"))
        assertFalse(close.contains("strings.Contains"))
    }

    private fun source(relativePath: String): String = File("src/$relativePath").readText()
}
