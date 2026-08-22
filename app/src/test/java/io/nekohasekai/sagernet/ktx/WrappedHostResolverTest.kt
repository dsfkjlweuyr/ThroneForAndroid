package io.nekohasekai.sagernet.ktx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WrappedHostResolverTest {

    private open class FakeContext

    private class FakeActivity : FakeContext()

    private class FakeContextWrapper(var base: FakeContext) : FakeContext()

    private fun resolve(context: FakeContext) = resolveWrappedHost(
        initial = context,
        hostOrNull = { it as? FakeActivity },
        baseOrNull = { (it as? FakeContextWrapper)?.base },
    )

    @Test
    fun themedContextWrappersResolveActivityHost() {
        val activity = FakeActivity()
        val themedContext = FakeContextWrapper(FakeContextWrapper(activity))

        val resolution = resolve(themedContext)

        assertSame(activity, resolution.host)
        assertEquals(2, resolution.wrapperDepth)
        assertFalse(resolution.loopDetected)
    }

    @Test
    fun contextWithoutActivityReturnsNoHost() {
        val resolution = resolve(FakeContextWrapper(FakeContext()))

        assertNull(resolution.host)
        assertEquals(1, resolution.wrapperDepth)
        assertFalse(resolution.loopDetected)
    }

    @Test
    fun selfReferencingWrapperDoesNotLoop() {
        val wrapper = FakeContextWrapper(FakeContext())
        wrapper.base = wrapper

        val resolution = resolve(wrapper)

        assertNull(resolution.host)
        assertEquals(1, resolution.wrapperDepth)
        assertTrue(resolution.loopDetected)
    }
}
