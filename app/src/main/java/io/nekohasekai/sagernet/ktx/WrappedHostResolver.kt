package io.nekohasekai.sagernet.ktx

import java.util.Collections
import java.util.IdentityHashMap

internal data class WrappedHostResolution<Host : Any>(
    val host: Host?,
    val wrapperDepth: Int,
    val loopDetected: Boolean,
)

internal fun <Node : Any, Host : Any> resolveWrappedHost(
    initial: Node,
    hostOrNull: (Node) -> Host?,
    baseOrNull: (Node) -> Node?,
): WrappedHostResolution<Host> {
    val visited = Collections.newSetFromMap(IdentityHashMap<Node, Boolean>())
    var current = initial
    var wrapperDepth = 0

    while (visited.add(current)) {
        hostOrNull(current)?.let {
            return WrappedHostResolution(it, wrapperDepth, loopDetected = false)
        }
        current = baseOrNull(current)
            ?: return WrappedHostResolution(null, wrapperDepth, loopDetected = false)
        wrapperDepth++
    }

    return WrappedHostResolution(null, wrapperDepth, loopDetected = true)
}
