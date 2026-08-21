package io.nekohasekai.sagernet.fmt.wireguard

import com.esotericsoftware.kryo.io.ByteBufferOutput
import io.nekohasekai.sagernet.fmt.KryoConverters
import moe.matsuri.nb4a.utils.JavaUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun parseWireGuardConfigSplitsPeersAndMapsIpv4AndIpv6Endpoints() {
        val beans = parseWireGuardConfig(
            """
                [Interface]
                Address = 10.0.0.2/32, fd00::2/128
                PrivateKey = $TEST_PRIVATE_KEY
                MTU = 1380
                ListenPort = 51821

                [Peer]
                Endpoint = 198.51.100.10:51820
                PublicKey = $TEST_PUBLIC_KEY
                PresharedKey = $TEST_PRE_SHARED_KEY
                PersistentKeepalive = 25
                Reserved = 0, 1, 2

                [Peer]
                Endpoint = [2001:db8::10]:51822
                PublicKey = $TEST_SECOND_PUBLIC_KEY

                [Peer]
                Endpoint = invalid.example:51823
            """.trimIndent()
        )

        assertEquals(2, beans.size)
        val ipv4 = beans[0]
        assertEquals("10.0.0.2/32\nfd00::2/128", ipv4.localAddress)
        assertTrue(TEST_PRIVATE_KEY == ipv4.privateKey)
        assertEquals(1380, ipv4.mtu)
        assertEquals(51821, ipv4.listenPort)
        assertEquals("198.51.100.10", ipv4.serverAddress)
        assertEquals(51820, ipv4.serverPort)
        assertEquals(TEST_PUBLIC_KEY, ipv4.peerPublicKey)
        assertTrue(TEST_PRE_SHARED_KEY == ipv4.peerPreSharedKey)
        assertEquals(25, ipv4.persistentKeepaliveInterval)
        assertEquals("0, 1, 2", ipv4.reserved)

        val ipv6 = beans[1]
        assertEquals("2001:db8::10", ipv6.serverAddress)
        assertEquals(51822, ipv6.serverPort)
        assertEquals(TEST_SECOND_PUBLIC_KEY, ipv6.peerPublicKey)
        assertEquals(0, ipv6.persistentKeepaliveInterval)
    }

    @Test
    fun endpointJsonRoundTripPreservesWireGuardFields() {
        val endpoint = buildSingBoxEndpointWireGuardBean(completeBean("[0, 1, 2]")).apply {
            tag = "wireguard-round-trip"
        }
        val json = JavaUtil.gson.toJsonTree(endpoint).asJsonObject

        val bean = requireNotNull(parseWireGuardEndpoint(json))

        assertEquals("wireguard-round-trip", bean.name)
        assertEquals("10.0.0.2/32\nfd00::2/128", bean.localAddress)
        assertTrue(TEST_PRIVATE_KEY == bean.privateKey)
        assertEquals(1380, bean.mtu)
        assertEquals(51821, bean.listenPort)
        assertEquals("198.51.100.10", bean.serverAddress)
        assertEquals(51820, bean.serverPort)
        assertEquals(TEST_PUBLIC_KEY, bean.peerPublicKey)
        assertTrue(TEST_PRE_SHARED_KEY == bean.peerPreSharedKey)
        assertEquals(25, bean.persistentKeepaliveInterval)
        assertEquals("AAEC", bean.reserved)
    }

    @Test
    fun endpointJsonAcceptsStringNumbersAndRejectsMalformedPeers() {
        val json = JavaUtil.gson.fromJson(
            """
                {
                  "type": "wireguard",
                  "address": "10.0.0.2/32",
                  "private_key": "$TEST_PRIVATE_KEY",
                  "mtu": "1380",
                  "listen_port": "51821",
                  "peers": [{
                    "address": "198.51.100.10",
                    "port": "51820",
                    "public_key": "$TEST_PUBLIC_KEY",
                    "persistent_keepalive_interval": "25",
                    "reserved": [0, 1, 2]
                  }]
                }
            """.trimIndent(),
            com.google.gson.JsonObject::class.java
        )

        val bean = requireNotNull(parseWireGuardEndpoint(json))
        assertEquals(1380, bean.mtu)
        assertEquals(51821, bean.listenPort)
        assertEquals(51820, bean.serverPort)
        assertEquals(25, bean.persistentKeepaliveInterval)
        assertEquals("0, 1, 2", bean.reserved)

        assertNull(parseWireGuardEndpoint(json.deepCopy().apply { remove("peers") }))
        assertNull(parseWireGuardEndpoint(json.deepCopy().apply { addProperty("peers", "wrong") }))
        assertNull(parseWireGuardEndpoint(json.deepCopy().apply {
            getAsJsonArray("peers").set(0, com.google.gson.JsonPrimitive("wrong"))
        }))
        assertNull(parseWireGuardEndpoint(json.deepCopy().apply {
            getAsJsonArray("peers")[0].asJsonObject.remove("public_key")
        }))

        val root = com.google.gson.JsonObject().apply {
            add("endpoints", com.google.gson.JsonArray().apply {
                add(json)
                add(com.google.gson.JsonPrimitive("wrong"))
                add(json.deepCopy().apply { remove("peers") })
            })
        }
        val endpoints = parseWireGuardEndpoints(root)
        assertEquals(1, endpoints.size)
        assertEquals(TEST_PUBLIC_KEY, endpoints.single().peerPublicKey)
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

        val resaved = KryoConverters.deserialize(
            WireGuardBean(),
            KryoConverters.serialize(bean.apply {
                listenPort = 51821
                persistentKeepaliveInterval = 25
            })
        )
        assertEquals("10.0.0.2/32", resaved.localAddress)
        assertTrue(TEST_PRIVATE_KEY == resaved.privateKey)
        assertEquals(TEST_PUBLIC_KEY, resaved.peerPublicKey)
        assertTrue(TEST_PRE_SHARED_KEY == resaved.peerPreSharedKey)
        assertEquals(1380, resaved.mtu)
        assertEquals("AAEC", resaved.reserved)
        assertEquals(51821, resaved.listenPort)
        assertEquals(25, resaved.persistentKeepaliveInterval)
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
        const val TEST_SECOND_PUBLIC_KEY = "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD="
        const val TEST_PRE_SHARED_KEY = "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC="
    }
}
