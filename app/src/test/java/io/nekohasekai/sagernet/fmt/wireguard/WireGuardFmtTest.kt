package io.nekohasekai.sagernet.fmt.wireguard

import com.esotericsoftware.kryo.io.ByteBufferOutput
import io.nekohasekai.sagernet.fmt.KryoConverters
import moe.matsuri.nb4a.utils.JavaUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class WireGuardFmtTest {

    @Test
    fun buildEndpointMapsCompleteFieldsAndDualStackAllowedIps() {
        val endpoint = buildSingBoxEndpointWireGuardBean(completeBean("[0, 1, 2]"))

        assertEquals("wireguard", endpoint.type)
        assertEquals(listOf("10.0.0.2/32", "fd00::2/128"), endpoint.address)
        assertTrue(TEST_PRIVATE_KEY == endpoint.private_key)
        assertEquals(1380, endpoint.mtu)
        assertEquals(51821, endpoint.listen_port)

        assertEquals(1, endpoint.peers.size)
        val peer = endpoint.peers.single()
        assertEquals("198.51.100.10", peer.address)
        assertEquals(51820, peer.port)
        assertEquals(TEST_PUBLIC_KEY, peer.public_key)
        assertTrue(TEST_PRE_SHARED_KEY == peer.pre_shared_key)
        assertEquals(listOf("0.0.0.0/0", "::/0"), peer.allowed_ips)
        assertEquals(25, peer.persistent_keepalive_interval)
        assertEquals("AAEC", peer.reserved)
    }

    @Test
    fun buildEndpointOmitsOptionalZeroAndBlankFieldsFromJson() {
        val bean = completeBean("").apply {
            serverAddress = ""
            serverPort = 0
            peerPreSharedKey = ""
            mtu = 0
            listenPort = 0
            persistentKeepaliveInterval = 0
        }

        val endpointJson = JavaUtil.gson.toJsonTree(buildSingBoxEndpointWireGuardBean(bean)).asJsonObject
        assertFalse(endpointJson.has("mtu"))
        assertFalse(endpointJson.has("listen_port"))

        val peerJson = endpointJson.getAsJsonArray("peers").single().asJsonObject
        assertFalse(peerJson.has("address"))
        assertFalse(peerJson.has("port"))
        assertFalse(peerJson.has("pre_shared_key"))
        assertFalse(peerJson.has("persistent_keepalive_interval"))
        assertFalse(peerJson.has("reserved"))
    }

    @Test
    fun genReservedConvertsThreeByteListFormsToBase64() {
        assertEquals("AAEC", genReserved("[0, 1, 2]"))
        assertEquals("AAEC", genReserved("0,\n1 2"))
    }

    @Test
    fun genReservedPreservesExistingBase64() {
        assertEquals("AAEC", genReserved("AAEC"))
    }

    @Test
    fun wireGuardBeanDeserializesVersionTwoWithNewFieldsDefaulted() {
        val bean = KryoConverters.deserialize(WireGuardBean(), versionTwoFixture())

        assertEquals("198.51.100.10", bean.serverAddress)
        assertEquals(51820, bean.serverPort)
        assertEquals("10.0.0.2/32", bean.localAddress)
        assertTrue(TEST_PRIVATE_KEY == bean.privateKey)
        assertEquals(TEST_PUBLIC_KEY, bean.peerPublicKey)
        assertTrue(TEST_PRE_SHARED_KEY == bean.peerPreSharedKey)
        assertEquals(1380, bean.mtu)
        assertEquals("AAEC", bean.reserved)
        assertEquals(0, bean.listenPort)
        assertEquals(0, bean.persistentKeepaliveInterval)
    }

    private fun completeBean(reservedValue: String) = WireGuardBean().apply {
        serverAddress = "198.51.100.10"
        serverPort = 51820
        localAddress = "10.0.0.2/32, fd00::2/128"
        privateKey = TEST_PRIVATE_KEY
        peerPublicKey = TEST_PUBLIC_KEY
        peerPreSharedKey = TEST_PRE_SHARED_KEY
        mtu = 1380
        reserved = reservedValue
        listenPort = 51821
        persistentKeepaliveInterval = 25
    }

    private fun versionTwoFixture(): ByteArray {
        val bytes = ByteArrayOutputStream()
        val output = ByteBufferOutput(bytes)

        // WireGuardBean v2 payload. This deliberately does not call the current serializer.
        output.writeInt(2)
        output.writeString("198.51.100.10")
        output.writeInt(51820)
        output.writeString("10.0.0.2/32")
        output.writeString(TEST_PRIVATE_KEY)
        output.writeString(TEST_PUBLIC_KEY)
        output.writeString(TEST_PRE_SHARED_KEY)
        output.writeInt(1380)
        output.writeString("AAEC")

        // AbstractBean extra payload.
        output.writeInt(1)
        output.writeString("legacy-wireguard-test")
        output.writeString("")
        output.writeString("")
        output.flush()
        output.close()
        return bytes.toByteArray()
    }

    private companion object {
        // Deliberately invalid-for-production, deterministic fixture material.
        const val TEST_PRIVATE_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        const val TEST_PUBLIC_KEY = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="
        const val TEST_PRE_SHARED_KEY = "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC="
    }
}
